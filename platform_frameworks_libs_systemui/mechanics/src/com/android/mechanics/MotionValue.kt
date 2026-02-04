/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.mechanics

import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.android.mechanics.debug.DebugInspector
import com.android.mechanics.debug.FrameData
import com.android.mechanics.impl.Computations
import com.android.mechanics.impl.DiscontinuityAnimation
import com.android.mechanics.impl.GuaranteeState
import com.android.mechanics.spec.Breakpoint
import com.android.mechanics.spec.Guarantee
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.SegmentData
import com.android.mechanics.spec.SegmentKey
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spring.SpringState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Computes an animated [output] value, by mapping the [currentInput] according to the [spec].
 *
 * A [MotionValue] represents a single animated value within a larger animation. It takes a
 * numerical [currentInput] value, typically a spatial value like width, height, or gesture length,
 * and transforms it into an [output] value using a [MotionSpec].
 *
 * ## Mapping Input to Output
 *
 * The [MotionSpec] defines the relationship between the input and output values. It does this by
 * specifying a series of [Mapping] functions and [Breakpoint]s. Breakpoints divide the input domain
 * into segments. Each segment has an associated [Mapping] function, which determines how input
 * values within that segment are transformed into output values.
 *
 * These [Mapping] functions can be arbitrary, as long as they are
 * 1. deterministic: When invoked repeatedly for the same input, they must produce the same output.
 * 2. continuous: meaning infinitesimally small changes in input result in infinitesimally small
 *    changes in output
 *
 * A valid [Mapping] function is one whose graph could be drawn without lifting your pen from the
 * paper, meaning there are no abrupt jumps or breaks.
 *
 * ## Animating Discontinuities
 *
 * When the input value crosses a breakpoint, there might be a discontinuity in the output value due
 * to the switch between mapping functions. `MotionValue` automatically animates these
 * discontinuities using a spring animation. The spring parameters are defined for each
 * [Breakpoint].
 *
 * ## Guarantees for Choreography
 *
 * Breakpoints can also define [Guarantee]s. These guarantees can make the spring animation finish
 * faster, in response to quick input value changes. Thus, [Guarantee]s allows to maintain a
 * predictable choreography, even as the input is unpredictably changed by a user's gesture.
 *
 * ## Updating the MotionSpec
 *
 * The [spec] property can be changed at any time. If the new spec produces a different output for
 * the current input, the difference will be animated using the spring parameters defined in
 * [MotionSpec.resetSpring].
 *
 * ## Gesture Context
 *
 * The [GestureContext] augments the [currentInput] value with the user's intent. The
 * [GestureContext] is created wherever gesture input is handled. If the motion value is not driven
 * by a gesture, it is OK for the [GestureContext] to return static values.
 *
 * ## Usage
 *
 * The [MotionValue] does animate the [output] implicitly, whenever a change in [currentInput],
 * [spec], or [gestureContext] requires it. The animated value is computed whenever the [output]
 * property is read, or the latest once the animation frame is complete.
 * 1. Create an instance, providing the input value, gesture context, and an initial spec.
 * 2. Call [keepRunning] in a coroutine scope, and keep the coroutine running while the
 *    `MotionValue` is in use.
 * 3. Access the animated output value through the [output] property.
 *
 * Internally, the [keepRunning] coroutine is automatically suspended if there is nothing to
 * animate.
 *
 * @param currentInput Provides the current input value.
 * @param gestureContext The [GestureContext] augmenting the [currentInput].
 * @param label An optional label to aid in debugging.
 * @param stableThreshold A threshold value (in output units) that determines when the
 *   [MotionValue]'s internal spring animation is considered stable.
 */
