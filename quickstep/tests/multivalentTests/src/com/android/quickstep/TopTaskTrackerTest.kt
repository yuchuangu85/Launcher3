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

package com.android.quickstep

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.Context
import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.R
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statehandlers.DesktopVisibilityController.Companion.INACTIVE_DESK_ID
import com.android.launcher3.taskbar.bubbles.BubbleHelper
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.quickstep.TopTaskTracker.HISTORY_SIZE
import com.android.quickstep.util.FakeTaskFactory
import com.android.quickstep.util.binder.OneWayBinderList
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING
import com.android.wm.shell.shared.GroupedTaskInfo
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_DESK
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_FULLSCREEN
import com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT
import com.android.wm.shell.splitscreen.ISplitScreenListener
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private const val SECONDARY_DISPLAY_ID = 1

/** Test for [TopTaskTracker] */
@RunWith(AndroidJUnit4::class)
class TopTaskTrackerTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val mockContext = mock<Context>()
    private val mockResources = mock<Resources>()

    private lateinit var topTaskTracker: TopTaskTracker

    @Before
    fun setUp() {
        doReturn(mockResources).whenever(mockContext).resources

        val mockDaggerSingletonTracker = mock<DaggerSingletonTracker>()
        val mockSystemUiProxy = mock<SystemUiProxy>()
        val mockDesktopVisibilityController = mock<DesktopVisibilityController>()
        doReturn(OneWayBinderList(ISplitScreenListener.Stub::asInterface))
            .whenever(mockSystemUiProxy)
            .splitScreenListeners

        topTaskTracker =
            TopTaskTracker(
                mockContext,
                mockDaggerSingletonTracker,
                mockSystemUiProxy,
                mockDesktopVisibilityController,
            )
    }

    @After
    fun tearDown() {
        BubbleHelper.updateBubbleRootTaskId(INVALID_TASK_ID)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingEnabled_noVisibleTasks() {
        val cachedTaskInfo = TopTaskTracker.CachedTaskInfo(null)
        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)
        assertThat(result).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withoutTopTask_withSplitTasks() {
        val cachedTaskInfo = TopTaskTracker.CachedTaskInfo(null)
        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(intArrayOf(1, 2))
        assertThat(result).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingEnabled_withVisibleTasks() {
        val taskInfo = createTaskInfo(1, DEFAULT_DISPLAY)
        val groupedTaskInfo = GroupedTaskInfo.forFullscreenTasks(taskInfo)
        val cachedTaskInfo = TopTaskTracker.CachedTaskInfo(groupedTaskInfo)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)
        assertThat(result).isEqualTo(groupedTaskInfo)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_noTasks() {
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(
                emptyList(),
                mockContext,
                DEFAULT_DISPLAY,
                INACTIVE_DESK_ID,
            )

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withFullscreenTask() {
        val taskInfo = createTaskInfo(1, DEFAULT_DISPLAY)
        val tasks = listOf(taskInfo)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, INACTIVE_DESK_ID)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_FULLSCREEN)).isTrue()
        assertThat(result.taskInfo1).isEqualTo(taskInfo)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withSplitTasks() {
        val taskInfo1 = createTaskInfo(1, DEFAULT_DISPLAY)
        val taskInfo2 = createTaskInfo(2, DEFAULT_DISPLAY)
        val tasks = listOf(taskInfo1, taskInfo2)
        val splitTaskIds = intArrayOf(1, 2)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, INACTIVE_DESK_ID)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(splitTaskIds)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_SPLIT)).isTrue()
        assertThat(result.taskInfo1).isEqualTo(taskInfo1)
        assertThat(result.taskInfo2).isEqualTo(taskInfo2)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withDesktopEnabled_noActiveDesk() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(true)
            .whenever(mockResources)
            .getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))
        val taskInfo = createTaskInfo(1, DEFAULT_DISPLAY, WINDOWING_MODE_FREEFORM)
        val tasks = listOf(taskInfo)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, INACTIVE_DESK_ID)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_FULLSCREEN)).isTrue()
        assertThat(result.deskId).isEqualTo(INACTIVE_DESK_ID)
        assertThat(result.taskInfo1).isEqualTo(taskInfo)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getPlaceholderGroupedTaskInfo_shellTopTaskTrackingDisabled_withDesktopEnabled_withActiveDesk() {
        doReturn(true).whenever(mockResources).getBoolean(eq(R.bool.config_isDesktopModeSupported))
        doReturn(true)
            .whenever(mockResources)
            .getBoolean(eq(R.bool.config_canInternalDisplayHostDesktops))
        val activeDeskId = 10
        val taskInfo = createTaskInfo(1, DEFAULT_DISPLAY, WINDOWING_MODE_FREEFORM)
        val tasks = listOf(taskInfo)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, DEFAULT_DISPLAY, activeDeskId)

        val result = cachedTaskInfo.getPlaceholderGroupedTaskInfo(null)

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_DESK)).isTrue()
        assertThat(result.deskId).isEqualTo(activeDeskId)
        assertThat(result.getTaskInfoList()).isEqualTo(tasks)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getVisibleNonExcludedTask_deskInactive_withoutExcludedTasks() {
        val displayId = 5
        val excludedTask =
            createTaskInfo(
                1,
                displayId,
                WINDOWING_MODE_FULLSCREEN,
                isExcluded = true,
                isVisible = true,
            )
        val visibleTask1 =
            createTaskInfo(
                2,
                displayId,
                WINDOWING_MODE_FULLSCREEN,
                isExcluded = false,
                isVisible = true,
            )
        val visibleTask2 =
            createTaskInfo(
                3,
                displayId,
                WINDOWING_MODE_FULLSCREEN,
                isExcluded = false,
                isVisible = true,
            )
        val tasks = listOf(excludedTask, visibleTask1, visibleTask2)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, displayId, INACTIVE_DESK_ID)

        val result =
            cachedTaskInfo.visibleNonExcludedTask?.getPlaceholderGroupedTaskInfo(
                /* splitTaskIds= */ null
            )

        assertThat(result).isNotNull()
        assertThat(result!!.isBaseType(TYPE_FULLSCREEN)).isTrue()
        assertThat(result.taskInfo1).isEqualTo(visibleTask1)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getVisibleNonExcludedTask_deskActive_null() {
        val activeDeskId = 10
        val displayId = 5
        val excludedTask =
            createTaskInfo(
                1,
                displayId,
                WINDOWING_MODE_FREEFORM,
                isExcluded = true,
                isVisible = true,
            )
        val visibleTask1 =
            createTaskInfo(
                2,
                displayId,
                WINDOWING_MODE_FREEFORM,
                isExcluded = false,
                isVisible = true,
            )
        val visibleTask2 =
            createTaskInfo(
                3,
                displayId,
                WINDOWING_MODE_FREEFORM,
                isExcluded = false,
                isVisible = true,
            )
        val tasks = listOf(excludedTask, visibleTask1, visibleTask2)
        val cachedTaskInfo =
            TopTaskTracker.CachedTaskInfo(tasks, mockContext, displayId, activeDeskId)

        val result =
            cachedTaskInfo.visibleNonExcludedTask?.getPlaceholderGroupedTaskInfo(
                /* splitTaskIds= */ null
            )

        assertThat(result).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getCachedTopTask_filtersOutBubbleTask() {
        BubbleHelper.updateBubbleRootTaskId(5)
        val appBubbleTask = createAppBubbleTaskInfo(taskId = 100, parentTaskId = 5)
        val convoBubbleTask = createNotifBubbleTaskInfo(taskId = 101)
        val normalTask = createTaskInfo(taskId = 102)

        topTaskTracker.handleTaskMovedToFront(normalTask)
        topTaskTracker.handleTaskMovedToFront(appBubbleTask)
        topTaskTracker.handleTaskMovedToFront(convoBubbleTask)

        val topTask =
            topTaskTracker.getCachedTopTask(/* filterOnlyVisibleRecents= */ false, DEFAULT_DISPLAY)

        assertThat(topTask.taskId).isEqualTo(normalTask.taskId)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getCachedTopTask_allBubbles_noTopTask() {
        BubbleHelper.updateBubbleRootTaskId(5)
        val convoBubbleTask = createNotifBubbleTaskInfo(taskId = 100)
        val appBubbleTask = createAppBubbleTaskInfo(taskId = 101, parentTaskId = 5)

        topTaskTracker.handleTaskMovedToFront(convoBubbleTask)
        topTaskTracker.handleTaskMovedToFront(appBubbleTask)

        val topTask =
            topTaskTracker.getCachedTopTask(/* filterOnlyVisibleRecents= */ false, DEFAULT_DISPLAY)

        assertThat(topTask.taskId).isEqualTo(INVALID_TASK_ID)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun getAllTasks_tasksMoreThanHistorySize_onlyFreeFormTasksNotRemoved() {
        val freeformTaskCount = HISTORY_SIZE * 2
        val fullScreenTaskCount = HISTORY_SIZE + 2
        var taskId = 0
        val freeformTasks = mutableListOf<TaskInfo>()
        repeat(freeformTaskCount) {
            val task = createTaskInfo(taskId = ++taskId, windowingMode = WINDOWING_MODE_FREEFORM)
            freeformTasks.add(task)
            topTaskTracker.handleTaskMovedToFront(task)
        }
        val fullScreenTasks = mutableListOf<TaskInfo>()
        repeat(fullScreenTaskCount) {
            val task = createTaskInfo(taskId = ++taskId, windowingMode = WINDOWING_MODE_FULLSCREEN)
            fullScreenTasks.add(task)
            topTaskTracker.handleTaskMovedToFront(task)
        }

        val cachedInfo =
            topTaskTracker.getCachedTopTask(/* filterOnlyVisibleRecents= */ false, DEFAULT_DISPLAY)
        val tasks = cachedInfo.mAllCachedTasks

        val expectedTasks = freeformTasks + fullScreenTasks.takeLast(HISTORY_SIZE)
        assertThat(tasks).containsExactlyElementsIn(expectedTasks)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun handleTaskMovedToFront_nonDefaultDisplayTask_flagEnabled_doesNotReorder() {
        // Arrange: Add a task on the default display.
        val taskOnDefaultDisplay = createTaskInfo(taskId = 1, displayId = DEFAULT_DISPLAY)
        topTaskTracker.handleTaskMovedToFront(taskOnDefaultDisplay)

        // Add a task on a secondary display.
        val taskOnSecondaryDisplay = createTaskInfo(taskId = 2, displayId = SECONDARY_DISPLAY_ID)

        // Act: Move the task on the secondary display to the front.
        topTaskTracker.handleTaskMovedToFront(taskOnSecondaryDisplay)

        // Assert: With the flag enabled, each display maintains its own top task. The task on the
        // secondary display should be the top task for that display.
        val topTaskSecondary =
            topTaskTracker
                .getCachedTopTask(/* filterOnlyVisibleRecents= */ false, SECONDARY_DISPLAY_ID)
                .getLegacyBaseTask()
        assertThat(topTaskSecondary?.taskId).isEqualTo(taskOnSecondaryDisplay.taskId)

        // And the task on the default display should remain the top task for the default display.
        val topTaskDefault =
            topTaskTracker
                .getCachedTopTask(/* filterOnlyVisibleRecents= */ false, DEFAULT_DISPLAY)
                .getLegacyBaseTask()
        assertThat(topTaskDefault?.taskId).isEqualTo(taskOnDefaultDisplay.taskId)
    }

    private fun createTaskInfo(
        taskId: Int,
        displayId: Int = DEFAULT_DISPLAY,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        isExcluded: Boolean = false,
        isVisible: Boolean = true,
    ) =
        FakeTaskFactory.newTaskInfo(
            taskId = taskId,
            displayId = displayId,
            windowingMode = windowingMode,
            isExcluded = isExcluded,
            isVisible = isVisible,
        )

    private fun createNotifBubbleTaskInfo(taskId: Int, displayId: Int = DEFAULT_DISPLAY): TaskInfo {
        val taskInfo = createTaskInfo(taskId, displayId)
        taskInfo.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_MULTI_WINDOW
        taskInfo.configuration.windowConfiguration.isAlwaysOnTop = true
        return taskInfo
    }

    private fun createAppBubbleTaskInfo(
        taskId: Int,
        parentTaskId: Int,
        displayId: Int = DEFAULT_DISPLAY,
    ): TaskInfo {
        val taskInfo = createTaskInfo(taskId, displayId)
        taskInfo.parentTaskId = parentTaskId
        return taskInfo
    }
}
