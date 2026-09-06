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

import com.android.launcher3.appfunctions.workspace.provider.WorkspaceProvider
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.repository.HomeScreenRepository
import javax.inject.Inject

/** A provider that manages the [WorkspaceData] for AppFunctions. */
class LauncherWorkspaceProvider
@Inject
constructor(private val homeScreenRepository: HomeScreenRepository) :
    WorkspaceProvider<WorkspaceData> {

    override suspend fun getWorkspace(): WorkspaceData {
        return homeScreenRepository.workspaceState.value
    }
}
