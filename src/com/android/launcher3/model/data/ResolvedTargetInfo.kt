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

package com.android.launcher3.model.data

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import com.android.launcher3.util.ComponentKey

/** Data class for activity alias target activity component and activity component. */
data class ResolvedTargetInfo(
    val targetActivityComponentName: ComponentName?,
    val componentName: ComponentName?,
    val user: UserHandle,
) {
    /**
     * Returns a [ComponentKey] representing the resolved target activity. If it's activity alias
     * target activity component is set, it will be used. Otherwise, the key of the activity
     * component will be returned.
     */
    fun getTargetComponentKey(): ComponentKey? {
        if (targetActivityComponentName != null) {
            return ComponentKey(targetActivityComponentName, user)
        }

        if (componentName != null) {
            return ComponentKey(componentName, user)
        }

        return null
    }

    /**
     * Checks if this resolved target matches the provided [baseIntent] and [userHandle].
     *
     * The matching logic first verifies the [userHandle]. If it matches, it then checks if the
     * [baseIntent]'s component name matches either the resolved target's primary [componentName] or
     * its [targetActivityComponentName].
     *
     * @param baseIntent The base intent for the task containing the component to match.
     * @param userHandle The handle of the user the task belongs to.
     * @return `true` if the user matches and the intent component matches a known target component.
     */
    fun matchTaskLaunchActivity(baseIntent: Intent, userHandle: UserHandle): Boolean {
        if (userHandle != user) return false

        val intentComponent = baseIntent.component ?: return false
        return intentComponent in listOf(componentName, targetActivityComponentName)
    }
}
