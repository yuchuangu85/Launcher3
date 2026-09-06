/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wallpaper.weathereffects.graphics.snow

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.annotation.FloatRange
import com.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils

/** Configuration for a snow effect. */
data class SnowEffectConfig(
    /** The main shader of the effect. */
    val shader: RuntimeShader,
    /** The shader of foreground outline, which will be used in accumulatedSnowResultShader. */
    val accumulatedSnowOutlineShader: RuntimeShader,
    /** The color grading shader. */
    val colorGradingShader: RuntimeShader,
    /** The shader of accumulated snow with fluffy effects. */
    val accumulatedSnowResultShader: RuntimeShader,
    /** The shader of generate snow flake patterns. */
    val snowFlakeSamples: RuntimeShader,
    /**
     * The noise texture, which will be used to add fluffiness to the snow flakes. The texture is
     * expected to be tileable, and at least 16-bit per channel for render quality.
     */
    val noiseTexture: Bitmap,
    /** The main lut (color grading) for the effect. */
    val lut: Bitmap?,
    /** Pixel density of the display. Used for dithering. */
    val pixelDensity: Float,
    /** The intensity of the color grading. 0: no color grading, 1: color grading in full effect. */
    @FloatRange(from = 0.0, to = 1.0) val colorGradingIntensity: Float,
    /** Max thickness for the accumulated snow. */
    val maxAccumulatedSnowThickness: Float,
) {
    /**
     * Constructor for [SnowEffectConfig].
     *
     * @param assets asset manager,
     * @param pixelDensity pixel density of the display. Expected range is [0, 1]. You can always
     *   change the intensity dynamically. Defaults to 1.
     */
    constructor(
        assets: AssetManager,
        pixelDensity: Float,
    ) : this(
        shader = GraphicsUtils.loadShader(assets, SHADER_PATH),
        accumulatedSnowOutlineShader =
            GraphicsUtils.loadShader(assets, ACCUMULATED_SNOW_OUTLINE_SHADER_PATH),
        colorGradingShader = GraphicsUtils.loadShader(assets, COLOR_GRADING_SHADER_PATH),
        accumulatedSnowResultShader =
            GraphicsUtils.loadShader(assets, ACCUMULATED_SNOW_RESULT_SHADER_PATH),
        snowFlakeSamples = GraphicsUtils.loadShader(assets, SNOW_FLAKE_SPRITE_SHEET_PATH),
        noiseTexture =
            GraphicsUtils.loadTexture(assets, NOISE_TEXTURE_PATH)
                ?: throw RuntimeException("Noise texture is missing."),
        lut = GraphicsUtils.loadTexture(assets, LOOKUP_TABLE_TEXTURE_PATH),
        pixelDensity,
        COLOR_GRADING_INTENSITY,
        MAX_SNOW_THICKNESS,
    )

    companion object {
        private const val SHADER_PATH = "shaders/snow_effect.agsl"
        private const val ACCUMULATED_SNOW_OUTLINE_SHADER_PATH =
            "shaders/snow_accumulation_outline.agsl"
        private const val ACCUMULATED_SNOW_RESULT_SHADER_PATH =
            "shaders/snow_accumulation_result.agsl"
        private const val COLOR_GRADING_SHADER_PATH = "shaders/color_grading_lut.agsl"
        private const val SNOW_FLAKE_SPRITE_SHEET_PATH = "shaders/snow_flake_samples.agsl"
        private const val NOISE_TEXTURE_PATH = "textures/clouds.png"
        private const val LOOKUP_TABLE_TEXTURE_PATH = "textures/snow_lut.png"
        private const val COLOR_GRADING_INTENSITY = 0.25f
        const val MAX_SNOW_THICKNESS = 10f
    }
}
