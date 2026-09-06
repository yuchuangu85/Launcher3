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

package com.android.launcher3.widget.custom

import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Context
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.systemui.plugins.CustomWidgetPlugin

/** Transforms a [CustomWidgetPlugin] into a [CustomWidget] */
class CustomWidgetPluginWrapper(private val plugin: CustomWidgetPlugin, override val id: String) :
    CustomWidget {

    override fun updateWidgetInfo(context: Context, info: LauncherAppWidgetProviderInfo) =
        plugin.updateWidgetInfo(info)

    override fun createView(context: Context, info: LauncherAppWidgetProviderInfo) =
        LauncherAppWidgetHostView(context).apply {
            setAppWidget(INVALID_APPWIDGET_ID, info)
            plugin.onViewCreated(this)
        }
}
