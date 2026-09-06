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

package com.android.launcher3.organizer.dagger

import com.android.launcher3.organizer.creation.screen.ui.foldercreator.FolderCreatorViewModel
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.SpaceCreatorViewModel
import com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer.WorkspaceOrganizerViewModel
import dagger.Subcomponent

/**
 * A sub-component that provides dependencies for the Smart Organizer flows. This component is
 * activity-scoped and is shared between OrganizerActivity and FolderCreatorActivity.
 */
@OrganizerScope
@Subcomponent(modules = [GeneratorModule::class, OrganizerModule::class])
interface OrganizerComponent {

    @Subcomponent.Builder
    interface Builder {
        fun build(): OrganizerComponent
    }

    fun getWorkspaceOrganizerViewModel(): WorkspaceOrganizerViewModel

    fun getFolderCreatorViewModel(): FolderCreatorViewModel

    fun getSpaceCreatorViewModel(): SpaceCreatorViewModel
}
