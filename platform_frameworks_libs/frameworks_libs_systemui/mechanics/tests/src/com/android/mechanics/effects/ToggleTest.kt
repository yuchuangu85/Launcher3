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

package com.android.mechanics.effects

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.DistanceGestureContext
import com.android.mechanics.MotionValue
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.spatialMotionSpec
import com.android.mechanics.testing.ComposeMotionValueToolkit
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.FeatureCaptures
import com.android.mechanics.testing.InputScope
import com.android.mechanics.testing.MotionSpecSubject.Companion.assertThat
import com.android.mechanics.testing.animateValueTo
import com.android.mechanics.testing.goldenTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.golden.feature
import platform.test.motion.testing.createGoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext

@RunWith(AndroidJUnit4::class)
class ToggleSpecTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    val underTest = ExpansionToggle.Default

    @Test
    fun toggle_matchesSpec() {
        val spec = spatialMotionSpec {
            between(
                0f,
                100f,
                Toggle(
                    ExpansionToggle.IsExpandedKey,
                    minState = false,
                    maxState = true,
                    toggleFraction = .75f,
                ),
            )
        }

        assertThat(spec).maxDirection().breakpoints().positions().containsExactly(0f, 75f, 100f)
        assertThat(spec).minDirection().breakpoints().positions().containsExactly(0f, 25f, 100f)
    }

    @Test
    fun stateSemantics_isApplied() {
        val underTests = spatialMotionSpec { between(10f, 20f, underTest) }

        assertThat(underTests)
            .maxDirection()
            .semantics()
            .withKey(ExpansionToggle.IsExpandedKey)
            .containsExactly(false, false, true, true)
        assertThat(underTests)
            .minDirection()
            .semantics()
            .withKey(ExpansionToggle.IsExpandedKey)
            .containsExactly(false, false, true, true)
    }
}

class ToggleGoldenTest() : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    private val goldenPathManager =
        createGoldenPathManager(
            "frameworks/libs/systemui/mechanics/tests/goldens",
            PathConfig(PathElementNoContext("effect", isDir = true) { "Toggle" }),
        )

    @get:Rule val motion = MotionTestRule(ComposeMotionValueToolkit, goldenPathManager)

    val underTest = ExpansionToggle.Default

    @Test
    fun maxDirection_togglesAtThreshold() =
        goldenTest(spatialMotionSpec { between(10f, 20f, underTest) }, 8f, InputDirection.Max) {
            animateValueTo(17f, changePerFrame = 1f)
            awaitStable()
            animateValueTo(22f, changePerFrame = 1f)
        }

    @Test
    fun maxDirection_preventsDirectionChangeBeforeToggle() =
        goldenTest(spatialMotionSpec { between(10f, 20f, underTest) }, 8f, InputDirection.Max) {
            animateValueTo(15f, changePerFrame = 1f)
            awaitStable()
            animateValueTo(8f, changePerFrame = 1f)
        }

    @Test
    fun maxDirection_AfterToggle_preventsJumpOnDirectionChange() =
        goldenTest(spatialMotionSpec { between(10f, 20f, underTest) }, 8f, InputDirection.Max) {
            animateValueTo(18f, changePerFrame = 1f)
            awaitStable()
            animateValueTo(15f, changePerFrame = 1f)
            animateValueTo(22f, changePerFrame = 1f)
        }

    @Test
    fun minDirection_togglesAtThreshold() =
        goldenTest(spatialMotionSpec { between(10f, 20f, underTest) }, 22f, InputDirection.Min) {
            animateValueTo(13f, changePerFrame = 1f)
            awaitStable()
            animateValueTo(8f, changePerFrame = 1f)
        }

    @Test
    fun minDirection_preventsDirectionChangeBeforeToggle() =
        goldenTest(spatialMotionSpec { between(10f, 20f, underTest) }, 22f, InputDirection.Min) {
            animateValueTo(15f, changePerFrame = 1f)
            awaitStable()
            animateValueTo(22f, changePerFrame = 1f)
        }

    @Test
    fun minDirection_AfterToggle_preventsJumpOnDirectionChange() =
        goldenTest(spatialMotionSpec { between(10f, 20f, underTest) }, 22f, InputDirection.Min) {
            animateValueTo(12f, changePerFrame = 1f)
            awaitStable()
            animateValueTo(15f, changePerFrame = 1f)
            animateValueTo(8f, changePerFrame = 1f)
        }

    @Test
    fun output_groundedInBaseMapping() =
        goldenTest(
            spatialMotionSpec(baseMapping = Mapping.Linear(factor = -10f)) {
                between(10f, 20f, underTest)
            },
            8f,
            InputDirection.Max,
        ) {
            animateValueTo(22f, changePerFrame = 1f)
            awaitStable()
        }

    private fun goldenTest(
        spec: MotionSpec,
        initialValue: Float,
        initialDirection: InputDirection,
        testInput: suspend (InputScope<MotionValue, DistanceGestureContext>).() -> Unit,
    ) =
        motion.goldenTest(
            spec,
            initialValue,
            initialDirection,
            directionChangeSlop = 0.5f,
            stableThreshold = 0.1f,
            capture = {
                feature(FeatureCaptures.input)
                feature(FeatureCaptures.gestureDirection)
                feature(FeatureCaptures.output)
                feature(FeatureCaptures.outputTarget)
            },
            testInput = testInput,
        )
}
