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

package com.android.mechanics.spring

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpringParameterTest {

    @Test
    fun lerp_interpolatesDampingLinearly() {
        val start = SpringParameters(stiffness = 100f, dampingRatio = 1.5f)
        val stop = SpringParameters(stiffness = 100f, dampingRatio = 0.5f)

        assertThat(lerp(start, stop, 0f).dampingRatio).isEqualTo(1.5f)
        assertThat(lerp(start, stop, .25f).dampingRatio).isEqualTo(1.25f)
        assertThat(lerp(start, stop, .5f).dampingRatio).isEqualTo(1f)
        assertThat(lerp(start, stop, 1f).dampingRatio).isEqualTo(.5f)
    }

    @Test
    fun lerp_interpolatesStiffnessLogarithmically() {
        val start = SpringParameters(stiffness = 100f, dampingRatio = 1f)
        val stop = SpringParameters(stiffness = 500_000f, dampingRatio = 1f)

        assertThat(lerp(start, stop, 0f).stiffness).isEqualTo(100f)
        assertThat(lerp(start, stop, .25f).stiffness).isWithin(1f).of(840f)
        assertThat(lerp(start, stop, .5f).stiffness).isWithin(1f).of(7_071f)
        assertThat(lerp(start, stop, .75f).stiffness).isWithin(1f).of(59_460f)
        assertThat(lerp(start, stop, 1f).stiffness).isEqualTo(500_000f)
    }

    @Test
    fun lerp_limitsFraction() {
        val start = SpringParameters(stiffness = 100f, dampingRatio = 0.5f)
        val stop = SpringParameters(stiffness = 1000f, dampingRatio = 1.5f)

        assertThat(lerp(start, stop, -1f)).isEqualTo(start)
        assertThat(lerp(start, stop, +2f)).isEqualTo(stop)
    }
}
