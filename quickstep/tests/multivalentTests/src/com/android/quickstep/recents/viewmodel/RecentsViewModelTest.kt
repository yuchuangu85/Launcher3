/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.recents.viewmodel

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.quickstep.recents.data.AppTimerResponse
import com.android.quickstep.recents.data.FakeAppTimersRepository
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RecentsViewModelTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val tasksRepository = FakeTasksRepository()
    private val appTimersRepository = FakeAppTimersRepository()
    private val recentsViewData = RecentsViewData()
    private val systemUnderTest =
        RecentsViewModel(tasksRepository, recentsViewData, appTimersRepository, DEFAULT_DISPLAY)

    private val tasks = (0..5).map(::createTaskWithId)

    @Test
    fun taskVisibilityControlThumbnailsAvailability() = runTest {
        val thumbnailData1 = createThumbnailData()
        val thumbnailData2 = createThumbnailData()
        tasksRepository.seedTasks(tasks)
        tasksRepository.seedThumbnailData(mapOf(1 to thumbnailData1, 2 to thumbnailData2))

        val thumbnailDataFlow1 = tasksRepository.getThumbnailById(1)
        val thumbnailDataFlow2 = tasksRepository.getThumbnailById(2)

        systemUnderTest.refreshAllTaskData()

        assertThat(thumbnailDataFlow1.first()).isNull()
        assertThat(thumbnailDataFlow2.first()).isNull()

        systemUnderTest.updateVisibleTasks(listOf(1, 2))

        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()).isEqualTo(thumbnailData2)

        systemUnderTest.updateVisibleTasks(listOf(1))

        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()).isNull()

        systemUnderTest.onReset()

        assertThat(thumbnailDataFlow1.first()).isNull()
        assertThat(thumbnailDataFlow2.first()).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun onReset_invalidatesCachedAppTimers() = runTest {
        appTimersRepository.setTimer(
            APP_TIMER_PKG_NAME,
            APP_TIMER_USER_HANDLE,
            Duration.ofHours(1L),
        )

        systemUnderTest.onReset()

        assertThat(
                appTimersRepository.getRemainingDuration(APP_TIMER_PKG_NAME, APP_TIMER_USER_HANDLE)
            )
            .isEqualTo(AppTimerResponse.NoTimer)
    }

    @Test
    fun updatesRunningTaskShowScreenshot() = runTest {
        systemUnderTest.setRunningTaskShowScreenshot(true)
        systemUnderTest.waitForRunningTaskShowScreenshotToUpdate()
    }

    @Test
    fun waitForThumbnailsToUpdate() = runTest {
        // Given taskRepository with visible 2 tasks containing thumbnailData
        val thumbnailData1 = createThumbnailData().apply { snapshotId = 1 }
        val thumbnailData2 = createThumbnailData().apply { snapshotId = 2 }
        tasksRepository.seedTasks(tasks)
        tasksRepository.seedThumbnailData(mapOf(1 to thumbnailData1, 2 to thumbnailData2))
        systemUnderTest.updateVisibleTasks(listOf(1, 2))

        val thumbnailDataFlow1 = tasksRepository.getThumbnailById(1)
        val thumbnailDataFlow2 = tasksRepository.getThumbnailById(2)

        // Then getThumbnailById should initially contains correct thumbnailData
        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()).isEqualTo(thumbnailData2)

        // When thumbnailData is updated in taskRepository
        tasksRepository.seedThumbnailData(
            mapOf(1 to thumbnailData1, 2 to createThumbnailData().apply { snapshotId = 3 })
        )
        // setVisibleTasks forces FakeTasksRepository to update the flows returned by
        // getThumbnailById
        tasksRepository.setVisibleTasks(DEFAULT_DISPLAY, setOf(1, 2))

        // Then wait for thumbnailData should complete, and the previous getThumbnailById flow
        // should return updated values
        systemUnderTest.waitForThumbnailsToUpdate(
            mapOf(2 to createThumbnailData().apply { snapshotId = 3 })
        )
        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()?.snapshotId).isEqualTo(3)
    }

    @Test
    fun setHighResThumbnailsRequired_passesSettingToTasksRepository() {
        systemUnderTest.setHighResThumbnailsRequired(true)
        assertThat(tasksRepository.getHighResThumbnailsRequired()).isTrue()

        systemUnderTest.setHighResThumbnailsRequired(false)
        assertThat(tasksRepository.getHighResThumbnailsRequired()).isFalse()
    }

    @Test
    fun waitForThumbnailsToUpdate_emptyMap() = runTest {
        systemUnderTest.waitForThumbnailsToUpdate(emptyMap())
    }

    @Test
    fun waitForThumbnailsToUpdate_null() = runTest {
        systemUnderTest.waitForThumbnailsToUpdate(null)
    }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
            colorBackground = Color.argb(taskId, taskId, taskId, taskId)
        }

    private fun createThumbnailData(rotation: Int = Surface.ROTATION_0): ThumbnailData {
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(THUMBNAIL_WIDTH)
        whenever(bitmap.height).thenReturn(THUMBNAIL_HEIGHT)

        return ThumbnailData(thumbnail = bitmap, rotation = rotation)
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200

        const val APP_TIMER_PKG_NAME = "com.test.test"
        val APP_TIMER_USER_HANDLE = UserHandle(1)
    }
}
