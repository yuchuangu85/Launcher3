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
import com.android.mechanics.effects.MagneticDetach.Defaults.AttachPosition
import com.android.mechanics.effects.MagneticDetach.Defaults.DetachPosition
import com.android.mechanics.effects.MagneticDetach.State.Attached
import com.android.mechanics.effects.MagneticDetach.State.Detached
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.builder.EffectPlacemenType
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.spatialMotionSpec
import com.android.mechanics.testing.ComposeMotionValueToolkit
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.MotionSpecSubject.Companion.assertThat
import com.android.mechanics.testing.VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden
import com.android.mechanics.testing.animateValueTo
import com.android.mechanics.testing.goldenTest
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import platform.test.motion.MotionTestRule
import platform.test.motion.testing.createGoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext

@RunWith(AndroidJUnit4::class)
class MagneticDetachSpecTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    @Test
    fun magneticDetach_matchesSpec() {
        val underTests = spatialMotionSpec { after(10f, MagneticDetach()) }

        assertThat(underTests).maxDirection().breakpoints().positions().containsExactly(10f, 90f)
        assertThat(underTests)
            .minDirection()
            .breakpoints()
            .positions()
            .containsExactly(10f, 50f, 90f)
    }

    @Test
    fun attachDetachSemantics_placedAfter_isAppliedOutside() {
        val underTests = spatialMotionSpec { after(10f, MagneticDetach()) }

        assertThat(underTests)
            .maxDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachDetachState)
            .containsExactly(Attached, Attached, Detached)

        assertThat(underTests)
            .minDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachDetachState)
            .containsExactly(Attached, Attached, Detached, Detached)
    }

    @Test
    fun attachValueSemantics_placedAfter_isAppliedInside() {
        val underTests = spatialMotionSpec { after(10f, MagneticDetach()) }

        assertThat(underTests)
            .maxDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachedValue)
            .containsExactly(null, 10f, null)

        assertThat(underTests)
            .minDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachedValue)
            .containsExactly(null, 10f, null, null)
    }

    @Test
    fun attachDetachSemantics_placedBefore_isAppliedOutside() {
        val underTests = spatialMotionSpec { before(10f, MagneticDetach()) }

        assertThat(underTests)
            .maxDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachDetachState)
            .containsExactly(Detached, Detached, Attached, Attached)

        assertThat(underTests)
            .minDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachDetachState)
            .containsExactly(Detached, Attached, Attached)
    }

    @Test
    fun attachValueSemantics_placedBefore_isAppliedInside() {
        val underTests = spatialMotionSpec { before(10f, MagneticDetach()) }

        assertThat(underTests)
            .maxDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachedValue)
            .containsExactly(null, null, 10f, null)

        assertThat(underTests)
            .minDirection()
            .semantics()
            .withKey(MagneticDetach.Defaults.AttachedValue)
            .containsExactly(null, 10f, null)
    }
}

@RunWith(Parameterized::class)
class MagneticDetachGoldenTest(private val placement: EffectPlacemenType) :
    MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun placements() = listOf(EffectPlacemenType.After, EffectPlacemenType.Before)
    }

    private val goldenPathManager =
        createGoldenPathManager(
            "frameworks/libs/systemui/mechanics/tests/goldens",
            PathConfig(
                PathElementNoContext("effect", isDir = true) { "MagneticDetach" },
                PathElementNoContext("placement", isDir = false) { "placed${placement.name}" },
            ),
        )

    @get:Rule val motion = MotionTestRule(ComposeMotionValueToolkit, goldenPathManager)

    private val directionSign: Float
        get() =
            when (placement) {
                EffectPlacemenType.After -> 1f
                EffectPlacemenType.Before -> -1f
                else -> fail()
            }

    private fun createTestSpec() = spatialMotionSpec {
        if (placement == EffectPlacemenType.After) {
            after(10f, MagneticDetach())
        } else if (placement == EffectPlacemenType.Before) {
            before(-10f, MagneticDetach())
        }
    }

    @Test
    fun detach_animatesDetach() {
        motion.goldenTest(
            createTestSpec(),
            verifyTimeSeries = { AssertTimeSeriesMatchesGolden("detach_animatesDetach") },
        ) {
            animateValueTo((DetachPosition.toPx() + 10f) * directionSign, changePerFrame = 5f)
            awaitStable()
        }
    }

    @Test
    fun attach_snapsToOrigin() {
        motion.goldenTest(
            createTestSpec(),
            initialValue = (DetachPosition.toPx() + 20f) * directionSign,
            initialDirection = InputDirection.Min,
            verifyTimeSeries = { AssertTimeSeriesMatchesGolden("attach_snapsToOrigin") },
        ) {
            animateValueTo(0f, changePerFrame = 5f)
            awaitStable()
        }
    }

    @Test
    fun beforeAttach_suppressesDirectionReverse() {
        motion.goldenTest(
            createTestSpec(),
            initialValue = (DetachPosition.toPx() + 20f) * directionSign,
            initialDirection = InputDirection.Min,
            verifyTimeSeries = {
                AssertTimeSeriesMatchesGolden("beforeAttach_suppressesDirectionReverse")
            },
        ) {
            animateValueTo((AttachPosition.toPx() + 11f) * directionSign)
            animateValueTo((DetachPosition.toPx() + 20f) * directionSign)
            awaitStable()
        }
    }

    @Test
    fun afterAttach_detachesAgain() {
        motion.goldenTest(
            createTestSpec(),
            initialValue = (DetachPosition.toPx() + 20f) * directionSign,
            initialDirection = InputDirection.Min,
            verifyTimeSeries = { AssertTimeSeriesMatchesGolden("afterAttach_detachesAgain") },
        ) {
            animateValueTo((AttachPosition.toPx() / 2f + 10f) * directionSign, changePerFrame = 5f)
            awaitStable()
            animateValueTo((DetachPosition.toPx() + 20f) * directionSign, changePerFrame = 5f)
            awaitStable()
        }
    }

    @Test
    fun beforeDetach_suppressesDirectionReverse() {
        motion.goldenTest(
            createTestSpec(),
            verifyTimeSeries = {
                AssertTimeSeriesMatchesGolden("beforeDetach_suppressesDirectionReverse")
            },
        ) {
            animateValueTo((DetachPosition.toPx() - 9f) * directionSign)
            animateValueTo(0f)
            awaitStable()
        }
    }

    @Test
    fun placedWithDifferentBaseMapping() {
        motion.goldenTest(
            spatialMotionSpec(baseMapping = Mapping.Linear(factor = -10f)) {
                if (placement == EffectPlacemenType.After) {
                    after(-10f, MagneticDetach())
                } else if (placement == EffectPlacemenType.Before) {
                    before(10f, MagneticDetach())
                }
            },
            initialValue = (-10f) * directionSign,
            verifyTimeSeries = { AssertTimeSeriesMatchesGolden("placedWithDifferentBaseMapping") },
        ) {
            animateValueTo((DetachPosition.toPx() - 10f) * directionSign)
            awaitStable()
        }
    }
}
