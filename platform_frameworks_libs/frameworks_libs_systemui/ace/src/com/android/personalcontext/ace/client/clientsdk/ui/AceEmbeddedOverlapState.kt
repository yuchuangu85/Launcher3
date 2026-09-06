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
package com.android.personalcontext.ace.client.clientsdk.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.AndroidExternalSurfaceZOrder
import androidx.compose.foundation.AndroidExternalSurfaceZOrder.Companion.Behind
import androidx.compose.foundation.AndroidExternalSurfaceZOrder.Companion.OnTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedZOrderOverrideValue
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedZOrderOverrideValue.ForceBehind
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedZOrderOverrideValue.ForceOnTop
import com.android.personalcontext.ace.client.clientsdk.state.NullableRect

/**
 * Manages the Z-order compositing state of an [AceEmbeddedSurfaceView].
 *
 * Because SurfaceViews render on a dedicated window separate from the standard Android View
 * hierarchy, they are either strictly **Behind** the window or **On Top** of it. This interface
 * coordinates the logic to switch between these modes based on:
 * 1. Overlap with specified insets.
 * 2. Transient overrides (e.g., for Drawers, BottomSheets, Toasts, or other temporary UI states).
 */
sealed interface AceEmbeddedOverlapState {

    /**
     * The overlap behavior of the [AceEmbeddedSurfaceView], defining how the surface behaves when
     * the bounds of the composable intersect with any overlap zones
     */
    var overlapBehavior: AceEmbeddedOverlapBehavior

    /**
     * The insets relative to each edge of the
     * [root layout coordinates][androidx.compose.ui.layout.findRootCoordinates] where the
     * [AceEmbeddedSurfaceView] should not overlap.
     *
     * To disable overlap detection for a specific edge, assign `null` to that edge's inset.
     */
    var overlapInsets: NullableRect

    /** Animation spec for fading in. */
    var fadeInAnimationSpec: AnimationSpec<Float>

    /** Animation spec for fading out. */
    var fadeOutAnimationSpec: AnimationSpec<Float>

    /**
     * Transient states should set a key/value pair to override the z-order of the
     * [AceEmbeddedSurfaceView], taking precedence over the behavior of overlap detection.
     *
     * If multiple [key]s are set with conflicting values, the order of precedence is [ForceBehind],
     * then [ForceOnTop], then
     * [com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedZOrderOverrideValue.None].
     */
    fun setZOrderOverride(key: String, value: () -> AceEmbeddedZOrderOverrideValue)
}

/**
 * Implementation of [AceEmbeddedOverlapState], exposing additional state to internal components.
 */
class AceEmbeddedOverlapStateImpl(
    initialOverlapBehavior: AceEmbeddedOverlapBehavior,
    initialOverlapInsets: NullableRect,
) : AceEmbeddedOverlapState {

    override var overlapBehavior: AceEmbeddedOverlapBehavior by
        mutableStateOf(initialOverlapBehavior)

    override var overlapInsets: NullableRect by mutableStateOf(initialOverlapInsets)

    override var fadeInAnimationSpec: AnimationSpec<Float> by mutableStateOf(spring())

    override var fadeOutAnimationSpec: AnimationSpec<Float> by mutableStateOf(spring())

    override fun setZOrderOverride(key: String, value: () -> AceEmbeddedZOrderOverrideValue) {
        zOrderOverrides[key] = value()
    }

    /** The backing map for z-order overrides. */
    private val zOrderOverrides: MutableMap<String, AceEmbeddedZOrderOverrideValue> =
        mutableStateMapOf()

    /**
     * Calculates and remembers the current desired z-order of the [AceEmbeddedSurfaceView].
     *
     * The z-order impacts whether the DUI session can receive touches, and whether the DUI session
     * draws over all other overlapping native UI.
     */
    val zOrder: AndroidExternalSurfaceZOrder by derivedStateOf {
        when {
            zOrderOverrides.values.any { it is ForceBehind } -> Behind
            zOrderOverrides.values.any { it is ForceOnTop } -> OnTop
            else -> overlapZOrder
        }
    }

    /** Whether the [AceEmbeddedSurfaceView] is currently overlapping with any overlap zones. */
    var isOverlapping: Boolean by mutableStateOf(false)

    /**
     * The resolved z-order of the [AceEmbeddedSurfaceView] after overlap detection and taking into
     * account any animations.
     */
    var overlapZOrder: AndroidExternalSurfaceZOrder by mutableStateOf(OnTop)

    override fun toString(): String {
        return """
      OverlapState(overlapBehavior=$overlapBehavior, zOrder=${zOrder.toDebugString()}) {
        [overlapZOrder=${overlapZOrder.toDebugString()}, overlapInsets=$overlapInsets, isOverlapping=$isOverlapping],
        [zOrderOverrides=$zOrderOverrides],
      }
    """
            .trimIndent()
    }

    private fun AndroidExternalSurfaceZOrder.toDebugString(): String {
        return when (this) {
            Behind -> "Behind"
            OnTop -> "OnTop"
            else -> "Unknown"
        }
    }
}

/**
 * Create and [remember] an [AceEmbeddedOverlapState] to allow setting the overlap behavior,
 * overriding the z-order, or observing overlap state.
 *
 * @param initialOverlapBehavior Defines how [AceEmbeddedSurfaceView] behaves when the bounds of the
 *   composable intersect with any overlap zones.
 * @param initialOverlapInsets The insets relative to each edge of the
 *   [root layout coordinates][androidx.compose.ui.layout.findRootCoordinates] where the
 *   [AceEmbeddedSurfaceView] should not overlap. To disable overlap detection for a specific edge,
 *   assign `null` to that edge's inset.
 */
@Composable
fun rememberOverlapState(
    initialOverlapBehavior: AceEmbeddedOverlapBehavior,
    initialOverlapInsets: NullableRect = NullableRect.Companion.Empty,
): AceEmbeddedOverlapState {
    return remember {
        AceEmbeddedOverlapStateImpl(
            initialOverlapBehavior = initialOverlapBehavior,
            initialOverlapInsets = initialOverlapInsets,
        )
    }
}
