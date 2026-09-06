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

import android.content.ComponentName
import android.content.Intent
import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceData.MutableWorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.workspacefunctions.translators.AppInFolderTranslator
import com.android.launcher3.workspacefunctions.translators.FolderInfoHotseatTranslator
import com.android.launcher3.workspacefunctions.translators.FolderInfoWorkspaceTranslator
import com.android.launcher3.workspacefunctions.translators.HotseatItemTranslator
import com.android.launcher3.workspacefunctions.translators.LauncherAppWidgetInfoWorkspaceTranslator
import com.android.launcher3.workspacefunctions.translators.LauncherWorkspaceTypeTranslator
import com.android.launcher3.workspacefunctions.translators.TranslatorRegistry
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemInfoAppInFolderTranslator
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemInfoHotseatTranslator
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemInfoWorkspaceTranslator
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemTranslator
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Tests for [LauncherWorkspaceTypeTranslator]. */
@RunWith(AndroidJUnit4::class)
class LauncherWorkspaceTypeTranslatorTest {

    private val translatorRegistry: TranslatorRegistry by lazy {
        val workspaceItemTranslators =
            mutableMapOf<Class<*>, Provider<WorkspaceItemTranslator<*>>>()
        val hotseatItemTranslators = mutableMapOf<Class<*>, Provider<HotseatItemTranslator<*>>>()
        val appInFolderTranslators = mutableMapOf<Class<*>, Provider<AppInFolderTranslator<*>>>()

        val registry =
            TranslatorRegistry(
                workspaceItemTranslators,
                hotseatItemTranslators,
                appInFolderTranslators,
                emptyMap(),
                emptyMap(),
                emptyMap(),
            )

        workspaceItemTranslators[WorkspaceItemInfo::class.java] = Provider {
            WorkspaceItemInfoWorkspaceTranslator()
        }
        hotseatItemTranslators[WorkspaceItemInfo::class.java] = Provider {
            WorkspaceItemInfoHotseatTranslator()
        }
        appInFolderTranslators[WorkspaceItemInfo::class.java] = Provider {
            WorkspaceItemInfoAppInFolderTranslator()
        }
        workspaceItemTranslators[LauncherAppWidgetInfo::class.java] = Provider {
            LauncherAppWidgetInfoWorkspaceTranslator()
        }
        workspaceItemTranslators[FolderInfo::class.java] = Provider {
            FolderInfoWorkspaceTranslator(registry)
        }
        hotseatItemTranslators[FolderInfo::class.java] = Provider {
            FolderInfoHotseatTranslator(registry)
        }
        registry
    }

    private val idp: InvariantDeviceProfile =
        mock<InvariantDeviceProfile>().apply {
            numRows = 5
            numColumns = 4
        }

    private val translator = LauncherWorkspaceTypeTranslator(translatorRegistry, idp)

    @Test
    fun toSpec_spatialTransformation_groupsByScreenAndHotseat() {
        val workspace = MutableWorkspaceData()
        val item1 = createAppInfo(id = 1, container = CONTAINER_DESKTOP, screenId = 0, x = 0, y = 0)
        val item2 = createAppInfo(id = 2, container = CONTAINER_DESKTOP, screenId = 0, x = 1, y = 0)
        val item3 = createAppInfo(id = 3, container = CONTAINER_DESKTOP, screenId = 1, x = 0, y = 0)
        val item4 = createAppInfo(id = 4, container = CONTAINER_HOTSEAT, screenId = 0, x = 0, y = 0)

        workspace.replaceDataMap(
            SparseArray<ItemInfo>().apply {
                put(1, item1)
                put(2, item2)
                put(3, item3)
                put(4, item4)
            }
        )

        val spec = translator.toSpec(workspace)

        assertThat(spec.screens).hasSize(2)
        assertThat(spec.screens[0].items).hasSize(2)
        assertThat(spec.screens[1].items).hasSize(1)
        assertThat(spec.hotseat.items).hasSize(1)
    }

    @Test
    fun toSpec_itemMapping_convertsAppCorrectly() {
        val workspace = MutableWorkspaceData()
        val component = ComponentName("com.test.pkg", "com.test.Activity")
        val app =
            createAppInfo(id = 1, container = CONTAINER_DESKTOP, screenId = 0, x = 2, y = 3).apply {
                title = "Test App"
                intent = Intent().setComponent(component)
            }
        workspace.replaceDataMap(SparseArray<ItemInfo>().apply { put(1, app) })

        val spec = translator.toSpec(workspace)
        val itemSpec = spec.screens[0].items[0]

        assertThat(itemSpec.packageName).isEqualTo("com.test.pkg")
        assertThat(itemSpec.className).isEqualTo("com.test.Activity")
        assertThat(itemSpec.label).isEqualTo("Test App")
        assertThat(itemSpec.x).isEqualTo(2)
        assertThat(itemSpec.y).isEqualTo(3)
    }

