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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.InputDirection
import com.google.common.truth.Truth.assertThat
import kotlin.math.nextDown
import kotlin.math.nextUp
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewGestureContextTest {

    @Test
    fun update_maxDirection_increasingInput_keepsDirection() {
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
    fun update_minDirection_decreasingInput_keepsDirection() {
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
    fun update_maxDirection_decreasingInput_keepsDirection_belowDirectionChangeSlop() {
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
    fun update_maxDirection_decreasingInput_switchesDirection_aboveDirectionChangeSlop() {
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
    fun update_minDirection_increasingInput_keepsDirection_belowDirectionChangeSlop() {
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
    fun update_minDirection_decreasingInput_switchesDirection_aboveDirectionChangeSlop() {
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
    fun callback_invokedOnChange() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        var invocationCount = 0
        underTest.addUpdateCallback { invocationCount++ }

        assertThat(invocationCount).isEqualTo(0)
        underTest.dragOffset += 1
        assertThat(invocationCount).isEqualTo(1)
    }

    @Test
    fun callback_invokedOnReset() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        var invocationCount = 0
        underTest.addUpdateCallback { invocationCount++ }

        assertThat(invocationCount).isEqualTo(0)
        underTest.reset(0f, InputDirection.Max)
        assertThat(invocationCount).isEqualTo(1)
    }

    @Test
    fun callback_ignoredForSameValues() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        var invocationCount = 0
        underTest.addUpdateCallback { invocationCount++ }

        assertThat(invocationCount).isEqualTo(0)
        underTest.dragOffset += 0
        assertThat(invocationCount).isEqualTo(0)
    }

    @Test
    fun callback_removeUpdateCallback_removesCallback() {
        val underTest =
            DistanceGestureContext(
                initialDragOffset = 0f,
                initialDirection = InputDirection.Max,
                directionChangeSlop = 5f,
            )

        var invocationCount = 0
        val callback = GestureContextUpdateListener { invocationCount++ }
        underTest.addUpdateCallback(callback)
        assertThat(invocationCount).isEqualTo(0)
        underTest.removeUpdateCallback(callback)
        underTest.dragOffset += 1
        assertThat(invocationCount).isEqualTo(0)
    }
}
