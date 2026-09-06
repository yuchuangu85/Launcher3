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

import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for the various ItemInfoTranslator implementations. */
@RunWith(AndroidJUnit4::class)
class ItemInfoTranslatorsTest {

    private val workspaceItemInfoWorkspaceTranslator = WorkspaceItemInfoWorkspaceTranslator()
    private val workspaceItemInfoHotseatTranslator = WorkspaceItemInfoHotseatTranslator()
    private val workspaceItemInfoAppInFolderTranslator = WorkspaceItemInfoAppInFolderTranslator()
    private val launcherAppWidgetInfoWorkspaceTranslator =
        LauncherAppWidgetInfoWorkspaceTranslator()

    // Create a mock TranslatorRegistry and real Translators for Folder tests
    private val translatorRegistry =
        TranslatorRegistry(
            emptyMap(),
            emptyMap(),
            mapOf<Class<*>, Provider<AppInFolderTranslator<*>>>(
                WorkspaceItemInfo::class.java to
                    Provider { WorkspaceItemInfoAppInFolderTranslator() }
            ),
            emptyMap(),
            emptyMap(),
            emptyMap(),
        )
    private val folderInfoWorkspaceTranslator = FolderInfoWorkspaceTranslator(translatorRegistry)
    private val folderInfoHotseatTranslator = FolderInfoHotseatTranslator(translatorRegistry)

    @Test
    fun toWorkspaceItemSpec_appInfo_translatesCorrectly() {
        val component = ComponentName("com.test.pkg", "com.test.Activity")
        val app =
            createAppInfo(id = 1, container = 1, screenId = 0, x = 2, y = 3).apply {
                title = "Test App"
                intent = Intent().setComponent(component)
            }

        val spec = workspaceItemInfoWorkspaceTranslator.toSpec(ItemContext(app, emptyMap()))

        assertThat(spec.packageName).isEqualTo("com.test.pkg")
        assertThat(spec.className).isEqualTo("com.test.Activity")
        assertThat(spec.label).isEqualTo("Test App")
        assertThat(spec.x).isEqualTo(2)
        assertThat(spec.y).isEqualTo(3)
    }

    @Test
    fun toHotseatItemSpec_appInfo_translatesCorrectly() {
        val component = ComponentName("com.test.pkg", "com.test.Activity")
        val app =
            createAppInfo(id = 1, container = 1, screenId = 0, x = 2, y = 3).apply {
                title = "Test App"
                intent = Intent().setComponent(component)
            }

        val spec = workspaceItemInfoHotseatTranslator.toSpec(ItemContext(app, emptyMap()))

        assertThat(spec.packageName).isEqualTo("com.test.pkg")
        assertThat(spec.className).isEqualTo("com.test.Activity")
        assertThat(spec.label).isEqualTo("Test App")
        // Hotseat items don't have x/y in the spec
    }

    @Test
    fun toAppInFolderSpec_appInfo_translatesCorrectly() {
        val component = ComponentName("com.test.pkg", "com.test.Activity")
        val app =
            createAppInfo(id = 1, container = 1, screenId = 0, x = 2, y = 3).apply {
                title = "Test App"
                intent = Intent().setComponent(component)
            }

        val spec = workspaceItemInfoAppInFolderTranslator.toSpec(app)

        assertThat(spec.packageName).isEqualTo("com.test.pkg")
        assertThat(spec.className).isEqualTo("com.test.Activity")
        assertThat(spec.label).isEqualTo("Test App")
    }

    @Test
    fun toWorkspaceItemSpec_widgetInfo_translatesCorrectly() {
        val component = ComponentName("com.widget.pkg", "com.widget.Provider")
        val widget =
            LauncherAppWidgetInfo(1, component).apply {
                id = 1
                container = 1
                screenId = 0
                cellX = 0
                cellY = 0
                spanX = 4
                spanY = 2
            }

        val spec = launcherAppWidgetInfoWorkspaceTranslator.toSpec(ItemContext(widget, emptyMap()))

        assertThat(spec.packageName).isEqualTo("com.widget.pkg")
        assertThat(spec.className).isEqualTo("com.widget.Provider")
        assertThat(spec.spanX).isEqualTo(4)
        assertThat(spec.spanY).isEqualTo(2)
        assertThat(spec.x).isEqualTo(0)
        assertThat(spec.y).isEqualTo(0)
    }

    @Test
    fun toWorkspaceItemSpec_folderInfo_translatesCorrectly() {
        val folder =
            FolderInfo().apply {
                id = 10
                title = "My Folder"
                container = 1
                screenId = 0
                cellX = 0
                cellY = 0
            }
        val appInFolder =
            createAppInfo(id = 1, container = 10, screenId = 0, x = 0, y = 0).apply {
                title = "In Folder"
                intent = Intent().setComponent(ComponentName("pkg", "cls"))
            }

        val folderContentsMap = mapOf(10 to listOf<ItemInfo>(appInFolder))

        val spec = folderInfoWorkspaceTranslator.toSpec(ItemContext(folder, folderContentsMap))

        assertThat(spec.title).isEqualTo("My Folder")
        assertThat(spec.items).hasSize(1)
        assertThat(spec.items!![0].packageName).isEqualTo("pkg")
        assertThat(spec.items!![0].label).isEqualTo("In Folder")
    }

    @Test
    fun toHotseatItemSpec_folderInfo_translatesCorrectly() {
        val folder =
            FolderInfo().apply {
                id = 10
                title = "My Folder"
                container = 1
                screenId = 0
                cellX = 0
                cellY = 0
            }
        val appInFolder =
            createAppInfo(id = 1, container = 10, screenId = 0, x = 0, y = 0).apply {
                title = "In Folder"
                intent = Intent().setComponent(ComponentName("pkg", "cls"))
            }

        val folderContentsMap = mapOf(10 to listOf<ItemInfo>(appInFolder))

        val spec = folderInfoHotseatTranslator.toSpec(ItemContext(folder, folderContentsMap))

        assertThat(spec.title).isEqualTo("My Folder")
        assertThat(spec.items).hasSize(1)
        assertThat(spec.items!![0].packageName).isEqualTo("pkg")
        assertThat(spec.items!![0].label).isEqualTo("In Folder")
    }

    private fun createAppInfo(id: Int, container: Int, screenId: Int, x: Int, y: Int) =
        WorkspaceItemInfo().apply {
            this.id = id
            this.container = container
            this.screenId = screenId
            this.cellX = x
            this.cellY = y
            this.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
        }
}
