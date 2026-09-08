/*
 * Copyright (C) 2026
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
package com.android.systemui.plugins;

import android.content.Context;

/** Callback API kept for Launcher plugin extension points. */
public interface PluginListener<T extends Plugin> {
    default void onPluginConnected(T plugin, Context pluginContext) {}

    default boolean onPluginAttached(PluginLifecycleManager<T> manager) {
        return true;
    }

    default void onPluginDisconnected(T plugin) {}

    default void onPluginDetached(PluginLifecycleManager<T> manager) {}

    default void onPluginLoaded(T plugin, Context pluginContext, PluginLifecycleManager<T> manager) {}

    default void onPluginUnloaded(T plugin, PluginLifecycleManager<T> manager) {}
}
