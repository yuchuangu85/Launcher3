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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.mechanics.view

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.rememberMotionBuilderContext
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewMotionBuilderContextTest {

    @get:Rule(order = 0) val rule = createComposeRule()

    @Test
    fun materialSprings_standardScheme_matchesComposeDefinition() {
        lateinit var viewContext: Context
        lateinit var composeReference: MotionBuilderContext

        rule.setContent {
            viewContext = LocalContext.current
            MaterialTheme(motionScheme = MotionScheme.standard()) {
                composeReference = rememberMotionBuilderContext()
            }
        }

        val underTest = standardViewMotionBuilderContext(viewContext)

        Truth.assertThat(underTest.density).isEqualTo(composeReference.density)
        Truth.assertThat(underTest.spatial).isEqualTo(composeReference.spatial)
        Truth.assertThat(underTest.effects).isEqualTo(composeReference.effects)
    }

    @Test
    fun materialSprings_expressiveScheme_matchesComposeDefinition() {
        lateinit var viewContext: Context
        lateinit var composeReference: MotionBuilderContext

        rule.setContent {
            viewContext = LocalContext.current
            MaterialTheme(motionScheme = MotionScheme.expressive()) {
                composeReference = rememberMotionBuilderContext()
            }
        }

        val underTest = expressiveViewMotionBuilderContext(viewContext)

        Truth.assertThat(underTest.density).isEqualTo(composeReference.density)
        Truth.assertThat(underTest.spatial).isEqualTo(composeReference.spatial)
        Truth.assertThat(underTest.effects).isEqualTo(composeReference.effects)
    }
}