    @Test
    fun toSpec_itemMapping_convertsShortcutCorrectly() {
        val workspace = MutableWorkspaceData()
        val shortcut =
            createShortcutInfo(id = 1, container = CONTAINER_DESKTOP, screenId = 0, x = 0, y = 0)
                .apply {
                    intent = Intent().setPackage("com.shortcut.pkg")
                    itemType = ITEM_TYPE_DEEP_SHORTCUT

                    intent.putExtra("shortcut_id", "my_shortcut_id")
                }
        workspace.replaceDataMap(SparseArray<ItemInfo>().apply { put(1, shortcut) })

        val spec = translator.toSpec(workspace)
        val itemSpec = spec.screens[0].items[0]

        assertThat(itemSpec.packageName).isEqualTo("com.shortcut.pkg")
    }

    @Test
    fun toSpec_itemMapping_convertsWidgetCorrectly() {
        val workspace = MutableWorkspaceData()
        val component = ComponentName("com.widget.pkg", "com.widget.Provider")
        val widget =
            LauncherAppWidgetInfo(1, component).apply {
                id = 1
                container = CONTAINER_DESKTOP
                screenId = 0
                cellX = 0
                cellY = 0
                spanX = 4
                spanY = 2
            }
        workspace.replaceDataMap(SparseArray<ItemInfo>().apply { put(1, widget) })

        val spec = translator.toSpec(workspace)
        val itemSpec = spec.screens[0].items[0]

        assertThat(itemSpec.packageName).isEqualTo("com.widget.pkg")
        assertThat(itemSpec.className).isEqualTo("com.widget.Provider")
        assertThat(itemSpec.spanX).isEqualTo(4)
        assertThat(itemSpec.spanY).isEqualTo(2)
    }

    @Test
    fun toSpec_itemMapping_convertsFolderAndRecursiveItemsCorrectly() {
        val workspace = MutableWorkspaceData()
        val folder =
            FolderInfo().apply {
                id = 10
                title = "My Folder"
                container = CONTAINER_DESKTOP
                screenId = 0
                cellX = 0
                cellY = 0
            }
        val appInFolder =
            createAppInfo(id = 1, container = 10, screenId = 0, x = 0, y = 0).apply {
                title = "In Folder"
                intent = Intent().setComponent(ComponentName("pkg", "cls"))
            }

        workspace.replaceDataMap(
            SparseArray<ItemInfo>().apply {
                put(10, folder)
                put(1, appInFolder)
            }
        )

        val spec = translator.toSpec(workspace)
        val folderSpec = spec.screens[0].items[0]

        assertThat(folderSpec.title).isEqualTo("My Folder")
        assertThat(folderSpec.items).hasSize(1)
        assertThat(folderSpec.items!![0].packageName).isEqualTo("pkg")
        assertThat(folderSpec.items!![0].label).isEqualTo("In Folder")
    }

    @Test
    fun toSpec_correctlyIgnoresUnsupportedItemTypes() {
        class TestUnsupportedItemInfo : ItemInfo()

        val workspace = MutableWorkspaceData()
        val item =
            TestUnsupportedItemInfo().apply {
                id = 1
                container = CONTAINER_DESKTOP
                screenId = 0
                cellX = 0
                cellY = 0
            }
        // use another item type to ensure that there is a screen
        val item2 = createAppInfo(id = 2, container = CONTAINER_DESKTOP, screenId = 0, x = 0, y = 0)
        workspace.replaceDataMap(
            SparseArray<ItemInfo>().apply {
                put(1, item)
                put(2, item2)
            }
        )

        val spec = translator.toSpec(workspace)

        assertThat(spec.screens).hasSize(1)
        assertThat(spec.screens[0].items).hasSize(1)
    }

    private fun createAppInfo(id: Int, container: Int, screenId: Int, x: Int, y: Int) =
        WorkspaceItemInfo().apply {
            this.id = id
            this.container = container
            this.screenId = screenId
            this.cellX = x
            this.cellY = y
            this.itemType = ITEM_TYPE_APPLICATION
        }

    private fun createShortcutInfo(id: Int, container: Int, screenId: Int, x: Int, y: Int) =
        WorkspaceItemInfo().apply {
            this.id = id
            this.container = container
            this.screenId = screenId
            this.cellX = x
            this.cellY = y
            this.itemType = ITEM_TYPE_DEEP_SHORTCUT
        }

    @Test
    fun toSpec_setsGridDimensionsFromDeviceProfile() {
        val workspace = MutableWorkspaceData()

        val spec = translator.toSpec(workspace)

        assertThat(spec.rows).isEqualTo(5)
        assertThat(spec.columns).isEqualTo(4)
    }
}
