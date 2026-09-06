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

import android.content.pm.LauncherActivityInfo
import com.android.launcher3.appfunctions.workspace.UnplacedAppSpec
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTransaction
import com.android.launcher3.appfunctions.workspace.provider.InstalledItemsProvider
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.workspacefunctions.translators.TranslatorRegistry
import javax.inject.Inject

/**
 * The concrete implementation of the [WorkspaceRepository].
 *
 * This class acts as the bridge between the abstract AppFunctions API and the concrete launcher
 * model. It will use the [LauncherWorkspaceProvider] to read data and the `IModelWriter` (not yet
 * injected) to handle transactions.
 */
class WorkspaceRepositoryImpl
@Inject
constructor(
    private val workspaceProvider: LauncherWorkspaceProvider,
    private val installedAppsProvider: InstalledItemsProvider<LauncherActivityInfo>,
    private val installedWidgetsProvider: InstalledItemsProvider<LauncherAppWidgetProviderInfo>,
    private val translators: TranslatorRegistry,
) : WorkspaceRepository {

    override suspend fun getWorkspace(): WorkspaceSpec {
        val workspace = workspaceProvider.getWorkspace()
        return translators.translate(workspace)
    }

    override suspend fun getInstalledApps(orderByUsageStats: Boolean): List<UnplacedAppSpec> {
        val apps = installedAppsProvider.getInstalledItems(orderByUsageStats)
        return apps.map { translators.translate(it) }
    }

    override suspend fun getInstalledWidgets(orderByUsageStats: Boolean): List<UnplacedWidgetSpec> {
        val widgets = installedWidgetsProvider.getInstalledItems(orderByUsageStats)
        return widgets.map { translators.translate(it) }
    }

    override fun newTransaction(): WorkspaceTransaction {
        TODO("Not yet implemented")
    }
}
