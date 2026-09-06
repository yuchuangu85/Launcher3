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

import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
import android.util.Log
import com.android.launcher3.BaseActivity
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupData
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject

/** Provides option items when QSB is long pressed. */
open class OseWidgetOptionsProvider
@Inject
constructor(
    private val oseWidgetManager: OseWidgetManager,
    private val activityContext: ActivityContext,
) {

    open fun getOptionItems(): List<PopupData> {
        if (appWidgetSupportsReconfigure() && activityContext is BaseActivity) {
            val widgetSettingsItem =
                PopupData(
                    iconResId = R.drawable.ic_setting,
                    labelResId = R.string.gadget_setup_text,
                    category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
                    eventId = StatsLogManager.LauncherEvent.LAUNCHER_QSB_WIDGET_SETTINGS_TAP,
                ) { activityContext, _, _ ->
                    oseWidgetManager.startConfigActivity(activityContext as BaseActivity)
                }
            return listOf(widgetSettingsItem)
        }
        return emptyList()
    }

    /**
     * Checks whether the widget supports configuration.
     *
     * A widget supports configuration if it has a configuration activity
     *
     * @return true if the widget supports configuration, false otherwise.
     */
    fun appWidgetSupportsReconfigure(): Boolean {
        val providerInfo = oseWidgetManager.providerInfo.value
        val featureFlags = providerInfo?.widgetFeatures ?: 0
        val canReconfigure = (featureFlags and WIDGET_FEATURE_RECONFIGURABLE) != 0
        if (DEBUG) {
            Log.i(
                TAG,
                "configurationActivity = " +
                    providerInfo?.configure +
                    " reconfigurable= " +
                    (featureFlags and WIDGET_FEATURE_RECONFIGURABLE),
            )
        }
        return providerInfo?.configure != null && canReconfigure
    }

    companion object {
        private const val TAG = "SearchWidgetOptionsProvider"
        private const val DEBUG = false
    }
}
