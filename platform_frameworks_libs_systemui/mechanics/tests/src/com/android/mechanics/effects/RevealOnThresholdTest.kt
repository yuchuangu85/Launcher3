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
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.spatialMotionSpec
import com.android.mechanics.testing.ComposeMotionValueToolkit
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.MotionSpecSubject.Companion.assertThat
import com.android.mechanics.testing.animateValueTo
import com.android.mechanics.testing.goldenTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
class RevealOnThresholdTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    private val goldenPathManager =
        createGoldenPathManager("frameworks/libs/systemui/mechanics/tests/goldens")

    @get:Rule val motion = MotionTestRule(ComposeMotionValueToolkit, goldenPathManager)

    @Test
    fun matchesSpec() {
        val underTests = spatialMotionSpec(Mapping.Zero) { between(3f, 30f, RevealOnThreshold()) }

        val minSize = RevealOnThreshold.Defaults.MinSize.toPx()

        assertThat(3f + minSize).isLessThan(30f)

        assertThat(underTests)
            .maxDirection()
            .breakpoints()
            .positions()
            .containsExactly(3f, 3f + minSize, 30f)

        assertThat(underTests)
            .minDirection()
            .breakpoints()
            .positions()
            .containsExactly(3f, 3f + minSize, 30f)
    }

    @Test
    fun revealAnimation() {
        motion.goldenTest(
            spatialMotionSpec(Mapping.Zero) { between(3f, 30f, RevealOnThreshold()) }
        ) {
            animateValueTo(36f, changePerFrame = 3f)
            awaitStable()
        }
    }

    @Test
    fun revealAnimation_afterFixedValue() {
        motion.goldenTest(
            spatialMotionSpec(Mapping.Zero) { between(3f, 30f, RevealOnThreshold()) }
        ) {
            animateValueTo(36f, changePerFrame = 3f)
            awaitStable()
        }
    }

    @Test
    fun hideAnimation() {
        motion.goldenTest(
            spatialMotionSpec(Mapping.Zero) { between(3f, 30f, RevealOnThreshold()) },
            initialValue = 36f,
            initialDirection = InputDirection.Min,
        ) {
            animateValueTo(0f, changePerFrame = 3f)
            awaitStable()
        }
    }

    @Test
    fun doNothingBeforeThreshold() {
        motion.goldenTest(
            spatialMotionSpec(Mapping.Zero) { between(3f, 30f, RevealOnThreshold()) }
        ) {
            animateValueTo(2f + RevealOnThreshold.Defaults.MinSize.toPx(), changePerFrame = 3f)
            awaitStable()
        }
    }

    @Test
    fun hideAnimationOnThreshold() {
        motion.goldenTest(
            spatialMotionSpec(Mapping.Zero) { between(3f, 30f, RevealOnThreshold()) },
            initialValue = 36f,
            initialDirection = InputDirection.Min,
        ) {
            animateValueTo(3f + RevealOnThreshold.Defaults.MinSize.toPx(), changePerFrame = 3f)
            awaitStable()
        }
    }
}
