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

package com.android.mechanics

import android.util.Log
import android.util.Log.TerribleFailureHandler
import androidx.compose.runtime.mutableFloatStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.Breakpoint
import com.android.mechanics.spec.BreakpointKey
import com.android.mechanics.spec.Guarantee.GestureDragDelta
import com.android.mechanics.spec.Guarantee.InputDelta
import com.android.mechanics.spec.Guarantee.None
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.SegmentKey
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spec.SemanticValue
import com.android.mechanics.spec.builder.CanBeLastSegment
import com.android.mechanics.spec.builder.DirectionalBuilderScope
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.directionalMotionSpec
import com.android.mechanics.spec.with
import com.android.mechanics.testing.ComposeMotionValueToolkit
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.FeatureCaptures
import com.android.mechanics.testing.VerifyTimeSeriesResult.AssertTimeSeriesMatchesGolden
import com.android.mechanics.testing.VerifyTimeSeriesResult.SkipGoldenVerification
import com.android.mechanics.testing.animateValueTo
import com.android.mechanics.testing.animatedInputSequence
import com.android.mechanics.testing.dataPoints
import com.android.mechanics.testing.defaultFeatureCaptures
import com.android.mechanics.testing.goldenTest
import com.android.mechanics.testing.input
import com.android.mechanics.testing.isStable
import com.android.mechanics.testing.output
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.compose.runMonotonicClockTest
import platform.test.motion.golden.DataPointTypes
import platform.test.motion.testing.createGoldenPathManager

@RunWith(AndroidJUnit4::class)
class MotionValueTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {
    private val goldenPathManager =
        createGoldenPathManager("frameworks/libs/systemui/mechanics/tests/goldens")

    @get:Rule(order = 1) val motion = MotionTestRule(ComposeMotionValueToolkit, goldenPathManager)
    @get:Rule(order = 2) val wtfLog = WtfLogRule()

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

    // TODO the tests should describe the expected values not only in terms of goldens, but
    //  also explicitly in verifyTimeSeries

