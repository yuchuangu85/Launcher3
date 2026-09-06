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

package com.android.quickstep.dagger

import com.android.launcher3.dagger.BaseActivityContextComponent
import com.android.launcher3.taskbar.TaskbarEduTooltipController
import com.android.launcher3.taskbar.allapps.TaskbarSearchSessionController
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.quickstep.fallback.FallbackActivityRecentsView
import com.android.quickstep.fallback.FallbackWindowRecentsView
import com.android.quickstep.views.DesktopTaskView
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.LauncherRecentsView
import com.android.quickstep.views.TaskView

/** Activity Quickstep base component for Dagger injection. */
interface QuickstepBaseActivityComponent : BaseActivityContextComponent {

    fun createTaskbarSearchSessionController(): TaskbarSearchSessionController

    fun createTaskbarEduTooltipController(): TaskbarEduTooltipController

    fun getTaskbarFeatureEvaluator(): TaskbarFeatureEvaluator

    /** Recents Specific methods */
    fun inject(taskView: TaskView)

    fun inject(taskView: GroupedTaskView)

    fun inject(taskView: DesktopTaskView)

    fun inject(recentsView: LauncherRecentsView)

    fun inject(recentsView: FallbackActivityRecentsView)

    fun inject(recentsView: FallbackWindowRecentsView)
}
