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

package com.android.mechanics.testing

import androidx.compose.ui.unit.Density
import com.android.mechanics.spec.builder.MaterialSprings
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spring.SpringParameters

/**
 * [MotionBuilderContext] implementation for unit tests.
 *
 * Only use when the specifics of the spring parameters do not matter for the test.
 *
 * While the values are copied from the current material motion tokens, this can (and likely will)
 * get out of sync with the material tokens, and is not intended reflect the up-to-date tokens, but
 * provide a stable definitions of "some" spring parameters.
 */
class FakeMotionSpecBuilderContext(density: Float = 1f) :
    MotionBuilderContext, Density by Density(density) {
    override val spatial =
        MaterialSprings(
            SpringParameters(700.0f, 0.9f),
            SpringParameters(1400.0f, 0.9f),
            SpringParameters(300.0f, 0.9f),
            MotionBuilderContext.StableThresholdSpatial,
        )

    override val effects =
        MaterialSprings(
            SpringParameters(1600.0f, 1.0f),
            SpringParameters(3800.0f, 1.0f),
            SpringParameters(800.0f, 1.0f),
            MotionBuilderContext.StableThresholdEffects,
        )

    companion object {
        val Default = FakeMotionSpecBuilderContext()
    }
}
