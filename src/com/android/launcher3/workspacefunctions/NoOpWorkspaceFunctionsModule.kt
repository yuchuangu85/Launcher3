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

import com.android.launcher3.appfunctions.workspace.HotseatSpec
import com.android.launcher3.appfunctions.workspace.UnplacedAppSpec
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceAppFunctions
import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTransaction
import dagger.Module
import dagger.Provides

/**
 * A module that provides a dummy [WorkspaceAppFunctions] for dagger graph that doesn't involve
 * workspace functions e.g. launcher preview.
 */
@Module
class NoOpWorkspaceFunctionsModule {

    @Provides
    fun provideWorkspaceAppFunctions(): WorkspaceAppFunctions {
        return WorkspaceAppFunctions(NoOpWorkspaceRepository())
    }

    private class NoOpWorkspaceRepository : WorkspaceRepository {
        override suspend fun getWorkspace(): WorkspaceSpec {
            return WorkspaceSpec(
                screens = listOf(),
                hotseat = HotseatSpec(listOf()),
                rows = null,
                columns = null,
            )
        }

        override suspend fun getInstalledApps(orderByUsageStats: Boolean): List<UnplacedAppSpec> {
            return listOf()
        }

        override suspend fun getInstalledWidgets(
            orderByUsageStats: Boolean
        ): List<UnplacedWidgetSpec> {
            return listOf()
        }

        override fun newTransaction(): WorkspaceTransaction {
            throw UnsupportedOperationException("Not implemented")
        }
    }
}
