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

package com.android.launcher3.dagger

import android.content.Context
import android.view.Display
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.quickstep.FallbackWindowInterface
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.window.RecentsWindowManager
import com.android.quickstep.window.RecentsWindowTracker
import dagger.BindsInstance
import dagger.Subcomponent

/**
 * A sub-component that host shared objects between multiple components that are bound to lifecycle
 * of a display instance.
 */
@PerDisplaySingleton
@PerDisplayScope
@Subcomponent(modules = [PerDisplayObjectsModule::class])
interface PerDisplayComponent {
    // Factories for container objects that create components bound to a specific display.
    // e.g. RecentsWindowManager for creating RecentsComponent.
    val recentsWindowManager: RecentsWindowManager

    // Shared components between multiple components like Recents and Gesture Nav.
    // Ideally only interfaces should be provided.
    val recentsAnimationDeviceState: RecentsAnimationDeviceState
    val taskAnimationManager: TaskAnimationManager
    val rotationTouchHelper: RotationTouchHelper
    val recentsWindowTracker: RecentsWindowTracker
    val fallbackWindowInterface: FallbackWindowInterface

    @WindowContext fun getWindowContext(): Context

    fun getTaskbarFeatureEvaluator(): TaskbarFeatureEvaluator

    val cleanupTasks: PerDisplayCleanupTask

    // End Shared components.

    @LauncherAppSingleton
    @Subcomponent.Factory
    interface Factory {
        fun build(@BindsInstance display: Display): PerDisplayComponent
    }
}
