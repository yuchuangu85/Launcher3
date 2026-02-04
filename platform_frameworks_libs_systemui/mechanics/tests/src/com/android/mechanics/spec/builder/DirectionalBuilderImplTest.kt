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
import com.android.mechanics.spec.BreakpointKey
import com.android.mechanics.spec.Guarantee
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spec.with
import com.android.mechanics.spring.SpringParameters
import com.android.mechanics.testing.DirectionalMotionSpecSubject.Companion.assertThat
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectionalBuilderImplTest {

    @Test
    fun directionalSpec_buildEmptySpec() {
        val result = directionalMotionSpec()

        assertThat(result).breakpoints().isEmpty()
        assertThat(result).mappings().containsExactly(Mapping.Identity)
    }

    @Test
    fun directionalSpec_addBreakpointsAndMappings() {
        val result =
            directionalMotionSpec(Spring, Mapping.Zero) {
                mapping(breakpoint = 0f, mapping = Mapping.One, key = B1)
                mapping(breakpoint = 10f, mapping = Mapping.Two, key = B2)
            }

        assertThat(result).breakpoints().keys().containsExactly(B1, B2).inOrder()
        assertThat(result).breakpoints().withKey(B1).isAt(0f)
        assertThat(result).breakpoints().withKey(B2).isAt(10f)
        assertThat(result)
            .mappings()
            .containsExactly(Mapping.Zero, Mapping.One, Mapping.Two)
            .inOrder()
    }

    @Test
    fun directionalSpec_mappingBuilder_setsDefaultSpring() {
        val result = directionalMotionSpec(Spring) { fixedValue(breakpoint = 10f, value = 20f) }

        assertThat(result).breakpoints().atPosition(10f).spring().isEqualTo(Spring)
    }

    @Test
    fun directionalSpec_mappingBuilder_canOverrideDefaultSpring() {
        val otherSpring = SpringParameters(stiffness = 10f, dampingRatio = 0.1f)
        val result =
            directionalMotionSpec(Spring) {
                fixedValue(breakpoint = 10f, value = 20f, spring = otherSpring)
            }

        assertThat(result).breakpoints().atPosition(10f).spring().isEqualTo(otherSpring)
    }

    @Test
    fun directionalSpec_mappingBuilder_defaultsToNoGuarantee() {
        val result = directionalMotionSpec(Spring) { fixedValue(breakpoint = 10f, value = 20f) }

        assertThat(result).breakpoints().atPosition(10f).guarantee().isEqualTo(Guarantee.None)
    }

    @Test
    fun directionalSpec_mappingBuilder_canSetGuarantee() {
        val guarantee = Guarantee.InputDelta(10f)
        val result =
            directionalMotionSpec(Spring) {
                fixedValue(breakpoint = 10f, value = 20f, guarantee = guarantee)
            }

        assertThat(result).breakpoints().atPosition(10f).guarantee().isEqualTo(guarantee)
    }

    @Test
    fun directionalSpec_mappingBuilder_jumpTo_setsAbsoluteValue() {
        val result =
            directionalMotionSpec(Spring, Mapping.Fixed(99f)) {
                fixedValue(breakpoint = 10f, value = 20f)
            }

        assertThat(result).breakpoints().positions().containsExactly(10f)
        assertThat(result).mappings().atOrAfter(10f).isFixedValue(20f)
    }

    @Test
    fun directionalSpec_mappingBuilder_jumpBy_setsRelativeValue() {
        val result =
            directionalMotionSpec(Spring, Mapping.Linear(factor = 0.5f)) {
                // At 10f the current value is 5f (10f * 0.5f)
                fixedValueFromCurrent(breakpoint = 10f, delta = 30f)
            }

        assertThat(result).breakpoints().positions().containsExactly(10f)
        assertThat(result).mappings().atOrAfter(10f).isFixedValue(35f)
    }

    @Test
    fun directionalSpec_mappingBuilder_continueWithFixedValue_usesSourceValue() {
        val result =
            directionalMotionSpec(Spring, Mapping.Linear(factor = 0.5f)) {
                // At 5f the current value is 2.5f (5f * 0.5f)
                fixedValueFromCurrent(breakpoint = 5f)
            }

        assertThat(result).mappings().atOrAfter(5f).isFixedValue(2.5f)
    }

    @Test
    fun directionalSpec_mappingBuilder_continueWithFractionalInput_matchesLinearMapping() {
        val result =
            directionalMotionSpec(Spring) {
                fractionalInput(breakpoint = 5f, from = 1f, fraction = .1f)
            }

        assertThat(result)
            .mappings()
            .atOrAfter(5f)
            .matchesLinearMapping(in1 = 5f, out1 = 1f, in2 = 15f, out2 = 2f)
    }

    @Test
    fun directionalSpec_mappingBuilder_continueWithTargetValue_matchesLinearMapping() {
        val result =
            directionalMotionSpec(Spring) {
                target(breakpoint = 5f, from = 1f, to = 20f)
                mapping(breakpoint = 30f, mapping = Mapping.Identity)
            }

        assertThat(result)
            .mappings()
            .atOrAfter(5f)
            .matchesLinearMapping(in1 = 5f, out1 = 1f, in2 = 30f, out2 = 20f)
    }

    @Test
    fun directionalSpec_mappingBuilder_breakpointsAtSamePosition_producesValidSegment() {
        val result =
            directionalMotionSpec(Spring) {
                target(breakpoint = 5f, from = 1f, to = 20f)
                mapping(breakpoint = 5f, mapping = Mapping.Identity)
            }
        assertThat(result)
            .mappings()
            .containsExactly(Mapping.Identity, Mapping.Fixed(1f), Mapping.Identity)
            .inOrder()
    }

    @Test
    fun directionalSpec_mappingBuilder_identity_addsIdentityMapping() {
        val result = directionalMotionSpec(Spring, Mapping.Zero) { identity(breakpoint = 10f) }
        assertThat(result).mappings().containsExactly(Mapping.Zero, Mapping.Identity).inOrder()
        assertThat(result).breakpoints().positions().containsExactly(10f)
    }

    @Test
    fun directionalSpec_mappingBuilder_identityWithDelta_producesLinearMapping() {
        val result =
            directionalMotionSpec(Spring, Mapping.Zero) { identity(breakpoint = 10f, delta = 2f) }

        assertThat(result)
            .mappings()
            .atOrAfter(10f)
            .matchesLinearMapping(in1 = 10f, out1 = 12f, in2 = 20f, out2 = 22f)
    }

    @Test
    fun semantics_appliedForSingleSegment() {
        val result = directionalMotionSpec(Mapping.Identity, listOf(S1 with "One", S2 with "Two"))

        assertThat(result).semantics().containsExactly(S1, S2)
        assertThat(result).semantics().withKey(S1).containsExactly("One")
        assertThat(result).semantics().withKey(S2).containsExactly("Two")
    }

    @Test
    fun directionalSpec_semantics_appliedForAllSegments() {
        val result =
            directionalMotionSpec(Spring, semantics = listOf(S1 with "One")) {
                mapping(breakpoint = 0f, mapping = Mapping.Identity)
            }
        assertThat(result).mappings().hasSize(2)
        assertThat(result).semantics().containsExactly(S1)
        assertThat(result).semantics().withKey(S1).containsExactly("One", "One")
    }

    @Test
    fun directionalSpec_semantics_appliedForCurrentSegment() {
        val result =
            directionalMotionSpec(Spring, semantics = listOf(S1 with "One")) {
                mapping(breakpoint = 0f, mapping = Mapping.Identity)
                mapping(
                    breakpoint = 2f,
                    mapping = Mapping.Identity,
                    semantics = listOf(S1 with "Two"),
                )
            }
        assertThat(result).mappings().hasSize(3)
        assertThat(result).semantics().withKey(S1).containsExactly("One", "One", "Two").inOrder()
    }

    @Test
    fun directionalSpec_semantics_changingUndeclaredSemantics_backfills() {
        val result =
            directionalMotionSpec(Spring) {
                mapping(
                    breakpoint = 0f,
                    mapping = Mapping.Identity,
                    semantics = listOf(S1 with "Two"),
                )
            }

        assertThat(result).mappings().hasSize(2)
        assertThat(result).semantics().withKey(S1).containsExactly("Two", "Two").inOrder()
    }

    @Test
    fun directionalSpec_semantics_changeableIndividually() {
        val result =
            directionalMotionSpec(Spring, semantics = listOf(S1 with "One", S2 with "AAA")) {
                mapping(
                    breakpoint = 0f,
                    mapping = Mapping.Identity,
                    semantics = listOf(S2 with "BBB"),
                )
                mapping(
                    breakpoint = 2f,
                    mapping = Mapping.Identity,
                    semantics = listOf(S1 with "Two"),
                )
            }
        assertThat(result).mappings().hasSize(3)
        assertThat(result).semantics().withKey(S1).containsExactly("One", "One", "Two").inOrder()
        assertThat(result).semantics().withKey(S2).containsExactly("AAA", "BBB", "BBB").inOrder()
    }

    @Test
    fun directionalSpec_semantics_lateCompletedSegmentsRetainSemantics() {
        val result =
            directionalMotionSpec(Spring, semantics = listOf(S1 with "One")) {
                targetFromCurrent(breakpoint = 0f, to = 10f, semantics = listOf(S1 with "Two"))
                identity(breakpoint = 1f, semantics = listOf(S1 with "Three"))
            }
        assertThat(result).mappings().hasSize(3)
        assertThat(result).semantics().withKey(S1).containsExactly("One", "Two", "Three").inOrder()
    }

    @Test
    fun builderContext_spatialDirectionalMotionSpec_defaultsToSpatialSpringAndIdentityMapping() {
        val context = FakeMotionSpecBuilderContext.Default

        val result = with(context) { spatialDirectionalMotionSpec { fixedValue(0f, value = 1f) } }

        assertThat(result).mappings().containsExactly(Mapping.Identity, Mapping.One).inOrder()
        assertThat(result).breakpoints().atPosition(0f).spring().isEqualTo(context.spatial.default)
    }

    @Test
    fun builderContext_effectsDirectionalMotionSpec_defaultsToEffectsSpringAndZeroMapping() {
        val context = FakeMotionSpecBuilderContext.Default

        val result = with(context) { effectsDirectionalMotionSpec { fixedValue(0f, value = 1f) } }

        assertThat(result).mappings().containsExactly(Mapping.Zero, Mapping.One).inOrder()
        assertThat(result).breakpoints().atPosition(0f).spring().isEqualTo(context.effects.default)
    }

    companion object {
        val Spring = SpringParameters(stiffness = 100f, dampingRatio = 1f)
        val B1 = BreakpointKey("One")
        val B2 = BreakpointKey("Two")
        val S1 = SemanticKey<String>("Foo")
        val S2 = SemanticKey<String>("Bar")
    }
}
