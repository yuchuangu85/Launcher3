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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.runMonotonicClockTest

@RunWith(AndroidJUnit4::class)
class ComposeStateTest {
    @Test
    fun mutableState_sendApplyNotifications() = runMonotonicClockTest {
        val mutableState = mutableStateOf(0f)

        var lastRead = -1f
        snapshotFlow { mutableState.value }.onEach { lastRead = it }.launchIn(backgroundScope)
        check(lastRead == -1f) { "[1] lastRead $lastRead, snapshotFlow launchIn" }

        // snapshotFlow will emit the first value (0f).
        testScheduler.advanceTimeBy(1)
        check(lastRead == 0f) { "[2] lastRead $lastRead, first advanceTimeBy()" }

        // update composeState x5.
        repeat(5) {
            mutableState.value++
            check(lastRead == 0f) { "[3 loop] lastRead $lastRead, composeState.floatValue++" }

            testScheduler.advanceTimeBy(1)
            check(lastRead == 0f) { "[4 loop] lastRead $lastRead, advanceTimeBy()" }
        }

        // Try to wait with a delay. It does nothing (lastRead == 0f).
        delay(1)
        check(mutableState.value == 5f) { "[5] mutableState ${mutableState.value}, after loop" }
        check(lastRead == 0f) { "[5] lastRead $lastRead, after loop" }

        // This should trigger the flow.
        Snapshot.sendApplyNotifications()
        check(lastRead == 0f) { "[6] lastRead $lastRead, Snapshot.sendApplyNotifications()" }

        // lastRead will be updated (5f) after advanceTimeBy (or a delay).
        testScheduler.advanceTimeBy(1)
        check(lastRead == 5f) { "[7] lastRead $lastRead, advanceTimeBy" }
    }

    @Test
    fun derivedState_readNotRequireASendApplyNotifications() = runMonotonicClockTest {
        val mutableState = mutableStateOf(0f)

        var derivedRuns = 0
        val derived = derivedStateOf {
            derivedRuns++
            mutableState.value * 2f
        }
        check(derivedRuns == 0) { "[1] derivedRuns: $derivedRuns, should be 0" }

        var lastRead = -1f
        snapshotFlow { derived.value }.onEach { lastRead = it }.launchIn(backgroundScope)
        check(lastRead == -1f) { "[2] lastRead $lastRead, snapshotFlow launchIn" }
        check(derivedRuns == 0) { "[2] derivedRuns: $derivedRuns, should be 0" }

        // snapshotFlow will emit the first value (0f * 2f = 0f).
        testScheduler.advanceTimeBy(16)
        check(lastRead == 0f) { "[3] lastRead $lastRead, first advanceTimeBy()" }
        check(derivedRuns == 1) { "[3] derivedRuns: $derivedRuns, should be 1" }

        // update composeState x5.
        repeat(5) {
            mutableState.value++
            check(lastRead == 0f) { "[4 loop] lastRead $lastRead, composeState.floatValue++" }

            testScheduler.advanceTimeBy(16)
            check(lastRead == 0f) { "[5 loop] lastRead $lastRead, advanceTimeBy()" }
        }

        // Try to wait with a delay. It does nothing (lastRead == 0f).
        delay(1)
        check(mutableState.value == 5f) { "[6] mutableState ${mutableState.value}, after loop" }
        check(lastRead == 0f) { "[6] lastRead $lastRead, after loop" }
        check(derivedRuns == 1) { "[6] derivedRuns $derivedRuns, after loop" }

        // Reading a derived state, this will trigger the flow.
        // NOTE: We are not using Snapshot.sendApplyNotifications()
        derived.value
        check(lastRead == 0f) { "[7] lastRead $lastRead, read derivedDouble" }
        check(derivedRuns == 2) { "[7] derivedRuns $derivedRuns, read derived" } // Triggered

        // lastRead will be updated (5f * 2f = 10f) after advanceTimeBy (or a delay)
        testScheduler.advanceTimeBy(16)
        check(lastRead == 5f * 2f) { "[8] lastRead $lastRead, advanceTimeBy" } // New value
        check(derivedRuns == 2) { "[8] derivedRuns $derivedRuns, read derived" }
    }

    @Test
    fun derivedState_readADerivedStateTriggerOthersDerivedState() = runMonotonicClockTest {
        val mutableState = mutableStateOf(0f)

        var derivedRuns = 0
        val derived = derivedStateOf {
            derivedRuns++
            mutableState.value
        }

        var otherRuns = 0
        repeat(100) {
            val otherState = derivedStateOf {
                otherRuns++
                mutableState.value
            }
            // Observer all otherStates.
            snapshotFlow { otherState.value }.launchIn(backgroundScope)
        }
        check(derivedRuns == 0) { "[1] derivedRuns: $derivedRuns" }
        check(otherRuns == 0) { "[1] otherRuns: $otherRuns" }

        // Wait for snapshotFlow.
        testScheduler.advanceTimeBy(16)
        check(derivedRuns == 0) { "[2] derivedRuns: $derivedRuns" }
        check(otherRuns == 100) { "[2] otherRuns: $otherRuns" }

        // This write might trigger all otherStates observed, but it does not.
        mutableState.value++
        check(derivedRuns == 0) { "[3] derivedRuns: $derivedRuns" }
        check(otherRuns == 100) { "[3] otherRuns: $otherRuns" }

        // Wait for several frames, but still doesn't trigger otherStates.
        repeat(10) { testScheduler.advanceTimeBy(16) }
        check(derivedRuns == 0) { "[4] derivedRuns: $derivedRuns" }
        check(otherRuns == 100) { "[4] otherRuns: $otherRuns" }

        // Reading derived state will trigger all otherStates.
        // This behavior is causing us some problems, because reading a derived state causes all
        // the
        // dirty derived states to be reread, and this can happen multiple times per frame,
        // making
        // derived states much more expensive than one might expect.
        derived.value
        check(derivedRuns == 1) { "[5] derivedRuns: $derivedRuns" }
        check(otherRuns == 100) { "[5] otherRuns: $otherRuns" }

        // Now we pay the cost of those derived states.
        testScheduler.advanceTimeBy(1)
        check(derivedRuns == 1) { "[6] derivedRuns: $derivedRuns" }
        check(otherRuns == 200) { "[6] otherRuns: $otherRuns" }
    }
}