class MotionValue(
    currentInput: () -> Float,
    gestureContext: GestureContext,
    initialSpec: MotionSpec = MotionSpec.Empty,
    label: String? = null,
    stableThreshold: Float = StableThresholdEffect,
) : FloatState {
    private val impl =
        ObservableComputations(currentInput, gestureContext, initialSpec, stableThreshold, label)

    /** The [MotionSpec] describing the mapping of this [MotionValue]'s input to the output. */
    var spec: MotionSpec by impl::spec

    /** Animated [output] value. */
    val output: Float by impl::output

    /**
     * [output] value, but without animations.
     *
     * This value always reports the target value, even before a animation is finished.
     *
     * While [isStable], [outputTarget] and [output] are the same value.
     */
    val outputTarget: Float by impl::outputTarget

    /** The [output] exposed as [FloatState]. */
    override val floatValue: Float by impl::output

    /** Whether an animation is currently running. */
    val isStable: Boolean by impl::isStable

    /**
     * The current value for the [SemanticKey].
     *
     * `null` if not defined in the spec.
     */
    operator fun <T> get(key: SemanticKey<T>): T? {
        return impl.semanticState(key)
    }

    /** The current segment used to compute the output. */
    val segmentKey: SegmentKey
        get() = impl.currentComputedValues.segment.key

    /**
     * Keeps the [MotionValue]'s animated output running.
     *
     * Clients must call [keepRunning], and keep the coroutine running while the [MotionValue] is in
     * use. When disposing this [MotionValue], cancel the coroutine.
     *
     * Internally, this method does suspend, unless there are animations ongoing.
     */
    suspend fun keepRunning(): Nothing {
        withContext(CoroutineName("MotionValue($label)")) { impl.keepRunning { true } }

        // `keepRunning` above will never finish,
        throw AssertionError("Unreachable code")
    }

    /**
     * Keeps the [MotionValue]'s animated output running while [continueRunning] returns `true`.
     *
     * When [continueRunning] returns `false`, the coroutine will end by the next frame.
     *
     * To keep the [MotionValue] running until the current animations are complete, check for
     * `isStable` as well.
     *
     * ```kotlin
     * motionValue.keepRunningWhile { !shouldEnd() || !isStable }
     * ```
     */
    suspend fun keepRunningWhile(continueRunning: MotionValue.() -> Boolean) =
        withContext(CoroutineName("MotionValue($label)")) {
            impl.keepRunning { continueRunning.invoke(this@MotionValue) }
        }

    val label: String? by impl::label

    companion object {
        /** Creates a [MotionValue] whose [currentInput] is the animated [output] of [source]. */
        fun createDerived(
            source: MotionValue,
            initialSpec: MotionSpec = MotionSpec.Empty,
            label: String? = null,
            stableThreshold: Float = 0.01f,
        ): MotionValue {
            return MotionValue(
                currentInput = source::output,
                gestureContext = source.impl.gestureContext,
                initialSpec = initialSpec,
                label = label,
                stableThreshold = stableThreshold,
            )
        }

        const val StableThresholdEffect = 0.01f
        const val StableThresholdSpatial = 1f

        internal const val TAG = "MotionValue"
    }

    private var debugInspectorRefCount = AtomicInteger(0)

    private fun onDisposeDebugInspector() {
        if (debugInspectorRefCount.decrementAndGet() == 0) {
            impl.debugInspector = null
        }
    }

    /**
     * Provides access to internal state for debug tooling and tests.
     *
     * The returned [DebugInspector] must be [DebugInspector.dispose]d when no longer needed.
     */
    fun debugInspector(): DebugInspector {
        if (debugInspectorRefCount.getAndIncrement() == 0) {
            impl.debugInspector =
                DebugInspector(
                    FrameData(
                        impl.lastInput,
                        impl.lastSegment.direction,
                        impl.lastGestureDragOffset,
                        impl.lastFrameTimeNanos,
                        impl.lastSpringState,
                        impl.lastSegment,
                        impl.lastAnimation,
                    ),
                    impl.isActive,
                    impl.debugIsAnimating,
                    ::onDisposeDebugInspector,
                )
        }

        return checkNotNull(impl.debugInspector)
    }
}

