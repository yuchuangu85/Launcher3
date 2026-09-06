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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.InputDirection
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.fixedSpatialValueSpec
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposableMotionValueTest : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    @get:Rule(order = 0) val rule = createComposeRule()

    @Test
    fun rememberMotionValue_updatingSpecLambda_isApplied() {

        val spec1 = fixedSpatialValueSpec(1f)
        val spec2 = fixedSpatialValueSpec(2f)

        var specLamda by mutableStateOf({ spec1 })
        val gestureContext = ProvidedGestureContext(0f, InputDirection.Max)

        lateinit var underTest: MotionValue
        rule.setContent {
            underTest = rememberMotionValue(gestureContext::dragOffset, gestureContext, specLamda)
        }

        val inspector = underTest.debugInspector()
        rule.waitForIdle()
        assertThat(inspector.frame.spec).isSameInstanceAs(spec1)

        specLamda = { spec2 }
        rule.waitForIdle()
        assertThat(inspector.frame.spec).isSameInstanceAs(spec2)
    }
}
