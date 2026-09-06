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

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import android.window.RemoteTransition
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING
import com.android.launcher3.Flags.FLAG_ENABLE_KQS_FORCE_TAKE_RUNNING_TASK_THUMBNAIL
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.waitForIdleSync
import com.android.launcher3.taskbar.rules.MockedRecentsModelHelper
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext_ModifiedComponent
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SlideInRemoteTransition
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@MutatedComponent(target = TaskbarWindowSandboxContext_ModifiedComponent::class)
class KeyboardQuickSwitchControllerTest {
    private var systemUiProxySpy: SystemUiProxy? = null
    private val mockRecentsModelHelper: MockedRecentsModelHelper = MockedRecentsModelHelper()
    private val taskIdCaptor = argumentCaptor<Int>()
    private val transitionCaptor = argumentCaptor<RemoteTransition>()

    @BindValue val recentsModel: RecentsModel by mockRecentsModelHelper

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1)
    val context =
        TaskbarWindowSandboxContext.create(
            params =
                SandboxParams(builderBase = mutatedComponentBuilder()) {
                    systemUiProxySpy = it.systemUiProxy
                }
        )

    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val keyboardQuickSwitchController by
        taskbarUnitTestRule.delegate { it.keyboardQuickSwitchController }
    private val allAppsController by taskbarUnitTestRule.delegate { it.taskbarAllAppsController }

    private val isKqsShown: Boolean
        get() = getOnTaskbarUiThread { keyboardQuickSwitchController.isShown }

    private val shownTaskIds: List<Int>
        get() = getOnTaskbarUiThread { keyboardQuickSwitchController.shownTaskIds() }

    @Test
    fun noRecentTasks_noShownTaskIds() {
        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).isEmpty()
    }

    @Test
    fun onlySingleTasksPresent_shouldShowAllTaskIds() {
        updateRecentsModel(
            listOf(createSingleTask(PREVIOUS_TASK_ID), createSingleTask(RUNNING_TASK_ID))
        )

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID).inOrder()
    }

    @Test
    fun onlyDesktopTasksPresent_shouldShowAllTaskIds() {
        updateRecentsModel(listOf(createDesktopTask(listOf(RUNNING_TASK_ID, PREVIOUS_TASK_ID))))
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID).inOrder()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun singleAndDesktopTasksPresent_onDesktopWithFlatenningOff_onlyShowDesktopTaskIds() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(RUNNING_TASK_ID, OLDEST_TASK_ID)),
                createSingleTask(PREVIOUS_TASK_ID),
            )
        )
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, OLDEST_TASK_ID).inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun singleAndDesktopTasksPresent_onDesktopWithFlatenningOn_showAllTaskIds() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(RUNNING_TASK_ID, OLDEST_TASK_ID)),
                createSingleTask(PREVIOUS_TASK_ID),
            )
        )
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds)
            .containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID, OLDEST_TASK_ID)
            .inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun singleAndDesktopTasksPresent_notOnDesktopWithFlatenningOn_showAllTaskIds() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(PREVIOUS_TASK_ID, OLDEST_TASK_ID)),
                createSingleTask(RUNNING_TASK_ID),
            )
        )

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds)
            .containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID, OLDEST_TASK_ID)
            .inOrder()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun multipleDesktopTasksPresent_onDesktop_showAllDesktopTasks() {
        updateRecentsModel(
            listOf(
                createDesktopTask(listOf(RUNNING_TASK_ID)),
                createDesktopTask(listOf(PREVIOUS_TASK_ID)),
            )
        )
        enableDesktopMode()

        triggerAltTab()

        assertThat(isKqsShown).isTrue()
        assertThat(shownTaskIds).containsExactly(RUNNING_TASK_ID, PREVIOUS_TASK_ID).inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun splitAndSingleTaskPresent_withFlatenningOn_shouldSortTaskIds() {
        updateRecentsModel(
            listOf(
                createSplitTask(OLDEST_TASK_ID to RUNNING_TASK_ID),
                createSingleTask(PREVIOUS_TASK_ID),
            )
        )

        triggerAltTab()

        // Although single task is more recent than one of the split tasks, the split tasks should
        // be together. Furthermore, the shownTaskIds returns left split task first.
        assertThat(shownTaskIds)
            .containsExactly(OLDEST_TASK_ID, RUNNING_TASK_ID, PREVIOUS_TASK_ID)
            .inOrder()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun launchDesktopApp_notOnDesktop_shouldCallSysUIProxyToStartSpecificApp() {
        val deskId = 1
        updateRecentsModel(listOf(createDesktopTask(listOf(PREVIOUS_TASK_ID), deskId)))

        triggerAltTabAndLaunchFocusedTask()

        val deskIdCaptor = argumentCaptor<Int>()
        verify(systemUiProxySpy)
            ?.activateDesk(
                deskIdCaptor.capture(),
                transitionCaptor.capture(),
                taskIdToReorderToFront = eq(PREVIOUS_TASK_ID),
                transitionSource = eq(DesktopModeTransitionSource.KEYBOARD_SHORTCUT),
            )
        assertThat(deskIdCaptor.firstValue).isEqualTo(deskId)
        assertThat(transitionCaptor.firstValue.remoteTransition)
            .isInstanceOf(SlideInRemoteTransition::class.java)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun launchSingleApp_onDesktop_shouldCallSysUIProxyToMoveToFullscreen() {
        updateRecentsModel(listOf(createSingleTask(PREVIOUS_TASK_ID)))
        enableDesktopMode()

        triggerAltTabAndLaunchFocusedTask()

        verify(systemUiProxySpy)
            ?.moveToFullscreen(
                taskIdCaptor.capture(),
                eq(DesktopModeTransitionSource.KEYBOARD_SHORTCUT),
                transitionCaptor.capture(),
            )
        assertThat(taskIdCaptor.firstValue).isEqualTo(PREVIOUS_TASK_ID)
        assertThat(transitionCaptor.firstValue.remoteTransition)
            .isInstanceOf(SlideInRemoteTransition::class.java)
    }

    @Test
    fun testOpenAllAppsClosesKeyboardQuickSwitchView() {
        triggerAltTab()

        assertThat(allAppsController.isOpen).isFalse()
        assertThat(isKqsShown).isTrue()

        runOnTaskbarUiThreadSync { allAppsController.toggle() }

        assertThat(isKqsShown).isFalse()
        assertThat(allAppsController.isOpen).isTrue()
    }

    @Test
    fun isShown_returnsCorrectState_whenOpenedAndClosed() {
        // Verify KQS is not shown initially.
        assertThat(isKqsShown).isFalse()

        // Open KQS and verify it is shown.
        triggerAltTab()
        assertThat(isKqsShown).isTrue()

        // Close KQS and verify it is not shown.
        runOnTaskbarUiThreadSync { keyboardQuickSwitchController.closeQuickSwitchView() }
        assertThat(isKqsShown).isFalse()
    }

    @Test
    fun openQuickSwitchView_closedBeforeTasksLoad_doesNotCrash() {
        // Setup some tasks to be loaded.
        updateRecentsModel(
            listOf(createSingleTask(PREVIOUS_TASK_ID), createSingleTask(RUNNING_TASK_ID))
        )

        // 1. Trigger KQS opening, which will request tasks from RecentsModel asynchronously.
        //    DO NOT resolve the pending task request yet.
        runOnTaskbarUiThreadSync { keyboardQuickSwitchController.openQuickSwitchView() }

        // Sanity check: the view is in the process of being shown.
        assertThat(isKqsShown).isTrue()

        // 2. Close the view *before* the tasks have been loaded and passed to the view.
        runOnTaskbarUiThreadSync { keyboardQuickSwitchController.closeQuickSwitchView() }
        assertThat(isKqsShown).isFalse()

        // 3. Now, simulate the tasks being loaded and the callback firing.
        //    Without the null-check fix, this would cause a NullPointerException.
        mockRecentsModelHelper.resolvePendingTaskRequests()

        // 4. Assert that the view remains closed and no crash occurred.
        assertThat(isKqsShown).isFalse()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_KQS_FORCE_TAKE_RUNNING_TASK_THUMBNAIL)
    fun openQuickSwitchView_withFlagOff_getsRunningTaskCachedThumbnail() {
        val task = createTask(RUNNING_TASK_ID)
        updateThumbnailInBackground(task)

        verify(recentsModel.thumbnailCache, times(1)).getThumbnailInBackground(eq(task), any())
        verifyNoMoreInteractions(recentsModel.thumbnailCache)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_KQS_FORCE_TAKE_RUNNING_TASK_THUMBNAIL)
    fun openQuickSwitchView_withFlagOn_updatesRunningTaskThumbnail() {
        updateThumbnailInBackground(createTask(RUNNING_TASK_ID))

        verify(recentsModel.thumbnailCache, times(1)).updateTaskSnapShot(eq(RUNNING_TASK_ID), any())
        verifyNoMoreInteractions(recentsModel.thumbnailCache)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_KQS_FORCE_TAKE_RUNNING_TASK_THUMBNAIL)
    fun openQuickSwitchView_withFlagOff_getsPreviousTaskCachedThumbnail() {
        val task = createTask(PREVIOUS_TASK_ID)
        updateThumbnailInBackground(task)

        verify(recentsModel.thumbnailCache, times(1)).getThumbnailInBackground(eq(task), any())
        verifyNoMoreInteractions(recentsModel.thumbnailCache)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_KQS_FORCE_TAKE_RUNNING_TASK_THUMBNAIL)
    fun openQuickSwitchView_withFlagOn_getsPreviousTaskCachedThumbnail() {
        val task = createTask(PREVIOUS_TASK_ID)
        updateThumbnailInBackground(task)

        verify(recentsModel.thumbnailCache, times(1)).getThumbnailInBackground(eq(task), any())
        verifyNoMoreInteractions(recentsModel.thumbnailCache)
    }

    private fun updateThumbnailInBackground(task: Task) {
        runOnMainSync {
            keyboardQuickSwitchController.mControllerCallbacks.updateThumbnailInBackground(
                task,
                /* isTaskRunning= */ task.key.id == RUNNING_TASK_ID,
                /* callback= */ mock(),
            )
        }
    }

    private fun createSingleTask(taskId: Int) = SingleTask(createTask(taskId))

    private fun createSplitTask(taskIds: Pair<Int, Int>) =
        SplitTask(
            createTask(taskIds.first),
            createTask(taskIds.second),
            SplitBounds(
                /* leftTopBounds = */ Rect(),
                /* rightBottomBounds = */ Rect(),
                /* leftTopTaskId = */ 1,
                /* rightBottomTaskId = */ 2,
                /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50,
            ),
        )

    private fun createDesktopTask(taskIds: List<Int>, deskId: Int = 0) =
        DesktopTask(deskId, DEFAULT_DISPLAY, taskIds.map { createTask(it) })

    private fun enableDesktopMode() {
        whenever(DesktopVisibilityController.INSTANCE[context].isInDesktopMode(any()))
            .thenReturn(true)
    }

    /*
     * Returns a task with the given ID and a fake package name.
     *
     * Note: the task ID is added to last active time, thus higher task ID indicates a more recent
     * active task.
     */
    private fun createTask(taskId: Int): Task {
        return Task(
            Task.TaskKey(
                taskId,
                0,
                Intent().apply { `package` = "Fake${taskId}" },
                ComponentName("Fake${taskId}", ""),
                Process.myUserHandle().identifier,
                2000L + taskId,
            )
        )
    }

    private fun updateRecentsModel(tasks: List<GroupTask>) {
        mockRecentsModelHelper.updateRecentTasks(tasks)
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }
    }

    private fun triggerAltTab() = runOnTaskbarUiThreadSync {
        runOnTaskbarUiThreadSync { keyboardQuickSwitchController.openQuickSwitchView() }
        mockRecentsModelHelper.resolvePendingTaskRequests()
    }

    private fun triggerAltTabAndLaunchFocusedTask() {
        triggerAltTab()
        runOnTaskbarUiThreadSync { keyboardQuickSwitchController.launchFocusedTask() }
        // `keyboardQuickSwitchController.launchFocusedTask()` will post a task to activate target
        // desk to `UI_HELPER_EXECUTOR`. Flush the executor to make sure the task runs before
        // verifying mocks.
        UI_HELPER_EXECUTOR.waitForIdleSync()
    }

    private companion object {
        const val OLDEST_TASK_ID = 1
        const val PREVIOUS_TASK_ID = 2
        const val RUNNING_TASK_ID = 3
    }
}
