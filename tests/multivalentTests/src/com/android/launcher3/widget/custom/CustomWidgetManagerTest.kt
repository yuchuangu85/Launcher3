/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget.custom

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.systemui.plugins.CustomWidgetPlugin
import com.android.systemui.plugins.PluginLifecycleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class CustomWidgetManagerTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val context = SandboxApplication()

    private lateinit var mockAppWidgetManager: AppWidgetManager

    private lateinit var underTest: CustomWidgetManager

    @Mock private lateinit var pluginManager: PluginManagerWrapper
    @Mock private lateinit var tracker: DaggerSingletonTracker

    @Captor private lateinit var closableCaptor: ArgumentCaptor<SafeCloseable>

    @Before
    fun setUp() {
        mockAppWidgetManager = context.spyService()
        underTest = CustomWidgetManager(context, pluginManager, emptySet(), tracker)
    }

    @Test
    fun plugin_manager_added_after_initialization() {
        verify(pluginManager)
            .addPluginListener(same(underTest), same(CustomWidgetPlugin::class.java), eq(true))
    }

    @Test
    fun close_widget_manager_should_remove_plugin_listener() {
        verify(tracker).addCloseable(capture(closableCaptor))
        closableCaptor.allValues.forEach(SafeCloseable::close)
        verify(pluginManager).removePluginListener(same(underTest))
    }

    @Test
    fun on_plugin_connected_no_provider_info() {
        doReturn(emptyList<LauncherAppWidgetProviderInfo>())
            .whenever(mockAppWidgetManager)
            .getInstalledProvidersForProfile(any())
        val mockPlugin: CustomWidgetPlugin = mock()
        underTest.onPluginLoaded(mockPlugin, context, fakePluginLifeCycle())
        assertEquals(0, underTest.widgets.size)
    }

    @Test
    fun on_plugin_connected_exist_provider_info() {
        doReturn(listOf(WidgetUtils.createAppWidgetProviderInfo(TEST_COMPONENT_NAME)))
            .whenever(mockAppWidgetManager)
            .getInstalledProvidersForProfile(eq(Process.myUserHandle()))
        val mockPlugin: CustomWidgetPlugin = mock()
        underTest.onPluginLoaded(mockPlugin, context, fakePluginLifeCycle())
        assertEquals(1, underTest.widgets.size)
    }

    @Test
    fun on_plugin_disconnected() {
        doReturn(listOf(WidgetUtils.createAppWidgetProviderInfo(TEST_COMPONENT_NAME)))
            .whenever(mockAppWidgetManager)
            .getInstalledProvidersForProfile(eq(Process.myUserHandle()))
        val mockPlugin: CustomWidgetPlugin = mock()
        underTest.onPluginLoaded(mockPlugin, context, fakePluginLifeCycle())
        underTest.onPluginUnloaded(mockPlugin, fakePluginLifeCycle())
        assertEquals(0, underTest.widgets.size)
    }

    @Test
    fun on_view_created() {
        val mockWidgetView: LauncherAppWidgetHostView = mock()
        val mockWidget: CustomWidget = mock()
        doReturn(mockWidgetView).whenever(mockWidget).createView(any(), any())

        underTest.widgets[TEST_COMPONENT_NAME] = mockWidget
        val mockProviderInfo: CustomAppWidgetProviderInfo = mock()
        mockProviderInfo.provider = TEST_COMPONENT_NAME
        assertEquals(mockWidgetView, underTest.createView(context, mockProviderInfo))
    }

    @Test
    fun generate_stream() {
        assertTrue(underTest.stream().toList().isEmpty())
        doReturn(listOf(WidgetUtils.createAppWidgetProviderInfo(TEST_COMPONENT_NAME)))
            .whenever(mockAppWidgetManager)
            .getInstalledProvidersForProfile(eq(Process.myUserHandle()))
        val mockPlugin: CustomWidgetPlugin = mock()
        underTest.onPluginLoaded(mockPlugin, context, fakePluginLifeCycle())
        assertEquals(1, underTest.stream().toList().size)
    }

    private fun fakePluginLifeCycle() =
        mock<PluginLifecycleManager<CustomWidgetPlugin>>().apply {
            doReturn(TEST_COMPONENT_NAME).whenever(this).componentName
        }

    companion object {
        private const val TEST_CLASS = "TEST_CLASS"
        private val TEST_COMPONENT_NAME =
            ComponentName(getInstrumentation().targetContext.packageName, TEST_CLASS)
    }
}
