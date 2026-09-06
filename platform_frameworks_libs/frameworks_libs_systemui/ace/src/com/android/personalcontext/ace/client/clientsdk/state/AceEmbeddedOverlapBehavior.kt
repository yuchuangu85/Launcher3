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
package com.android.personalcontext.ace.client.clientsdk.state

/** Mapping from the type hierarchy structure to flat enum structure. */
object AceEmbeddedOverlapBehaviorEnum {
    val entries =
        listOf(
            AceEmbeddedOverlapBehavior.KeepZOrderOnTop,
            AceEmbeddedOverlapBehavior.SetZOrderBehind.NoFade,
            AceEmbeddedOverlapBehavior.SetZOrderBehind.WithFade,
        )
}

/**
 * Behavior of [com.android.personalcontext.ace.client.clientsdk.ui.AceEmbeddedSurfaceView] on
 * overlap. Determines the z-order and other details like whether to fade out.
 */
sealed interface AceEmbeddedOverlapBehavior {

    /**
     * Choose this behavior if you can guarantee that there are no possible overlaps with native UI.
     * This is an optimization that disables all overlap detection.
     *
     * The z-order is always set to [OnTop] unless overridden with [AceEmbeddedZOrderOverrideValue]
     * or when reconnecting to the DUI session (eg: scrolling completely off screen and on again),
     * in which case the z-order will be set to [Behind] so we can fade in the composable to avoid
     * visual jank.
     */
    data object KeepZOrderOnTop : AceEmbeddedOverlapBehavior

    /**
     * Sets the z-order to [Behind] if the composable bounds overlaps any of the zones defined by
     * [overlapInsets]. Otherwise, restores the z-order to [OnTop].
     */
    sealed interface SetZOrderBehind : AceEmbeddedOverlapBehavior {

        /**
         * Choose this behavior if you want the composable to render [Behind] any native UI in
         * overlap zones, and you're OK with the composable coming to rest and still looking active
         * while half-way overlapped, where it is not clickable.
         *
         * Behavior details:
         * * Inherits the z-order behavior of [AceEmbeddedOverlapBehavior.SetZOrderBehind].
         * * When entering or leaving the overlap state, continue to render the composable at 100%
         *   opacity.
         * * When reconnecting to the DUI session (eg: scrolling completely off screen and on
         *   again), fade in the composable to avoid visual jank.
         */
        data object NoFade : SetZOrderBehind

        /**
         * Choose this behavior if you want the composable to fade out when intersecting with any
         * overlap zones. This ensures the composable will never be visible while half-way
         * overlapped, naturally communicating to users that it is not clickable in this state.
         *
         * Behavior details:
         * * Inherits the z-order behavior of [AceEmbeddedOverlapBehavior.SetZOrderBehind].
         * * When entering or leaving the overlap state, fade the composable out and in
         *   respectively.
         * * When reconnecting to the DUI session (eg: scrolling completely off screen and on
         *   again), keep the composable at 0% opacity until it has fully left the overlap state, at
         *   which point fade it in to avoid visual jank.
         */
        data object WithFade : SetZOrderBehind
    }
}
