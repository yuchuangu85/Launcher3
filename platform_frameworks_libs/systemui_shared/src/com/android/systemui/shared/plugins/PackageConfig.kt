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
package com.android.systemui.shared.plugins

import android.content.ComponentName

/** Holds configuration information about specific plugin packages */
class PackageConfig(vararg privilegedNames: String) {
    private val privilegedPackages: Set<String>
    private val privilegedComponents: Set<ComponentName>

    init {
        val packages = mutableSetOf<String>()
        val components = mutableSetOf<ComponentName>()
        for (name in privilegedNames) {
            val component = ComponentName.unflattenFromString(name)
            if (component != null) {
                components.add(component)
                packages.add(component.packageName)
            } else {
                packages.add(name)
            }
        }

        privilegedPackages = packages
        privilegedComponents = components
    }

    fun isPrivileged(pluginName: ComponentName): Boolean {
        return pluginName in privilegedComponents || isPackagePrivileged(pluginName.packageName)
    }

    fun isPackagePrivileged(packageName: String): Boolean {
        return packageName in privilegedPackages
    }
}
