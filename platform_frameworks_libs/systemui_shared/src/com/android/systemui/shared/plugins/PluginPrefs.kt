/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.shared.plugins

import android.content.Context
import androidx.core.content.edit

/**
 * Storage for all plugin actions in SharedPreferences.
 *
 * This allows the list of actions that the Tuner needs to search for to be generated instead of
 * hard coded.
 */
class PluginPrefs(context: Context) {
    private val sharedPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val pluginActions = sharedPrefs.getStringSet(PLUGIN_ACTIONS, null) ?: mutableSetOf()

    @get:Synchronized
    val pluginList: Set<String>
        get() = pluginActions.toSet()

    @Synchronized
    fun addAction(action: String) {
        if (pluginActions.add(action)) {
            sharedPrefs.edit { putStringSet(PLUGIN_ACTIONS, pluginActions) }
        }
    }

    @Synchronized
    fun removeAction(action: String) {
        if (pluginActions.remove(action)) {
            sharedPrefs.edit { putStringSet(PLUGIN_ACTIONS, pluginActions) }
        }
    }

    var hasPlugins: Boolean
        get() = sharedPrefs.getBoolean(HAS_PLUGINS, false)
        set(value) {
            sharedPrefs.edit { putBoolean(HAS_PLUGINS, value) }
        }

    companion object {
        private const val PREFS = "plugin_prefs"

        private const val PLUGIN_ACTIONS = "actions"
        private const val HAS_PLUGINS = "plugins"

        @JvmStatic
        @Deprecated(
            "Prefer non-static version",
            ReplaceWith(
                "PluginPrefs(context).hasPlugins",
                "com.android.systemui.shared.plugins.PluginPrefs",
            ),
        )
        fun hasPlugins(context: Context): Boolean {
            return PluginPrefs(context).hasPlugins
        }

        @JvmStatic
        @Deprecated(
            "Prefer non-static version",
            ReplaceWith(
                "PluginPrefs(context).hasPlugins = true",
                "com.android.systemui.shared.plugins.PluginPrefs",
            ),
        )
        fun setHasPlugins(context: Context) {
            PluginPrefs(context).hasPlugins = true
        }
    }
}
