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
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.util.fastForEach
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.runMonotonicClockTest

/** Benchmark, which will execute on an Android device. Previous results: go/mm-microbenchmarks */
@RunWith(AndroidJUnit4::class)
class ComposeBaselineBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    // Compose specific

    @Test
    fun writeState_1snapshotFlow() = runMonotonicClockTest {
        val composeState = mutableFloatStateOf(0f)

        var lastRead = 0f
        snapshotFlow { composeState.floatValue }.onEach { lastRead = it }.launchIn(backgroundScope)

        benchmarkRule.measureRepeated {
            composeState.floatValue++
            Snapshot.sendApplyNotifications()
            testScheduler.advanceTimeBy(16)
        }

        check(lastRead == composeState.floatValue) {
            "snapshotFlow lastRead $lastRead != ${composeState.floatValue} (current composeState)"
        }
    }

    @Test
    fun writeState_100snapshotFlow() = runMonotonicClockTest {
        val composeState = mutableFloatStateOf(0f)

        repeat(100) { snapshotFlow { composeState.floatValue }.launchIn(backgroundScope) }

        benchmarkRule.measureRepeated {
            composeState.floatValue++
            Snapshot.sendApplyNotifications()
            testScheduler.advanceTimeBy(16)
        }
    }

    @Test
    fun readAnimatableValue_100animatables_keepRunning() = runMonotonicClockTest {
        val anim = List(100) { Animatable(0f) }

        benchmarkRule.measureRepeated {
            testScheduler.advanceTimeBy(16)
            anim.fastForEach {
                it.value

                if (!it.isRunning) {
                    launch { it.animateTo(if (it.targetValue != 0f) 0f else 1f) }
                }
            }
        }

        testScheduler.advanceTimeBy(2000)
    }

    @Test
    fun readAnimatableValue_100animatables_restartEveryFrame() = runMonotonicClockTest {
        val animatables = List(100) { Animatable(0f) }

        benchmarkRule.measureRepeated {
            testScheduler.advanceTimeBy(16)
            animatables.fastForEach { animatable ->
                animatable.value
                launch { animatable.animateTo(if (animatable.targetValue != 0f) 0f else 1f) }
            }
        }

        testScheduler.advanceTimeBy(2000)
    }
}
