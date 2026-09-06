/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.taskbar

import android.content.Intent
import android.window.RemoteTransition
import androidx.compose.ui.input.key.key
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TestUtil
import com.android.quickstep.split.SplitSelectStateController
import com.android.quickstep.util.SplitTask
import com.android.quickstep.views.RecentsView
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitScreenConstants
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit tests for [RecentsViewInteractor]. */
@RunWith(AndroidJUnit4::class)
class RecentsViewInteractorTest : TaskbarBaseTestCase() {

    private val recentsView: RecentsView<*, *> = mock()
    private lateinit var recentsViewInteractor: RecentsViewInteractor
    private val splitSelectStateController: SplitSelectStateController = mock()

    @Before
    override fun setup() {
        super.setup()

        // Initialize the interactor with the mock RecentsView.
        recentsViewInteractor = RecentsViewInteractor(recentsView)
        whenever(recentsView.splitSelectController).thenReturn(splitSelectStateController)
    }

    @Test
    fun launchSplitTask_callsSplitController() {
        val taskKey1 = Task.TaskKey(1, 0, Intent(), null, 0, 0)
        val task1 = Task(taskKey1)

        val taskKey2 = Task.TaskKey(2, 0, Intent(), null, 0, 0)
        val task2 = Task(taskKey2)

        val splitTask = SplitTask(task1, task2, null)
        val remoteTransition: RemoteTransition = mock()

        recentsViewInteractor.launchSplitTask(splitTask, remoteTransition)

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            verify(splitSelectStateController)
                .launchExistingSplitPair(
                    eq(null), // launchingTaskView
                    eq(task1.key.id), // firstTaskId
                    eq(task2.key.id), // secondTaskId
                    eq(SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT), // stagePosition
                    any(), // callback
                    eq(false), // freezeTaskList
                    eq(SplitScreenConstants.SNAP_TO_2_50_50), // snapPosition
                    eq(remoteTransition), // remoteTransition
                )
        }
    }

    @Test
    fun launchSplitTask_withNullRemoteTransition_callsSplitControllerWithNull() {
        // Arrange
        val taskKey1 = Task.TaskKey(1, 0, Intent(), null, 0, 0)
        val task1 = Task(taskKey1)

        val taskKey2 = Task.TaskKey(2, 0, Intent(), null, 0, 0)
        val task2 = Task(taskKey2)

        val splitTask = SplitTask(task1, task2, null)

        recentsViewInteractor.launchSplitTask(splitTask, null)

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            verify(splitSelectStateController)
                .launchExistingSplitPair(
                    eq(null), // launchingTaskView
                    eq(task1.key.id), // firstTaskId
                    eq(task2.key.id), // secondTaskId
                    eq(SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT), // stagePosition
                    any(), // callback
                    eq(false), // freezeTaskList
                    eq(SplitScreenConstants.SNAP_TO_2_50_50), // snapPosition
                    eq(null), // remoteTransition
                )
        }
    }

    @Test
    fun launchSplitTask_whenSplitControllerIsNull_doesNothing() {
        whenever(recentsView.splitSelectController).thenReturn(null)

        val taskKey1 = Task.TaskKey(1, 0, Intent(), null, 0, 0)
        val task1 = Task(taskKey1)
        val taskKey2 = Task.TaskKey(2, 0, Intent(), null, 0, 0)
        val task2 = Task(taskKey2)
        val splitTask = SplitTask(task1, task2, null)
        val remoteTransition: RemoteTransition = mock()

        recentsViewInteractor.launchSplitTask(splitTask, remoteTransition)

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            verify(splitSelectStateController, never())
                .launchExistingSplitPair(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }
}
