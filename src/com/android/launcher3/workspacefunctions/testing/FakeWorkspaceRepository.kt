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

package com.android.launcher3.workspacefunctions.testing

import com.android.launcher3.appfunctions.workspace.HotseatSpec
import com.android.launcher3.appfunctions.workspace.UnplacedAppSpec
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTransaction

class FakeWorkspaceRepository : WorkspaceRepository {
    var workspace = WorkspaceSpec(emptyList(), HotseatSpec(emptyList()), null, null)
    var lastTransaction: FakeWorkspaceTransaction? = null

    override suspend fun getWorkspace(): WorkspaceSpec = workspace

    override fun newTransaction(): WorkspaceTransaction {
        lastTransaction = FakeWorkspaceTransaction()
        return lastTransaction!!
    }

    override suspend fun getInstalledApps(orderByUsageStats: Boolean): List<UnplacedAppSpec> = emptyList()

    override suspend fun getInstalledWidgets(orderByUsageStats: Boolean): List<UnplacedWidgetSpec> = emptyList()
}