private class ObservableComputations(
    val input: () -> Float,
    val gestureContext: GestureContext,
    initialSpec: MotionSpec = MotionSpec.Empty,
    override val stableThreshold: Float,
    override val label: String?,
) : Computations() {

    // ----  CurrentFrameInput ---------------------------------------------------------------------

    override var spec by mutableStateOf(initialSpec)
    override val currentInput: Float
        get() = input.invoke()

    override val currentDirection: InputDirection
        get() = gestureContext.direction

    override val currentGestureDragOffset: Float
        get() = gestureContext.dragOffset

    override var currentAnimationTimeNanos by mutableLongStateOf(-1L)

    // ----  LastFrameState ---------------------------------------------------------------------

    override var lastSegment: SegmentData by
        mutableStateOf(
            spec.segmentAtInput(currentInput, currentDirection),
            referentialEqualityPolicy(),
        )

    override var lastGuaranteeState: GuaranteeState
        get() = GuaranteeState(_lastGuaranteeStatePacked)
        set(value) {
            _lastGuaranteeStatePacked = value.packedValue
        }

    private var _lastGuaranteeStatePacked: Long by
        mutableLongStateOf(GuaranteeState.Inactive.packedValue)

    override var lastAnimation: DiscontinuityAnimation by
        mutableStateOf(DiscontinuityAnimation.None, referentialEqualityPolicy())

    override var directMappedVelocity: Float = 0f

    override var lastSpringState: SpringState
        get() = SpringState(_lastSpringStatePacked)
        set(value) {
            _lastSpringStatePacked = value.packedValue
        }

    private var _lastSpringStatePacked: Long by
        mutableLongStateOf(lastAnimation.springStartState.packedValue)

    override var lastFrameTimeNanos by mutableLongStateOf(-1L)

    override var lastInput by mutableFloatStateOf(currentInput)

    override var lastGestureDragOffset by mutableFloatStateOf(currentGestureDragOffset)

    // ---- Computations ---------------------------------------------------------------------------

    suspend fun keepRunning(continueRunning: () -> Boolean) {
        check(!isActive) { "MotionValue($label) is already running" }
        isActive = true

        // These `captured*` values will be applied to the `last*` values, at the beginning
        // of the each new frame.
        // TODO(b/397837971): Encapsulate the state in a StateRecord.
        val initialValues = currentComputedValues
        var capturedSegment = initialValues.segment
        var capturedGuaranteeState = initialValues.guarantee
        var capturedAnimation = initialValues.animation
        var capturedSpringState = currentSpringState
        var capturedFrameTimeNanos = currentAnimationTimeNanos
        var capturedInput = currentInput
        var capturedGestureDragOffset = currentGestureDragOffset
        var capturedDirection = currentDirection

        try {
            debugIsAnimating = true

            // indicates whether withFrameNanos is called continuously (as opposed to being
            // suspended for an undetermined amount of time in between withFrameNanos).
            // This is essential after `withFrameNanos` returned: if true at this point,
            // currentAnimationTimeNanos - lastFrameNanos is the duration of the last frame.
            var isAnimatingUninterrupted = false

            while (continueRunning()) {

                withFrameNanos { frameTimeNanos ->
                    currentAnimationTimeNanos = frameTimeNanos

                    // With the new frame started, copy

                    lastSegment = capturedSegment
                    lastGuaranteeState = capturedGuaranteeState
                    lastAnimation = capturedAnimation
                    lastSpringState = capturedSpringState
                    lastFrameTimeNanos = capturedFrameTimeNanos
                    lastInput = capturedInput
                    lastGestureDragOffset = capturedGestureDragOffset
                }

                // At this point, the complete frame is done (including layout, drawing and
                // everything else), and this MotionValue has been updated.

                // Capture the `current*` MotionValue state, so that it can be applied as the
                // `last*` state when the next frame starts. Its imperative to capture at this point
                // already (since the input could change before the next frame starts), while at the
                // same time not already applying the `last*` state (as this would cause a
                // re-computation if the current state is being read before the next frame).
                if (isAnimatingUninterrupted) {
                    directMappedVelocity =
                        computeDirectMappedVelocity(currentAnimationTimeNanos - lastFrameTimeNanos)
                } else {
                    directMappedVelocity = 0f
                }

                var scheduleNextFrame = false
                if (!isSameSegmentAndAtRest) {
                    // Read currentComputedValues only once and update it, if necessary
                    val currentValues = currentComputedValues

                    if (capturedSegment != currentValues.segment) {
                        capturedSegment = currentValues.segment
                        scheduleNextFrame = true
                    }

                    if (capturedGuaranteeState != currentValues.guarantee) {
                        capturedGuaranteeState = currentValues.guarantee
                        scheduleNextFrame = true
                    }

                    if (capturedAnimation != currentValues.animation) {
                        capturedAnimation = currentValues.animation
                        scheduleNextFrame = true
                    }

                    if (capturedSpringState != currentSpringState) {
                        capturedSpringState = currentSpringState
                        scheduleNextFrame = true
                    }
                }

                if (capturedInput != currentInput) {
                    capturedInput = currentInput
                    scheduleNextFrame = true
                }

                if (capturedGestureDragOffset != currentGestureDragOffset) {
                    capturedGestureDragOffset = currentGestureDragOffset
                    scheduleNextFrame = true
                }

                if (capturedDirection != currentDirection) {
                    capturedDirection = currentDirection
                    scheduleNextFrame = true
                }

                capturedFrameTimeNanos = currentAnimationTimeNanos

                debugInspector?.run {
                    frame =
                        FrameData(
                            capturedInput,
                            capturedDirection,
                            capturedGestureDragOffset,
                            capturedFrameTimeNanos,
                            capturedSpringState,
                            capturedSegment,
                            capturedAnimation,
                        )
                }

                isAnimatingUninterrupted = scheduleNextFrame
                if (scheduleNextFrame) {
                    continue
                }

                debugIsAnimating = false
                snapshotFlow {
                        val wakeup =
                            !continueRunning() ||
                                spec != capturedSegment.spec ||
                                currentInput != capturedInput ||
                                currentDirection != capturedDirection ||
                                currentGestureDragOffset != capturedGestureDragOffset
                        wakeup
                    }
                    .first { it }
                debugIsAnimating = true
            }
        } finally {
            isActive = false
            debugIsAnimating = false
        }
    }

    /** Whether a [keepRunning] coroutine is active currently. */
    var isActive = false
        set(value) {
            field = value
            debugInspector?.isActive = value
        }

    /**
     * `false` whenever the [keepRunning] coroutine is suspended while no animation is running and
     * the input is not changing.
     */
    var debugIsAnimating = false
        set(value) {
            field = value
            debugInspector?.isAnimating = value
        }

    var debugInspector: DebugInspector? = null
}
