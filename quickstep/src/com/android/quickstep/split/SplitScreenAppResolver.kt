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

package com.android.quickstep.split

import android.app.ActivityTaskManager
import android.app.IActivityTaskManager
import android.content.ComponentName
import android.util.Log
import com.android.launcher3.Launcher
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.OverviewComponentObserver
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.ActivityDestinationPackageResolver.Companion.getDestinationPackage

/**
 * Resolver for managing and validating application components within Split-Screen and multitasking
 * flows.
 *
 * This class provides utility methods to resolve destination packages via [ActivityTaskManager],
 * determine if an existing task is a "single instance" match for a new launch request, and fetch
 * [AppInfo] from the Launcher's internal store.
 *
 * @property context The [ActivityContext] used to interact with the system UI and Launcher state.
 */
class SplitScreenAppResolver(var context: ActivityContext?) {
    private val TAG = "SplitScreenAppResolver"

    /**
     * Gets the destination package name associated with the given component key.
     *
     * @param activityTaskManager The [ActivityTaskManager] instance for accessing task information.
     * @param componentName The [ComponentName] representing the component.
     * @return The resolved destination package name. 'null' if no destination package.
     */
    fun getResolvedDestinationPackage(
        activityTaskManager: IActivityTaskManager,
        componentName: ComponentName,
    ): String? {
        val originalPackageName = componentName.packageName
        val resolvedDestinationPackage =
            getDestinationPackage(activityTaskManager, originalPackageName)
        Log.d(
            TAG,
            "getResolvedDestinationPackage originalPackageName=$originalPackageName " +
                "DestinationPackage=$resolvedDestinationPackage",
        )
        if (resolvedDestinationPackage == originalPackageName) {
            return null
        }
        return resolvedDestinationPackage
    }

    /**
     * Checks if the given [task] belongs to the same package as the [destinationPackageName] and
     * identifies if it is single instance.
     *
     * This is primarily used in Split-Screen and multitasking flows to determine if a new instance
     * can be launched or if the existing task must be reused.
     *
     * @param task The task to evaluate against the destination.
     * @param initialTaskId The ID of the task that should be excluded
     * @param appInfo The [AppInfo] associated with the task.
     * @param destinationPackageName The package name of the component being launched.
     * @param userId The user ID associated with the launch request.
     * @return `true` if the task is from the same package/user and the app **does not** support
     *   multi-instance.
     */
    fun isTaskAppSingleInstance(
        task: Task?,
        initialTaskId: Int,
        appInfo: AppInfo?,
        destinationPackageName: String,
        userId: Int,
    ): Boolean {
        if (task == null || task.key.id == initialTaskId) {
            return false
        }
        if (userId != task.key.userId) {
            return false
        }

        val taskPackageName: String = task.key.packageName ?: return false

        if (taskPackageName != destinationPackageName) {
            return false
        }

        if (appInfo == null) {
            return false
        }

        return !appInfo.supportsMultiInstance()
    }

    /**
     * Resolves the [AppInfo] for a given [ComponentKey] by searching the Launcher's [AllAppsStore].
     *
     * This method first attempts an exact match via the provided key. If no direct match is found,
     * it falls back to a package-level search using [AppInfo.PACKAGE_KEY_COMPARATOR].
     *
     * @param key The [ComponentKey] identifying the specific app component to resolve.
     * @return The corresponding [AppInfo] if found in the [AllAppsStore]; null if the launcher is
     *   unavailable or the component does not exist in the store.
     */
    fun resolveAppInfoByComponent(key: ComponentKey): AppInfo? {
        val appsStore = getLauncher()?.appsView?.appsStore ?: return null
        return appsStore.getApp(key) ?: appsStore.getApp(key, AppInfo.PACKAGE_KEY_COMPARATOR)
    }

    /**
     * Retrieves the current [Launcher] instance if the Home and Overview components are identical.
     *
     * This check ensures that the launcher context is only returned when the system is in a state
     * where the launcher is handling both the home screen and the recent apps list (Overview).
     *
     * @return The current [Launcher] instance, or `null` if the state is not unified or the context
     *   is missing.
     */
    fun getLauncher(): Launcher? {
        val isHomeSameAsOverview =
            context?.let {
                OverviewComponentObserver.INSTANCE.get(it.asContext()).isHomeAndOverviewSame()
            } ?: false

        return if (isHomeSameAsOverview) {
            Launcher.ACTIVITY_TRACKER.getCreatedContext()
        } else {
            null
        }
    }

    fun destroy() {
        context = null
    }
}
