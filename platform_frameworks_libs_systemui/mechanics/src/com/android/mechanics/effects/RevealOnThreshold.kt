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

package com.android.mechanics.effects

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import com.android.mechanics.spec.BreakpointKey
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.builder.Effect
import com.android.mechanics.spec.builder.EffectApplyScope
import com.android.mechanics.spec.builder.EffectPlacement

/** An effect that reveals a component when the available space reaches a certain threshold. */
data class RevealOnThreshold(val minSize: Dp = Defaults.MinSize) : Effect.PlaceableBetween {
    init {
        require(minSize >= 0.dp)
    }

    override fun EffectApplyScope.createSpec(
        minLimit: Float,
        minLimitKey: BreakpointKey,
        maxLimit: Float,
        maxLimitKey: BreakpointKey,
        placement: EffectPlacement,
    ) {
        val maxSize = maxLimit - minLimit
        val minSize = minSize.toPx().fastCoerceAtMost(maxSize)

        unidirectional(initialMapping = Mapping.Zero) {
            before(mapping = Mapping.Zero)

            target(breakpoint = minLimit + minSize, from = minSize, to = maxSize)

            after(mapping = Mapping.Fixed(maxSize))
        }
    }

    object Defaults {
        val MinSize: Dp = 8.dp
    }
}
