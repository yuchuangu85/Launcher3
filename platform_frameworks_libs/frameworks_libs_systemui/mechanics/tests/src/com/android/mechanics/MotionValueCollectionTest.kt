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

package com.android.mechanics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.MotionValueTest.Companion.specBuilder
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.testing.CaptureTimeSeriesFn
import com.android.mechanics.testing.CollectionInputScope
import com.android.mechanics.testing.ComposeMotionValueCollectionToolkit
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.FeatureCaptures
import com.android.mechanics.testing.VerifyTimeSeriesFn
import com.android.mechanics.testing.VerifyTimeSeriesResult
import com.android.mechanics.testing.animateValueTo
import com.android.mechanics.testing.goldenTest
import com.android.mechanics.testing.nullableDataPoints
import com.android.mechanics.testing.whenActive
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.golden.feature
import platform.test.motion.testing.createGoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext

@RunWith(AndroidJUnit4::class)
class MotionValueCollectionTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {
    private val goldenPathManager =
        createGoldenPathManager(
            "frameworks/libs/systemui/mechanics/tests/goldens",
            PathConfig(PathElementNoContext("base", isDir = true, { "collection" })),
        )

    @get:Rule(order = 1)
    val motion = MotionTestRule(ComposeMotionValueCollectionToolkit, goldenPathManager)

    @Test
    fun oneAnimatingValue_collectionIsAnimating() =
        goldenTest(spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }) {
            animateValueTo(2f)
            awaitStable()
        }

    @Test
    fun twoAnimatingValues_oneStops_collectionKeepsAnimating() =
        goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) },
            createDerived = {
                val secondSpec =
                    specBuilder(Mapping.One) { fixedValue(breakpoint = 2f, value = 2f) }
                listOf(it.create({ secondSpec }, "second"))
            },
        ) {
            animateValueTo(3f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun animatingValueIsDisposed_collectionStopsAnimating() =
        goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) },
            verifyTimeSeries = {
                val output = nullableDataPoints<Float>("primary-output")
                assertThat(output.last()).isNull()
                assertThat(output.dropLast(1)).doesNotContain(null)

                VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden()
            },
        ) {
            animateValueTo(1.5f)
            awaitFrames(2)
            motionValues.first().dispose()
            awaitStable()
        }

    @Test
    fun wakeUp_onInputChange() =
        goldenTest(spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }) {
            awaitStable()
            updateInput(2f)
            awaitStable()
        }

    @Test
    fun wakeUp_onSpecChange() =
        goldenTest(spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }) {
            awaitStable()
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = -1f, value = 1f) }
            awaitStable()
        }

    private fun goldenTest(
        spec: MotionSpec,
        initialValue: Float = 0f,
        initialDirection: InputDirection = InputDirection.Max,
        directionChangeSlop: Float = 5f,
        stableThreshold: Float = 0.1f,
        verifyTimeSeries: VerifyTimeSeriesFn = {
            VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden()
        },
        createDerived: (underTest: MotionValueCollection) -> List<ManagedMotionValue> = {
            emptyList()
        },
        capture: CaptureTimeSeriesFn = defaultManagedFeatureCaptures,
        testInput: suspend CollectionInputScope.() -> Unit,
    ) =
        motion.goldenTest(
            spec,
            initialValue,
            initialDirection,
            directionChangeSlop,
            stableThreshold,
            verifyTimeSeries,
            createDerived,
            capture,
            testInput,
        )

    companion object {
        /** Default feature captures. */
        val defaultManagedFeatureCaptures: CaptureTimeSeriesFn = {
            feature(FeatureCaptures.output.whenActive())
            feature(FeatureCaptures.outputTarget.whenActive())
            feature(FeatureCaptures.isStable.whenActive())
            feature(FeatureCaptures.isAnimating)
        }
    }
}
