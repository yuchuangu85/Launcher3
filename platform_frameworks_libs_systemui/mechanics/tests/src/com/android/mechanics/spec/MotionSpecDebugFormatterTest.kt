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

package com.android.mechanics.spec

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.ChangeSegmentHandlers.PreventDirectionChangeWithinCurrentSegment
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.effectsDirectionalMotionSpec
import com.android.mechanics.spec.builder.spatialDirectionalMotionSpec
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionSpecDebugFormatterTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    @Test
    fun motionSpec_unidirectionalSpec_formatIsUseful() {
        val spec = MotionSpec(effectsDirectionalMotionSpec { fixedValue(0f, value = 1f) })

        assertThat(formatForTest(spec.toDebugString()))
            .isEqualTo(
                """
unidirectional:
  @-Infinity [built-in::min|id:0x1234cdef]
    Fixed(value=0.0)
  @0.0 [id:0x1234cdef] spring=1600.0/1.0
    Fixed(value=1.0)
  @Infinity [built-in::max|id:0x1234cdef]"""
                    .trimIndent()
            )
    }

    @Test
    fun motionSpec_bidirectionalSpec_formatIsUseful() {
        val spec =
            MotionSpec(
                spatialDirectionalMotionSpec(Mapping.Zero) { fixedValue(0f, value = 1f) },
                spatialDirectionalMotionSpec(Mapping.One) { fixedValue(0f, value = 0f) },
            )

        assertThat(formatForTest(spec.toDebugString()))
            .isEqualTo(
                """
maxDirection:
  @-Infinity [built-in::min|id:0x1234cdef]
    Fixed(value=0.0)
  @0.0 [id:0x1234cdef] spring=700.0/0.9
    Fixed(value=1.0)
  @Infinity [built-in::max|id:0x1234cdef]
minDirection:
  @-Infinity [built-in::min|id:0x1234cdef]
    Fixed(value=1.0)
  @0.0 [id:0x1234cdef] spring=700.0/0.9
    Fixed(value=0.0)
  @Infinity [built-in::max|id:0x1234cdef]"""
                    .trimIndent()
            )
    }

    @Test
    fun motionSpec_semantics_formatIsUseful() {
        val semanticKey = SemanticKey<Float>("foo")

        val spec =
            MotionSpec(
                effectsDirectionalMotionSpec(semantics = listOf(semanticKey with 42f)) {
                    fixedValue(0f, value = 1f, semantics = listOf(semanticKey with 43f))
                }
            )

        assertThat(formatForTest(spec.toDebugString()))
            .isEqualTo(
                """
unidirectional:
  @-Infinity [built-in::min|id:0x1234cdef]
    Fixed(value=0.0)
      foo[id:0x1234cdef]=42.0
  @0.0 [id:0x1234cdef] spring=1600.0/1.0
    Fixed(value=1.0)
      foo[id:0x1234cdef]=43.0
  @Infinity [built-in::max|id:0x1234cdef]"""
                    .trimIndent()
            )
    }

    @Test
    fun motionSpec_segmentHandlers_formatIsUseful() {
        val key1 = BreakpointKey("1")
        val key2 = BreakpointKey("2")
        val spec =
            MotionSpec(
                effectsDirectionalMotionSpec {
                    fixedValue(0f, value = 1f, key = key1)
                    fixedValue(2f, value = 2f, key = key1)
                },
                segmentHandlers =
                    mapOf(
                        SegmentKey(key1, key2, InputDirection.Max) to
                            PreventDirectionChangeWithinCurrentSegment,
                        SegmentKey(key1, key2, InputDirection.Min) to
                            PreventDirectionChangeWithinCurrentSegment,
                    ),
            )

        assertThat(formatForTest(spec.toDebugString()))
            .isEqualTo(
                """
unidirectional:
  @-Infinity [built-in::min|id:0x1234cdef]
    Fixed(value=0.0)
  @0.0 [1|id:0x1234cdef] spring=1600.0/1.0
    Fixed(value=1.0)
  @2.0 [1|id:0x1234cdef] spring=1600.0/1.0
    Fixed(value=2.0)
  @Infinity [built-in::max|id:0x1234cdef]
segmentHandlers:
  1|id:0x1234cdef >> 2|id:0x1234cdef
  1|id:0x1234cdef << 2|id:0x1234cdef"""
                    .trimIndent()
            )
    }

    companion object {
        private val idMatcher = Regex("id:0x[0-9a-f]{8}")

        fun formatForTest(debugString: String) =
            debugString.replace(idMatcher, "id:0x1234cdef").trim()
    }
}
