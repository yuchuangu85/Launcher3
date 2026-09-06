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

import android.util.Log
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.AndroidExternalSurfaceZOrder.Companion.Behind
import androidx.compose.foundation.AndroidExternalSurfaceZOrder.Companion.OnTop
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior.SetZOrderBehind.WithFade
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionState
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Shown

/**
 * While [AceEmbeddedOverlapZOrderEffect] is in the composition, it handles changes in the
 * [AceEmbeddedSurfaceView]'s overlap state, and sets the SurfaceView's new z-order onto
 * [AceEmbeddedOverlapStateImpl.overlapZOrder].
 *
 * If required by
 * [com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior], this effect
 * is also responsible for fading out the [AceEmbeddedSurfaceView] upon overlap. It does this by
 * drawing a "background" overlay on top of the SurfaceView, which it ensures has a z-order of
 * [Behind] throughout the duration of the animation.
 *
 * This also draws the background color to cover up the black pixels when the
 * [AceEmbeddedSurfaceView] is not yet connected.
 */
@Composable
internal fun AceEmbeddedOverlapZOrderEffect(
    sessionState: AceEmbeddedSessionState,
    modifier: Modifier,
    overlapState: AceEmbeddedOverlapState,
    backgroundColor: Color,
    shouldHideOnUpdating: Boolean,
) {
    val overlapState = overlapState as AceEmbeddedOverlapStateImpl

    val state by sessionState.uiStateFlow.collectAsState()
    val shouldHide = overlapState.shouldHide(state.visibility, shouldHideOnUpdating)
    val shouldSetBehind = overlapState.shouldSetBehind(shouldHide)

    val targetAlpha = if (shouldHide) 1f else 0f
    val animationSpec = overlapState.getAnimationSpec(shouldHide)

    if (shouldSetBehind) {
        LaunchedEffect(Unit) {
            overlapState.overlapZOrder = Behind
            Log.v(TAG, "AceEmbeddedOverlapZOrderEffect Z-order: Behind")
        }
    }
    val animatedAlpha by
        animateFloatAsState(targetValue = targetAlpha, animationSpec = animationSpec)
    if (!shouldSetBehind && animatedAlpha == targetAlpha) {
        LaunchedEffect(Unit) {
            overlapState.overlapZOrder = OnTop
            Log.v(TAG, "AceEmbeddedOverlapZOrderEffect Z-order: OnTop")
        }
    }

    Box(
        modifier.drawBehind {
            drawRect(color = backgroundColor, size = size, alpha = animatedAlpha)
        }
    )
}

private const val TAG = "AceEmbeddedSurfaceView"

private fun AceEmbeddedOverlapStateImpl.shouldHide(
    visibility: AceEmbeddedUiVisibility,
    shouldHideOnUpdating: Boolean,
) =
    when {
        visibility !is Shown -> true
        visibility is Shown.Updating && shouldHideOnUpdating -> true
        overlapBehavior is WithFade && isOverlapping -> true
        else -> false
    }

private fun AceEmbeddedOverlapStateImpl.shouldSetBehind(shouldHide: Boolean) =
    shouldHide || isOverlapping

private fun AceEmbeddedOverlapStateImpl.getAnimationSpec(
    shouldHide: Boolean
): AnimationSpec<Float> = if (shouldHide) fadeOutAnimationSpec else fadeInAnimationSpec
