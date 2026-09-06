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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER
import android.content.ComponentName
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.graphics.theme.ThemePreference
import com.android.launcher3.qsb.OSEManager.Companion.OSE_LOOPER
import com.android.launcher3.qsb.OSEManager.OSEInfo
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.widget.util.WidgetSizeHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class OseWidgetManagerTest {

    @get:Rule val context = SandboxApplication()
    @get:Rule val widgetsPermission = RoboApiWrapper.grantShortcutsPermissionRule()
    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule var mlimitDevicesRule: LimitDevicesRule = LimitDevicesRule()

    @Mock lateinit var oseManager: OSEManager
    @Mock lateinit var widgetHost: QsbAppWidgetHost
    @Mock lateinit var sizeHandler: WidgetSizeHandler
    @Mock lateinit var tracker: DaggerSingletonTracker
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var themePreference: ThemePreference

    private val mockOseInfo = MutableListenableRef(OSEInfo(TEST_PKG))
    private val executor = OSE_LOOPER

    @Before
    fun setup() {
        widgetManager = context.spyService(AppWidgetManager::class.java)
        themePreference = context.appComponent.themePreference
        doReturn(mockOseInfo).whenever(oseManager).oseInfo
        doReturn(true).whenever(widgetManager).bindAppWidgetIdIfAllowed(any(), any())
    }

    @Test
    /** Test returns the wrong value. */
    @SkipOnDeviceless
    fun findSearchWidgetForPackage_returns_non_config_widget() {
        val infoWithoutConfig = WidgetUtils.findWidgetProvider(false)
        val infoWithConfig = WidgetUtils.findWidgetProvider(true)

        doReturn(listOf(infoWithConfig, infoWithoutConfig))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(any(), any())

        assertEquals(
            infoWithoutConfig.provider,
            OseWidgetManager.findSearchWidgetForPackage(context, "test")?.provider,
        )
    }

    @Test
    fun findSearchWidgetForPackage_prefers_search_widget() {
        val infoWithoutConfig = WidgetUtils.findWidgetProvider(false)
        val infoWithConfig = WidgetUtils.findWidgetProvider(true)
        val infoSearch =
            WidgetUtils.findWidgetProvider(false).apply {
                provider = ComponentName("s", "d")
                widgetCategory = WIDGET_CATEGORY_SEARCHBOX
            }

        doReturn(listOf(infoWithConfig, infoWithoutConfig, infoSearch))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(any(), any())
        assertEquals(
            infoSearch.provider,
            OseWidgetManager.findSearchWidgetForPackage(context, "test")?.provider,
        )
    }

    @Test
    fun findSearchWidgetForPackage_multipleSearchWidgets_prefers_notHiddenSearchWidget() {
        val infoWithoutConfig = WidgetUtils.findWidgetProvider(false)
        val hiddenSearchWidget =
            WidgetUtils.findWidgetProvider(false).apply {
                provider = ComponentName("s", "d")
                widgetCategory = WIDGET_CATEGORY_SEARCHBOX
                widgetFeatures = WIDGET_FEATURE_HIDE_FROM_PICKER
            }
        val notHiddenSearchWidget =
            WidgetUtils.findWidgetProvider(false).apply {
                provider = ComponentName("s", "d")
                widgetCategory = WIDGET_CATEGORY_SEARCHBOX
            }

        doReturn(listOf(infoWithoutConfig, hiddenSearchWidget, notHiddenSearchWidget))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(any(), any())
        assertEquals(
            notHiddenSearchWidget.provider,
            OseWidgetManager.findSearchWidgetForPackage(context, "test")?.provider,
        )
    }

    @Test
    fun no_update_when_current_widget_is_same() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val currentWidgetId = 1

        doReturn(listOf(widgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())
        doReturn(widgetInfo).whenever(widgetManager).getAppWidgetInfo(eq(currentWidgetId))
        doReturn(currentWidgetId).whenever(widgetHost).getBoundWidgetId()

        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}
        verify(widgetHost).setActiveWidget(eq(currentWidgetId), eq(widgetInfo))
    }

    @Test
    fun binds_widget_when_nothing_bound() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        doReturn(listOf(widgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())

        val newWidgetId = 1

        doReturn(INVALID_APPWIDGET_ID).whenever(widgetHost).getBoundWidgetId()
        doReturn(newWidgetId).whenever(widgetHost).allocateAppWidgetId()

        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}
        verify(widgetHost).setActiveWidget(eq(newWidgetId), eq(widgetInfo))
    }

    @Test
    fun nothing_active_when_no_search_widget() {
        doReturn(listOf<AppWidgetProviderInfo>())
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())
        doReturn(INVALID_APPWIDGET_ID).whenever(widgetHost).getBoundWidgetId()

        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}
        verify(widgetHost).setActiveWidget(eq(INVALID_APPWIDGET_ID), isNull())
    }

    @Test
    fun recreates_widget_when_ose_changes() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val currentWidgetId = 1

        doReturn(listOf(widgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())
        doReturn(widgetInfo).whenever(widgetManager).getAppWidgetInfo(eq(currentWidgetId))
        doReturn(currentWidgetId).whenever(widgetHost).getBoundWidgetId()

        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}
        verify(widgetHost).setActiveWidget(eq(currentWidgetId), eq(widgetInfo))

        val newWidgetId = 2
        val newPackage = "something_new"
        val newWidgetInfo =
            WidgetUtils.findWidgetProvider(true).apply {
                widgetFeatures = WIDGET_FEATURE_CONFIGURATION_OPTIONAL
            }
        assertNotEquals(newWidgetInfo, widgetInfo)
        doReturn(newWidgetId).whenever(widgetHost).allocateAppWidgetId()
        doReturn(listOf(newWidgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(newPackage), any())

        mockOseInfo.dispatchValue(OSEInfo(newPackage))
        TestUtil.runOnExecutorSync(executor) {}
        verify(widgetHost).setActiveWidget(eq(newWidgetId), eq(newWidgetInfo))
    }

    @Test
    fun nothing_active_when_ose_changes_binding_fails() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val currentWidgetId = 1

        doReturn(listOf(widgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())
        doReturn(widgetInfo).whenever(widgetManager).getAppWidgetInfo(eq(currentWidgetId))
        doReturn(currentWidgetId).whenever(widgetHost).getBoundWidgetId()

        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}
        verify(widgetHost).setActiveWidget(eq(currentWidgetId), eq(widgetInfo))

        val newWidgetId = 2
        val newPackage = "something_new"
        val newWidgetInfo =
            WidgetUtils.findWidgetProvider(true).apply {
                widgetFeatures = WIDGET_FEATURE_CONFIGURATION_OPTIONAL
            }
        assertNotEquals(newWidgetInfo, widgetInfo)
        doReturn(newWidgetId).whenever(widgetHost).allocateAppWidgetId()
        doReturn(listOf(newWidgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(newPackage), any())
        // bindAppWidgetIdIfAllowed fails.
        doReturn(false).whenever(widgetManager).bindAppWidgetIdIfAllowed(any(), any())

        mockOseInfo.dispatchValue(OSEInfo(newPackage))
        TestUtil.runOnExecutorSync(executor) {}
        verify(widgetHost).deleteAppWidgetId(newWidgetId)
        verify(widgetHost).setActiveWidget(eq(INVALID_APPWIDGET_ID), isNull())
    }

    @Test
    fun onOseInfoUpdate_withSameWidget_updatesWidgetSize() {
        // Arrange: A widget is already bound and is the correct one for the OSE package.
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val currentWidgetId = 1
        doReturn(listOf(widgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())
        doReturn(widgetInfo).whenever(widgetManager).getAppWidgetInfo(eq(currentWidgetId))
        doReturn(currentWidgetId).whenever(widgetHost).getBoundWidgetId()
        doReturn(currentWidgetId).whenever(widgetHost).getActiveWidgetId()

        // Act: Initialize the manager, which triggers the OSE info update.
        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}

        // Assert: The widget size is updated.
        verify(sizeHandler, times(2))
            .updateHotseatQsbSizeRangesAsync(eq(currentWidgetId), eq(executor))
    }

    @Test
    fun onOseInfoUpdate_withNewWidget_updatesWidgetSize() {
        // Arrange: No widget is currently bound. A new widget needs to be allocated and bound.
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val newWidgetId = 2
        doReturn(listOf(widgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())
        doReturn(INVALID_APPWIDGET_ID).whenever(widgetHost).getBoundWidgetId()
        doReturn(newWidgetId).whenever(widgetHost).allocateAppWidgetId()
        doReturn(newWidgetId).whenever(widgetHost).getActiveWidgetId()

        // Act: Initialize the manager, which triggers the OSE info update.
        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}

        // Assert: The widget size is updated for the newly bound widget.
        verify(sizeHandler, times(2)).updateHotseatQsbSizeRangesAsync(eq(newWidgetId), eq(executor))
    }

    @Test
    fun onThemeChanged_updatesWidgetOptions() {
        val widgetInfo = WidgetUtils.findWidgetProvider(false)
        val widgetId = 2
        doReturn(listOf(widgetInfo))
            .whenever(widgetManager)
            .getInstalledProvidersForPackage(eq(TEST_PKG), any())
        doReturn(INVALID_APPWIDGET_ID).whenever(widgetHost).getBoundWidgetId()
        doReturn(widgetId).whenever(widgetHost).allocateAppWidgetId()
        doReturn(widgetId).whenever(widgetHost).getActiveWidgetId()

        themePreference.setValue(null)
        createOseWidgetManager()
        TestUtil.runOnExecutorSync(executor) {}
        // Once each for handleOseInfoUpdate, initial value of themePreference,
        verify(sizeHandler, times(2)).updateHotseatQsbSizeRangesAsync(eq(widgetId), eq(executor))
        themePreference.setValue(ThemePreference.MONO_THEME_VALUE)
        TestUtil.runOnExecutorSync(executor) {}

        // themePreference change triggers to update widget options.
        verify(sizeHandler, times(3)).updateHotseatQsbSizeRangesAsync(eq(widgetId), eq(executor))
    }

    private fun createOseWidgetManager() =
        OseWidgetManager(
            context,
            oseManager,
            widgetHost,
            sizeHandler,
            context.appComponent.idp,
            tracker,
            themePreference,
        )

    companion object {
        private const val TEST_PKG = "test"
    }
}
