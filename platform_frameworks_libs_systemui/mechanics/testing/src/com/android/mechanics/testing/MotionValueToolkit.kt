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

package com.android.mechanics.testing

import com.android.mechanics.MotionValue
import com.android.mechanics.debug.DebugInspector
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.MotionSpec
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds
import platform.test.motion.MotionTestRule
import platform.test.motion.RecordedMotion.Companion.create
import platform.test.motion.golden.DataPoint
import platform.test.motion.golden.Feature
import platform.test.motion.golden.FrameId
import platform.test.motion.golden.TimeSeries
import platform.test.motion.golden.TimeSeriesCaptureScope

/**
 * Records and verifies a timeseries of the [MotionValue]'s output.
 *
 * Tests provide at a minimum the initial [spec], and a [testInput] function, which defines the
 * [MotionValue] input over time.
 *
 * @param spec The initial [MotionSpec]
 * @param initialValue The initial value of the [MotionValue]
 * @param initialDirection The initial [InputDirection] of the [MotionValue]
 * @param directionChangeSlop the minimum distance for the input to change in the opposite direction
 *   before the underlying GestureContext changes direction.
 * @param stableThreshold The maximum remaining oscillation amplitude for the springs to be
 *   considered stable.
 * @param verifyTimeSeries Custom verification function to write assertions on the captured time
 *   series. If the function returns `SkipGoldenVerification`, the timeseries won`t be compared to a
 *   golden.
 * @param createDerived (experimental) Creates derived MotionValues
 * @param capture The features to capture on each motion value. See [defaultFeatureCaptures] for
 *   defaults.
 * @param testInput Controls the MotionValue during the test. The timeseries is being recorded until
 *   the function completes.
 * @see ComposeMotionValueToolkit
 * @see ViewMotionValueToolkit
 */
fun <
    T : MotionValueToolkit<MotionValueType, GestureContextType>,
    MotionValueType,
    GestureContextType,
> MotionTestRule<T>.goldenTest(
    spec: MotionSpec,
    initialValue: Float = 0f,
    initialDirection: InputDirection = InputDirection.Max,
    directionChangeSlop: Float = 5f,
    stableThreshold: Float = 0.01f,
    verifyTimeSeries: VerifyTimeSeriesFn = {
        VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden()
    },
    createDerived: (underTest: MotionValueType) -> List<MotionValueType> = { emptyList() },
    capture: CaptureTimeSeriesFn = defaultFeatureCaptures,
    testInput: suspend (InputScope<MotionValueType, GestureContextType>).() -> Unit,
) {
    toolkit.goldenTest(
        this,
        spec,
        createDerived,
        initialValue,
        initialDirection,
        directionChangeSlop,
        stableThreshold,
        verifyTimeSeries,
        capture,
        testInput,
    )
}

/** Scope to control the MotionValue during a test. */
interface InputScope<MotionValueType, GestureContextType> {
    /** Current input of the `MotionValue` */
    val input: Float
    /** GestureContext created for the `MotionValue` */
    val gestureContext: GestureContextType
    /** MotionValue being tested. */
    val underTest: MotionValueType

    /** Updates the input value *and* the `gestureContext.dragOffset`. */
    fun updateInput(value: Float)

    /** Resets the input value *and* the `gestureContext.dragOffset`, inclusive of direction. */
    fun reset(position: Float, direction: InputDirection)

    /** Waits for `underTest` and derived `MotionValues` to become stable. */
    suspend fun awaitStable()

    /** Waits for the next "frame" (16ms). */
    suspend fun awaitFrames(frames: Int = 1)
}

/** Animates the input linearly from the current [input] to the [targetValue]. */
suspend fun InputScope<*, *>.animateValueTo(
    targetValue: Float,
    changePerFrame: Float = abs(input - targetValue) / 5f,
) {
    require(changePerFrame > 0f)
    var currentValue = input
    val delta = targetValue - currentValue
    val step = changePerFrame * delta.sign

    val stepCount = floor((abs(delta) / changePerFrame) - 1).toInt()
    repeat(stepCount) {
        currentValue += step
        updateInput(currentValue)
        awaitFrames()
    }

    updateInput(targetValue)
    awaitFrames()
}

