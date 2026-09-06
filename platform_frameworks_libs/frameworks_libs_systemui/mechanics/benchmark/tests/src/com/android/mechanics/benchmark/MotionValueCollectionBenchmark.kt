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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.util.fastForEach
import com.android.mechanics.DistanceGestureContext
import com.android.mechanics.ManagedMotionValue
import com.android.mechanics.MotionValueCollection
import com.android.mechanics.spec.Guarantee
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.directionalMotionSpec
import com.android.mechanics.spring.SpringParameters
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import platform.test.motion.compose.MonotonicClockTestScope

/** Benchmark, which will execute on an Android device. Previous results: go/mm-microbenchmarks */
@RunWith(Parameterized::class)
class MotionValueCollectionBenchmark(private val instanceCount: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "instanceCount={0}")
        fun instanceCount() = listOf(1, 100)

        val DefaultSpring = SpringParameters(stiffness = 300f, dampingRatio = .9f)
    }

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

    private data class TestFixture(
        val collection: MotionValueCollection,
        val gestureContext: DistanceGestureContext,
        val instances: List<MotionValueInstance>,
    )

    private data class MotionValueInstance(
        val value: ManagedMotionValue,
        val spec: MutableState<MotionSpec>,
    )

    private fun MonotonicClockTestScope.testFixture(
        initialInput: Float = 0f,
        init: (Int) -> MotionSpec = { MotionSpec.Identity },
    ): TestFixture {
        val gestureContext = DistanceGestureContext(initialInput, InputDirection.Max, 2f)
        val collection =
            MotionValueCollection(
                { gestureContext.dragOffset },
                gestureContext,
                stableThreshold = MotionBuilderContext.StableThresholdEffects,
            )

        val instances =
            List(instanceCount) {
                val spec = mutableStateOf(init(it))
                val value = collection.create(spec::value)
                MotionValueInstance(value, spec)
            }

        val keepRunningJob = launch { collection.keepRunning() }
        tearDownOperations += { keepRunningJob.cancel() }

        return TestFixture(
            collection = collection,
            gestureContext = gestureContext,
            instances = instances,
        )
    }

    private fun MonotonicClockTestScope.nextFrame() {
        Snapshot.sendApplyNotifications()
        testScheduler.advanceTimeBy(16)
    }

    private fun MonotonicClockTestScope.measureOscillatingInput(
        fixture: TestFixture,
        stepSize: Float = 1f,
    ) {
        var step = stepSize
        benchmarkRule.measureRepeated {
            val lastInput = fixture.gestureContext.dragOffset
            if (lastInput <= .5f) step = stepSize else if (lastInput >= 9.5f) step = -stepSize
            fixture.gestureContext.dragOffset = lastInput + step
            nextFrame()
        }
    }

    @Test
    fun noChange() = runMonotonicClockTest {
        val fixture = testFixture()

        measureOscillatingInput(fixture, stepSize = 0f)
    }

    @Test
    fun changeInput() = runMonotonicClockTest {
        val fixture = testFixture()

        measureOscillatingInput(fixture)
    }

    @Test
    fun changeInput_sameOutput() = runMonotonicClockTest {
        val spec = MotionSpec(directionalMotionSpec(Mapping.Zero))

        val fixture = testFixture(initialInput = 4f) { spec }
        measureOscillatingInput(fixture)
    }

    @Test
    fun changeSegment_noDiscontinuity() = runMonotonicClockTest {
        val spec =
            MotionSpec(
                directionalMotionSpec(DefaultSpring, Mapping.Zero) {
                    mapping(breakpoint = 5f, mapping = Mapping.Zero)
                }
            )

        val fixture = testFixture(initialInput = 4f) { spec }
        measureOscillatingInput(fixture)
    }

    @Test
    fun animateOutput() = runMonotonicClockTest {
        val spec =
            MotionSpec(
                directionalMotionSpec(DefaultSpring, Mapping.Zero) {
                    fixedValue(breakpoint = 5f, value = 1f)
                }
            )

        val fixture = testFixture(initialInput = 4f) { spec }
        measureOscillatingInput(fixture)
    }

    @Test
    fun animateWithGuarantee() = runMonotonicClockTest {
        val spec =
            MotionSpec(
                directionalMotionSpec(DefaultSpring, Mapping.Zero) {
                    fixedValue(breakpoint = 5f, value = 1f, guarantee = Guarantee.InputDelta(4f))
                }
            )

        val fixture = testFixture { spec }
        measureOscillatingInput(fixture)
    }
}
