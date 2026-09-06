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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.mechanics.testing

import android.annotation.SuppressLint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.android.mechanics.DistanceGestureContext
import com.android.mechanics.ManagedMotionValue
import com.android.mechanics.MotionValueCollection
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.MotionSpec
import kotlinx.coroutines.CompletableDeferred
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
import platform.test.motion.golden.asDataPoint

interface CollectionInputScope : InputScope<MotionValueCollection, DistanceGestureContext> {
    val motionValues: Set<ManagedMotionValue>

    fun motionValueWithLabel(label: String): ManagedMotionValue?
}

/** Toolkit to support [MotionValueCollection] motion tests. */
object ComposeMotionValueCollectionToolkit :
    MotionValueToolkit<
        CollectionInputScope,
        MotionValueCollection,
        ManagedMotionValue,
        DistanceGestureContext,
    >() {

    @SuppressLint("VisibleForTests")
    override fun goldenTest(
        motionTestRule: MotionTestRule<*>,
        spec: MotionSpec,
        createDerived: (underTest: MotionValueCollection) -> List<ManagedMotionValue>,
        initialValue: Float,
        initialDirection: InputDirection,
        directionChangeSlop: Float,
        stableThreshold: Float,
        verifyTimeSeries: TimeSeries.() -> VerifyTimeSeriesResult,
        capture: CaptureTimeSeriesFn,
        testInput: suspend CollectionInputScope.() -> Unit,
    ) = runMonotonicClockTest {
        val frameEmitter = MutableStateFlow(0L)
        val testHarness =
            ComposeMotionValueCollectionTestHarness(
                frameEmitter.asStateFlow(),
                spec,
                initialValue,
                initialDirection,
                directionChangeSlop,
                stableThreshold,
            )
        val underTest = testHarness.underTest
        testHarness.createMotionValue("primary", testHarness::spec)
        createDerived(underTest)

        val motionValueCaptures = buildList {
            testHarness.motionValues.forEach {
                add(MotionValueCapture(it.debugInspector(), "${it.label}-"))
            }
        }

        val collectionCapture = GenericValueCapture(testHarness.underTest)

        val keepRunningJob = launch { underTest.keepRunning() }

        val latch = CompletableDeferred<Unit>()

        val recordingJob = launch {
            latch.await()
            testInput.invoke(testHarness)
        }
        val frameIds = mutableListOf<FrameId>()

        fun recordFrame(frameId: TimestampFrameId) {

            frameIds.add(frameId)

            collectionCapture.captureCurrentFrame {
                feature("input") { it.currentInput.asDataPoint() }
                feature("gestureDirection") { it.currentDirection.name.asDataPoint() }
            }
            motionValueCaptures.forEach { it.captureCurrentFrame(capture) }
        }

        runBlocking(Dispatchers.Main) {
            while (!underTest.isActive) {
                testScheduler.runCurrent()
                Snapshot.sendApplyNotifications()
                testScheduler.advanceTimeBy(FrameDuration)
                testScheduler.runCurrent()
            }

            latch.complete(Unit)

            val startFrameTime = testScheduler.currentTime
            while (!recordingJob.isCompleted) {
                recordFrame(TimestampFrameId(testScheduler.currentTime - startFrameTime))

                frameEmitter.tryEmit(testScheduler.currentTime)
                testScheduler.runCurrent()
                Snapshot.sendApplyNotifications()

                testScheduler.advanceTimeBy(FrameDuration)
                testScheduler.runCurrent()
            }
        }

        val timeSeries =
            createTimeSeries(
                frameIds,
                buildList {
                    add(collectionCapture)
                    addAll(motionValueCaptures)
                },
            )
        motionValueCaptures.forEach { it.debugger.dispose() }
        keepRunningJob.cancel()
        verifyTimeSeries(motionTestRule, timeSeries, verifyTimeSeries)
    }
}

private class ComposeMotionValueCollectionTestHarness(
    private val onFrame: StateFlow<Long>,
    primarySpec: MotionSpec,
    initialInput: Float,
    initialDirection: InputDirection,
    directionChangeSlop: Float,
    stableThreshold: Float,
) : CollectionInputScope {
    override val motionValues: Set<ManagedMotionValue>
        get() = underTest.managedMotionValues

    override fun motionValueWithLabel(label: String): ManagedMotionValue? {
        return motionValues.firstOrNull { it.label == label }
    }

    override var input by mutableFloatStateOf(initialInput)
    override val gestureContext =
        DistanceGestureContext(initialInput, initialDirection, directionChangeSlop)

    override val underTest = MotionValueCollection(::input, gestureContext, stableThreshold)
    override var spec: MotionSpec by mutableStateOf(primarySpec)

    fun createMotionValue(label: String, spec: () -> MotionSpec): ManagedMotionValue {
        return underTest.create(spec, label)
    }

    override fun updateInput(value: Float) {
        input = value
        gestureContext.dragOffset = value
    }

    override suspend fun awaitStable() {
        val debugInspectors = buildList { addAll(motionValues.map { it.debugInspector() }) }
        try {
            onFrame.drop(1).takeWhile { debugInspectors.any { !it.frame.isStable } }.collect {}
        } finally {
            debugInspectors.forEach { it.dispose() }
        }
    }

    override suspend fun awaitFrames(frames: Int) {
        onFrame.drop(1).take(frames).collect {}
    }

    override fun reset(position: Float, direction: InputDirection) {
        input = position
        gestureContext.reset(position, direction)
    }
}
