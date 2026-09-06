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

package com.android.mechanics.compose.modifier

import android.util.Log
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.content.state.TransitionState.Idle
import com.android.compose.animation.scene.content.state.TransitionState.Transition
import com.android.mechanics.GestureContext
import com.android.mechanics.spec.InputDirection

/**
 * Provides a [GestureContext] retrieving gesture details from the current [SceneTransitionLayout]
 * transition, using default values as a fallback if the transition's own gesture context isn't
 * available.
 */
internal fun ContentScope.gestureContextOrDefault(): GestureContext {
    return object : GestureContext {

        override val direction: InputDirection
            get() = gestureContext?.direction ?: InputDirection.Max.also { logOrThrow("direction") }

        override val dragOffset: Float
            get() = gestureContext?.dragOffset ?: 0f.also { logOrThrow("dragOffset") }

        private fun logOrThrow(propertyName: String) {
            val message = buildString {
                append("Cannot resolve '$propertyName': ")
                val gestureContext = gestureContext
                if (gestureContext == null) {
                    append("Cannot retrieve a GestureContext")
                } else {
                    append("Found GestureContext(")
                    append("direction=${gestureContext.direction}, ")
                    append("dragOffset=${gestureContext.dragOffset})")
                }
                val transitionState = layoutState.transitionState
                append(" from the current transitionState=$transitionState. ")

                if (transitionState.isTransitioning()) {
                    append("Have you defined the `intrinsicDirection` in the transition DSL?")
                } else {
                    append("The previous transition didn't have a GestureContext, ")
                    append("check $lastTransitionForDebug")
                }
            }

            Log.wtf(TAG, message)
        }

        /**
         * Caches the [GestureContext] from the most recent transition.
         *
         * This is used when the layoutState becomes [TransitionState.Idle], allowing any animations
         * triggered from that idle state to still access the gesture information from the gesture
         * that just completed.
         */
        private var lastTransitionGestureContext: GestureContext? = null

        private var lastTransitionForDebug: Transition? = null

        private val gestureContext: GestureContext?
            get() {
                return when (val state = layoutState.transitionState) {
                    is Idle -> lastTransitionGestureContext
                    is Transition ->
                        state.gestureContext.also {
                            lastTransitionForDebug = state
                            lastTransitionGestureContext = it
                        }
                }
            }
    }
}

private const val TAG = "GestureContextUtils"
