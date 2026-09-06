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

package com.android.quickstep.views

import android.annotation.UserIdInt
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.widget.OverScroller
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_TOUCH_CONTROLLER_NO_INTERCEPT
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.automation.AutomationChange
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.display.DisplayController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.util.ListenableDiffAwareRef
import com.android.launcher3.util.MutableListenableStream
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.fallback.RecentsState
import com.android.quickstep.fallback.RecentsState.Companion.BACKGROUND_APP
import com.android.quickstep.fallback.RecentsState.Companion.DEFAULT
import com.android.quickstep.fallback.RecentsState.Companion.HIDDEN
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.window.RecentsWindowManager
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [RecentsViewUtils]. */
@RunWith(AndroidJUnit4::class)
class RecentsViewUtilsTest {
    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val recentsView = mock<RecentsView<*, *>>()
    private val displayController = mock<DisplayController>()
    private val taskAnimationManager = mock<TaskAnimationManager>()
    private val rotationTouchHelper = mock<RotationTouchHelper>()
    private val systemUiProxy = mock<SystemUiProxy>()
    private val automationRepository = mock<AutomationRepository>()
    private val uiExecutor = mock<Executor>()
    private val taskOverlayFactory: TaskOverlayFactory =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val abstractFloatingViewHelper = mock<AbstractFloatingViewHelper>()
    private val recentsViewContainer = mock<RecentsViewContainer>()

    private lateinit var utils: RecentsViewUtils

