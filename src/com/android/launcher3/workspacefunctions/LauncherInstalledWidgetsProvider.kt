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
package com.android.launcher3.workspacefunctions

import com.android.launcher3.appfunctions.workspace.provider.InstalledItemsProvider
import com.android.launcher3.model.WidgetsModel
import com.android.launcher3.util.Executors
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Implementation of [InstalledItemsProvider] for Launcher that returns a list of
 * [LauncherAppWidgetProviderInfo] objects representing the installed widgets and static shortcuts
 * on the device.
 *
 * It uses the [WidgetsModel] as the single source of truth for discovered widgets.
 */
class LauncherInstalledWidgetsProvider
@Inject
constructor(private val widgetsModelProvider: Provider<WidgetsModel>) :
    InstalledItemsProvider<LauncherAppWidgetProviderInfo> {

    override suspend fun getInstalledItems(
        orderByUsageStats: Boolean
    ): List<LauncherAppWidgetProviderInfo> {
        // TODO(b/457459203): Implement ordering by usage stats if needed.
        val widgetsModel = widgetsModelProvider.get()
        withContext(Executors.MODEL_EXECUTOR.asCoroutineDispatcher()) {
            // For now, we return all widgets currently tracked by the model.
            widgetsModel.update(/* packageUser= */ null)
        }
        return widgetsModel.widgetsByComponentKeyForPicker.values.mapNotNull { it.widgetInfo }
    }
}
