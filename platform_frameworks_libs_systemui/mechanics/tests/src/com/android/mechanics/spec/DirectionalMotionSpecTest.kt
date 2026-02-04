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

package com.android.mechanics.spec

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.builder.directionalMotionSpec
import com.android.mechanics.spring.SpringParameters
import com.google.common.truth.Truth.assertThat
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectionalMotionSpecTest {

    @Test
    fun noBreakpoints_throws() {
        assertFailsWith<IllegalArgumentException> {
            DirectionalMotionSpec(emptyList(), emptyList())
        }
    }

    @Test
    fun wrongSentinelBreakpoints_throws() {
        val breakpoint1 = Breakpoint(B1, position = 10f, Spring, Guarantee.None)
        val breakpoint2 = Breakpoint(B2, position = 20f, Spring, Guarantee.None)

        assertFailsWith<IllegalArgumentException> {
            DirectionalMotionSpec(listOf(breakpoint1, breakpoint2), listOf(Mapping.Identity))
        }
    }

    @Test
    fun tooFewMappings_throws() {
        assertFailsWith<IllegalArgumentException> {
            DirectionalMotionSpec(listOf(Breakpoint.minLimit, Breakpoint.maxLimit), emptyList())
        }
    }

    @Test
    fun tooManyMappings_throws() {
        assertFailsWith<IllegalArgumentException> {
            DirectionalMotionSpec(
                listOf(Breakpoint.minLimit, Breakpoint.maxLimit),
                listOf(Mapping.One, Mapping.Two),
            )
        }
    }

    @Test
    fun breakpointsOutOfOrder_throws() {
        val breakpoint1 = Breakpoint(B1, position = 10f, Spring, Guarantee.None)
        val breakpoint2 = Breakpoint(B2, position = 20f, Spring, Guarantee.None)
        assertFailsWith<IllegalArgumentException> {
            DirectionalMotionSpec(
                listOf(Breakpoint.minLimit, breakpoint2, breakpoint1, Breakpoint.maxLimit),
                listOf(Mapping.Zero, Mapping.One, Mapping.Two),
            )
        }
    }

    @Test
    fun findBreakpointIndex_returnsMinForEmptySpec() {
        val underTest = DirectionalMotionSpec.Empty

        assertThat(underTest.findBreakpointIndex(0f)).isEqualTo(0)
        assertThat(underTest.findBreakpointIndex(Float.MAX_VALUE)).isEqualTo(0)
        assertThat(underTest.findBreakpointIndex(-Float.MAX_VALUE)).isEqualTo(0)
    }

    @Test
    fun findBreakpointIndex_throwsForNonFiniteInput() {
        val underTest = DirectionalMotionSpec.Empty

        assertFailsWith<IllegalArgumentException> { underTest.findBreakpointIndex(Float.NaN) }
        assertFailsWith<IllegalArgumentException> {
            underTest.findBreakpointIndex(Float.NEGATIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            underTest.findBreakpointIndex(Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun findBreakpointIndex_atBreakpoint_returnsIndex() {
        val underTest =
            directionalMotionSpec(Spring) { mapping(breakpoint = 10f, mapping = Mapping.Identity) }

        assertThat(underTest.findBreakpointIndex(10f)).isEqualTo(1)
    }

    @Test
    fun findBreakpointIndex_afterBreakpoint_returnsPreviousIndex() {
        val underTest =
            directionalMotionSpec(Spring) { mapping(breakpoint = 10f, mapping = Mapping.Identity) }

        assertThat(underTest.findBreakpointIndex(10f.nextUp())).isEqualTo(1)
    }

    @Test
    fun findBreakpointIndex_beforeBreakpoint_returnsIndex() {
        val underTest =
            directionalMotionSpec(Spring) { mapping(breakpoint = 10f, mapping = Mapping.Identity) }

        assertThat(underTest.findBreakpointIndex(10f.nextDown())).isEqualTo(0)
    }

    @Test
    fun findBreakpointIndexByKey_returnsIndex() {
        val underTest =
            directionalMotionSpec(Spring) {
                mapping(breakpoint = 10f, key = B1, mapping = Mapping.Identity)
            }

        assertThat(underTest.findBreakpointIndex(B1)).isEqualTo(1)
    }

    @Test
    fun findBreakpointIndexByKey_unknown_returnsMinusOne() {
        val underTest =
            directionalMotionSpec(Spring) {
                mapping(breakpoint = 10f, key = B1, mapping = Mapping.Identity)
            }

        assertThat(underTest.findBreakpointIndex(B2)).isEqualTo(-1)
    }

    @Test
    fun findSegmentIndex_returnsIndexForSegment_ignoringDirection() {
        val underTest =
            directionalMotionSpec(Spring) {
                mapping(breakpoint = 10f, key = B1, mapping = Mapping.One)
                mapping(breakpoint = 20f, key = B2, mapping = Mapping.Identity)
            }

        assertThat(underTest.findSegmentIndex(SegmentKey(B1, B2, InputDirection.Max))).isEqualTo(1)
        assertThat(underTest.findSegmentIndex(SegmentKey(B1, B2, InputDirection.Min))).isEqualTo(1)
    }

    @Test
    fun findSegmentIndex_forInvalidKeys_returnsMinusOne() {
        val underTest =
            directionalMotionSpec(Spring) {
                mapping(breakpoint = 10f, key = B1, mapping = Mapping.One)
                mapping(breakpoint = 20f, key = B2, mapping = Mapping.One)
                mapping(breakpoint = 30f, key = B3, mapping = Mapping.Identity)
            }

        assertThat(underTest.findSegmentIndex(SegmentKey(B2, B1, InputDirection.Max))).isEqualTo(-1)
        assertThat(underTest.findSegmentIndex(SegmentKey(B1, B3, InputDirection.Max))).isEqualTo(-1)
    }

    @Test
    fun semantics_tooFewValues_throws() {
        assertFailsWith<IllegalArgumentException> {
            DirectionalMotionSpec(
                listOf(Breakpoint.minLimit, Breakpoint.maxLimit),
                listOf(Mapping.Identity),
                listOf(SegmentSemanticValues(Semantic1, emptyList())),
            )
        }
    }

    @Test
    fun semantics_tooManyValues_throws() {
        assertFailsWith<IllegalArgumentException> {
            DirectionalMotionSpec(
                listOf(Breakpoint.minLimit, Breakpoint.maxLimit),
                listOf(Mapping.Identity),
                listOf(SegmentSemanticValues(Semantic1, listOf("One", "Two"))),
            )
        }
    }

    companion object {
        val B1 = BreakpointKey("one")
        val B2 = BreakpointKey("two")
        val B3 = BreakpointKey("three")
        val Semantic1 = SemanticKey<String>("Foo")
        val Semantic2 = SemanticKey<String>("Bar")

        val Spring = SpringParameters(stiffness = 100f, dampingRatio = 1f)
    }
}
