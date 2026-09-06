/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.client.clientsdk.compat

import androidx.core.view.doOnAttach
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedZOrderOverrideValue.ForceBehind
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedZOrderOverrideValue.ForceOnTop

/**
 * Handles updates to the [AceEmbeddedSurfaceViewCompat]'s z-order, taking into account both
 * [AceEmbeddedOverlapFadeEffectCompat.overlapZOrderOnTop] from overlap detection, and
 * [AceEmbeddedSurfaceViewCompat.zOrderOverrides]. Responsible for ultimately setting the z-order on
 * the [SurfaceView].
 *
 * [AceEmbeddedSurfaceViewCompat] should instantiate an instance of this class as a field at
 * construction. For each DelegatedUiSurfaceView method that has a matching method signature in this
 * class, delegate the operation to this instance in an overridden method implementation.
 *
 * Call [invoke] on this instance to set the updated z-order on the [SurfaceView].
 */
internal class AceEmbeddedZOrderUpdateEffectCompat(
    override val view: AceEmbeddedSurfaceViewCompat
) : AceEmbeddedSurfaceViewCompat.Effect {

    /**
     * Invoke [AceEmbeddedZOrderUpdateEffectCompat] to set the updated z-order on the [SurfaceView].
     */
    operator fun invoke() {
        view.doOnAttach {
            view.surfaceView.setZOrderOnTop(zOrderOnTop)
            view.surfaceView.isClickable = !zOrderOnTop && view.surfaceView.hasOnClickListeners()
            view.invalidate()
        }
    }

    /**
     * Calculates the current desired z-order of the [AceEmbeddedSurfaceViewCompat].
     *
     * The z-order impacts whether the DUI session can receive touches, and whether the DUI session
     * draws over all other overlapping native UI.
     */
    internal val zOrderOnTop: Boolean
        get() =
            when {
                view.zOrderOverrides.values.any { it is ForceBehind } -> false
                view.zOrderOverrides.values.any { it is ForceOnTop } -> true
                else -> view.overlapFadeEffectCompat.overlapZOrderOnTop
            }
}
