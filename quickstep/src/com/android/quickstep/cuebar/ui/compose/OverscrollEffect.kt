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

package com.android.quickstep.cuebar.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Allows you to overshoot a scroll, so elements stretch beyond their bounds. */
class OverscrollEffect(
    private val scope: CoroutineScope,
    private val orientation: Orientation,
    private val minOffset: Float,
    private val maxOffset: Float,
    private val resistanceFactor: Float = 0.1f,
    private val visibilityThreshold: Float = Spring.DefaultDisplacementThreshold,
    private val flingAnimationSpec: AnimationSpec<Float> = spring(),
) : OverscrollEffect {

    private val _overscrollOffset = Animatable(0f)
    val offset: State<Float> = _overscrollOffset.asState()

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val overscrollDelta =
            (if (orientation == Orientation.Horizontal) delta.x else delta.y) * resistanceFactor

        scope.launch {
            val newOffset =
                (_overscrollOffset.value + overscrollDelta)
                    .coerceAtLeast(minOffset)
                    .coerceAtMost(maxOffset)
            _overscrollOffset.snapTo(newOffset)
        }

        // Consume nothing here, let it be handled by the external state, and pass through
        performScroll(delta)
        return Offset.Zero
    }

    override val isInProgress: Boolean
        get() = _overscrollOffset.isRunning

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val velocityByOrientation =
            (if (orientation == Orientation.Horizontal) velocity.x else velocity.y)

        // Always animate any change to zero if needed
        if (abs(_overscrollOffset.value) > visibilityThreshold) {
            _overscrollOffset.animateTo(
                targetValue = 0f,
                initialVelocity = velocityByOrientation,
                animationSpec = flingAnimationSpec,
            )
        } else {
            _overscrollOffset.snapTo(0f)
        }

        // Pass through the fling
        performFling(velocity)
    }
}
