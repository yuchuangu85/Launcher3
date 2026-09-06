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

package com.android.launcher3.model.tasks

import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo

/** Task which update widget features when a windowInfo changes */
class WidgetFeaturesUpdateTask(
    private val appWidgetId: Int,
    private val info: LauncherAppWidgetProviderInfo,
) : ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        dataModel.itemsIdMap.forEach {
            if (it is LauncherAppWidgetInfo && it.appWidgetId == appWidgetId) {
                it.updateWidgetFeatures(info)
            }
        }
        // No need to dispatch update to the repository as it should "not" affect the renderers
    }
}
