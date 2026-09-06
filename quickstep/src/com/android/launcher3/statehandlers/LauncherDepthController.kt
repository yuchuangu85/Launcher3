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

package com.android.launcher3.statehandlers

import android.graphics.RenderEffect
import android.graphics.Shader
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Flags
import com.android.launcher3.LauncherState
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.ListenableRef

/** Controls blur and wallpaper zoom, for the Launcher surface only. */
class LauncherDepthController(
    private val launcher: QuickstepLauncher,
    blurState: ListenableRef<Boolean>,
) : DepthController<LauncherState, QuickstepLauncher>(launcher, blurState) {

    override fun shouldBlur(): Boolean {
        return super.shouldBlur() && !Flags.allAppsSurface()
    }

    override fun onDepthAndBlurApplied() {
        super.onDepthAndBlurApplied()
        blurWorkspaceDepthTargets()
    }

    /** Returns `true` if the workspace should be blurred. This return value is only for tests. */
    @VisibleForTesting
    fun blurWorkspaceDepthTargets(): Boolean {
        if (Flags.allAppsSurface()) {
            // AllApps is in a separate layer, so we use background blur and don't blur views.
            return false
        }
        val stateManager = launcher.stateManager
        val targetState = stateManager.targetState ?: stateManager.state
        // Only blur workspace if the current state wants to blur based on the target state.
        val shouldBlurWorkspace =
            stateManager.currentStableState.shouldBlurWorkspace(launcher, targetState)

        val blurEffect =
            if (shouldBlurWorkspace && mCurrentBlur > 0)
                RenderEffect.createBlurEffect(
                    mCurrentBlur.toFloat(),
                    mCurrentBlur.toFloat(),
                    Shader.TileMode.DECAL,
                ) // If blur is not desired, clear the blur effect from the depth targets.
            else null
        Log.d(
            TAG,
            "shouldBlurWorkspace: $shouldBlurWorkspace" +
                " targetState: $targetState" +
                " currentStableState: ${stateManager.currentStableState}" +
                " mCurrentBlur: $mCurrentBlur" +
                " mLauncher.getDepthBlurTargets(): ${launcher.depthBlurTargets}",
        )
        launcher.depthBlurTargets.forEach { it.setRenderEffect(blurEffect) }
        return shouldBlurWorkspace
    }
}
