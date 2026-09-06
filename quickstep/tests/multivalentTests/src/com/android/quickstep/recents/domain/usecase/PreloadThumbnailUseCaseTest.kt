/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.recents.domain.usecase

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.quickstep.recents.data.FakeRecentTasksKeysDataSource
import com.android.quickstep.recents.data.FakeTaskThumbnailDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution.LOW_RES
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PreloadThumbnailUseCaseTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val thumbnailDataSource = FakeTaskThumbnailDataSource()
    private val recentTasksKeysDataSource = FakeRecentTasksKeysDataSource()
    private val resources =
        mock<Resources>().also {
            whenever(it.getBoolean(R.bool.config_enableTaskSnapshotPreloading)).thenReturn(true)
        }
    private val context = mock<Context>().also { whenever(it.resources).thenReturn(this.resources) }
    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(unconfinedTestDispatcher)
    var systemUnderTest = createPreloadThumbnailUseCase()

    private val tasks = (0..5).map(::createTaskWithId)
    private val defaultTaskList =
        listOf(
            SingleTask(tasks[0]),
            SplitTask(
                tasks[1],
                tasks[2],
                SplitBounds(
                    /* leftTopBounds = */ Rect(),
                    /* rightBottomBounds = */ Rect(),
                    /* leftTopTaskId = */ 1,
                    /* rightBottomTaskId = */ 2,
                    /* snapPosition = */ SNAP_TO_2_50_50,
                ),
            ),
            DesktopTask(deskId = 0, DEFAULT_DISPLAY, tasks.subList(3, 6)),
        )

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun preloadThumbnails_withFlagOff_doesNothing() =
        testScope.runTest {
            systemUnderTest.preloadThumbnails()

            assertThat(recentTasksKeysDataSource.taskKeysCalls).isEqualTo(0)
            assertThat(thumbnailDataSource.getNumberOfGetThumbnailCalls(0)).isEqualTo(0)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun preloadThumbnails_withFlagOn_AndPreloadingOff_doesNothing() =
        testScope.runTest {
            whenever(resources.getBoolean(R.bool.config_enableTaskSnapshotPreloading))
                .thenReturn(false)
            // Reinitialize systemUnderTest to get new value from resources
            systemUnderTest = createPreloadThumbnailUseCase()

            systemUnderTest.preloadThumbnails()

            assertThat(recentTasksKeysDataSource.taskKeysCalls).isEqualTo(0)
            assertThat(thumbnailDataSource.getNumberOfGetThumbnailCalls(0)).isEqualTo(0)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun preloadThumbnails_requestsThumbnailsForCorrectTasks() =
        testScope.runTest {
            recentTasksKeysDataSource.setGroupTasks(defaultTaskList)
            thumbnailDataSource.setCacheSize(1)
            systemUnderTest.preloadThumbnails()

            assertThat(thumbnailDataSource.getThumbnailCallsRes(0)).containsExactly(LOW_RES)
            (1..5).forEach { taskId ->
                assertThat(thumbnailDataSource.getNumberOfGetThumbnailCalls(taskId)).isEqualTo(0)
            }
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun preloadThumbnails_requestsThumbnailsForCorrectTasks_largerCacheSize() =
        testScope.runTest {
            recentTasksKeysDataSource.setGroupTasks(defaultTaskList)
            thumbnailDataSource.setCacheSize(3)
            systemUnderTest.preloadThumbnails()

            (0..5).forEach { taskId ->
                assertThat(thumbnailDataSource.getThumbnailCallsRes(taskId))
                    .containsExactly(LOW_RES)
            }
        }

    private fun createPreloadThumbnailUseCase() =
        PreloadThumbnailUseCase(
            context = context,
            taskThumbnailDataSource = thumbnailDataSource,
            recentTasksKeysDataSource = recentTasksKeysDataSource,
        )

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000))
}
