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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.InputDirection
import com.google.common.truth.Truth.assertThat
import kotlin.math.nextDown
import kotlin.math.nextUp
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DistanceGestureContextTest {

    @Test
    fun setDistance_maxDirection_increasingInput_keepsDirection() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        for (value in 0..6) {
            underTest.dragOffset = value.toFloat()
            assertThat(underTest.direction).isEqualTo(InputDirection.Max)
        }
    }

    @Test
    fun setDistance_minDirection_decreasingInput_keepsDirection() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Min,
                directionChangeSlop = 5f,
            )

        for (value in 0 downTo -6) {
            underTest.dragOffset = value.toFloat()
            assertThat(underTest.direction).isEqualTo(InputDirection.Min)
        }
    }

    @Test
    fun setDistance_maxDirection_decreasingInput_keepsDirection_belowDirectionChangeSlop() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        underTest.dragOffset = -5f
        assertThat(underTest.direction).isEqualTo(InputDirection.Max)
    }

    @Test
    fun setDistance_maxDirection_decreasingInput_switchesDirection_aboveDirectionChangeSlop() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        underTest.dragOffset = (-5f).nextDown()
        assertThat(underTest.direction).isEqualTo(InputDirection.Min)
    }

    @Test
    fun setDistance_minDirection_increasingInput_keepsDirection_belowDirectionChangeSlop() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Min,
                directionChangeSlop = 5f,
            )

        underTest.dragOffset = 5f
        assertThat(underTest.direction).isEqualTo(InputDirection.Min)
    }

    @Test
    fun setDistance_minDirection_decreasingInput_switchesDirection_aboveDirectionChangeSlop() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Min,
                directionChangeSlop = 5f,
            )

        underTest.dragOffset = 5f.nextUp()
        assertThat(underTest.direction).isEqualTo(InputDirection.Max)
    }

    @Test
    fun reset_resetsFurthestValue() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 10f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 1f,
            )

        underTest.reset(5f, direction = InputDirection.Max)
        assertThat(underTest.direction).isEqualTo(InputDirection.Max)
        assertThat(underTest.dragOffset).isEqualTo(5f)

        underTest.dragOffset -= 1f
        assertThat(underTest.direction).isEqualTo(InputDirection.Max)
        assertThat(underTest.dragOffset).isEqualTo(4f)

        underTest.dragOffset = underTest.dragOffset.nextDown()
        assertThat(underTest.direction).isEqualTo(InputDirection.Min)
        assertThat(underTest.dragOffset).isWithin(0.0001f).of(4f)
    }

    @Test
    fun setDirectionChangeSlop_smallerThanCurrentDelta_switchesDirection() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 10f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        underTest.dragOffset -= 2f
        assertThat(underTest.direction).isEqualTo(InputDirection.Max)
        assertThat(underTest.dragOffset).isEqualTo(8f)

        underTest.directionChangeSlop = 1f
        assertThat(underTest.direction).isEqualTo(InputDirection.Min)
        assertThat(underTest.dragOffset).isEqualTo(8f)
    }
}
