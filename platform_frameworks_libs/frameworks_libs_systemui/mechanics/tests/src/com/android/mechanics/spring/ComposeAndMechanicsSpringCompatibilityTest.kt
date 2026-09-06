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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeAndMechanicsSpringCompatibilityTest {

    @Test
    fun criticallyDamped_matchesComposeSpring() = runTestWithFrameClock {
        assertMechanicsMatchesComposeSpringMovement(
            SpringParameters(stiffness = 100f, dampingRatio = 1f)
        )
    }

    @Test
    fun underDamped_matchesComposeSpring() = runTestWithFrameClock {
        assertMechanicsMatchesComposeSpringMovement(
            SpringParameters(stiffness = 1000f, dampingRatio = .5f)
        )
    }

    @Test
    fun overDamped_matchesComposeSpring() = runTestWithFrameClock {
        assertMechanicsMatchesComposeSpringMovement(
            SpringParameters(stiffness = 2000f, dampingRatio = 1.5f)
        )
    }

    @Test
    fun withInitialVelocity_matchesComposeSpring() = runTestWithFrameClock {
        assertMechanicsMatchesComposeSpringMovement(
            SpringParameters(stiffness = 2000f, dampingRatio = .85f),
            startDisplacement = 0f,
            initialVelocity = 10f,
        )
    }

    private suspend fun assertMechanicsMatchesComposeSpringMovement(
        parameters: SpringParameters,
        startDisplacement: Float = 10f,
        initialVelocity: Float = 0f,
    ) {
        val byCompose = computeComposeSpringValues(startDisplacement, initialVelocity, parameters)

        val byMechanics =
            computeMechanicsSpringValues(startDisplacement, initialVelocity, parameters)

        assertSpringValuesMatch(byMechanics, byCompose)
    }

    private suspend fun computeComposeSpringValues(
        displacement: Float,
        initialVelocity: Float,
        parameters: SpringParameters,
    ) = buildList {
        Animatable(displacement, DisplacementThreshold).animateTo(
            0f,
            parameters.asSpringSpec(),
            initialVelocity,
        ) {
            add(SpringState(value, velocity))
        }
    }

    private fun computeMechanicsSpringValues(
        displacement: Float,
        initialVelocity: Float,
        parameters: SpringParameters,
    ) = buildList {
        var state = SpringState(displacement, initialVelocity)
        while (!state.isStable(parameters, DisplacementThreshold)) {
            add(state)
            state = state.calculateUpdatedState(FrameDelayNanos, parameters)
        }
    }

    private fun assertSpringValuesMatch(
        byMechanics: List<SpringState>,
        byCompose: List<SpringState>,
    ) {
        // Last element by compose is zero displacement, zero velocity
        assertThat(byCompose.last()).isEqualTo(SpringState.AtRest)

        // Mechanics computes when the spring is stable differently. Allow some variance.
        assertThat(abs(byMechanics.size - byCompose.size)).isAtMost(2)

        // All frames until either one is considered stable must produce the same displacement
        // and velocity
        val maxFramesToExactlyCompare = min(byMechanics.size, byCompose.size - 1)
        val tolerance = 0.0001f
        for (i in 0 until maxFramesToExactlyCompare) {
            val mechanics = byMechanics[i]
            val compose = byCompose[i]
            assertThat(mechanics.displacement).isWithin(tolerance).of(compose.displacement)
            assertThat(mechanics.velocity).isWithin(tolerance).of(compose.velocity)
        }

        // Afterwards, the displacement must be within displacementThreshold.
        for (i in maxFramesToExactlyCompare until max(byMechanics.size, byCompose.size)) {
            val mechanics = byMechanics.elementAtOrNull(i) ?: SpringState.AtRest
            val compose = byCompose.elementAtOrNull(i) ?: SpringState.AtRest
            assertThat(mechanics.displacement)
                .isWithin(DisplacementThreshold)
                .of(compose.displacement)
        }
    }

    private fun SpringParameters.asSpringSpec(): SpringSpec<Float> {
        return SpringSpec(dampingRatio, stiffness)
    }

    private fun runTestWithFrameClock(testBody: suspend () -> Unit) = runTest {
        val testScope: TestScope = this
        withContext(TestMonotonicFrameClock(testScope, FrameDelayNanos)) { testBody() }
    }

    companion object {
        private val FrameDelayNanos: Long = 16_000_000L
        private val DisplacementThreshold: Float = 0.01f
    }
}