    @Before
    fun setUp() {
        val automatedPackagesMock =
            mock<ListenableDiffAwareRef<Set<PackageUserKey>, AutomationChange>>()
        whenever(automatedPackagesMock.changes).thenReturn(MutableListenableStream())
        whenever(automationRepository.automatedPackages).thenReturn(automatedPackagesMock)
        whenever(recentsView.container).thenReturn(recentsViewContainer)
        utils =
            RecentsViewUtils(
                recentsView,
                displayController,
                DISPLAY_ID,
                taskAnimationManager,
                rotationTouchHelper,
                systemUiProxy,
                automationRepository,
                uiExecutor,
                abstractFloatingViewHelper,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onStateTransitionComplete_stateChange_notifiesSystemUiProxy() {
        utils.onStateTransitionComplete(DEFAULT) // Default is considered in Overview.
        verify(systemUiProxy).onOverviewShown(DISPLAY_ID)
        clearInvocations(systemUiProxy)

        utils.onStateTransitionComplete(BACKGROUND_APP) // Not considered in Overview.
        verify(systemUiProxy).onOverviewHidden(DISPLAY_ID)

        utils.onStateTransitionComplete(DEFAULT) // Default is considered in Overview.
        verify(systemUiProxy).onOverviewShown(DISPLAY_ID)
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onStateTransitionComplete_noOverviewChange_doesNotNotifySystemUiProxy() {
        utils.onStateTransitionComplete(DEFAULT) // Default is considered in Overview.
        clearInvocations(systemUiProxy)

        utils.onStateTransitionComplete(DEFAULT)

        verify(systemUiProxy, never()).onOverviewShown(DISPLAY_ID)
        verify(systemUiProxy, never()).onOverviewHidden(DISPLAY_ID)
    }

    @Test
    fun getTaskIdsByPackageNames_returnIdsWithMatchingUserAndPackage() {
        val myUserId = Process.myUserHandle().identifier
        val otherUserId = Process.myUserHandle().identifier + 1

        // Matching user id and package name
        val taskView1 = createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, myUserId)
        val taskView2 = createTaskView(TASK_ID_2, MY_PACKAGE_NAME_2, myUserId)

        // Matching user id but wrong package names
        val taskView3 = createTaskView(TASK_ID_3, OTHER_PACKAGE_NAME, myUserId)

        // Matching package names but wrong user id
        val taskView4 = createTaskView(TASK_ID_4, MY_PACKAGE_NAME_1, otherUserId)
        val taskView5 = createTaskView(TASK_ID_5, MY_PACKAGE_NAME_2, otherUserId)

        // Both package names and user id are unmatched
        val taskView6 = createTaskView(TASK_ID_6, OTHER_PACKAGE_NAME, otherUserId)
        val taskViews = listOf(taskView1, taskView2, taskView3, taskView4, taskView5, taskView6)

        val ids =
            utils.getTaskIdsByPackageNamesAndUserHandle(
                taskViews,
                setOf(MY_PACKAGE_NAME_1, MY_PACKAGE_NAME_2),
                Process.myUserHandle(),
            )

        assertThat(ids).containsExactly(TASK_ID_1, TASK_ID_2)
    }

    @Test
    fun swipeDownDisallowedWhenFloatingViewIsOpened() {
        val taskView =
            createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, Process.myUserHandle().identifier)
        whenever(
                abstractFloatingViewHelper.getTopOpenViewWithType(
                    any(),
                    eq(TYPE_TOUCH_CONTROLLER_NO_INTERCEPT),
                )
            )
            .thenReturn(mock<AbstractFloatingView>())

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView)).isFalse()
    }

    @Test
    fun swipeDownDisallowedWhenScrolling() {
        val taskView =
            createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, Process.myUserHandle().identifier)
        val scroller = mock<OverScroller>().apply { whenever(isFinished).thenReturn(false) }
        whenever(recentsView.scroller).thenReturn(scroller)

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView)).isFalse()
    }

    @Test
    fun swipeDownDisallowedWhenTaskViewNotInteractive() {
        val taskView =
            createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, Process.myUserHandle().identifier)
        val scroller = mock<OverScroller>().apply { whenever(isFinished).thenReturn(true) }
        whenever(recentsView.scroller).thenReturn(scroller)
        val stateManager =
            mock<StateManager<RecentsState, RecentsWindowManager>>().apply {
                whenever(state).thenReturn(HIDDEN)
            }
        whenever(recentsView.getStateManager()).thenReturn(stateManager)

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView)).isFalse()
    }

    @Test
    fun swipeDownDisallowedWhenTaskViewIsNull() {
        val scroller = mock<OverScroller>().apply { whenever(isFinished).thenReturn(true) }
        whenever(recentsView.scroller).thenReturn(scroller)
        val stateManager =
            mock<StateManager<RecentsState, RecentsWindowManager>>().apply {
                whenever(state).thenReturn(DEFAULT)
            }
        whenever(recentsView.getStateManager()).thenReturn(stateManager)

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView = null)).isFalse()
    }

    @Test
    fun swipeDownDisallowedWhenTaskViewNotCurrentPage() {
        val taskView =
            createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, Process.myUserHandle().identifier)
        val currentPageTaskView =
            createTaskView(TASK_ID_2, MY_PACKAGE_NAME_2, Process.myUserHandle().identifier)
        val scroller = mock<OverScroller>().apply { whenever(isFinished).thenReturn(true) }
        whenever(recentsView.scroller).thenReturn(scroller)
        val stateManager =
            mock<StateManager<RecentsState, RecentsWindowManager>>().apply {
                whenever(state).thenReturn(DEFAULT)
            }
        whenever(recentsView.getStateManager()).thenReturn(stateManager)
        whenever(recentsView.currentPageTaskView).thenReturn(currentPageTaskView)

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView)).isFalse()
    }

    @Test
    fun swipeDownDisallowedForGridTask() {
        val taskView =
            createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, Process.myUserHandle().identifier).apply {
                whenever(isGridTask).thenReturn(true)
            }
        val scroller = mock<OverScroller>().apply { whenever(isFinished).thenReturn(true) }
        whenever(recentsView.scroller).thenReturn(scroller)
        val stateManager =
            mock<StateManager<RecentsState, RecentsWindowManager>>().apply {
                whenever(state).thenReturn(DEFAULT)
            }
        whenever(recentsView.getStateManager()).thenReturn(stateManager)
        whenever(recentsView.currentPageTaskView).thenReturn(taskView)

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView)).isFalse()
    }

    @Test
    fun swipeDownDisallowedWhenNotInExpectedScrollPosition() {
        val taskView =
            createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, Process.myUserHandle().identifier)
        val scroller = mock<OverScroller>().apply { whenever(isFinished).thenReturn(true) }
        whenever(recentsView.scroller).thenReturn(scroller)
        val stateManager =
            mock<StateManager<RecentsState, RecentsWindowManager>>().apply {
                whenever(state).thenReturn(DEFAULT)
            }
        whenever(recentsView.getStateManager()).thenReturn(stateManager)
        whenever(recentsView.currentPageTaskView).thenReturn(taskView)
        whenever(recentsView.isTaskInExpectedScrollPosition(taskView)).thenReturn(false)

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView)).isFalse()
    }

    @Test
    fun swipeDownAllowed() {
        val taskView =
            createTaskView(TASK_ID_1, MY_PACKAGE_NAME_1, Process.myUserHandle().identifier)
        val scroller = mock<OverScroller>().apply { whenever(isFinished).thenReturn(true) }
        whenever(recentsView.scroller).thenReturn(scroller)
        val stateManager =
            mock<StateManager<RecentsState, RecentsWindowManager>>().apply {
                whenever(state).thenReturn(DEFAULT)
            }
        whenever(recentsView.getStateManager()).thenReturn(stateManager)
        whenever(recentsView.currentPageTaskView).thenReturn(taskView)
        whenever(recentsView.isTaskInExpectedScrollPosition(taskView)).thenReturn(true)

        assertThat(utils.shouldSwipeDownLaunchTaskView(taskView)).isTrue()
    }

    private fun createTaskView(id: Int, packageName: String, @UserIdInt userId: Int): TaskView {
        val mockTaskView = mock<TaskView>()
        val task = createTask(id, packageName, userId)
        val containers = listOf(createTaskContainer(mockTaskView, task))
        whenever(mockTaskView.taskContainers).thenReturn(containers)
        return mockTaskView
    }

    private fun createTask(id: Int, packageName: String, @UserIdInt userId: Int): Task {
        val intent = Intent()
        intent.setComponent(ComponentName(packageName, CLASS_NAME))
        return Task(TaskKey(id, 0, intent, ComponentName(packageName, CLASS_NAME), userId, 2000))
    }

    private fun createTaskContainer(taskView: TaskView, task: Task): TaskContainer =
        TaskContainer(
            taskView,
            task,
            mock<TaskContentView>(),
            mock<TaskThumbnailView>(),
            mock<IconAppChipView>(),
            mock<TransformingTouchDelegate>(),
            SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
            digitalWellBeingToast = null,
            taskOverlayFactory,
        )

    private companion object {
        private const val DISPLAY_ID = 100
        private const val TASK_ID_1 = 55
        private const val TASK_ID_2 = 66
        private const val TASK_ID_3 = 77
        private const val TASK_ID_4 = 22
        private const val TASK_ID_5 = 33
        private const val TASK_ID_6 = 44
        private const val MY_PACKAGE_NAME_1 = "foo"
        private const val MY_PACKAGE_NAME_2 = "bar"
        private const val OTHER_PACKAGE_NAME = "other"
        private const val CLASS_NAME = "class"
    }
}
