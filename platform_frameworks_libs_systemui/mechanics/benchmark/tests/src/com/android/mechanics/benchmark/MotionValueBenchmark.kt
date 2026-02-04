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

package com.android.mechanics.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastForEach
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.DistanceGestureContext
import com.android.mechanics.MotionValue
import com.android.mechanics.spec.Guarantee
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.builder.directionalMotionSpec
import com.android.mechanics.spring.SpringParameters
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.MonotonicClockTestScope

/** Benchmark, which will execute on an Android device. Previous results: go/mm-microbenchmarks */
@RunWith(AndroidJUnit4::class)
class MotionValueBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    private val tearDownOperations = mutableListOf<() -> Unit>()

    /**
     * Runs a test block within a [MonotonicClockTestScope] provided by the underlying
     * [platform.test.motion.compose.runMonotonicClockTest] and ensures automatic cleanup.
     *
     * This mechanism provides a convenient way to register cleanup actions (e.g., stopping
     * coroutines, resetting states) that should reliably run at the end of the test, simplifying
     * test setup and teardown.
     */
    private fun runMonotonicClockTest(block: suspend MonotonicClockTestScope.() -> Unit) {
        return platform.test.motion.compose.runMonotonicClockTest {
            try {
                block()
            } finally {
                tearDownOperations.fastForEach { it.invoke() }
            }
        }
    }

    private data class TestData(
        val motionValue: MotionValue,
        val gestureContext: DistanceGestureContext,
        val input: MutableFloatState,
        val spec: MotionSpec,
    )

    private fun testData(
        gestureContext: DistanceGestureContext = DistanceGestureContext(0f, InputDirection.Max, 2f),
        input: Float = 0f,
        spec: MotionSpec = MotionSpec.Empty,
    ): TestData {
        val inputState = mutableFloatStateOf(input)
        return TestData(
            motionValue = MotionValue(inputState::floatValue, gestureContext, spec),
            gestureContext = gestureContext,
            input = inputState,
            spec = spec,
        )
    }

    // Fundamental operations on MotionValue: create, read, update.

    @Test
    fun createMotionValue() {
        val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 2f)
        val input = { 0f }

        benchmarkRule.measureRepeated { MotionValue(input, gestureContext) }
    }

    @Test
    fun stable_readOutput_noChanges() {
        val data = testData()

        // The first read may cost more than the others, it is not interesting for this test.
        data.motionValue.floatValue

        benchmarkRule.measureRepeated { data.motionValue.floatValue }
    }

    @Test
    fun stable_readOutput_afterWriteInput() {
        val data = testData()

        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled { data.input.floatValue += 1f }
            data.motionValue.floatValue
        }
    }

    @Test
    fun stable_writeInput_AND_readOutput() {
        val data = testData()

        benchmarkRule.measureRepeated {
            data.input.floatValue += 1f
            data.motionValue.floatValue
        }
    }

    @Test
    fun stable_writeInput_AND_readOutput_keepRunning() = runMonotonicClockTest {
        val data = testData()
        keepRunningDuringTest(data.motionValue)

        benchmarkRule.measureRepeated {
            data.input.floatValue += 1f
            testScheduler.advanceTimeBy(16)
            data.motionValue.floatValue
        }
    }

    @Test
    fun stable_writeInput_AND_readOutput_100motionValues_keepRunning() = runMonotonicClockTest {
        val dataList = List(100) { testData() }
        dataList.forEach { keepRunningDuringTest(it.motionValue) }

        benchmarkRule.measureRepeated {
            dataList.fastForEach { it.input.floatValue += 1f }
            testScheduler.advanceTimeBy(16)
            dataList.fastForEach { it.motionValue.floatValue }
        }
    }

    @Test
    fun stable_readOutput_100motionValues_keepRunning() = runMonotonicClockTest {
        val dataList = List(100) { testData() }
        dataList.forEach { keepRunningDuringTest(it.motionValue) }

        benchmarkRule.measureRepeated {
            testScheduler.advanceTimeBy(16)
            dataList.fastForEach { it.motionValue.floatValue }
        }
    }

    // Animations

    private fun MonotonicClockTestScope.keepRunningDuringTest(motionValue: MotionValue) {
        val keepRunningJob = launch { motionValue.keepRunning() }
        tearDownOperations += { keepRunningJob.cancel() }
    }

    private val MotionSpec.Companion.ZeroToOne_AtOne
        get() =
            MotionSpec(
                directionalMotionSpec(
                    defaultSpring = SpringParameters(stiffness = 300f, dampingRatio = .9f),
                    initialMapping = Mapping.Zero,
                ) {
                    fixedValue(breakpoint = 1f, value = 1f)
                }
            )

    private val InputDirection.opposite
        get() = if (this == InputDirection.Min) InputDirection.Max else InputDirection.Min

    @Test
    fun unstable_resetGestureContext_readOutput() = runMonotonicClockTest {
        val data = testData(input = 1f, spec = MotionSpec.ZeroToOne_AtOne)
        keepRunningDuringTest(data.motionValue)

        benchmarkRule.measureRepeated {
            if (data.motionValue.isStable) {
                data.gestureContext.reset(0f, data.gestureContext.direction.opposite)
            }
            testScheduler.advanceTimeBy(16)
            data.motionValue.floatValue
        }
    }

    @Test
    fun unstable_resetGestureContext_readOutput_100motionValues() = runMonotonicClockTest {
        val dataList = List(100) { testData(input = 1f, spec = MotionSpec.ZeroToOne_AtOne) }
        dataList.forEach { keepRunningDuringTest(it.motionValue) }

        benchmarkRule.measureRepeated {
            dataList.fastForEach { data ->
                if (data.motionValue.isStable) {
                    data.gestureContext.reset(0f, data.gestureContext.direction.opposite)
                }
            }
            testScheduler.advanceTimeBy(16)
            dataList.fastForEach { it.motionValue.floatValue }
        }
    }

    @Test
    fun unstable_resetGestureContext_snapshotFlowOutput() = runMonotonicClockTest {
        val data = testData(input = 1f, spec = MotionSpec.ZeroToOne_AtOne)
        keepRunningDuringTest(data.motionValue)

        snapshotFlow { data.motionValue.floatValue }.launchIn(backgroundScope)

        benchmarkRule.measureRepeated {
            if (data.motionValue.isStable) {
                data.gestureContext.reset(0f, data.gestureContext.direction.opposite)
            }
            testScheduler.advanceTimeBy(16)
        }
    }

    private val MotionSpec.Companion.ZeroToOne_AtOne_WithGuarantee
        get() =
            MotionSpec(
                directionalMotionSpec(
                    defaultSpring = SpringParameters(stiffness = 300f, dampingRatio = .9f),
                    initialMapping = Mapping.Zero,
                ) {
                    fixedValue(
                        breakpoint = 1f,
                        value = 1f,
                        guarantee = Guarantee.GestureDragDelta(1f),
                    )
                }
            )

    @Test
    fun unstable_resetGestureContext_guarantee_readOutput() = runMonotonicClockTest {
        val data = testData(input = 1f, spec = MotionSpec.ZeroToOne_AtOne_WithGuarantee)
        keepRunningDuringTest(data.motionValue)

        benchmarkRule.measureRepeated {
            if (data.motionValue.isStable) {
                data.gestureContext.reset(0f, data.gestureContext.direction.opposite)
            } else {
                val isMax = data.gestureContext.direction == InputDirection.Max
                data.gestureContext.dragOffset += if (isMax) 0.01f else -0.01f
            }

            testScheduler.advanceTimeBy(16)
            data.motionValue.floatValue
        }
    }
}
