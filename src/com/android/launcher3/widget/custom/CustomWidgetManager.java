/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.widget.custom;

import static com.android.launcher3.model.data.LauncherAppWidgetInfo.CUSTOM_WIDGET_ID;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.widget.LauncherAppWidgetProviderInfo.CUSTOM_WIDGET_PACKAGE;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.PluginManagerWrapper;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.systemui.plugins.CustomWidgetPlugin;
import com.android.systemui.plugins.PluginLifecycleManager;
import com.android.systemui.plugins.PluginListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * CustomWidgetManager handles custom widgets implemented as a plugin.
 */
@LauncherAppSingleton
public class CustomWidgetManager implements PluginListener<CustomWidgetPlugin> {

    public static final String NAMED_CUSTOM_WIDGETS = "CUSTOM_WIDGETS";

    public static final DaggerSingletonObject<CustomWidgetManager> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getCustomWidgetManager);

    private final Context mContext;
    private final HashMap<ComponentName, CustomWidget> mWidgets = new HashMap<>();
    private final List<CustomAppWidgetProviderInfo> mInfos = new ArrayList<>();

    private final List<Runnable> mWidgetRefreshCallbacks = new CopyOnWriteArrayList<>();
    private final @NonNull AppWidgetManager mAppWidgetManager;

    @Inject
    CustomWidgetManager(@ApplicationContext Context context,
            PluginManagerWrapper pluginManager,
            @Named(NAMED_CUSTOM_WIDGETS) Set<CustomWidget> customWidgetSet,
            DaggerSingletonTracker tracker) {
        mContext = context;
        mAppWidgetManager = AppWidgetManager.getInstance(context);

        pluginManager.addPluginListener(this, CustomWidgetPlugin.class, true);
        tracker.addCloseable(() -> pluginManager.removePluginListener(this));

        for (CustomWidget w : customWidgetSet) {
            onCustomWidgetAdded(w);
        }
    }

    private void onCustomWidgetAdded(CustomWidget widget) {
        CustomAppWidgetProviderInfo info = getAndAddInfo(
                new ComponentName(CUSTOM_WIDGET_PACKAGE, widget.getId()));
        if (info != null) {
            widget.updateWidgetInfo(mContext, info);
            mWidgets.put(info.provider, widget);
            mWidgetRefreshCallbacks.forEach(MAIN_EXECUTOR::execute);
        }
    }

    @Override
    public void onPluginLoaded(@NonNull CustomWidgetPlugin plugin,
            @NonNull Context pluginContext,
            @NonNull PluginLifecycleManager<CustomWidgetPlugin> manager) {
        onCustomWidgetAdded(new CustomWidgetPluginWrapper(
                plugin, manager.getComponentName().flattenToShortString()));
    }

    @Override
    public void onPluginUnloaded(
            @NonNull CustomWidgetPlugin plugin,
            @NonNull PluginLifecycleManager<CustomWidgetPlugin> manager) {
        mWidgets.remove(new ComponentName(
                CUSTOM_WIDGET_PACKAGE, manager.getComponentName().flattenToShortString()));
    }

    @VisibleForTesting
    @NonNull
    Map<ComponentName, CustomWidget> getWidgets() {
        return mWidgets;
    }

    /**
     * Inject a callback function to refresh the widgets.
     * @return a closeable to remove this callback
     */
    public SafeCloseable addWidgetRefreshCallback(Runnable callback) {
        mWidgetRefreshCallbacks.add(callback);
        return () -> mWidgetRefreshCallbacks.remove(callback);
    }

    /**
     * Creates a view corresponding to the [info] using [context]
     */
    public AppWidgetHostView createView(Context context, LauncherAppWidgetProviderInfo info) {
        CustomWidget widget = mWidgets.get(info.provider);
        return widget != null ? widget.createView(context, info) : new AppWidgetHostView(context);
    }

    /**
     * Returns the stream of custom widgets.
     */
    @NonNull
    public Stream<CustomAppWidgetProviderInfo> stream() {
        return mInfos.stream();
    }

    /**
     * Returns the widget provider in respect to given widget id.
     */
    @Nullable
    public LauncherAppWidgetProviderInfo getWidgetProvider(ComponentName cn) {
        // If the info is not present, add a placeholder info since the
        // plugin might get loaded later
        return mInfos.stream()
                .filter(w -> w.getComponent().equals(cn))
                .findAny()
                .orElseGet(() -> getAndAddInfo(cn));
    }

    /**
     * Returns an id to set as the appWidgetId for a custom widget.
     */
    public int allocateCustomAppWidgetId(ComponentName componentName) {
        int total = mInfos.size();
        for (int i = 0; i < total; i++) {
            if (componentName.equals(mInfos.get(i).provider)) {
                return CUSTOM_WIDGET_ID - i;
            }
        }
        return CUSTOM_WIDGET_ID - total;
    }

    @Nullable
    private CustomAppWidgetProviderInfo getAndAddInfo(ComponentName cn) {
        for (CustomAppWidgetProviderInfo info : mInfos) {
            if (info.provider.equals(cn)) return info;
        }

        List<AppWidgetProviderInfo> providers = mAppWidgetManager
                .getInstalledProvidersForProfile(Process.myUserHandle());
        if (providers.isEmpty()) return null;
        Parcel parcel = Parcel.obtain();
        providers.get(0).writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CustomAppWidgetProviderInfo info = new CustomAppWidgetProviderInfo(parcel, false);
        parcel.recycle();

        info.provider = cn;
        info.initialLayout = 0;
        mInfos.add(info);
        return info;
    }
}
