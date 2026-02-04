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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.mechanics.effects

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.android.mechanics.spec.BreakpointKey
import com.android.mechanics.spec.ChangeSegmentHandlers.PreventDirectionChangeWithinCurrentSegment
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.SegmentKey
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spec.builder.Effect
import com.android.mechanics.spec.builder.EffectApplyScope
import com.android.mechanics.spec.builder.EffectPlacemenType
import com.android.mechanics.spec.builder.EffectPlacement
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.with
import com.android.mechanics.spring.SpringParameters

/**
 * Gesture effect that emulates effort to detach an element from its resting position.
 *
 * @param semanticState semantic state used to check the state of this effect.
 * @param detachPosition distance from the origin to detach
 * @param attachPosition distance from the origin to re-attach
 * @param detachScale fraction of input changes propagated during detach.
 * @param attachScale fraction of input changes propagated after re-attach.
 * @param detachSpring spring used during detach
 * @param attachSpring spring used during attach
 */
class MagneticDetach(
    private val semanticState: SemanticKey<State> = Defaults.AttachDetachState,
    private val semanticAttachedValue: SemanticKey<Float?> = Defaults.AttachedValue,
    private val detachPosition: Dp = Defaults.DetachPosition,
    private val attachPosition: Dp = Defaults.AttachPosition,
    private val detachScale: Float = Defaults.AttachDetachScale,
    private val attachScale: Float = Defaults.AttachDetachScale * (attachPosition / detachPosition),
    private val detachSpring: SpringParameters = Defaults.Spring,
    private val attachSpring: SpringParameters = Defaults.Spring,
) : Effect.PlaceableAfter, Effect.PlaceableBefore {

    init {
        require(attachPosition <= detachPosition)
    }

    enum class State {
        Attached,
        Detached,
    }

    override fun MotionBuilderContext.intrinsicSize(): Float {
        return detachPosition.toPx()
    }

    override fun EffectApplyScope.createSpec(
        minLimit: Float,
        minLimitKey: BreakpointKey,
        maxLimit: Float,
        maxLimitKey: BreakpointKey,
        placement: EffectPlacement,
    ) {
        if (placement.type == EffectPlacemenType.Before) {
            createPlacedBeforeSpec(minLimit, minLimitKey, maxLimit, maxLimitKey)
        } else {
            assert(placement.type == EffectPlacemenType.After)
            createPlacedAfterSpec(minLimit, minLimitKey, maxLimit, maxLimitKey)
        }
    }

    object Defaults {
        val AttachDetachState = SemanticKey<State>()
        val AttachedValue = SemanticKey<Float?>()
        val AttachDetachScale = .3f
        val DetachPosition = 80.dp
        val AttachPosition = 40.dp
        val Spring = SpringParameters(stiffness = 800f, dampingRatio = 0.95f)
    }

    /* Effect is attached at minLimit, and detaches at maxLimit. */
    private fun EffectApplyScope.createPlacedAfterSpec(
        minLimit: Float,
        minLimitKey: BreakpointKey,
        maxLimit: Float,
        maxLimitKey: BreakpointKey,
    ) {
        val attachedValue = baseValue(minLimit)
        val detachedValue = baseValue(maxLimit)
        val reattachPos = minLimit + attachPosition.toPx()
        val reattachValue = baseValue(reattachPos)

        val attachedSemantics =
            listOf(semanticState with State.Attached, semanticAttachedValue with attachedValue)
        val detachedSemantics =
            listOf(semanticState with State.Detached, semanticAttachedValue with null)

        val scaledDetachValue = attachedValue + (detachedValue - attachedValue) * detachScale
        val scaledReattachValue = attachedValue + (reattachValue - attachedValue) * attachScale

        val attachKey = BreakpointKey("attach")
        forward(
            initialMapping = Mapping.Linear(minLimit, attachedValue, maxLimit, scaledDetachValue),
            semantics = attachedSemantics,
        ) {
            after(spring = detachSpring, semantics = detachedSemantics)
            before(semantics = listOf(semanticAttachedValue with null))
        }

        backward(
            initialMapping =
                Mapping.Linear(minLimit, attachedValue, reattachPos, scaledReattachValue),
            semantics = attachedSemantics,
        ) {
            mapping(
                breakpoint = reattachPos,
                key = attachKey,
                spring = attachSpring,
                semantics = detachedSemantics,
                mapping = baseMapping,
            )
            before(semantics = listOf(semanticAttachedValue with null))
            after(semantics = listOf(semanticAttachedValue with null))
        }

        addSegmentHandlers(
            beforeDetachSegment = SegmentKey(minLimitKey, maxLimitKey, InputDirection.Max),
            beforeAttachSegment = SegmentKey(attachKey, maxLimitKey, InputDirection.Min),
            afterAttachSegment = SegmentKey(minLimitKey, attachKey, InputDirection.Min),
            minLimit = minLimit,
            maxLimit = maxLimit,
        )
    }

    /* Effect is attached at maxLimit, and detaches at minLimit. */
    private fun EffectApplyScope.createPlacedBeforeSpec(
        minLimit: Float,
        minLimitKey: BreakpointKey,
        maxLimit: Float,
        maxLimitKey: BreakpointKey,
    ) {
        val attachedValue = baseValue(maxLimit)
        val detachedValue = baseValue(minLimit)
        val reattachPos = maxLimit - attachPosition.toPx()
        val reattachValue = baseValue(reattachPos)

        val attachedSemantics =
            listOf(semanticState with State.Attached, semanticAttachedValue with attachedValue)
        val detachedSemantics =
            listOf(semanticState with State.Detached, semanticAttachedValue with null)

        val scaledDetachValue = attachedValue + (detachedValue - attachedValue) * detachScale
        val scaledReattachValue = attachedValue + (reattachValue - attachedValue) * attachScale

        val attachKey = BreakpointKey("attach")

        backward(
            initialMapping = Mapping.Linear(minLimit, scaledDetachValue, maxLimit, attachedValue),
            semantics = attachedSemantics,
        ) {
            before(spring = detachSpring, semantics = detachedSemantics)
            after(semantics = listOf(semanticAttachedValue with null))
        }

        forward(initialMapping = baseMapping, semantics = detachedSemantics) {
            target(
                breakpoint = reattachPos,
                key = attachKey,
                from = scaledReattachValue,
                to = attachedValue,
                spring = attachSpring,
                semantics = attachedSemantics,
            )
            after(semantics = listOf(semanticAttachedValue with null))
        }

        addSegmentHandlers(
            beforeDetachSegment = SegmentKey(minLimitKey, maxLimitKey, InputDirection.Min),
            beforeAttachSegment = SegmentKey(minLimitKey, attachKey, InputDirection.Max),
            afterAttachSegment = SegmentKey(attachKey, maxLimitKey, InputDirection.Max),
            minLimit = minLimit,
            maxLimit = maxLimit,
        )
    }

    private fun EffectApplyScope.addSegmentHandlers(
        beforeDetachSegment: SegmentKey,
        beforeAttachSegment: SegmentKey,
        afterAttachSegment: SegmentKey,
        minLimit: Float,
        maxLimit: Float,
    ) {
        // Suppress direction change during detach. This prevents snapping to the origin when
        // changing the direction while detaching.
        addSegmentHandler(beforeDetachSegment, PreventDirectionChangeWithinCurrentSegment)
        // Suppress direction when approaching attach. This prevents the detach effect when changing
        // direction just before reattaching.
        addSegmentHandler(beforeAttachSegment, PreventDirectionChangeWithinCurrentSegment)

        // When changing direction after re-attaching, the pre-detach ratio is tweaked to
        // interpolate between the direction change-position and the detach point.
        addSegmentHandler(afterAttachSegment) { currentSegment, newInput, newDirection ->
            val nextSegment = segmentAtInput(newInput, newDirection)
            if (nextSegment.key == beforeDetachSegment) {
                nextSegment.copy(
                    mapping =
                        switchMappingWithSamePivotValue(
                            currentSegment.mapping,
                            nextSegment.mapping,
                            minLimit,
                            newInput,
                            maxLimit,
                        )
                )
            } else {
                nextSegment
            }
        }
    }

    private fun switchMappingWithSamePivotValue(
        source: Mapping,
        target: Mapping,
        minLimit: Float,
        pivot: Float,
        maxLimit: Float,
    ): Mapping {
        val minValue = target.map(minLimit)
        val pivotValue = source.map(pivot)
        val maxValue = target.map(maxLimit)

        return Mapping { input ->
            if (input <= pivot) {
                val t = (input - minLimit) / (pivot - minLimit)
                lerp(minValue, pivotValue, t)
            } else {
                val t = (input - pivot) / (maxLimit - pivot)
                lerp(pivotValue, maxValue, t)
            }
        }
    }
}
