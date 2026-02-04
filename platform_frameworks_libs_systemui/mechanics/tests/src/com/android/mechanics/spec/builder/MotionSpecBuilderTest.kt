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

package com.android.mechanics.spec.builder

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.effects.FixedValue
import com.android.mechanics.spec.BreakpointKey
import com.android.mechanics.spec.Guarantee
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spec.SemanticValue
import com.android.mechanics.spec.with
import com.android.mechanics.spring.SpringParameters
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.MotionSpecSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionSpecBuilderTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    // placement & ordering
    // placement types
    // placement issues
    // before & after mapping, springs etc

    @Test
    fun motionSpec_empty_usesBaseMapping() {
        val result = spatialMotionSpec {}

        assertThat(result).bothDirections().mappingsMatch(Mapping.Identity)
        assertThat(result).bothDirections().breakpoints().isEmpty()
    }

    @Test
    fun placement_absoluteAfter_createsTwoSegments() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                after(42f, FixedValue(1f))
            }

        assertThat(result).bothDirections().mappingsMatch(Mapping.Zero, Mapping.One)
        assertThat(result).bothDirections().breakpointsPositionsMatch(42f)
    }

    @Test
    fun placement_absoluteBefore_createsTwoSegments() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                before(42f, FixedValue(1f))
            }

        assertThat(result).bothDirections().mappingsMatch(Mapping.One, Mapping.Zero)
        assertThat(result).bothDirections().breakpointsPositionsMatch(42f)
    }

    @Test
    fun placement_absoluteBetween_createsThreeSegments() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(42f, 43f, FixedValue(1f))
            }

        assertThat(result).bothDirections().mappingsMatch(Mapping.Zero, Mapping.One, Mapping.Zero)
        assertThat(result).bothDirections().breakpointsPositionsMatch(42f, 43f)
    }

    @Test
    fun placement_absoluteBetweenReverse_createsThreeSegments() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(43f, 42f, FixedValue(1f))
            }

        assertThat(result).bothDirections().mappingsMatch(Mapping.Zero, Mapping.One, Mapping.Zero)
        assertThat(result).bothDirections().breakpointsPositionsMatch(42f, 43f)
    }

    @Test
    fun placement_adjacent_sharesBreakpoint() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, FixedValue(1f))
                between(2f, 3f, FixedValue(2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 2f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 2f, 3f)
    }

    @Test
    fun placement_multiple_baseMappingInBetween() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, FixedValue(1f))
                // Implicit baseMapping between 2 & 3
                between(3f, 4f, FixedValue(2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 0f, 2f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 2f, 3f, 4f)
    }

    @Test
    fun placement_overlapping_throws() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                    between(1f, 2f, FixedValue(1f))
                    between(1.5f, 2.5f, FixedValue(2f))
                }
            }
        assertThat(exception).hasMessageThat().contains("overlap")
    }

    @Test
    fun placement_embedded_throws() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                    between(1f, 3f, FixedValue(1f))
                    between(1.5f, 2.5f, FixedValue(2f))
                }
            }
        assertThat(exception).hasMessageThat().contains("overlap")
    }

    @Test
    fun placement_subsequent_extendsToNext() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                after(1f, FixedValue(1f))
                between(3f, 4f, FixedValue(2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 2f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 3f, 4f)
    }

    @Test
    fun placement_subsequent_extendsToPrevious() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, FixedValue(1f))
                before(4f, FixedValue(2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 2f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 2f, 4f)
    }

    @Test
    fun placement_subsequent_bothExtend_throws() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                    after(1f, FixedValue(1f))
                    before(3f, FixedValue(2f))
                }
            }
        assertThat(exception).hasMessageThat().contains("extend")
    }

    @Test
    fun placement_withFixedExtent_after_limitsEffect() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                after(1f, FixedValueWithExtent(1f, 2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 3f)
    }

    @Test
    fun placement_withFixedExtent_before_limitsEffect() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                before(1f, FixedValueWithExtent(1f, 2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(-1f, 1f)
    }

    @Test
    fun placement_relative_afterEffect() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                val effect1 = between(1f, 2f, FixedValue(1f))
                after(effect1, FixedValue(2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 2f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 2f)
    }

    @Test
    fun placement_relative_beforeEffect() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                val effect1 = between(1f, 2f, FixedValue(1f))
                before(effect1, FixedValue(2f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(2f, 1f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 2f)
    }

    @Test
    fun placement_relative_chainOfMappings() {
        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                val rootEffect = after(1f, FixedValueWithExtent(-1f, 2f))

                val left = before(rootEffect, FixedValueWithExtent(-2f, 3f))
                before(left, FixedValueWithExtent(-3f, 4f))

                val right = after(rootEffect, FixedValueWithExtent(-4f, 3f))
                after(right, FixedValueWithExtent(-5f, 4f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, -3f, -2f, -1f, -4f, -5f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(-6f, -2f, 1f, 3f, 6f, 10f)
    }

    @Test
    fun placement_relative_overlappingChain_throws() {
        assertFailsWith<IllegalArgumentException> {
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                val rootEffect = between(1f, 3f, FixedValue(-1f))
                val left = before(rootEffect, FixedValue(-2f))
                after(left, FixedValue(-3f))
            }
        }
    }

    @Test
    fun effect_differentReverseSpec() {
        val effect = SimpleEffect {
            forward(Mapping.One)
            backward(Mapping.Two)
        }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result).maxDirection().fixedMappingsMatch(0f, 1f, 0f)
        assertThat(result).maxDirection().breakpointsPositionsMatch(1f, 2f)

        assertThat(result).minDirection().fixedMappingsMatch(0f, 2f, 0f)
        assertThat(result).minDirection().breakpointsPositionsMatch(1f, 2f)
    }

    @Test
    fun effect_separateReverseSpec_withBuilder_canProduceDifferentSegmentCount() {
        val effect =
            object : Effect.PlaceableBetween {
                override fun EffectApplyScope.createSpec(
                    minLimit: Float,
                    minLimitKey: BreakpointKey,
                    maxLimit: Float,
                    maxLimitKey: BreakpointKey,
                    placement: EffectPlacement,
                ) {
                    forward(Mapping.One) { fixedValue(breakpoint = minLimit + 0.5f, 10f) }
                    backward(Mapping.Two)
                }
            }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result).maxDirection().fixedMappingsMatch(0f, 1f, 10f, 0f)
        assertThat(result).maxDirection().breakpointsPositionsMatch(1f, 1.5f, 2f)

        assertThat(result).minDirection().fixedMappingsMatch(0f, 2f, 0f)
        assertThat(result).minDirection().breakpointsPositionsMatch(1f, 2f)
    }

    @Test
    fun effect_identicalBackward_withBuilder_producesSameSpecInBothDirections() {
        val breakpointKey = BreakpointKey("foo")
        val effect =
            UnidirectionalEffect(Mapping.One) {
                fixedValue(breakpoint = 1.5f, value = 10f, key = breakpointKey)
            }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 10f, 0f)
        assertThat(result).bothDirections().breakpointsPositionsMatch(1f, 1.5f, 2f)
    }

    @Test
    fun effect_setBreakpointBeforeMinLimit_throws() {
        val rogueEffect =
            UnidirectionalEffect(Mapping.One) { this.fixedValue(breakpoint = 0.5f, value = 0f) }

        assertFailsWith<IllegalStateException> {
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, rogueEffect)
            }
        }
    }

    @Test
    fun effect_setBreakpointAfterMinLimit_throws() {
        val rogueEffect =
            UnidirectionalEffect(Mapping.One) { this.fixedValue(breakpoint = 2.5f, value = 0f) }

        assertFailsWith<IllegalStateException> {
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, rogueEffect)
            }
        }
    }

    @Test
    fun effect_semantics_applyToFullInputRange() {
        val semanticKey = SemanticKey<String>("foo")
        val effect =
            UnidirectionalEffect(
                Mapping.One,
                semantics = listOf(SemanticValue(semanticKey, "initial")),
            ) {
                fixedValue(
                    breakpoint = 1.5f,
                    value = 2f,
                    semantics = listOf(SemanticValue(semanticKey, "second")),
                )
            }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result)
            .maxDirection()
            .semantics()
            .withKey(semanticKey)
            .containsExactly("initial", "initial", "second", "second")
            .inOrder()
    }

    @Test
    fun beforeAfter_minSpring_isChangeable() {
        val spring = SpringParameters(stiffness = 1f, dampingRatio = 2f)
        val effect = UnidirectionalEffect(Mapping.One) { before(spring = spring) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result).bothDirections().breakpoints().atPosition(1f).spring().isEqualTo(spring)
    }

    @Test
    fun beforeAfter_maxSpring_isChangeable() {
        val spring = SpringParameters(stiffness = 1f, dampingRatio = 2f)
        val effect = UnidirectionalEffect(Mapping.One) { after(spring = spring) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result).bothDirections().breakpoints().atPosition(2f).spring().isEqualTo(spring)
    }

    @Test
    fun beforeAfter_conflictingSpring_secondEffectWins() {
        val spring1 = SpringParameters(stiffness = 1f, dampingRatio = 2f)
        val spring2 = SpringParameters(stiffness = 2f, dampingRatio = 2f)

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, UnidirectionalEffect(Mapping.One) { after(spring = spring1) })
                between(2f, 3f, UnidirectionalEffect(Mapping.One) { before(spring = spring2) })
            }

        assertThat(result).bothDirections().breakpoints().atPosition(2f).spring().isEqualTo(spring2)
    }

    @Test
    fun beforeAfter_minGuarantee_isChangeable() {
        val guarantee = Guarantee.InputDelta(1f)
        val effect = UnidirectionalEffect(Mapping.One) { before(guarantee = guarantee) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result)
            .bothDirections()
            .breakpoints()
            .atPosition(1f)
            .guarantee()
            .isEqualTo(guarantee)
    }

    @Test
    fun beforeAfter_maxGuarantee_isChangeable() {
        val guarantee = Guarantee.InputDelta(1f)
        val effect = UnidirectionalEffect(Mapping.One) { after(guarantee = guarantee) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result)
            .bothDirections()
            .breakpoints()
            .atPosition(2f)
            .guarantee()
            .isEqualTo(guarantee)
    }

    @Test
    fun beforeAfter_conflictingGuarantee_secondEffectWins() {
        val guarantee1 = Guarantee.InputDelta(1f)
        val guarantee2 = Guarantee.InputDelta(2f)

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, UnidirectionalEffect(Mapping.One) { after(guarantee = guarantee1) })
                between(
                    2f,
                    3f,
                    UnidirectionalEffect(Mapping.One) { before(guarantee = guarantee2) },
                )
            }

        assertThat(result)
            .bothDirections()
            .breakpoints()
            .atPosition(2f)
            .guarantee()
            .isEqualTo(guarantee2)
    }

    @Test
    fun beforeAfter_maxSemantics_applyAfterEffect() {
        val effect =
            UnidirectionalEffect(Mapping.One, testSemantics("s1")) {
                after(semantics = testSemantics("s1+"))
            }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                val effect1 = between(1f, 2f, effect)
                after(effect1, FixedValue(2f))
            }

        assertThat(result)
            .maxDirection()
            .semantics()
            .withKey(TestSemantics)
            .containsExactly("s1", "s1", "s1+")
            .inOrder()
    }

    @Test
    fun beforeAfter_minSemantics_applyBeforeEffect() {
        val effect =
            UnidirectionalEffect(Mapping.One, testSemantics("s1")) {
                before(semantics = testSemantics("s1-"))
            }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
                before(1f, FixedValue(2f))
            }

        assertThat(result)
            .maxDirection()
            .semantics()
            .withKey(TestSemantics)
            .containsExactly("s1-", "s1", "s1")
            .inOrder()
    }

    @Test
    fun beforeAfter_conflictingSemantics_firstEffectWins() {
        val effect1 =
            UnidirectionalEffect(Mapping.One, testSemantics("s1")) {
                after(semantics = testSemantics("s1+"))
            }
        val effect2 =
            UnidirectionalEffect(Mapping.One, testSemantics("s2")) {
                before(semantics = testSemantics("s2-"))
            }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect1)
                between(3f, 4f, effect2)
            }

        assertThat(result)
            .maxDirection()
            .semantics()
            .withKey(TestSemantics)
            .containsExactly("s1", "s1", "s1+", "s2", "s2")
            .inOrder()
    }

    @Test
    fun beforeAfter_semantics_specifiedByNextEffect_afterSemanticsIgnored() {
        val effect1 =
            UnidirectionalEffect(Mapping.One, testSemantics("s1")) {
                after(semantics = testSemantics("s1+"))
            }

        val effect2 = UnidirectionalEffect(Mapping.One, semantics = testSemantics("s2"))

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect1)
                between(2f, 3f, effect2)
            }

        assertThat(result)
            .maxDirection()
            .semantics()
            .withKey(TestSemantics)
            .containsExactly("s1", "s1", "s2", "s2")
            .inOrder()
    }

    @Test
    fun beforeAfter_semantics_specifiedByPreviousEffect_beforeSemanticsIgnored() {
        val effect1 = UnidirectionalEffect(Mapping.One, testSemantics("s1"))

        val effect2 =
            UnidirectionalEffect(Mapping.One, semantics = testSemantics("s2")) {
                before(semantics = testSemantics("s2-"))
            }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect1)
                between(2f, 3f, effect2)
            }

        assertThat(result)
            .maxDirection()
            .semantics()
            .withKey(TestSemantics)
            .containsExactly("s1", "s1", "s2", "s2")
            .inOrder()
    }

    @Test
    fun beforeAfter_maxMapping_applyAfterEffect() {
        val effect = UnidirectionalEffect(Mapping.One) { after(mapping = Mapping.Two) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 2f)
    }

    @Test
    fun beforeAfter_minMapping_applyBeforeEffect() {
        val effect = UnidirectionalEffect(Mapping.One) { before(mapping = Mapping.Two) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
            }

        assertThat(result).bothDirections().fixedMappingsMatch(2f, 1f, 0f)
    }

    @Test
    fun beforeAfter_minMapping_ignoredWhenEffectBeforeSpecified() {
        val effect = UnidirectionalEffect(Mapping.One) { before(mapping = Mapping.Two) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
                before(1f, FixedValue(3f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(3f, 1f, 0f)
    }

    @Test
    fun beforeAfter_maxMapping_ignoredWhenEffectAfterSpecified() {
        val effect = UnidirectionalEffect(Mapping.One) { after(mapping = Mapping.Two) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                between(1f, 2f, effect)
                after(2f, FixedValue(3f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 3f)
    }

    @Test
    fun beforeAfter_minMapping_ignoredWhenFirstEffect() {
        val effect = UnidirectionalEffect(Mapping.One) { before(mapping = Mapping.Two) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                before(0f, effect)
            }

        assertThat(result).bothDirections().fixedMappingsMatch(1f, 0f)
    }

    @Test
    fun beforeAfter_maxMapping_ignoredWhenLastEffect() {
        val effect = UnidirectionalEffect(Mapping.One) { after(mapping = Mapping.Two) }

        val result =
            motionSpec(baseMapping = Mapping.Zero, defaultSpring = spatial.default) {
                after(0f, effect)
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f)
    }

    @Test
    fun order_sharedBreakpoint_betweenAndAfter_sortedCorrectly() {
        val result =
            spatialMotionSpec(Mapping.Zero) {
                after(2f, FixedValue(2f))
                between(1f, 2f, FixedValue(1f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(0f, 1f, 2f)
    }

    @Test
    fun order_sharedBreakpoint_betweenAndBefore_sortedCorrectly() {
        val result =
            spatialMotionSpec(Mapping.Zero) {
                between(1f, 2f, FixedValue(2f))
                before(1f, FixedValue(1f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(1f, 2f, 0f)
    }

    @Test
    fun order_sharedBreakpoint_beforeAfter_sortedCorrectly() {
        val result =
            spatialMotionSpec(Mapping.Zero) {
                after(1f, FixedValue(2f))
                before(1f, FixedValue(1f))
            }

        assertThat(result).bothDirections().fixedMappingsMatch(1f, 2f)
    }

    private class SimpleEffect(private val createSpec: EffectApplyScope.() -> Unit) :
        Effect.PlaceableBetween {
        override fun EffectApplyScope.createSpec(
            minLimit: Float,
            minLimitKey: BreakpointKey,
            maxLimit: Float,
            maxLimitKey: BreakpointKey,
            placement: EffectPlacement,
        ) {
            createSpec()
        }
    }

    private class UnidirectionalEffect(
        private val initialMapping: Mapping,
        private val semantics: List<SemanticValue<*>> = emptyList(),
        private val init: DirectionalEffectBuilderScope.() -> Unit = {},
    ) : Effect.PlaceableBetween, Effect.PlaceableAfter, Effect.PlaceableBefore {
        override fun MotionBuilderContext.intrinsicSize(): Float = Float.POSITIVE_INFINITY

        override fun EffectApplyScope.createSpec(
            minLimit: Float,
            minLimitKey: BreakpointKey,
            maxLimit: Float,
            maxLimitKey: BreakpointKey,
            placement: EffectPlacement,
        ) {
            unidirectional(initialMapping, semantics, init)
        }
    }

    private class FixedValueWithExtent(val value: Float, val extent: Float) :
        Effect.PlaceableAfter, Effect.PlaceableBefore {
        override fun MotionBuilderContext.intrinsicSize() = extent

        override fun EffectApplyScope.createSpec(
            minLimit: Float,
            minLimitKey: BreakpointKey,
            maxLimit: Float,
            maxLimitKey: BreakpointKey,
            placement: EffectPlacement,
        ) {
            return unidirectional(Mapping.Fixed(value))
        }
    }

    companion object {
        val TestSemantics = SemanticKey<String>("foo")

        fun testSemantics(value: String) = listOf(TestSemantics with value)
    }
}
