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

import android.annotation.SuppressLint
import android.service.personalcontext.hint.ContextHint
import android.util.Log
import androidx.annotation.StyleRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat.SCROLL_AXIS_HORIZONTAL
import androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL
import androidx.core.view.ViewCompat.ScrollAxis
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionState
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Error
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Hidden
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Retryable
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Shown
import com.android.personalcontext.ace.client.clientsdk.state.isTransient
import com.android.personalcontext.ace.client.clientsdk.ui.androidexternalsurface.AndroidExternalSurface

/**
 * [AceEmbeddedSurfaceView] is the main interface for using embedded ACE to securely render remote
 * UI in a composable view hierarchy.
 *
 * Each instance of this composable remotely renders one instance of a embedded ACE session. This
 * composable acts as a wrapper around
 * [com.android.personalcontext.ace.client.clientlib.AceEmbeddedProvider], fully managing the
 * lifecycle of an embedded ACE session by invoking the appropriate
 * [com.android.personalcontext.ace.client.clientlib.AceEmbeddedProvider.connect] operations behind
 * the scenes.
 *
 * This composable is designed to be self-contained, following all common compositional rules and
 * expectations. This way, callers can mostly treat this as a locally rendered composable.
 *
 * Built-in behaviors:
 * * To render the embedded ACE session, include this composable in your composition.
 * * To close the embedded ACE session, remove this composable from your composition.
 * * [AceEmbeddedSessionState] allows you to observe the state of the embedded ACE session, and
 *   offers APIs for more fine-grained control over the session. embedded ACE session events like
 *   data egress can be accessed here.
 * * When recomposition occurs due to updated parameters like [hints] and [constraintsModifier],
 *   this composable will correctly [AceEmbeddedSessionState.update] the embedded ACE session.
 * * Use [overlapState] to define how this composable interops with your native ui. For example, you
 *   can define the [overlap behavior][AceEmbeddedOverlapStateImpl.overlapBehavior] of the
 *   SurfaceView so that it draws behind any native UI when it intersects any overlap zones.
 * * This composable dispatches nested scroll events up the composition when touch gestures
 *   originate from the embedded ACE session.
 *
 * @param sessionState Allows you to observe the state of the embedded ACE session, and offers APIs
 *   to control the session. Call
 *   [com.android.personalcontext.ace.client.clientsdk.ui.rememberSessionState] to construct a
 *   unique session for each [AceEmbeddedSurfaceView] in your composition.
 * @param overlapState The state of the overlap, which determines how the surface is rendered
 *   relative to its parent window.
 * @param hints The context hints passed from the client as input to the embedded ACE understander
 *   service.
 * @param backgroundColor The background color that matches the color of the surface under the
 *   [AceEmbeddedSurfaceView]. This is required to support
 *   [com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior.SetZOrderBehind.WithFade].
 * @param shouldBlur Whether the remote UI should have a blur effect applied to the window. Change
 *   this value to trigger the remote UI to animate to the new state.
 * @param themeResourceId The custom [android.R.styleable#PersonalContextTheme] to be passed to a
 *   connected visualizer. A visualizer can use this name to look up the theme resource in the
 *   client's resources, which can then be used when creating an embedded surface for the client.
 * @param constraintsModifier The modifier to be applied for sizing purposes only.
 * @param modifier The modifier to be applied for non-sizing purposes. Known use cases include
 *   drawing a border with
 *   [com.android.personalcontext.ace.client.clientsdk.ui.modifiers.debugBorder], animating size
 *   changes with
 *   [com.android.personalcontext.ace.client.clientsdk.ui.modifiers.animateEmbeddedContentSize], or
 *   handling click events when the z-order is [Behind] with
 *   [com.android.personalcontext.ace.client.clientsdk.ui.modifiers.clickableWhenBehind]. Be aware
 *   that this modifier is applied to a composition that includes a SurfaceView, which means many
 *   gesture or drawing modifiers may not work as expected.
 * @param hiddenContent The placeholder content to show when the embedded ACE session state is
 *   [Hidden].
 * @param pendingContent The loading content to show when the embedded ACE session state
 *   [isTransient], which includes [Pending], and some [Shown] states.
 * @param errorContent The error content to show when the embedded ACE session state is [Error].
 * @param shouldHideOnUpdating Whether [AceEmbeddedSurfaceView] should hide on the [Shown.Updating]
 *   state.
 * @param stateTransitionIn The [EnterTransition] to apply to the optional content when the embedded
 *   ACE session state changes.
 * @param stateTransitionOut The [ExitTransition] to apply to the optional content when the embedded
 *   ACE session state changes.
 * @param onCompositionLifecycle Low-level composition lifecycle events, mostly observed for
 *   debugging. The high-level state of this composable can be accessed via
 *   [AceEmbeddedSessionState.uiStateFlow].
 */
