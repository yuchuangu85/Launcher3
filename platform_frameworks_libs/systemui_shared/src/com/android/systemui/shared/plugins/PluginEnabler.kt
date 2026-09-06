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

import android.content.ComponentName

/** Enables and disables plugins. */
interface PluginEnabler {
    enum class DisableReason(val value: Int, val autoEnable: Boolean) {
        ENABLED(0, autoEnable = true),
        DISABLED_MANUALLY(1, autoEnable = false),
        DISABLED_INVALID_VERSION(2, autoEnable = true),
        DISABLED_FROM_EXPLICIT_CRASH(3, autoEnable = true),
        DISABLED_FROM_SYSTEM_CRASH(4, autoEnable = true),
        DISABLED_UNKNOWN(100, autoEnable = false);

        companion object {
            private val valueMap by lazy { entries.associateBy { it.value } }

            fun fromValue(value: Int): DisableReason = valueMap[value] ?: DISABLED_UNKNOWN
        }
    }

    /** Enables plugin via the PackageManager. */
    fun setEnabled(component: ComponentName)

    /** Disables a plugin via the PackageManager and records the reason for disabling. */
    fun setDisabled(component: ComponentName, reason: DisableReason)

    /** Returns true if the plugin is enabled in the PackageManager. */
    fun isEnabled(component: ComponentName): Boolean

    /**
     * Returns the reason that a plugin is disabled, (if it is).
     * - It should return [DisableReason.ENABLED] if the plugin is turned on.
     * - It should return [DisableReason.DISABLED_UNKNOWN] if the plugin is off for unknown reasons.
     */
    fun getDisableReason(componentName: ComponentName): DisableReason

    /**
     * This is provided for ease of integration, but it should be replaced at some point with an
     * implementation that persists enabled status to avoid consistent crashes from bad plugins.
     */
    @Deprecated("Prefer to use an implementation which persists plugin status")
    class AlwaysEnabled() : PluginEnabler {
        override fun setEnabled(component: ComponentName) {}

        override fun setDisabled(component: ComponentName, reason: DisableReason) {}

        override fun isEnabled(component: ComponentName): Boolean {
            return true
        }

        override fun getDisableReason(componentName: ComponentName): DisableReason {
            return DisableReason.ENABLED
        }
    }
}