    @Test
    fun changingInput_addsAnimationToMapping_becomesStable() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    mapping(breakpoint = 1f, mapping = Mapping.Linear(factor = 0.5f))
                }
        ) {
            animateValueTo(1.1f, changePerFrame = 0.5f)
            while (underTest.isStable) {
                updateInput(input + 0.5f)
                awaitFrames()
            }
        }

    @Test
    fun segmentChange_inMaxDirection_animatedWhenReachingBreakpoint() =
        motion.goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }
        ) {
            animateValueTo(1f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun segmentChange_inMaxDirection_zeroDelta() =
        motion.goldenTest(spec = specBuilder(Mapping.Zero) { fixedValueFromCurrent(0.5f) }) {
            animateValueTo(1f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun segmentChange_inMinDirection_animatedWhenReachingBreakpoint() =
        motion.goldenTest(
            initialValue = 2f,
            initialDirection = InputDirection.Min,
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) },
        ) {
            animateValueTo(1f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun segmentChange_inMaxDirection_springAnimationStartedRetroactively() =
        motion.goldenTest(
            spec = specBuilder(Mapping.Zero) { mapping(breakpoint = .75f, mapping = Mapping.One) }
        ) {
            animateValueTo(1f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun segmentChange_inMinDirection_springAnimationStartedRetroactively() =
        motion.goldenTest(
            initialValue = 2f,
            initialDirection = InputDirection.Min,
            spec = specBuilder(Mapping.Zero) { mapping(breakpoint = 1.25f, mapping = Mapping.One) },
        ) {
            animateValueTo(1f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun segmentChange_guaranteeNone_springAnimatesIndependentOfInput() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, guarantee = None, value = 1f)
                }
        ) {
            animateValueTo(5f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun segmentChange_guaranteeInputDelta_springCompletesWithinDistance() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, guarantee = InputDelta(3f), value = 1f)
                }
        ) {
            animateValueTo(4f, changePerFrame = 0.5f)
        }

    @Test
    fun segmentChange_guaranteeGestureDragDelta_springCompletesWithinDistance() =
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
    fun segmentChange_appliesOutputVelocity_atSpringStart() =
        motion.goldenTest(spec = specBuilder { fixedValue(breakpoint = 10f, value = 20f) }) {
            animateValueTo(11f, changePerFrame = 3f)
            awaitStable()
        }

    @Test
    fun segmentChange_appliesOutputVelocity_springVelocityIsNotAppliedTwice() =
        motion.goldenTest(
            spec =
                specBuilder {
                    fractionalInputFromCurrent(breakpoint = 10f, fraction = 1f, delta = 20f)
                    fixedValueFromCurrent(breakpoint = 20f)
                }
        ) {
            animateValueTo(21f, changePerFrame = 3f)
            awaitStable()
        }

    @Test
    fun segmentChange_appliesOutputVelocity_velocityNotAddedOnContinuousSegment() =
        motion.goldenTest(
            spec =
                specBuilder {
                    fractionalInputFromCurrent(breakpoint = 10f, fraction = 5f, delta = 5f)
                    fixedValueFromCurrent(breakpoint = 20f)
                }
        ) {
            animateValueTo(30f, changePerFrame = 3f)
            awaitStable()
        }

    @Test
    fun segmentChange_appliesOutputVelocity_velocityAddedOnDiscontinuousSegment() =
        motion.goldenTest(
            spec =
                specBuilder {
                    fractionalInputFromCurrent(breakpoint = 10f, fraction = 5f, delta = 5f)
                    fixedValueFromCurrent(breakpoint = 20f, delta = -5f)
                }
        ) {
            animateValueTo(30f, changePerFrame = 3f)
            awaitStable()
        }

    @Test
    // Regression test for b/409726626
    fun segmentChange_animationAtRest_doesNotAffectVelocity() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, value = 20f)
                    fixedValue(breakpoint = 2f, value = 20f)
                    fixedValue(breakpoint = 3f, value = 10f)
                },
            stableThreshold = 1f,
        ) {
            this.updateInput(1.5f)
            awaitStable()
            animateValueTo(3f)
            awaitStable()
        }

    @Test
    fun specChange_shiftSegmentBackwards_doesNotAnimateWithinSegment_animatesSegmentChange() {
        fun generateSpec(offset: Float) =
            specBuilder(Mapping.Zero) {
                targetFromCurrent(breakpoint = offset, key = B1, delta = 1f, to = 2f)
                fixedValue(breakpoint = offset + 1f, key = B2, value = 0f)
            }

        motion.goldenTest(spec = generateSpec(0f), initialValue = .5f) {
            var offset = 0f
            repeat(4) {
                offset -= .2f
                underTest.spec = generateSpec(offset)
                awaitFrames()
            }
            awaitStable()
        }
    }

    @Test
    fun specChange_shiftSegmentForward_doesNotAnimateWithinSegment_animatesSegmentChange() {
        fun generateSpec(offset: Float) =
            specBuilder(Mapping.Zero) {
                targetFromCurrent(breakpoint = offset, key = B1, delta = 1f, to = 2f)
                fixedValue(breakpoint = offset + 1f, key = B2, value = 0f)
            }

        motion.goldenTest(spec = generateSpec(0f), initialValue = .5f) {
            var offset = 0f
            repeat(4) {
                offset += .2f
                underTest.spec = generateSpec(offset)
                awaitFrames()
            }
            awaitStable()
        }
    }

    @Test
    fun directionChange_maxToMin_changesSegmentWithDirectionChange() =
        motion.goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) },
            initialValue = 2f,
            initialDirection = InputDirection.Max,
            directionChangeSlop = 3f,
        ) {
            animateValueTo(-2f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun directionChange_minToMax_changesSegmentWithDirectionChange() =
        motion.goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) },
            initialValue = 0f,
            initialDirection = InputDirection.Min,
            directionChangeSlop = 3f,
        ) {
            animateValueTo(4f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun directionChange_maxToMin_appliesGuarantee_afterDirectionChange() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, value = 1f, guarantee = InputDelta(1f))
                },
            initialValue = 2f,
            initialDirection = InputDirection.Max,
            directionChangeSlop = 3f,
        ) {
            animateValueTo(-2f, changePerFrame = 0.5f)
            awaitStable()
        }

    @Test
    fun traverseSegments_maxDirection_noGuarantee_addsDiscontinuityToOngoingAnimation() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, value = 1f)
                    fixedValue(breakpoint = 2f, value = 2f)
                }
        ) {
            animateValueTo(3f, changePerFrame = 0.2f)
            awaitStable()
        }

    @Test
    fun traverseSegmentsInOneFrame_noGuarantee_combinesDiscontinuity() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, value = 1f)
                    fixedValue(breakpoint = 2f, value = 2f)
                }
        ) {
            updateInput(2.5f)
            awaitStable()
        }

    @Test
    fun traverseSegmentsInOneFrame_withGuarantee_appliesGuarantees() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValueFromCurrent(breakpoint = 1f, delta = 5f, guarantee = InputDelta(.9f))
                    fixedValueFromCurrent(breakpoint = 2f, delta = 1f, guarantee = InputDelta(.9f))
                }
        ) {
            updateInput(2.1f)
            awaitStable()
        }

    @Test
    fun traverseSegmentsInOneFrame_withDirectionChange_appliesGuarantees() =
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    fixedValue(breakpoint = 1f, value = 1f, guarantee = InputDelta(1f))
                    fixedValue(breakpoint = 2f, value = 2f)
                },
            initialValue = 2.5f,
            initialDirection = InputDirection.Max,
            directionChangeSlop = 1f,
        ) {
            updateInput(.5f)
            animateValueTo(0f)
            awaitStable()
        }

    @Test
    fun changeDirection_flipsBetweenDirectionalSegments() {
        val spec =
            MotionSpec(
                maxDirection = directionalMotionSpec(Mapping.Zero),
                minDirection = directionalMotionSpec(Mapping.One),
            )

        motion.goldenTest(
            spec = spec,
            initialValue = 2f,
            initialDirection = InputDirection.Max,
            directionChangeSlop = 1f,
        ) {
            animateValueTo(0f)
            awaitStable()
        }
    }

    @Test
    fun semantics_flipsBetweenDirectionalSegments() {
        val s1 = SemanticKey<String>("Foo")
        val spec =
            specBuilder(Mapping.Zero, semantics = listOf(s1 with "zero")) {
                fixedValue(1f, 1f, semantics = listOf(s1 with "one"))
                fixedValue(2f, 2f, semantics = listOf(s1 with "two"))
            }

        motion.goldenTest(
            spec = spec,
            capture = {
                defaultFeatureCaptures()
                feature(FeatureCaptures.semantics(s1, DataPointTypes.string))
            },
        ) {
            animateValueTo(3f, changePerFrame = .2f)
            awaitStable()
        }
    }

    @Test
    fun semantics_returnsNullForUnknownKey() {
        val underTest = MotionValue({ 1f }, FakeGestureContext)

        val s1 = SemanticKey<String>("Foo")

        assertThat(underTest[s1]).isNull()
    }

    @Test
    fun semantics_returnsValueMatchingSegment() {
        val s1 = SemanticKey<String>("Foo")
        val spec =
            specBuilder(Mapping.Zero, semantics = listOf(s1 with "zero")) {
                fixedValue(1f, 1f, semantics = listOf(s1 with "one"))
                fixedValue(2f, 2f, semantics = listOf(s1 with "two"))
            }

        val input = mutableFloatStateOf(0f)
        val underTest = MotionValue(input::value, FakeGestureContext, spec)

        assertThat(underTest[s1]).isEqualTo("zero")
        input.floatValue = 2f
        assertThat(underTest[s1]).isEqualTo("two")
    }

    @Test
    fun segment_returnsCurrentSegmentKey() {
        val spec =
            specBuilder(Mapping.Zero) {
                fixedValue(1f, 1f, key = B1)
                fixedValue(2f, 2f, key = B2)
            }

        val input = mutableFloatStateOf(1f)
        val underTest = MotionValue(input::value, FakeGestureContext, spec)

        assertThat(underTest.segmentKey).isEqualTo(SegmentKey(B1, B2, InputDirection.Max))
        input.floatValue = 2f
        assertThat(underTest.segmentKey)
            .isEqualTo(SegmentKey(B2, Breakpoint.maxLimit.key, InputDirection.Max))
    }

    @Test
    fun derivedValue_reflectsInputChangeInSameFrame() {
        motion.goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 0.5f, value = 1f) },
            createDerived = { primary ->
                listOf(MotionValue.createDerived(primary, MotionSpec.Empty, label = "derived"))
            },
            verifyTimeSeries = {
                // the output of the derived value must match the primary value
                assertThat(output)
                    .containsExactlyElementsIn(dataPoints<Float>("derived-output"))
                    .inOrder()
                // and its never animated.
                assertThat(dataPoints<Boolean>("derived-isStable")).doesNotContain(false)

                AssertTimeSeriesMatchesGolden()
            },
        ) {
            animateValueTo(1f, changePerFrame = 0.1f)
            awaitStable()
        }
    }

    @Test
    fun derivedValue_hasAnimationLifecycleOnItsOwn() {
        motion.goldenTest(
            spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 0.5f, value = 1f) },
            createDerived = { primary ->
                listOf(
                    MotionValue.createDerived(
                        primary,
                        specBuilder(Mapping.One) { fixedValue(breakpoint = 0.5f, value = 0f) },
                        label = "derived",
                    )
                )
            },
        ) {
            animateValueTo(1f, changePerFrame = 0.1f)
            awaitStable()
        }
    }

    @Test
    fun nonFiniteNumbers_producesNaN_recoversOnSubsequentFrames() {
        motion.goldenTest(
            spec = MotionSpec(directionalMotionSpec({ if (it >= 1f) Float.NaN else 0f })),
            verifyTimeSeries = {
                assertThat(output.drop(1).take(5))
                    .containsExactlyElementsIn(listOf(0f, Float.NaN, Float.NaN, 0f, 0f))
                    .inOrder()
                SkipGoldenVerification
            },
        ) {
            animatedInputSequence(0f, 1f, 1f, 0f, 0f)
        }

        assertThat(wtfLog.hasLoggedFailures()).isFalse()
    }

    @Test
    fun nonFiniteNumbers_segmentChange_skipsAnimation() {
        motion.goldenTest(
            spec = MotionSpec.Empty,
            verifyTimeSeries = {
                // The mappings produce a non-finite number during a segment change.
                // The animation thereof is skipped to avoid poisoning the state with non-finite
                // numbers
                assertThat(output.drop(1).take(5))
                    .containsExactlyElementsIn(listOf(0f, 1f, Float.NaN, 0f, 0f))
                    .inOrder()
                SkipGoldenVerification
            },
        ) {
            animatedInputSequence(0f, 1f)
            underTest.spec = specBuilder {
                mapping(breakpoint = 0f) { if (it >= 1f) Float.NaN else 0f }
            }

            awaitFrames()

            animatedInputSequence(0f, 0f)
        }

        val loggedFailures = wtfLog.removeLoggedFailures()
        assertThat(loggedFailures).hasSize(1)
        assertThat(loggedFailures.first()).startsWith("Delta between mappings is undefined")
    }

    @Test
    fun nonFiniteNumbers_segmentTraverse_skipsAnimation() {
        motion.goldenTest(
            spec =
                specBuilder(Mapping.Zero) {
                    mapping(breakpoint = 1f) { if (it < 2f) Float.NaN else 2f }
                },
            verifyTimeSeries = {
                // The mappings produce a non-finite number during a breakpoint traversal.
                // The animation thereof is skipped to avoid poisoning the state with non-finite
                // numbers
                assertThat(output.drop(1).take(6))
                    .containsExactlyElementsIn(listOf(0f, 0f, Float.NaN, Float.NaN, 2f, 2f))
                    .inOrder()
                SkipGoldenVerification
            },
        ) {
            animatedInputSequence(0f, 0.5f, 1f, 1.5f, 2f, 3f)
        }
        val loggedFailures = wtfLog.removeLoggedFailures()
        assertThat(loggedFailures).hasSize(1)
        assertThat(loggedFailures.first()).startsWith("Delta between breakpoints is undefined")
    }

    @Test
    fun keepRunning_concurrentInvocationThrows() = runMonotonicClockTest {
        val underTest = MotionValue({ 1f }, FakeGestureContext, label = "Foo")
        val realJob = launch { underTest.keepRunning() }
        testScheduler.runCurrent()

        assertThat(realJob.isActive).isTrue()
        try {
            underTest.keepRunning()
            // keepRunning returns Nothing, will never get here
        } catch (e: Throwable) {
            assertThat(e).isInstanceOf(IllegalStateException::class.java)
            assertThat(e).hasMessageThat().contains("MotionValue(Foo) is already running")
        }
        assertThat(realJob.isActive).isTrue()
        realJob.cancel()
    }

    @Test
    fun debugInspector_sameInstance_whileInUse() {
        val underTest = MotionValue({ 1f }, FakeGestureContext)

        val originalInspector = underTest.debugInspector()
        assertThat(underTest.debugInspector()).isSameInstanceAs(originalInspector)
    }

    @Test
    fun debugInspector_newInstance_afterUnused() {
        val underTest = MotionValue({ 1f }, FakeGestureContext)

        val originalInspector = underTest.debugInspector()
        originalInspector.dispose()
        assertThat(underTest.debugInspector()).isNotSameInstanceAs(originalInspector)
    }

    class WtfLogRule : ExternalResource() {
        private val loggedFailures = mutableListOf<String>()

        private lateinit var oldHandler: TerribleFailureHandler

        override fun before() {
            oldHandler =
                Log.setWtfHandler { tag, what, _ ->
                    if (tag == MotionValue.TAG) {
                        loggedFailures.add(checkNotNull(what.message))
                    }
                }
        }

        override fun after() {
            Log.setWtfHandler(oldHandler)

            // In eng-builds, some misconfiguration in a MotionValue would cause a crash. However,
            // in tests (and in production), we want animations to proceed even with such errors.
            // When a test ends, we should check loggedFailures, if they were expected.
            assertThat(loggedFailures).isEmpty()
        }

        fun hasLoggedFailures() = loggedFailures.isNotEmpty()

        fun removeLoggedFailures(): List<String> {
            if (loggedFailures.isEmpty()) error("loggedFailures is empty")
            val list = loggedFailures.toList()
            loggedFailures.clear()
            return list
        }
    }

    companion object {
        val B1 = BreakpointKey("breakpoint1")
        val B2 = BreakpointKey("breakpoint2")
        val FakeGestureContext =
            object : GestureContext {
                override val direction: InputDirection
                    get() = InputDirection.Max

                override val dragOffset: Float
                    get() = 0f
            }

        private val Springs = FakeMotionSpecBuilderContext.Default.spatial

        fun specBuilder(
            initialMapping: Mapping = Mapping.Identity,
            semantics: List<SemanticValue<*>> = emptyList(),
            init: DirectionalBuilderScope.() -> CanBeLastSegment,
        ): MotionSpec {
            return MotionSpec(
                directionalMotionSpec(Springs.default, initialMapping, semantics, init),
                resetSpring = Springs.fast,
            )
        }
    }
}