@Composable
@SuppressLint("ModifierParameter")
fun AceEmbeddedSurfaceView(
    sessionState: AceEmbeddedSessionState,
    overlapState: AceEmbeddedOverlapState,
    hints: Set<ContextHint>,
    backgroundColor: Color,
    constraintsModifier: Modifier,
    modifier: Modifier = Modifier,
    hiddenContent: (@Composable () -> Unit)? = null,
    pendingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable () -> Unit)? = null,
    @ScrollAxis nestedScrollAxes: Int = SCROLL_AXIS_HORIZONTAL or SCROLL_AXIS_VERTICAL,
    nestedScrollAxisLocked: Boolean = true,
    shouldBlur: Boolean = false,
    @StyleRes themeResourceId: Int = 0,
    shouldHideOnUpdating: Boolean = false,
    stateTransitionIn: EnterTransition = EnterTransition.None,
    stateTransitionOut: ExitTransition = ExitTransition.None,
    onCompositionLifecycle: (AceEmbeddedCompositionLifecycle) -> Unit = {},
) {
    val overlapState = overlapState as AceEmbeddedOverlapStateImpl

    var lastSizePx by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = modifier.onGloballyPositioned { if (it.size.hasArea()) lastSizePx = it.size }) {
        val context = LocalContext.current
        val density = LocalDensity.current

        AceEmbeddedInputsUpdateEffect(
            sessionState,
            constraintsModifier,
            hints,
            backgroundColor,
            nestedScrollAxes,
            nestedScrollAxisLocked,
            shouldBlur,
            themeResourceId,
        ) {
            Log.i(TAG, "[AceEmbeddedLifecycle] Client-sdk inputs updated.")
            sessionState.connect(context)
        }

        DisposableEffect(Unit) {
            Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk entered composition.")
            onCompositionLifecycle(AceEmbeddedCompositionLifecycle.Enter)

            onDispose {
                Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk exited composition.")
                onCompositionLifecycle(AceEmbeddedCompositionLifecycle.Exit)
                sessionState.cancel()
            }
        }

        val uiState by sessionState.uiStateFlow.collectAsState()
        val visibility = uiState.visibility

        val remoteWidthPx = uiState.size?.widthPx ?: lastSizePx.width
        val remoteHeightPx = uiState.size?.heightPx ?: lastSizePx.height

        val transientSizeModifier =
            with(density) {
                constraintsModifier.sizeIn(
                    minWidth = remoteWidthPx.coerceAtLeast(1).toDp(),
                    minHeight = remoteHeightPx.coerceAtLeast(1).toDp(),
                )
            }

        val remoteSizeModifier =
            with(density) {
                Modifier.size(
                    width = remoteWidthPx.coerceAtLeast(1).toDp(),
                    height = remoteHeightPx.coerceAtLeast(1).toDp(),
                )
            }

        AnimatedVisibility(
            visible = visibility is Hidden,
            modifier = constraintsModifier,
            enter = stateTransitionIn,
            exit = stateTransitionOut,
        ) {
            LaunchedEffect(visibility) {
                Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk → visibility Hidden: $visibility")
            }
            hiddenContent?.invoke()
        }

        AnimatedVisibility(
            visible = visibility.isTransient, // Includes Pending, Retryable, and some Shown states.
            modifier = transientSizeModifier,
            enter = stateTransitionIn,
            exit = stateTransitionOut,
        ) {
            LaunchedEffect(visibility) {
                Log.d(
                    TAG,
                    "[AceEmbeddedLifecycle] Client-sdk → visibility isTransient(): $visibility",
                )
            }
            pendingContent?.invoke()
        }

        AnimatedVisibility(
            visible = visibility is Error,
            modifier = constraintsModifier,
            enter = stateTransitionIn,
            exit = stateTransitionOut,
        ) {
            LaunchedEffect(visibility) {
                Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk → visibility Error: $visibility")
            }
            errorContent?.invoke()
        }

        // embedded ACE implements a custom fade in effect in OverlapZOrderEffect.
        if (visibility is Shown) {
            // AndroidExternalSurface and OverlapZOrderEffect must be drawn first for transitions to
            // work.
            Box(modifier = Modifier.zIndex(-1f)) {
                LaunchedEffect(visibility) {
                    Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk → visibility Shown: $visibility")
                }

                AceEmbeddedScrollDispatchEffect(sessionState)
                AceEmbeddedOverlapDetectionEffect(remoteSizeModifier, overlapState)

                AndroidExternalSurface(
                    modifier = remoteSizeModifier,
                    isOpaque = false,
                    zOrder = overlapState.zOrder,
                    onReset = {
                        onCompositionLifecycle(AceEmbeddedCompositionLifecycle.Reset)
                        sessionState.cancel()
                    },
                ) { surfaceView ->
                    onCompositionLifecycle(AceEmbeddedCompositionLifecycle.Attach)

                    onSurface { _, _, _ ->
                        if (surfaceView.childSurfacePackage == null) {
                            surfaceView.setChildSurfacePackage(visibility.surfacePackage)
                        }
                    }
                }

                AceEmbeddedOverlapZOrderEffect(
                    sessionState,
                    remoteSizeModifier,
                    overlapState,
                    backgroundColor,
                    shouldHideOnUpdating,
                )
            }
        }

        if (visibility is Retryable) {
            LaunchedEffect(visibility) {
                Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk → visibility Retryable: $visibility")
            }
            LaunchedEffect(Unit) { sessionState.connect(context) }
        }
    }
}

private fun IntSize.hasArea(): Boolean = width > 1 && height > 1

private const val TAG = "AceEmbeddedSurfaceView"

/**
 * The low-level composition lifecycle of a [AceEmbeddedSurfaceView] composable. Consider observing
 * [com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility] from
 * [AceEmbeddedSessionState.uiStateFlow] instead.
 */
enum class AceEmbeddedCompositionLifecycle {
    /** The [AceEmbeddedSurfaceView] has entered the composition hierarchy. */
    Enter,
    /** The [AceEmbeddedSurfaceView] has been attached to the window. */
    Attach,
    /**
     * The [AceEmbeddedSurfaceView] is about to be attached to the composition hierarchy in a
     * different context than its original creation.
     */
    Reset,
    /**
     * The [AceEmbeddedSurfaceView] has exited the composition hierarchy entirely and will not be
     * reused again.
     */
    Exit,
}
