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

package com.android.launcher3.taskbar.customization

import android.content.Context
import androidx.annotation.AnyThread
import com.android.app.displaylib.PerDisplayRepository
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.DisplayId
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.PerDisplaySingleton
import com.android.launcher3.display.DisplayController
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.NavigationMode.NO_BUTTON
import com.android.launcher3.util.NavigationMode.THREE_BUTTONS
import com.android.systemui.shared.Flags.enableRecentsInTaskbar
import javax.inject.Inject

/** Evaluates all the features taskbar can have. */
@PerDisplaySingleton
class TaskbarFeatureEvaluator
@Inject
constructor(
    @DisplayId val displayId: Int,
    @ApplicationContext val context: Context,
    private val displayController: DisplayController,
    private val desktopVisibilityController: DesktopVisibilityController,
    private val launcherPrefs: LauncherPrefs,
) {
    private val displayInfo: LauncherDisplayInfo?
        get() = displayController.getInfoForDisplay(displayId)

    val isPrimaryDisplay = displayId == context.displayId
    val hasBubbles = false
    val hasNavButtons: Boolean
        get() = displayInfo?.navigationMode == THREE_BUTTONS

    val isRecentsEnabled: Boolean
        get() = enableRecentsInTaskbar()

    @get:AnyThread
    val isTransient: Boolean
        get() =
            if (
                displayInfo?.navigationMode != NO_BUTTON ||
                    desktopVisibilityController.isInDesktopMode(displayId) ||
                    displayInfo?.showDesktopTaskbarForFreeformDisplay == true
            ) {
                false
            } else {
                !isPinned
            }

    val isPinned: Boolean
        get() =
            if (
                desktopVisibilityController.isInDesktopMode(displayId) ||
                    displayInfo?.showDesktopTaskbarForFreeformDisplay == true
            ) {
                true
            } else {
                launcherPrefs.get(TASKBAR_PINNING)
            }

    val supportsPinningPopup: Boolean
        get() = !hasNavButtons

    val isPersistent: Boolean
        get() = isPinned || hasNavButtons

    val supportsTransitionToTransientTaskbar: Boolean
        get() =
            !hasNavButtons &&
                !DisplayController.getInfo(context).showDesktopTaskbarForFreeformDisplay &&
                !desktopVisibilityController.isInDesktopMode(displayId)

    companion object {
        @JvmField
        val INSTANCE =
            DaggerSingletonObject<PerDisplayRepository<TaskbarFeatureEvaluator>>(
                LauncherAppComponent::getTaskbarFeatureEvaluatorRepository
            )
    }
}
