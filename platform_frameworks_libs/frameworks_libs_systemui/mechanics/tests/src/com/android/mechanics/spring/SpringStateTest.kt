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

package com.android.mechanics.spring

import android.platform.test.annotations.MotionTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.testing.asDataPoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.RecordedMotion.Companion.create
import platform.test.motion.golden.DataPoint
import platform.test.motion.golden.Feature
import platform.test.motion.golden.FrameId
import platform.test.motion.golden.TimeSeries
import platform.test.motion.golden.TimestampFrameId
import platform.test.motion.golden.asDataPoint
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
@MotionTest
class SpringStateTest {
    private val goldenPathManager =
        createGoldenPathManager("frameworks/libs/systemui/mechanics/tests/goldens")

    @get:Rule val motion = MotionTestRule(Unit, goldenPathManager)

    @Test
    fun criticallyDamped_matchesGolden() {
        val parameters = SpringParameters(stiffness = 100f, dampingRatio = 1f)
        val initialState = SpringState(displacement = 10f)

        assertSpringMotionMatchesGolden(initialState) { parameters }
    }

    @Test
    fun overDamped_matchesGolden() {
        val parameters = SpringParameters(stiffness = 100f, dampingRatio = 2f)
        val initialState = SpringState(displacement = 10f)

        assertSpringMotionMatchesGolden(initialState) { parameters }
    }

    @Test
    fun underDamped_matchesGolden() {
        val parameters = SpringParameters(stiffness = 100f, dampingRatio = .3f)
        val initialState = SpringState(displacement = 10f)

        assertSpringMotionMatchesGolden(initialState) { parameters }
    }

    @Test
    fun zeroDisplacement_initialVelocity_matchesGolden() {
        val parameters = SpringParameters(stiffness = 100f, dampingRatio = .3f)
        val initialState = SpringState(displacement = 0f, velocity = 10f)

        assertSpringMotionMatchesGolden(initialState) { parameters }
    }

    @Test
    fun snapSpring_updatesImmediately_matchesGolden() {
        val initialState = SpringState(displacement = 10f, velocity = -10f)

        assertSpringMotionMatchesGolden(initialState) { SpringParameters.Snap }
    }

    @Test
    fun stiffeningSpring_matchesGolden() {
        val parameters = SpringParameters(stiffness = 100f, dampingRatio = .3f)
        val initialState = SpringState(displacement = 10f, velocity = -10f)

        assertSpringMotionMatchesGolden(initialState) {
            lerp(parameters, SpringParameters.Snap, it / 200f)
        }
    }

    private fun assertSpringMotionMatchesGolden(
        initialState: SpringState,
        stableThreshold: Float = 0.01f,
        sampleFrequencyHz: Float = 100f,
        springParameters: (timeMillis: Long) -> SpringParameters,
    ) {
        val sampleDurationMillis = (1_000f / sampleFrequencyHz).toLong()

        val frameIds = mutableListOf<FrameId>()

        val displacement = mutableListOf<DataPoint<Float>>()
        val velocity = mutableListOf<DataPoint<Float>>()
        val isStable = mutableListOf<DataPoint<Boolean>>()
        val params = mutableListOf<DataPoint<SpringParameters>>()

        var iterationTimeMillis = 0L
        var keepRecording = 2

        var springState = initialState
        while (keepRecording > 0 && frameIds.size < 1000) {
            frameIds.add(TimestampFrameId(iterationTimeMillis))

            val parameters = springParameters(iterationTimeMillis)
            val currentlyStable = springState.isStable(parameters, stableThreshold)
            if (currentlyStable) {
                keepRecording--
            }

            displacement.add(springState.displacement.asDataPoint())
            velocity.add(springState.velocity.asDataPoint())
            isStable.add(currentlyStable.asDataPoint())
            params.add(parameters.asDataPoint())

            val elapsedNanos = sampleDurationMillis * 1_000_000
            springState = springState.calculateUpdatedState(elapsedNanos, parameters)
            iterationTimeMillis += sampleDurationMillis
        }

        val timeSeries =
            TimeSeries(
                frameIds.toList(),
                listOf(
                    Feature("displacement", displacement),
                    Feature("velocity", velocity),
                    Feature("stable", isStable),
                    Feature("parameters", params),
                ),
            )

        val recordedMotion = motion.create(timeSeries, screenshots = null)
        motion.assertThat(recordedMotion).timeSeriesMatchesGolden()
    }
}
