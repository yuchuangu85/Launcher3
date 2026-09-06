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

import com.android.launcher3.appfunctions.workspace.AppInFolderSpec
import com.android.launcher3.appfunctions.workspace.HotseatItemSpec
import com.android.launcher3.appfunctions.workspace.Translator
import com.android.launcher3.appfunctions.workspace.WorkspaceItemSpec
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import javax.inject.Inject

/** Bundles an [ItemInfo] with the [folderContentsMap] needed for translation. */
data class ItemContext<T : ItemInfo>(val item: T, val folderContentsMap: Map<Int, List<ItemInfo>>)

/** A translator for items on a workspace screen. */
interface WorkspaceItemTranslator<T : ItemInfo> : Translator<ItemContext<T>, WorkspaceItemSpec>

/** A translator for items in the hotseat. */
interface HotseatItemTranslator<T : ItemInfo> : Translator<ItemContext<T>, HotseatItemSpec>

/** A translator for items within a folder. */
interface AppInFolderTranslator<T : ItemInfo> : Translator<T, AppInFolderSpec>

/** Translates a [WorkspaceItemInfo] to a [WorkspaceItemSpec]. */
class WorkspaceItemInfoWorkspaceTranslator @Inject constructor() :
    WorkspaceItemTranslator<WorkspaceItemInfo> {
    override fun toSpec(obj: ItemContext<WorkspaceItemInfo>): WorkspaceItemSpec {
        val item = obj.item
        return WorkspaceItemSpec(
            x = item.cellX,
            y = item.cellY,
            packageName = item.intent?.component?.packageName ?: item.intent?.`package`,
            className = item.intent?.component?.className,
            label = item.title?.toString(),
            shortcutId = item.intent?.getStringExtra("shortcut_id"),
        )
    }
}

/** Translates a [WorkspaceItemInfo] to a [HotseatItemSpec]. */
class WorkspaceItemInfoHotseatTranslator @Inject constructor() :
    HotseatItemTranslator<WorkspaceItemInfo> {
    override fun toSpec(obj: ItemContext<WorkspaceItemInfo>): HotseatItemSpec {
        val item = obj.item
        return HotseatItemSpec(
            packageName = item.intent?.component?.packageName ?: item.intent?.`package`,
            className = item.intent?.component?.className,
            label = item.title?.toString(),
        )
    }
}

/** Translates a [WorkspaceItemInfo] to an [AppInFolderSpec]. */
class WorkspaceItemInfoAppInFolderTranslator @Inject constructor() :
    AppInFolderTranslator<WorkspaceItemInfo> {
    override fun toSpec(obj: WorkspaceItemInfo): AppInFolderSpec {
        return AppInFolderSpec(
            packageName = obj.intent?.component?.packageName ?: obj.intent?.`package` ?: "",
            className = obj.intent?.component?.className ?: "",
            label = obj.title?.toString(),
        )
    }
}

/** Translates a [LauncherAppWidgetInfo] to a [WorkspaceItemSpec]. */
class LauncherAppWidgetInfoWorkspaceTranslator @Inject constructor() :
    WorkspaceItemTranslator<LauncherAppWidgetInfo> {
    override fun toSpec(obj: ItemContext<LauncherAppWidgetInfo>): WorkspaceItemSpec {
        val item = obj.item
        return WorkspaceItemSpec(
            x = item.cellX,
            y = item.cellY,
            spanX = item.spanX,
            spanY = item.spanY,
            packageName = item.providerName.packageName,
            className = item.providerName.className,
        )
    }
}

/** Translates a [FolderInfo] to a [WorkspaceItemSpec]. */
class FolderInfoWorkspaceTranslator
@Inject
constructor(private val translators: TranslatorRegistry) : WorkspaceItemTranslator<FolderInfo> {
    override fun toSpec(obj: ItemContext<FolderInfo>): WorkspaceItemSpec {
        val item = obj.item
        return WorkspaceItemSpec(
            x = item.cellX,
            y = item.cellY,
            title = item.title?.toString(),
            items =
                obj.folderContentsMap[item.id]?.map {
                    translators.translate(it)
                },
        )
    }
}

/** Translates a [FolderInfo] to a [HotseatItemSpec]. */
class FolderInfoHotseatTranslator @Inject constructor(private val translators: TranslatorRegistry) :
    HotseatItemTranslator<FolderInfo> {
    override fun toSpec(obj: ItemContext<FolderInfo>): HotseatItemSpec {
        val item = obj.item
        return HotseatItemSpec(
            title = item.title?.toString(),
            items =
                obj.folderContentsMap[item.id]?.map {
                    translators.translate(it)
                },
        )
    }
}
