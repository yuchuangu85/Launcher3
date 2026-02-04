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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.android.mechanics.DistanceGestureContext
import com.android.mechanics.MotionValue
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.MotionSpec
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
import platform.test.motion.MotionTestRule
import platform.test.motion.compose.runMonotonicClockTest
import platform.test.motion.golden.FrameId
import platform.test.motion.golden.TimeSeries
import platform.test.motion.golden.TimestampFrameId

/** Toolkit to support [MotionValue] motion tests. */
data object ComposeMotionValueToolkit : MotionValueToolkit<MotionValue, DistanceGestureContext>() {

    override fun goldenTest(
        motionTestRule: MotionTestRule<*>,
        spec: MotionSpec,
        createDerived: (underTest: MotionValue) -> List<MotionValue>,
        initialValue: Float,
        initialDirection: InputDirection,
        directionChangeSlop: Float,
        stableThreshold: Float,
        verifyTimeSeries: TimeSeries.() -> VerifyTimeSeriesResult,
        capture: CaptureTimeSeriesFn,
        testInput: suspend InputScope<MotionValue, DistanceGestureContext>.() -> Unit,
    ) = runMonotonicClockTest {
        val frameEmitter = MutableStateFlow<Long>(0)

        val testHarness =
            ComposeMotionValueTestHarness(
                initialValue,
                initialDirection,
                spec,
                stableThreshold,
                directionChangeSlop,
                frameEmitter.asStateFlow(),
                createDerived,
            )
        val underTest = testHarness.underTest
        val derived = testHarness.derived

        val motionValueCaptures = buildList {
            add(MotionValueCapture(underTest.debugInspector()))
            derived.forEach { add(MotionValueCapture(it.debugInspector(), "${it.label}-")) }
        }

        val keepRunningJobs = (derived + underTest).map { launch { it.keepRunning() } }

        val recordingJob = launch { testInput.invoke(testHarness) }

        val frameIds = mutableListOf<FrameId>()

        fun recordFrame(frameId: TimestampFrameId) {
            frameIds.add(frameId)
            motionValueCaptures.forEach { it.captureCurrentFrame(capture) }
        }
        runBlocking(Dispatchers.Main) {
            val startFrameTime = testScheduler.currentTime
            while (!recordingJob.isCompleted) {
                recordFrame(TimestampFrameId(testScheduler.currentTime - startFrameTime))

                // Emulate setting input *before* the frame advances. This ensures the `testInput`
                // coroutine will continue if needed. The specific value for frameEmitter is
                // irrelevant, it only requires to be unique per frame.
                frameEmitter.tryEmit(testScheduler.currentTime)
                testScheduler.runCurrent()
                // Whenever keepRunning was suspended, allow the snapshotFlow to wake up
                Snapshot.sendApplyNotifications()

                // Now advance the test clock
                testScheduler.advanceTimeBy(FrameDuration)
                // Since the tests capture the debugInspector output, make sure keepRunning()
                // was able to complete the frame.
                testScheduler.runCurrent()
            }
        }

        val timeSeries = createTimeSeries(frameIds, motionValueCaptures)
        motionValueCaptures.forEach { it.debugger.dispose() }
        keepRunningJobs.forEach { it.cancel() }
        verifyTimeSeries(motionTestRule, timeSeries, verifyTimeSeries)
    }
}

private class ComposeMotionValueTestHarness(
    initialInput: Float,
    initialDirection: InputDirection,
    spec: MotionSpec,
    stableThreshold: Float,
    directionChangeSlop: Float,
    val onFrame: StateFlow<Long>,
    createDerived: (underTest: MotionValue) -> List<MotionValue>,
) : InputScope<MotionValue, DistanceGestureContext> {

    override var input by mutableFloatStateOf(initialInput)
    override val gestureContext: DistanceGestureContext =
        DistanceGestureContext(initialInput, initialDirection, directionChangeSlop)

    override val underTest =
        MotionValue(
            { input },
            gestureContext,
            stableThreshold = stableThreshold,
            initialSpec = spec,
        )

    val derived = createDerived(underTest)

    override fun updateInput(value: Float) {
        input = value
        gestureContext.dragOffset = value
    }

    override suspend fun awaitStable() {
        val debugInspectors = buildList {
            add(underTest.debugInspector())
            addAll(derived.map { it.debugInspector() })
        }
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
