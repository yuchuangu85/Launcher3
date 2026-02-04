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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.mechanics.testing

import android.animation.AnimatorTestRule
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.view.DistanceGestureContext
import com.android.mechanics.view.ViewMotionValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.test.motion.MotionTestRule
import platform.test.motion.golden.FrameId
import platform.test.motion.golden.TimeSeries
import platform.test.motion.golden.TimestampFrameId

/** Toolkit to support [ViewMotionValue] motion tests. */
class ViewMotionValueToolkit(private val animatorTestRule: AnimatorTestRule) :
    MotionValueToolkit<ViewMotionValue, DistanceGestureContext>() {

    override fun goldenTest(
        motionTestRule: MotionTestRule<*>,
        spec: MotionSpec,
        createDerived: (underTest: ViewMotionValue) -> List<ViewMotionValue>,
        initialValue: Float,
        initialDirection: InputDirection,
        directionChangeSlop: Float,
        stableThreshold: Float,
        verifyTimeSeries: TimeSeries.() -> VerifyTimeSeriesResult,
        capture: CaptureTimeSeriesFn,
        testInput: suspend InputScope<ViewMotionValue, DistanceGestureContext>.() -> Unit,
    ) = runTest {
        val frameEmitter = MutableStateFlow<Long>(0)

        val testHarness =
            runBlocking(Dispatchers.Main) {
                ViewMotionValueTestHarness(
                        initialValue,
                        initialDirection,
                        spec,
                        stableThreshold,
                        directionChangeSlop,
                        frameEmitter.asStateFlow(),
                        createDerived,
                    )
                    .also { animatorTestRule.initNewAnimators() }
            }

        val underTest = testHarness.underTest
        val motionValueCapture = MotionValueCapture(underTest.debugInspector())
        val recordingJob = launch { testInput.invoke(testHarness) }

        val frameIds = mutableListOf<FrameId>()

        fun recordFrame(frameId: TimestampFrameId) {
            frameIds.add(frameId)
            motionValueCapture.captureCurrentFrame(capture)
        }

        runBlocking(Dispatchers.Main) {
            val startFrameTime = animatorTestRule.currentTime
            while (!recordingJob.isCompleted) {
                recordFrame(TimestampFrameId(animatorTestRule.currentTime - startFrameTime))

                frameEmitter.tryEmit(animatorTestRule.currentTime)
                runCurrent()

                animatorTestRule.advanceTimeBy(FrameDuration.inWholeMilliseconds)
                runCurrent()
            }

            val timeSeries = createTimeSeries(frameIds, listOf(motionValueCapture))

            motionValueCapture.debugger.dispose()
            underTest.dispose()
            verifyTimeSeries(motionTestRule, timeSeries, verifyTimeSeries)
        }
    }
}

private class ViewMotionValueTestHarness(
    initialInput: Float,
    initialDirection: InputDirection,
    spec: MotionSpec,
    stableThreshold: Float,
    directionChangeSlop: Float,
    val onFrame: StateFlow<Long>,
    createDerived: (underTest: ViewMotionValue) -> List<ViewMotionValue>,
) : InputScope<ViewMotionValue, DistanceGestureContext> {

    override val gestureContext =
        DistanceGestureContext(initialInput, initialDirection, directionChangeSlop)

    override val underTest =
        ViewMotionValue(
            initialInput,
            gestureContext,
            stableThreshold = stableThreshold,
            initialSpec = spec,
        )

    override var input by underTest::input

    init {
        require(createDerived(underTest).isEmpty()) {
            "testing derived values is not yet supported"
        }
    }

    override fun updateInput(value: Float) {
        input = value
        gestureContext.dragOffset = value
    }

    override suspend fun awaitStable() {
        val debugInspectors = buildList { add(underTest.debugInspector()) }
        try {

            onFrame
                // Since this is a state-flow, the current frame is counted too.
                .drop(1)
                .takeWhile { debugInspectors.any { !it.frame.isStable } }
                .collect {}
        } finally {
            debugInspectors.forEach { it.dispose() }
        }
    }

    override suspend fun awaitFrames(frames: Int) {
        onFrame
            // Since this is a state-flow, the current frame is counted too.
            .drop(1)
            .take(frames)
            .collect {}
    }

    override fun reset(position: Float, direction: InputDirection) {
        input = position
        gestureContext.reset(position, direction)
    }
}
