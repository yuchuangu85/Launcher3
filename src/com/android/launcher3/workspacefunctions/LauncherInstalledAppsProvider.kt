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
package com.android.launcher3.workspacefunctions

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import com.android.launcher3.appfunctions.workspace.provider.InstalledItemsProvider
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.repository.AppsListRepository
import javax.inject.Inject

/**
 * Implementation of [InstalledItemsProvider] for Launcher that returns a list of [ApplicationInfo]
 * objects representing the installed apps on the device.
 *
 * @param context The application context.
 */
class LauncherInstalledAppsProvider
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appsListRepository: AppsListRepository,
) : InstalledItemsProvider<LauncherActivityInfo> {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    override suspend fun getInstalledItems(
        orderByUsageStats: Boolean
    ): List<LauncherActivityInfo> {
        val apps = appsListRepository.appsListStateRef.value.apps
        // TODO(b/457459203): Order by usage stats
        return apps.mapNotNull {
            launcherApps.resolveActivity(it.intent, it.user)
        }
    }
}
