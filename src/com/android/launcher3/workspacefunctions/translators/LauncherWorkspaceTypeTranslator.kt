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
package com.android.launcher3.workspacefunctions.translators

import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.appfunctions.workspace.HotseatSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceScreenSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTypeTranslator
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import javax.inject.Inject

/** A translator that converts between [WorkspaceData] and [WorkspaceSpec]. */
class LauncherWorkspaceTypeTranslator
@Inject
constructor(private val translators: TranslatorRegistry, private val idp: InvariantDeviceProfile) :
    WorkspaceTypeTranslator<WorkspaceData> {

    override fun toSpec(workspace: WorkspaceData): WorkspaceSpec {
        val desktopItems = mutableListOf<ItemInfo>()
        val hotseatItems = mutableListOf<ItemInfo>()
        val folderContentsMap = mutableMapOf<Int, MutableList<ItemInfo>>()

        // We only want WorkspaceItemInfo, LauncherAppWidgetInfo, and FolderInfo.
        val filteredItems = workspace.filter {
            it is WorkspaceItemInfo || it is LauncherAppWidgetInfo || it is FolderInfo
        }

        for (item in filteredItems) {
            when (item.container) {
                CONTAINER_DESKTOP -> desktopItems.add(item)
                CONTAINER_HOTSEAT -> hotseatItems.add(item)
                else -> {
                    if (item.container > 0) {
                        folderContentsMap.getOrPut(item.container) { mutableListOf() }.add(item)
                    }
                }
            }
        }

        val screenSpecs =
            desktopItems
                .groupBy { it.screenId }
                .toSortedMap()
                .map { (_, items) ->
                    WorkspaceScreenSpec(
                        items.map { item ->
                            translators.translate(ItemContext(item, folderContentsMap))
                        }
                    )
                }

        val hotseatSpec =
            HotseatSpec(
                hotseatItems.map { item ->
                    translators.translate(ItemContext(item, folderContentsMap))
                }
            )

        return WorkspaceSpec(
            screens = screenSpecs,
            hotseat = hotseatSpec,
            rows = idp.numRows,
            columns = idp.numColumns,
        )
    }
}
