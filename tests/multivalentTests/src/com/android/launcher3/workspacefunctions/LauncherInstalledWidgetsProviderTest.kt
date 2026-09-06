/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.workspacefunctions

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.WidgetsModel
import com.android.launcher3.pm.ShortcutConfigActivityInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [LauncherInstalledWidgetsProvider]. */
@RunWith(AndroidJUnit4::class)
class LauncherInstalledWidgetsProviderTest {

    private val widgetsModel = mock<WidgetsModel>()
    private lateinit var provider: LauncherInstalledWidgetsProvider

    @Before
    fun setUp() {
        provider = LauncherInstalledWidgetsProvider(Provider { widgetsModel })
    }

    @Test
    fun getInstalledItems_returnsWidgetsFromModel() = runTest {
        val widgetItem1 = createWidgetItem("pkg1", "cls1")
        val widgetItem2 = createWidgetItem("pkg2", "cls2")
        val shortcutItem = createShortcutItem("pkg3", "cls3")

        val widgetsMap =
            mapOf(
                widgetItem1 as ComponentKey to widgetItem1,
                widgetItem2 as ComponentKey to widgetItem2,
                shortcutItem as ComponentKey to shortcutItem,
            )
        whenever(widgetsModel.widgetsByComponentKeyForPicker).thenReturn(widgetsMap)

        val items = provider.getInstalledItems(false)

        assertThat(items).hasSize(2)
        assertThat(items).containsExactly(widgetItem1.widgetInfo, widgetItem2.widgetInfo)
    }

    private fun createWidgetItem(pkg: String, cls: String): WidgetItem {
        val widgetInfo =
            object : LauncherAppWidgetProviderInfo() {
                init {
                    provider = ComponentName(pkg, cls)
                    providerInfo =
                        ActivityInfo().apply {
                            applicationInfo = ApplicationInfo().apply { uid = 0 }
                        }
                }
            }
        val iconCache = mock<IconCache>()
        whenever(iconCache.getTitleNoCache(widgetInfo)).thenReturn("title")
        val idp = mock<InvariantDeviceProfile>()
        return WidgetItem(widgetInfo, idp, iconCache, mock<Context>())
    }

    private fun createShortcutItem(pkg: String, cls: String): WidgetItem {
        val shortcutInfo = mock<ShortcutConfigActivityInfo>()
        whenever(shortcutInfo.component).thenReturn(ComponentName(pkg, cls))
        whenever(shortcutInfo.user).thenReturn(Process.myUserHandle())
        whenever(shortcutInfo.isPersistable).thenReturn(true)
        val iconCache = mock<IconCache>()
        whenever(iconCache.getTitleNoCache(shortcutInfo)).thenReturn("title")
        return WidgetItem(shortcutInfo, iconCache)
    }

    @Test
    fun getInstalledItems_returnsEmptyListWhenNoWidgets() = runTest {
        whenever(widgetsModel.widgetsByComponentKeyForPicker).thenReturn(emptyMap())

        val items = provider.getInstalledItems(false)

        assertThat(items).isEmpty()
    }
}
