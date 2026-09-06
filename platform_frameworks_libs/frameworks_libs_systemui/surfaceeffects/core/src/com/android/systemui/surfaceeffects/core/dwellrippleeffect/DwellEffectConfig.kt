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

package com.android.systemui.surfaceeffects.core.dwellrippleeffect

import android.graphics.Color
import androidx.compose.runtime.Immutable
import androidx.core.animation.Interpolator
import androidx.core.animation.LinearInterpolator

/**
 * Captures static parameters for the Dwell Effect that are known during initialization.
 *
 * **Note:** This class is **not** meant for animatable parameters. Do not recreate this config
 * repeatedly to drive animations (e.g., changing [color] frame-by-frame).
 *
 * @property centerX The static horizontal center of the effect.
 * @property centerY The static vertical center of the effect.
 * @property maxRadius The maximum radius the effect should expand to.
 * @property color The base color of the effect.
 */
@Immutable
data class DwellEffectConfig(
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val maxRadius: Float = 0f,
    val color: Int = Color.WHITE,
    val expandingAnimationConfig: AnimationConfig = AnimationConfig(),
    val retractingAnimationConfig: AnimationConfig = AnimationConfig(),
    internal val distortionStrength: Float = DEFAULT_DISTORTION_STRENGTH,
) {
    companion object {
        const val DEFAULT_DISTORTION_STRENGTH = 0.4F
    }
}

data class AnimationConfig(
    val duration: Float = DEFAULT_DWELL_DURATION,
    val interpolator: Interpolator = LinearInterpolator(),
) {
    companion object {
        const val DEFAULT_DWELL_DURATION = 1000F
    }
}
