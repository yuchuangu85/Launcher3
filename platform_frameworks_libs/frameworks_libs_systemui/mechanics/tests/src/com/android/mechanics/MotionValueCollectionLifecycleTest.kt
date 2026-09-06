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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.MotionValueTest.Companion.FakeGestureContext
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.directionalMotionSpec
import com.android.mechanics.spec.builder.fixedSpatialValueSpec
import com.android.mechanics.spec.builder.spatialDirectionalMotionSpec
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionValueCollectionLifecycleTest :
    MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    @get:Rule(order = 0) val rule = createComposeRule()

    @Test
    fun keepRunning_empty_doesNotWakeup() = runTest {
        val input = mutableFloatStateOf(0f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)
        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        rule.awaitIdle()
        val framesCount = underTest.frameCount
        rule.mainClock.autoAdvance = false

        assertThat(underTest.isActive).isTrue()
        assertThat(underTest.isAnimating).isFalse()

        // Update the value, but WITHOUT causing an animation
        input.floatValue = 0.5f
        rule.awaitIdle()

        assertThat(framesCount).isEqualTo(underTest.frameCount)
        assertThat(underTest.isAnimating).isFalse()

        rule.mainClock.advanceTimeByFrame()
        rule.awaitIdle()

        assertThat(framesCount).isEqualTo(underTest.frameCount)
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun create_withoutKeepRunning_remainsInactive() = runTest {
        val input = mutableFloatStateOf(1f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)

        rule.setContent {}

        assertThat(underTest.isActive).isFalse()

        val motionValue = underTest.create({ MotionSpec.Identity })
        assertThat(motionValue.output).isNaN()
        val inspector = motionValue.debugInspector()
        assertThat(inspector.isActive).isFalse()
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun create_whileKeepRunning_isActivatedImmediately() = runTest {
        val input = mutableFloatStateOf(1f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)

        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }
        rule.awaitIdle()

        assertThat(underTest.isActive).isTrue()
        assertThat(underTest.managedMotionValues.size).isEqualTo(0)

        val motionValue = underTest.create({ MotionSpec.Identity })
        assertThat(motionValue.output).isEqualTo(1f)
        val inspector = motionValue.debugInspector()
        assertThat(inspector.isActive).isTrue()
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun keepRunning_activatesAlreadyCreated() = runTest {
        val input = mutableFloatStateOf(0f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)

        val motionValue = underTest.create({ MotionSpec.Identity })
        val inspector = motionValue.debugInspector()

        assertThat(underTest.frameCount).isEqualTo(0)
        assertThat(underTest.isActive).isFalse()
        assertThat(underTest.isAnimating).isFalse()
        assertThat(underTest.managedMotionValues.size).isEqualTo(1)
        assertThat(inspector.isActive).isFalse()
        assertThat(inspector.isAnimating).isFalse()
        assertThat(motionValue.output).isNaN()

        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        rule.awaitIdle()

        assertThat(underTest.frameCount).isEqualTo(1)
        assertThat(underTest.isActive).isTrue()
        assertThat(underTest.isAnimating).isFalse()
        assertThat(underTest.managedMotionValues.size).isEqualTo(1)
        assertThat(inspector.isActive).isTrue()
        assertThat(inspector.isAnimating).isFalse()
        assertThat(motionValue.output).isFinite()
    }

    @Test
    fun keepRunning_deavtivatesOnDispose() = runTest {
        val input = mutableFloatStateOf(0f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)

        val motionValue = underTest.create({ MotionSpec.Identity })
        val inspector = motionValue.debugInspector()

        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        rule.awaitIdle()

        assertThat(underTest.frameCount).isEqualTo(1)
        assertThat(underTest.isActive).isTrue()
        assertThat(underTest.isAnimating).isFalse()
        assertThat(underTest.managedMotionValues.size).isEqualTo(1)
        assertThat(inspector.isActive).isTrue()
        assertThat(inspector.isAnimating).isFalse()

        motionValue.dispose()
        rule.awaitIdle()

        assertThat(underTest.frameCount).isEqualTo(2)
        assertThat(underTest.isActive).isTrue()
        assertThat(underTest.isAnimating).isFalse()
        assertThat(underTest.managedMotionValues.size).isEqualTo(0)
        assertThat(inspector.isActive).isFalse()
        assertThat(inspector.isAnimating).isFalse()
    }

    @Test
    fun createAndDispose_withoutKeepRunning_isInactive() = runTest {
        val input = mutableFloatStateOf(0f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)

        rule.setContent {}
        assertThat(underTest.isActive).isFalse()

        val motionValue = underTest.create({ MotionSpec.Identity })
        val inspector = motionValue.debugInspector()
        rule.awaitIdle()

        assertThat(underTest.isActive).isFalse()
        assertThat(inspector.isActive).isFalse()
        assertThat(underTest.managedMotionValues.size).isEqualTo(1)

        motionValue.dispose()
        rule.awaitIdle()

        assertThat(underTest.isActive).isFalse()
        assertThat(inspector.isActive).isFalse()
        assertThat(underTest.managedMotionValues.size).isEqualTo(0)
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun keepRunning_withMultipleValues() = runTest {
        val input = mutableFloatStateOf(0f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)

        val mv1 = underTest.create({ MotionSpec.Identity })
        val inspector1 = mv1.debugInspector()
        val mv2 = underTest.create({ MotionSpec.Identity })
        val inspector2 = mv2.debugInspector()

        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }
        rule.awaitIdle()

        assertThat(underTest.isActive).isTrue()
        assertThat(underTest.managedMotionValues.size).isEqualTo(2)
        assertThat(inspector1.isActive).isTrue()
        assertThat(inspector2.isActive).isTrue()

        mv1.dispose()
        rule.awaitIdle()

        assertThat(underTest.managedMotionValues.size).isEqualTo(1)
        assertThat(inspector1.isActive).isFalse()
        assertThat(inspector2.isActive).isTrue()

        mv2.dispose()
        rule.awaitIdle()

        assertThat(underTest.managedMotionValues.size).isEqualTo(0)
        assertThat(inspector1.isActive).isFalse()
        assertThat(inspector2.isActive).isFalse()
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun keepRunning_cancelled_deactivates() = runTest {
        val input = mutableFloatStateOf(0f)
        val underTest = MotionValueCollection(input::value, FakeGestureContext)
        val inspector = underTest.create({ MotionSpec.Identity }).debugInspector()
        val keepRunning = mutableStateOf(true)

        rule.setContent {
            if (keepRunning.value) {
                LaunchedEffect(Unit) { underTest.keepRunning() }
            }
        }
        rule.awaitIdle()

        assertThat(underTest.isActive).isTrue()
        assertThat(inspector.isActive).isTrue()
        assertThat(underTest.managedMotionValues.size).isEqualTo(1)

        keepRunning.value = false
        rule.awaitIdle()

        assertThat(underTest.isActive).isFalse()
        assertThat(inspector.isActive).isFalse()
        assertThat(underTest.managedMotionValues.size).isEqualTo(1)
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun latchesInput_changesAreProcessedOnFrameStartOnly() = runTest {
        val input = mutableFloatStateOf(0f)

        val underTest = MotionValueCollection(input::value, FakeGestureContext)
        val motionValue = underTest.create({ MotionSpec.Identity })

        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        rule.awaitIdle()

        rule.mainClock.autoAdvance = false

        assertThat(motionValue.output).isEqualTo(0f)
        input.floatValue = 1f
        assertThat(motionValue.output).isEqualTo(0f)

        rule.mainClock.advanceTimeByFrame()
        rule.awaitIdle()
        assertThat(motionValue.output).isEqualTo(1f)

        rule.mainClock.autoAdvance = true
        rule.awaitIdle()
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun latchesGestureContext_changesAreProcessedOnFrameStartOnly() = runTest {
        val gestureContext = ProvidedGestureContext(0f, InputDirection.Max)
        val spec =
            MotionSpec(
                maxDirection = directionalMotionSpec(Mapping.Zero),
                minDirection = directionalMotionSpec(Mapping.One),
            )

        val underTest = MotionValueCollection({ 0f }, gestureContext)
        val motionValue = underTest.create({ spec })

        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        rule.awaitIdle()

        rule.mainClock.autoAdvance = false

        assertThat(motionValue.output).isEqualTo(0f)
        assertThat(motionValue.outputTarget).isEqualTo(0f)

        gestureContext.direction = InputDirection.Min
        assertThat(motionValue.output).isEqualTo(0f)
        assertThat(motionValue.outputTarget).isEqualTo(0f)

        rule.mainClock.advanceTimeByFrame()
        rule.awaitIdle()
        assertThat(motionValue.output).isGreaterThan(0f)
        assertThat(motionValue.outputTarget).isEqualTo(1f)

        rule.mainClock.advanceTimeUntil { motionValue.isStable }
        rule.awaitIdle()
        assertThat(motionValue.output).isEqualTo(1f)
        assertThat(motionValue.outputTarget).isEqualTo(1f)

        rule.mainClock.autoAdvance = true
        rule.awaitIdle()
        assertThat(underTest.isAnimating).isFalse()
    }

    @Test
    fun latchesSpec_changesAreProcessedOnFrameStartOnly() = runTest {
        val spec = mutableStateOf(fixedSpatialValueSpec(0f))

        val underTest = MotionValueCollection({ 0f }, FakeGestureContext)
        val motionValue = underTest.create(spec::value)

        rule.setContent { LaunchedEffect(Unit) { underTest.keepRunning() } }

        rule.awaitIdle()

        rule.mainClock.autoAdvance = false

        assertThat(motionValue.output).isEqualTo(0f)
        assertThat(motionValue.outputTarget).isEqualTo(0f)

        // Create a new spec: Reusing fixedSpatialValueSpec() creates identical
        // min/max limits, which MotionValue interprets as the sliding segment.
        spec.value = MotionSpec(spatialDirectionalMotionSpec { fixedValue(-1f, value = 1f) })
        assertThat(motionValue.output).isEqualTo(0f)
        assertThat(motionValue.outputTarget).isEqualTo(0f)

        rule.mainClock.advanceTimeByFrame()
        rule.awaitIdle()
        assertThat(motionValue.output).isGreaterThan(0f)
        assertThat(motionValue.outputTarget).isEqualTo(1f)

        rule.mainClock.advanceTimeUntil { motionValue.isStable }
        rule.awaitIdle()
        assertThat(motionValue.output).isEqualTo(1f)
        assertThat(motionValue.outputTarget).isEqualTo(1f)

        rule.mainClock.autoAdvance = true
        rule.awaitIdle()
        assertThat(underTest.isAnimating).isFalse()
    }
}
