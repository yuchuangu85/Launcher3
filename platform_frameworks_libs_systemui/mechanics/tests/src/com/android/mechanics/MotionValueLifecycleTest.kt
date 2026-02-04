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

package com.android.mechanics

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.MotionValueTest.Companion.FakeGestureContext
import com.android.mechanics.MotionValueTest.Companion.specBuilder
import com.android.mechanics.spec.Mapping
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionValueLifecycleTest {

    @get:Rule(order = 0) val rule = createComposeRule()

    @Test
    fun keepRunning_suspendsWithoutAnAnimation() = runTest {
        val input = mutableFloatStateOf(0f)
        val spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }
        val underTest = MotionValue(input::value, FakeGestureContext, spec)
        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        val inspector = underTest.debugInspector()
        var framesCount = 0
        backgroundScope.launch { snapshotFlow { inspector.frame }.collect { framesCount++ } }

        rule.awaitIdle()
        framesCount = 0
        rule.mainClock.autoAdvance = false

        assertThat(inspector.isActive).isTrue()
        assertThat(inspector.isAnimating).isFalse()

        // Update the value, but WITHOUT causing an animation
        input.floatValue = 0.5f
        rule.awaitIdle()

        // Still on the old frame..
        assertThat(framesCount).isEqualTo(0)
        // ... [underTest] is now waiting for an animation frame
        assertThat(inspector.isAnimating).isTrue()

        rule.mainClock.advanceTimeByFrame()
        rule.awaitIdle()

        // Produces the frame..
        assertThat(framesCount).isEqualTo(1)
        // ... and is suspended again.
        assertThat(inspector.isAnimating).isTrue()

        rule.mainClock.advanceTimeByFrame()
        rule.awaitIdle()

        // Produces the frame..
        assertThat(framesCount).isEqualTo(2)
        // ... and is suspended again.
        assertThat(inspector.isAnimating).isFalse()

        rule.mainClock.autoAdvance = true
        rule.awaitIdle()
        // Ensure that no more frames are produced
        assertThat(framesCount).isEqualTo(2)
    }

    @Test
    fun keepRunning_remainsActiveWhileAnimating() = runTest {
        val input = mutableFloatStateOf(0f)
        val spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }
        val underTest = MotionValue(input::value, FakeGestureContext, spec)
        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        val inspector = underTest.debugInspector()
        var framesCount = 0
        backgroundScope.launch { snapshotFlow { inspector.frame }.collect { framesCount++ } }

        rule.awaitIdle()
        framesCount = 0
        rule.mainClock.autoAdvance = false

        assertThat(inspector.isActive).isTrue()
        assertThat(inspector.isAnimating).isFalse()

        // Update the value, WITH triggering an animation
        input.floatValue = 1.5f
        rule.awaitIdle()

        // Still on the old frame..
        assertThat(framesCount).isEqualTo(0)
        // ... [underTest] is now waiting for an animation frame
        assertThat(inspector.isAnimating).isTrue()

        // A couple frames should be generated without pausing
        repeat(5) {
            rule.mainClock.advanceTimeByFrame()
            rule.awaitIdle()

            // The spring is still settling...
            assertThat(inspector.frame.isStable).isFalse()
            // ... animation keeps going ...
            assertThat(inspector.isAnimating).isTrue()
            // ... and frames are produces...
            assertThat(framesCount).isEqualTo(it + 1)
        }

        val timeBeforeAutoAdvance = rule.mainClock.currentTime

        // But this will stop as soon as the animation is finished. Skip forward.
        rule.mainClock.autoAdvance = true
        rule.awaitIdle()

        // At which point the spring is stable again...
        assertThat(inspector.frame.isStable).isTrue()
        // ... and animations are suspended again.
        assertThat(inspector.isAnimating).isFalse()

        rule.awaitIdle()

        // Stabilizing the spring during awaitIdle() took 160ms (obtained from looking at reference
        // test runs). That time is expected to be 100% reproducible, given the starting
        // state/configuration of the spring before awaitIdle().
        assertThat(rule.mainClock.currentTime).isEqualTo(timeBeforeAutoAdvance + 160)
    }

    @Test
    fun keepRunningWhile_stopRunningWhileStable_endsImmediately() = runTest {
        val input = mutableFloatStateOf(0f)
        val spec = specBuilder(Mapping.Zero) { fixedValue(breakpoint = 1f, value = 1f) }
        val underTest = MotionValue(input::value, FakeGestureContext, spec)

        val continueRunning = mutableStateOf(true)

        rule.setContent {
            LaunchedEffect(Unit) { underTest.keepRunningWhile { continueRunning.value } }
        }

        val inspector = underTest.debugInspector()

        rule.awaitIdle()

        assertWithMessage("isActive").that(inspector.isActive).isTrue()
        assertWithMessage("isAnimating").that(inspector.isAnimating).isFalse()

        val timeBeforeStopRunning = rule.mainClock.currentTime
        continueRunning.value = false
        rule.awaitIdle()

        assertWithMessage("isActive").that(inspector.isActive).isFalse()
        assertWithMessage("isAnimating").that(inspector.isAnimating).isFalse()
        assertThat(rule.mainClock.currentTime).isEqualTo(timeBeforeStopRunning)
    }
}
