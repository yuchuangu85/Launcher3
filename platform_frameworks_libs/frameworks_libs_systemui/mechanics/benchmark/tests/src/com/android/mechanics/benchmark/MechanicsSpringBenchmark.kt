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

package com.android.mechanics.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spring.SpringParameters
import com.android.mechanics.spring.SpringState
import com.android.mechanics.spring.calculateUpdatedState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MechanicsSpringBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test
    fun calculateUpdatedState_atRest() {
        val initialState = SpringState(0f, 0f)

        benchmarkRule.measureRepeated {
            initialState.calculateUpdatedState(FrameDuration, CriticallyDamped)
        }
    }

    @Test
    fun calculateUpdatedState_underDamped() {
        val initialState = SpringState(10f, -1f)

        benchmarkRule.measureRepeated {
            initialState.calculateUpdatedState(FrameDuration, UnderDamped)
        }
    }

    @Test
    fun calculateUpdatedState_criticallyDamped() {
        val initialState = SpringState(10f, -1f)

        benchmarkRule.measureRepeated {
            initialState.calculateUpdatedState(FrameDuration, CriticallyDamped)
        }
    }

    @Test
    fun calculateUpdatedState_overDamped() {
        val initialState = SpringState(10f, -1f)

        benchmarkRule.measureRepeated {
            initialState.calculateUpdatedState(FrameDuration, OverDamped)
        }
    }

    @Test
    fun isStable() {
        val initialState = SpringState(10f, -1f)

        benchmarkRule.measureRepeated { initialState.isStable(CriticallyDamped, 0.1f) }
    }

    companion object {
        val FrameDuration = 16_000_000L
        val UnderDamped = SpringParameters(stiffness = 100f, dampingRatio = 0.5f)
        val CriticallyDamped = SpringParameters(stiffness = 100f, dampingRatio = 1f)
        val OverDamped = SpringParameters(stiffness = 100f, dampingRatio = 2f)
    }
}
