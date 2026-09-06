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

package com.android.launcher3.organizer.creation.screen.ui.foldercreator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.launcher3.LauncherApplication
import com.android.launcher3.organizer.dagger.OrganizerComponent

/** Activity dedicated to the folder creation flow. */
class FolderCreatorActivity : ComponentActivity() {

    private lateinit var organizerComponent: OrganizerComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        val appComponent = (application as LauncherApplication).appComponent

        organizerComponent = appComponent.getOrganizerComponentBuilder().build()

        super.onCreate(savedInstanceState)
        setContent {
            val folderViewModel: FolderCreatorViewModel = viewModel {
                organizerComponent.getFolderCreatorViewModel()
            }
            FolderCreator(onDismiss = { finish() }, viewModel = folderViewModel)
        }
    }
}
