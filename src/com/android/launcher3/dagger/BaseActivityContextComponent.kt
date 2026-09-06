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

import com.android.launcher3.UndoDeleteController
import com.android.launcher3.WorkspaceSelectionManager
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.util.BlurBackgroundHelper
import com.android.launcher3.graphics.IconLoader
import com.android.launcher3.keyboard.KeyboardStateManager
import com.android.launcher3.popup.PopupDataProvider
import com.android.launcher3.qsb.OseWidgetOptionsProvider
import com.android.launcher3.recyclerview.AllAppsRecyclerViewPool
import com.android.launcher3.recyclerview.AllAppsRecyclerViewPool.Companion.PRELOAD_ALL_APPS_DAGGER_KEY
import com.android.launcher3.secondarydisplay.SecondaryDisplayDelegate
import com.android.launcher3.views.ActivityContext
import dagger.BindsInstance
import javax.inject.Named

/** Base component for ActivityContext Dagger injection. */
interface BaseActivityContextComponent {

    fun getSecondaryDisplayDelegate(): SecondaryDisplayDelegate

    fun getOseWidgetOptionsProvider(): OseWidgetOptionsProvider

    fun getUndoDeleteController(): UndoDeleteController

    fun getBlurBackgroundHelper(): BlurBackgroundHelper

    @DisplayId fun getDisplayId(): Int

    val appsStore: AllAppsStore
    val popupDataProvider: PopupDataProvider
    val sharedAppsPool: AllAppsRecyclerViewPool
    val keyboardStateManager: KeyboardStateManager
    val workspaceSelectionManager: WorkspaceSelectionManager
    val systemDragController: SystemDragController
    val iconLoader: IconLoader

    /** Builder for BaseActivityContextComponent. */
    interface Builder {
        @BindsInstance fun activityContext(activityContext: ActivityContext): Builder

        @BindsInstance
        fun setAllAppsPreloaded(
            @Named(PRELOAD_ALL_APPS_DAGGER_KEY) preloadAllApps: Boolean
        ): Builder

        fun build(): BaseActivityContextComponent
    }
}
