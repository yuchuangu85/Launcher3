/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.util;

import static android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER;
import static android.os.Process.myUserHandle;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Instrumentation;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetManagerHelper;

/**
 * Common method for widget binding
 */
public class WidgetUtils {

    private static final String TAG = "WidgetUtils";

    /**
     * Creates a LauncherAppWidgetInfo corresponding to {@param info}
     *
     * @param bindWidget if true the info is bound and a valid widgetId is assigned to
     *                   the LauncherAppWidgetInfo
     */
    public static LauncherAppWidgetInfo createWidgetInfo(
            LauncherAppWidgetProviderInfo info, Context targetContext, boolean bindWidget) {
        LauncherAppWidgetInfo item = new LauncherAppWidgetInfo(
                LauncherAppWidgetInfo.NO_ID, info.provider);
        item.spanX = info.minSpanX;
        item.spanY = info.minSpanY;
        item.minSpanX = info.minSpanX;
        item.minSpanY = info.minSpanY;
        item.user = info.getProfile();
        item.cellX = 0;
        item.cellY = 1;
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;

        if (bindWidget) {
            PendingAddWidgetInfo pendingInfo =
                    new PendingAddWidgetInfo(
                            info, LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY);
            pendingInfo.spanX = item.spanX;
            pendingInfo.spanY = item.spanY;
            pendingInfo.minSpanX = item.minSpanX;
            pendingInfo.minSpanY = item.minSpanY;
            Bundle options = pendingInfo.getDefaultSizeOptions(targetContext);

            LauncherWidgetHolder holder = LauncherWidgetHolder.newInstance(targetContext);
            try {
                int widgetId = holder.allocateAppWidgetId();
                if (!new WidgetManagerHelper(targetContext)
                        .bindAppWidgetIdIfAllowed(widgetId, info, options)) {
                    holder.deleteAppWidgetId(widgetId);
                    throw new IllegalArgumentException("Unable to bind widget id");
                }
                item.appWidgetId = widgetId;
            } finally {
                // Necessary to destroy the holder to free up possible activity context
                holder.destroy();
            }
        }
        return item;
    }

    /**
     * Creates a {@link AppWidgetProviderInfo} for the provided component name
     *
     * @param cn component name of the appwidget provider
     * @param hideFromPicker indicates if the widget should appear in widget picker
     */
    public static AppWidgetProviderInfo createAppWidgetProviderInfo(ComponentName cn,
            boolean hideFromPicker) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.uid = Process.myUid();
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        if (hideFromPicker) {
            info.widgetFeatures = WIDGET_FEATURE_HIDE_FROM_PICKER;
        }
        info.providerInfo = activityInfo;
        info.provider = cn;
        return info;
    }

    /**
     * Creates a {@link AppWidgetProviderInfo} for the provided component name
     *
     * @param cn component name of the appwidget provider
     */
    public static AppWidgetProviderInfo createAppWidgetProviderInfo(ComponentName cn) {
        return createAppWidgetProviderInfo(cn, /*hideFromPicker=*/ false);
    }

    /**
     * Finds a widget provider which can fit on the home screen.
     *
     * @param hasConfigureScreen if true, a provider with a config screen is returned.
     */
    public static LauncherAppWidgetProviderInfo findWidgetProvider(boolean hasConfigureScreen) {
        Instrumentation i = getInstrumentation();
        ComponentName cn = new ComponentName(i.getContext(),
                hasConfigureScreen ? AppWidgetWithConfig.class : AppWidgetNoConfig.class);
        Log.d(TAG, "findWidgetProvider componentName=" + cn.flattenToString());
        WidgetManagerHelper widgetManagerHelper = new WidgetManagerHelper(i.getTargetContext());
        LauncherAppWidgetProviderInfo providerInfo =
                widgetManagerHelper.findProvider(cn, myUserHandle());
        if (providerInfo != null) {
            return providerInfo;
        }
        return LauncherAppWidgetProviderInfo.fromProviderInfo(i.getTargetContext(),
                createAppWidgetProviderInfo(cn));
    }
}
