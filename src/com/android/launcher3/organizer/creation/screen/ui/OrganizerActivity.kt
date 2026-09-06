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

package com.android.launcher3.organizer.creation.screen.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.launcher3.LauncherApplication
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.CreateScreen
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.SpaceCreatorViewModel
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout.ChooseLayout
import com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer.WorkspaceOrganizer
import com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer.WorkspaceOrganizerViewModel
import com.android.launcher3.organizer.dagger.OrganizerComponent

/** Activity that handles workspace organizer and space creation flows. */
class OrganizerActivity : ComponentActivity() {

    private lateinit var organizerComponent: OrganizerComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        val startMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_WORKSPACE

        val appComponent = (application as LauncherApplication).appComponent

        organizerComponent = appComponent.getOrganizerComponentBuilder().build()

        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            val activity = LocalActivity.current as ComponentActivity

            NavHost(navController = navController, startDestination = startMode) {
                composable(MODE_WORKSPACE) {
                    val workspaceViewModel: WorkspaceOrganizerViewModel =
                        viewModel(activity) { organizerComponent.getWorkspaceOrganizerViewModel() }
                    WorkspaceOrganizer(
                        onArrowBack = { finish() },
                        onNavigateToSpaceCreator = { navController.navigate(MODE_SPACE) },
                        viewModel = workspaceViewModel,
                    )
                }

                composable(MODE_SPACE) {
                    val spaceViewModel: SpaceCreatorViewModel =
                        viewModel(activity) { organizerComponent.getSpaceCreatorViewModel() }
                    CreateScreen(
                        onArrowBack = { if (!navController.popBackStack()) finish() },
                        viewModel = spaceViewModel,
                        onNavigateToChooser = { topic ->
                            spaceViewModel.prepareLayoutsForTopic(topic)
                            navController.navigate(MODE_SPACE_CHOOSER)
                        },
                    )
                }

                composable(MODE_SPACE_CHOOSER) {
                    val spaceViewModel: SpaceCreatorViewModel =
                        viewModel(activity) { organizerComponent.getSpaceCreatorViewModel() }
                    ChooseLayout(
                        viewModel = spaceViewModel,
                        onBack = { navController.popBackStack() },
                        onAdd = { finish() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BlurController(this).apply()
    }

    companion object {
        const val EXTRA_MODE = "organizer_mode"
        const val MODE_WORKSPACE = "workspace"
        const val MODE_SPACE = "space"
        private const val MODE_SPACE_CHOOSER = "space_chooser"
    }
}
