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

package com.android.quickstep.cuebar.domain.interactor

import android.graphics.Rect
import com.android.launcher3.util.ListenableRef
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.data.repository.AmbientCueRepository
import javax.inject.Inject

/**
 * Interactor for the Ambient Cue feature within Launcher.
 *
 * This class mediates between the ViewModel and the adapted AmbientCueRepository, providing the
 * necessary data flows and control functions.
 */
class AmbientCueInteractor
@Inject
constructor(
    private val ambientCueRepository: AmbientCueRepository // Injects the Adapted Repository
) {

    /** ListenableRef of actions to be displayed in the Cuebar. */
    val actions: ListenableRef<List<ActionModel>> = ambientCueRepository.actions

    /** ListenableRef indicating if the Input Method Editor (keyboard) is visible. */
    val isImeVisible: ListenableRef<Boolean> = ambientCueRepository.isImeVisible

    /** ListenableRef indicating if the Cuebar is occluded by other system UI elements. */
    val isOccludedBySystemUi: ListenableRef<Boolean> = ambientCueRepository.isOccludedBySystemUi

    /** ListenableRef providing the timeout duration for the Ambient Cue. */
    val ambientCueTimeoutMs: ListenableRef<Int> = ambientCueRepository.ambientCueTimeoutMs

    /** ListenableRef indicating if gesture navigation is enabled. */
    val isGestureNav: ListenableRef<Boolean> = ambientCueRepository.isGestureNav

    /** ListenableRef indicating if the Taskbar is fully visible and not stashed. */
    val isTaskBarVisible: ListenableRef<Boolean> = ambientCueRepository.isTaskBarVisible

    /** ListenableRef providing the position of the recents button (in 3-button nav). */
    val recentsButtonPosition: ListenableRef<Rect?> = ambientCueRepository.recentsButtonPosition

    val isDeactivated: ListenableRef<Boolean> = ambientCueRepository.isDeactivated

    val isAmbientCueEnabled: ListenableRef<Boolean> = ambientCueRepository.isAmbientCueEnabled

    val isTestMode: ListenableRef<Boolean> = ambientCueRepository.isTestMode

    /**
     * Sets the deactivated state of the Ambient Cue.
     *
     * @param isDeactivated True to deactivate, false to potentially reactivate.
     */
    fun setDeactivated(isDeactivated: Boolean) {
        ambientCueRepository.isDeactivated.dispatchValue(isDeactivated)
    }

    val globallyFocusedTaskId: ListenableRef<Int> = ambientCueRepository.globallyFocusedTaskId

    val frontTaskPackageName: ListenableRef<String> = ambientCueRepository.frontTaskPackageName

    fun reportCloseEvent() {
        ambientCueRepository.reportCloseEvent()
    }
}
