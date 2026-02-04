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

package com.android.mechanics.view

import android.platform.test.annotations.MotionTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.MotionValueTest.Companion.B1
import com.android.mechanics.MotionValueTest.Companion.B2
import com.android.mechanics.MotionValueTest.Companion.specBuilder
import com.android.mechanics.spec.Breakpoint
import com.android.mechanics.spec.Guarantee.GestureDragDelta
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.SegmentKey
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spec.with
import com.android.mechanics.testing.VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden
import com.android.mechanics.testing.ViewMotionValueToolkit
import com.android.mechanics.testing.animateValueTo
import com.android.mechanics.testing.goldenTest
import com.android.mechanics.testing.input
import com.android.mechanics.testing.isStable
import com.android.mechanics.testing.output
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.testing.createGoldenPathManager
import platform.test.screenshot.PathConfig
import platform.test.screenshot.PathElementNoContext

/**
 * NOTE: This only tests the lifecycle of ViewMotionValue, plus some basic animations.
 *
 * Most code is shared with MotionValue, and tested there.
 */
@RunWith(AndroidJUnit4::class)
@MotionTest
class ViewMotionValueTest {
    private val goldenPathManager =
        createGoldenPathManager(
            "frameworks/libs/systemui/mechanics/tests/goldens",
            // The ViewMotionValue goldens do not currently match MotionValue goldens, because
            // the ViewMotionValue computes the output at the beginning of the new frame, while
            // MotionValue computes it at when read. Therefore, the output of these goldens is
            // delayed by one frame.
            PathConfig(PathElementNoContext("base", isDir = true, { "view" })),
        )

    //    @get:Rule(order = 1) val activityRule =
    // ActivityScenarioRule(EmptyTestActivity::class.java)
    @get:Rule(order = 2) val animatorTestRule = android.animation.AnimatorTestRule(this)

    @get:Rule(order = 3)
    val motion = MotionTestRule(ViewMotionValueToolkit(animatorTestRule), goldenPathManager)

    @Test
    fun emptySpec_outputMatchesInput_withoutAnimation() =
        motion.goldenTest(
            spec = MotionSpec.Empty,
            verifyTimeSeries = {
                // Output always matches the input
                assertThat(output).containsExactlyElementsIn(input).inOrder()
                // There must never be an ongoing animation.
                assertThat(isStable).doesNotContain(false)

                AssertTimeSeriesMatchesGolden()
            },
        ) {
            animateValueTo(100f)
        }

