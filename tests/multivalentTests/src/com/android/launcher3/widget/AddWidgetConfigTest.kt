/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.widget

import android.appwidget.AppWidgetManager
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.testcomponent.WidgetConfigActivity
import com.android.launcher3.testutil.Wait
import com.android.launcher3.util.BlockingBroadcastReceiver
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.ModelTestExtensions.setEmptyModelLayout
import com.android.launcher3.util.RoboApiWrapper.grantWidgetBindPermissionRule
import com.android.launcher3.util.WidgetUtils
import com.android.launcher3.widgetpicker.listeners.WidgetPickerAddItemListener
import com.android.launcher3.widgetpicker.shared.model.WidgetInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Test verifies that when adding a widget to homescreen using [WidgetPickerAddItemListener], if the
 * widget has a configuration activity, it is shown correctly.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@SkipOnDeviceless
class AddWidgetConfigTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()
    @get:Rule var grantWidgetRule: TestRule = grantWidgetBindPermissionRule()
    @get:Rule var launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private lateinit var widgetInfo: LauncherAppWidgetProviderInfo
    private lateinit var appWidgetManager: AppWidgetManager

    private val targetContext = getInstrumentation().targetContext
    @JvmField val uiDevice: UiDevice = UiDevice.getInstance(getInstrumentation())

    private var widgetId = 0

    @Before
    @Throws(Exception::class)
    fun setUp() {
        widgetInfo = WidgetUtils.findWidgetProvider(/* hasConfigureScreen= */ true)
        appWidgetManager = AppWidgetManager.getInstance(targetContext)
    }

    @Test
    @Throws(Throwable::class)
    fun testWidgetConfig() {
        runTest(acceptConfig = true)
    }

    @Test
    @Throws(Throwable::class)
    fun testConfigCancelled() {
        runTest(acceptConfig = false)
    }

    /** @param acceptConfig accept the config activity */
    @Throws(Throwable::class)
    private fun runTest(acceptConfig: Boolean) {
        targetContext.setEmptyModelLayout()
        LauncherAppState.getInstance(targetContext).model.loadModelSync()
        launcherActivity.initializeActivity()

        // Add widget to home screen
        val monitor = WidgetConfigStartupMonitor()
        launcherActivity.executeOnLauncher { l: Launcher ->
            val addItemListener =
                WidgetPickerAddItemListener(
                    container = Favorites.CONTAINER_WIDGETS_TRAY,
                    widgetInfo = WidgetInfo.AppWidgetInfo(widgetInfo),
                )
            addItemListener.init(l, /* isHomeStarted= */ true)
        }

        uiDevice.waitForIdle()

        // Widget id for which the config activity was opened
        widgetId = monitor.widgetId

        // Verify that the widget id is valid and bound
        assertThat(appWidgetManager.getAppWidgetInfo(widgetId)).isNotNull()
        setResult(acceptConfig)

        if (acceptConfig) {
            launcherActivity.getOnceNotNull("Widget was not added") { l: Launcher ->
                // Close the resize frame before searching for widget
                AbstractFloatingView.closeAllOpenViews(l)
                l.workspace.mapOverItems(WidgetSearchCondition())
            }
            assertThat(appWidgetManager.getAppWidgetInfo(widgetId)).isNotNull()
        } else {
            // Verify that the widget id is deleted.
            Wait.atMost(
                "no widget with id",
                { appWidgetManager.getAppWidgetInfo(widgetId) == null },
            )
        }
    }

    private fun setResult(success: Boolean) {
        getInstrumentation()
            .targetContext
            .sendBroadcast(
                WidgetConfigActivity.getCommandIntent(
                    WidgetConfigActivity::class.java,
                    if (success) "clickOK" else "clickCancel",
                )
            )
        uiDevice.waitForIdle()
    }

    /** Condition for searching widget id */
    private inner class WidgetSearchCondition : ItemOperator {
        override fun evaluate(info: ItemInfo?, view: View): Boolean {
            return info is LauncherAppWidgetInfo &&
                info.providerName == widgetInfo.provider &&
                info.appWidgetId == widgetId
        }
    }

    /** Broadcast receiver for receiving widget config activity status. */
    private class WidgetConfigStartupMonitor :
        BlockingBroadcastReceiver(WidgetConfigActivity::class.java.name) {
        @get:Throws(InterruptedException::class)
        val widgetId: Int
            get() {
                val intent = checkNotNull(blockingGetExtraIntent())
                assertThat(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).isEqualTo(intent.action)
                val widgetId =
                    intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        LauncherAppWidgetInfo.NO_ID,
                    )
                assertThat(widgetId).isNotEqualTo(LauncherAppWidgetInfo.NO_ID)
                return widgetId
            }
    }
}
