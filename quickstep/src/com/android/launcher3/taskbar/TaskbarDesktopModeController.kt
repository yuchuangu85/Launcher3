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

package com.android.launcher3.taskbar

import com.android.launcher3.display.DisplayController
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statehandlers.DesktopVisibilityController.DesktopVisibilityListener
import com.android.launcher3.taskbar.TaskbarBackgroundRenderer.Companion.MAX_ROUNDNESS
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.SafeCloseable

/** Handles Taskbar in Desktop Windowing mode. */
class TaskbarDesktopModeController(
    private val taskbarActivityContext: TaskbarActivityContext,
    private val desktopVisibilityController: DesktopVisibilityController,
) : DesktopVisibilityListener {

    private var displayInfoChangeSafeCloseable: SafeCloseable? = null

    private lateinit var taskbarControllers: TaskbarControllers
    private lateinit var taskbarSharedState: TaskbarSharedState
    private lateinit var taskbarUiState: TaskbarUiState

    val isLauncherAnimationRunning: Boolean
        get() = desktopVisibilityController.launcherAnimationRunning

    fun init(
        controllers: TaskbarControllers,
        sharedState: TaskbarSharedState,
        uiState: TaskbarUiState,
    ) {
        taskbarControllers = controllers
        taskbarSharedState = sharedState
        taskbarUiState = uiState
        desktopVisibilityController.registerDesktopVisibilityListener(this)
        displayInfoChangeSafeCloseable =
            DisplayController.INSTANCE.get(taskbarActivityContext).listenable?.forEach(
                getTaskbarUiThread()
            ) { _ ->
                updateTaskbarUiState()
            }
    }

    fun isInDesktopMode(displayId: Int) = desktopVisibilityController.isInDesktopMode(displayId)

    fun isInDesktopModeAndNotInOverview(displayId: Int) =
        desktopVisibilityController.isInDesktopModeAndNotInOverview(displayId)

    override fun onTaskbarCornerRoundingUpdate(
        doesAnyTaskRequireTaskbarRounding: Boolean,
        displayId: Int,
    ) {
        if (displayId != taskbarActivityContext.displayId) return
        if (taskbarControllers.taskbarActivityContext.isDestroyed) return

        taskbarSharedState.showCornerRadiusInDesktopMode = doesAnyTaskRequireTaskbarRounding
        val cornerRadius = getTaskbarCornerRoundness(doesAnyTaskRequireTaskbarRounding)
        taskbarControllers.taskbarCornerRoundness.animateToValue(cornerRadius).start()
    }

    fun shouldShowDesktopTasksInTaskbar(): Boolean {
        return shouldShowDesktopTasksInTaskbar(taskbarActivityContext.displayId)
    }

    fun shouldShowDesktopTasksInTaskbar(displayId: Int): Boolean {
        return isInDesktopMode(displayId) ||
            taskbarActivityContext.showDesktopTaskbarForFreeformDisplay()
    }

    fun getTaskbarCornerRoundness(doesAnyTaskRequireTaskbarRounding: Boolean): Float {
        return if (doesAnyTaskRequireTaskbarRounding) {
            MAX_ROUNDNESS
        } else {
            0f
        }
    }

    fun onDestroy() {
        desktopVisibilityController.unregisterDesktopVisibilityListener(this)
        displayInfoChangeSafeCloseable?.close()
        displayInfoChangeSafeCloseable = null
    }

    private fun updateTaskbarUiState() {
        val info = DisplayController.getInfo(taskbarActivityContext)
        taskbarUiState.showDesktopTaskbarForFreeformDisplay =
            info.showDesktopTaskbarForFreeformDisplay
    }
}