    @Test
    fun segmentChange_animatedWhenReachingBreakpoint() =
        motion.goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }
        ) {
            animateValueTo(1f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun semantics_returnsValueMatchingSegment() = runTest {
        runBlocking(Dispatchers.Main) {
            val s1 = SemanticKey<String>("Foo")
            val spec =
                specBuilder(Mapping.Zero, semantics = listOf(s1 with "zero")) {
                    fixedValue(1f, 1f, semantics = listOf(s1 with "one"))
                    fixedValue(2f, 2f, semantics = listOf(s1 with "two"))
                }

            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val underTest = ViewMotionValue(0f, gestureContext, spec)

            assertThat(underTest[s1]).isEqualTo("zero")
            underTest.input = 2f
            animatorTestRule.advanceTimeBy(16L)
            assertThat(underTest[s1]).isEqualTo("two")
        }
    }

    @Test
    fun segment_returnsCurrentSegmentKey() = runTest {
        runBlocking(Dispatchers.Main) {
            val spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(1f, 1f, key = B1)
                    fixedValue(2f, 2f, key = B2)
                }

            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val underTest = ViewMotionValue(1f, gestureContext, spec)

            assertThat(underTest.segmentKey).isEqualTo(SegmentKey(B1, B2, InputDirection.Max))
            underTest.input = 2f
            animatorTestRule.advanceTimeBy(16L)
            assertThat(underTest.segmentKey)
                .isEqualTo(SegmentKey(B2, Breakpoint.maxLimit.key, InputDirection.Max))
        }
    }

    @Test
    fun gestureContext_listensToGestureContextUpdates() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, guarantee = GestureDragDelta(3f), value = 1f)
                }
        ) {
            animateValueTo(1f, changePerFrame = 0.5f)
            while (!underTest.isStable) {
                gestureContext.dragOffset += 0.5f
                awaitFrames()
            }
        }

    @Test
    fun specChange_triggersAnimation() {
        fun generateSpec(offset: Float) =
            specBuilder(Mapping.Zero) {
                targetFromCurrent(breakpoint = offset, key = B1, delta = 1f, to = 2f)
                fixedValue(breakpoint = offset + 1f, key = B2, value = 0f)
            }

        motion.goldenTest(spec = generateSpec(0f), initialValue = .5f) {
            underTest.spec = generateSpec(1f)
            awaitFrames()
            awaitStable()
        }
    }

    @Test
    fun update_triggersCallback() = runTest {
        runBlocking(Dispatchers.Main) {
            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val underTest = ViewMotionValue(0f, gestureContext, MotionSpec.Empty)

            var invocationCount = 0
            underTest.addUpdateCallback { invocationCount++ }
            underTest.input = 1f
            repeat(60) { animatorTestRule.advanceTimeBy(16L) }

            assertThat(invocationCount).isEqualTo(2)
        }
    }

    @Test
    fun update_setSameValue_doesNotTriggerCallback() = runTest {
        runBlocking(Dispatchers.Main) {
            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val underTest = ViewMotionValue(0f, gestureContext, MotionSpec.Empty)

            var invocationCount = 0
            underTest.addUpdateCallback { invocationCount++ }
            underTest.input = 0f
            repeat(60) { animatorTestRule.advanceTimeBy(16L) }

            assertThat(invocationCount).isEqualTo(0)
        }
    }

    @Test
    fun update_triggersCallbacksWhileAnimating() = runTest {
        runBlocking(Dispatchers.Main) {
            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }
            val underTest = ViewMotionValue(0f, gestureContext, spec)

            var invocationCount = 0
            underTest.addUpdateCallback { invocationCount++ }
            underTest.input = 1f
            repeat(60) { animatorTestRule.advanceTimeBy(16L) }

            assertThat(invocationCount).isEqualTo(17)
        }
    }

    @Test
    fun removeCallback_doesNotTriggerAfterRemoving() = runTest {
        runBlocking(Dispatchers.Main) {
            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }
            val underTest = ViewMotionValue(0f, gestureContext, spec)

            var invocationCount = 0
            val callback = ViewMotionValueListener { invocationCount++ }
            underTest.addUpdateCallback(callback)
            underTest.input = 0.5f
            animatorTestRule.advanceTimeBy(16L)
            assertThat(invocationCount).isEqualTo(2)

            underTest.removeUpdateCallback(callback)
            underTest.input = 1f
            repeat(60) { animatorTestRule.advanceTimeBy(16L) }

            assertThat(invocationCount).isEqualTo(2)
        }
    }

    @Test
    fun debugInspector_sameInstance_whileInUse() = runTest {
        runBlocking(Dispatchers.Main) {
            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val underTest = ViewMotionValue(0f, gestureContext, MotionSpec.Empty)

            val originalInspector = underTest.debugInspector()
            assertThat(underTest.debugInspector()).isSameInstanceAs(originalInspector)
        }
    }

    @Test
    fun debugInspector_newInstance_afterUnused() = runTest {
        runBlocking(Dispatchers.Main) {
            val gestureContext = DistanceGestureContext(0f, InputDirection.Max, 5f)
            val underTest = ViewMotionValue(0f, gestureContext, MotionSpec.Empty)

            val originalInspector = underTest.debugInspector()
            originalInspector.dispose()
            assertThat(underTest.debugInspector()).isNotSameInstanceAs(originalInspector)
        }
    }
}
