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

import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.spatialMotionSpec
import com.android.mechanics.testing.CaptureTimeSeriesFn
import com.android.mechanics.testing.ComposeMotionValueToolkit
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.FeatureCaptures
import com.android.mechanics.testing.VerifyTimeSeriesResult
import com.android.mechanics.testing.animateValueTo
import com.android.mechanics.testing.defaultFeatureCaptures
import com.android.mechanics.testing.goldenTest
import com.android.mechanics.testing.input
import com.android.mechanics.testing.nullableDataPoints
import com.android.mechanics.testing.output
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
class OverdragTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {
    private val goldenPathManager =
        createGoldenPathManager("frameworks/libs/systemui/mechanics/tests/goldens")

    @get:Rule val motion = MotionTestRule(ComposeMotionValueToolkit, goldenPathManager)

    @Test
    fun overdrag_maxDirection_neverExceedsMaxOverdrag() {
        motion.goldenTest(
            spatialMotionSpec { after(10f, Overdrag(maxOverdrag = 20.dp)) },
            capture = captureOverdragFeatures,
            verifyTimeSeries = {
                assertThat(output.filter { it > 30 }).isEmpty()
                VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden()
            },
        ) {
            animateValueTo(100f, changePerFrame = 5f)
        }
    }

    @Test
    fun overdrag_minDirection_neverExceedsMaxOverdrag() {
        motion.goldenTest(
            spatialMotionSpec { before(-10f, Overdrag(maxOverdrag = 20.dp)) },
            capture = captureOverdragFeatures,
            initialDirection = InputDirection.Min,
            verifyTimeSeries = {
                assertThat(output.filter { it < -30 }).isEmpty()

                VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden()
            },
        ) {
            animateValueTo(-100f, changePerFrame = 5f)
        }
    }

    @Test
    fun overdrag_nonStandardBaseFunction() {
        motion.goldenTest(
            spatialMotionSpec(baseMapping = { -it }) { after(10f, Overdrag(maxOverdrag = 20.dp)) },
            capture = captureOverdragFeatures,
            initialValue = 5f,
            verifyTimeSeries = {
                assertThat(output.filter { it < -30 }).isEmpty()
                VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden()
            },
        ) {
            animateValueTo(100f, changePerFrame = 5f)
        }
    }

    @Test
    fun semantics_exposesOverdragLimitWhileOverdragging() {
        motion.goldenTest(
            spatialMotionSpec {
                before(-10f, Overdrag())
                after(10f, Overdrag())
            },
            capture = captureOverdragFeatures,
            verifyTimeSeries = {
                val isOverdragging = input.map { abs(it) >= 10 }
                val hasOverdragLimit = nullableDataPoints<Float>("overdragLimit").map { it != null }
                assertThat(hasOverdragLimit).isEqualTo(isOverdragging)
                VerifyTimeSeriesResult.SkipGoldenVerification
            },
        ) {
            animateValueTo(20f, changePerFrame = 5f)
            reset(0f, InputDirection.Min)
            animateValueTo(-20f, changePerFrame = 5f)
        }
    }

    companion object {
        val captureOverdragFeatures: CaptureTimeSeriesFn = {
            defaultFeatureCaptures()
            feature(
                FeatureCaptures.semantics(
                    Overdrag.Defaults.OverdragLimit,
                    DataPointTypes.float,
                    "overdragLimit",
                )
            )
        }
    }
}