/** Sets the input to the [values], one value per frame. */
suspend fun InputScope<*, *>.animatedInputSequence(vararg values: Float) {
    values.forEach {
        updateInput(it)
        awaitFrames()
    }
}

/** Custom functions to write assertions on the recorded [TimeSeries] */
typealias VerifyTimeSeriesFn = TimeSeries.() -> VerifyTimeSeriesResult

/** [VerifyTimeSeriesFn] indicating whether the timeseries should be verified the golden file. */
interface VerifyTimeSeriesResult {
    data object SkipGoldenVerification : VerifyTimeSeriesResult

    data class AssertTimeSeriesMatchesGolden(val goldenName: String? = null) :
        VerifyTimeSeriesResult
}

typealias CaptureTimeSeriesFn = TimeSeriesCaptureScope<DebugInspector>.() -> Unit

/** Default feature captures. */
val defaultFeatureCaptures: CaptureTimeSeriesFn = {
    feature(FeatureCaptures.input)
    feature(FeatureCaptures.gestureDirection)
    feature(FeatureCaptures.output)
    feature(FeatureCaptures.outputTarget)
    feature(FeatureCaptures.springParameters, name = "outputSpring")
    feature(FeatureCaptures.isStable)
}

sealed class MotionValueToolkit<MotionValueType, GestureContextType> {
    internal abstract fun goldenTest(
        motionTestRule: MotionTestRule<*>,
        spec: MotionSpec,
        createDerived: (underTest: MotionValueType) -> List<MotionValueType>,
        initialValue: Float,
        initialDirection: InputDirection,
        directionChangeSlop: Float,
        stableThreshold: Float,
        verifyTimeSeries: TimeSeries.() -> VerifyTimeSeriesResult,
        capture: CaptureTimeSeriesFn,
        testInput: suspend (InputScope<MotionValueType, GestureContextType>).() -> Unit,
    )

    internal fun createTimeSeries(
        frameIds: List<FrameId>,
        motionValueCaptures: List<MotionValueCapture>,
    ): TimeSeries {
        return TimeSeries(
            frameIds.toList(),
            motionValueCaptures.flatMap { motionValueCapture ->
                motionValueCapture.propertyCollector.entries.map { (name, dataPoints) ->
                    Feature("${motionValueCapture.prefix}$name", dataPoints)
                }
            },
        )
    }

    internal fun verifyTimeSeries(
        motionTestRule: MotionTestRule<*>,
        timeSeries: TimeSeries,
        verificationFn: TimeSeries.() -> VerifyTimeSeriesResult,
    ) {
        val recordedMotion = motionTestRule.create(timeSeries, screenshots = null)
        var assertTimeseriesMatchesGolden = false
        var goldenName: String? = null
        try {

            val result = verificationFn.invoke(recordedMotion.timeSeries)
            if (result is VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden) {
                assertTimeseriesMatchesGolden = true
                goldenName = result.goldenName
            }
        } finally {
            try {
                motionTestRule.assertThat(recordedMotion).timeSeriesMatchesGolden(goldenName)
            } catch (e: AssertionError) {
                if (assertTimeseriesMatchesGolden) {
                    throw e
                }
            }
        }
    }

    companion object {
        val FrameDuration = 16.milliseconds
    }
}

internal class MotionValueCapture(val debugger: DebugInspector, val prefix: String = "") {
    val propertyCollector = mutableMapOf<String, MutableList<DataPoint<*>>>()
    val captureScope = TimeSeriesCaptureScope(debugger, propertyCollector)

    fun captureCurrentFrame(captureFn: CaptureTimeSeriesFn) {
        captureFn(captureScope)
    }
}
