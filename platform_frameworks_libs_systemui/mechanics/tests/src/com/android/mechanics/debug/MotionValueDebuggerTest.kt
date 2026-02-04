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

package com.android.mechanics.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.MotionValue
import com.android.mechanics.ProvidedGestureContext
import com.android.mechanics.spec.InputDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionValueDebuggerTest {

    private val input: () -> Float = { 0f }
    private val gestureContext =
        ProvidedGestureContext(dragOffset = 0f, direction = InputDirection.Max)

    @get:Rule(order = 0) val rule = createComposeRule()

    @Test
    fun debugMotionValue_registersMotionValue_whenAddingToComposition() {
        val debuggerState = MotionValueDebuggerState()
        var hasValue by mutableStateOf(false)

        rule.setContent {
            Box(modifier = Modifier.motionValueDebugger(debuggerState)) {
                if (hasValue) {
                    val toDebug = remember { MotionValue(input, gestureContext) }
                    Box(modifier = Modifier.debugMotionValue(toDebug))
                }
            }
        }

        assertThat(debuggerState.observedMotionValues).isEmpty()

        hasValue = true
        rule.waitForIdle()

        assertThat(debuggerState.observedMotionValues).hasSize(1)
    }

    @Test
    fun debugMotionValue_unregistersMotionValue_whenLeavingComposition() {
        val debuggerState = MotionValueDebuggerState()
        var hasValue by mutableStateOf(true)

        rule.setContent {
            Box(modifier = Modifier.motionValueDebugger(debuggerState)) {
                if (hasValue) {
                    val toDebug = remember { MotionValue(input, gestureContext) }
                    Box(modifier = Modifier.debugMotionValue(toDebug))
                }
            }
        }

        assertThat(debuggerState.observedMotionValues).hasSize(1)

        hasValue = false
        rule.waitForIdle()
        assertThat(debuggerState.observedMotionValues).isEmpty()
    }

    @Test
    fun debugMotionValue_noDebugger_isNoOp() {
        rule.setContent {
            val toDebug = remember { MotionValue(input, gestureContext) }
            Box(modifier = Modifier.debugMotionValue(toDebug))
        }
    }
}
