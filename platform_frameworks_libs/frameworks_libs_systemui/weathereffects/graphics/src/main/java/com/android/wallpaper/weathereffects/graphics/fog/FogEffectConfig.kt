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

package com.android.wallpaper.weathereffects.graphics.fog

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.annotation.FloatRange
import com.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils

/** Configuration for a fog effect. */
data class FogEffectConfig(
    /** The main shader of the effect. */
    val shader: RuntimeShader,
    /** The color grading shader. */
    val colorGradingShader: RuntimeShader,
    /** The main lut (color grading) for the effect. */
    val lut: Bitmap?,
    /**
     * The clouds texture, which will be placed in front of the foreground. The texture is expected
     * to be tileable, and at least 16-bit per channel for render quality.
     */
    val cloudsTexture: Bitmap,
    /**
     * The fog texture. This will be placed behind the foreground. The texture is expected to be
     * tileable, and at least 16-bit per channel for render quality.
     */
    val fogTexture: Bitmap,
    /** Pixel density of the display. Used for dithering. */
    val pixelDensity: Float,
    /** The intensity of the color grading. 0: no color grading, 1: color grading in full effect. */
    @FloatRange(from = 0.0, to = 1.0) val colorGradingIntensity: Float,
) {
    /**
     * Constructor for [FogEffectConfig].
     *
     * @param assets the application [AssetManager].
     * @param pixelDensity pixel density of the display.
     */
    constructor(
        assets: AssetManager,
        pixelDensity: Float,
    ) : this(
        shader = GraphicsUtils.loadShader(assets, SHADER_PATH),
        colorGradingShader = GraphicsUtils.loadShader(assets, COLOR_GRADING_SHADER_PATH),
        lut = GraphicsUtils.loadTexture(assets, LOOKUP_TABLE_TEXTURE_PATH),
        cloudsTexture =
            GraphicsUtils.loadTexture(assets, CLOUDS_TEXTURE_PATH)
                ?: throw RuntimeException("Clouds texture is missing."),
        fogTexture =
            GraphicsUtils.loadTexture(assets, FOG_TEXTURE_PATH)
                ?: throw RuntimeException("Fog texture is missing."),
        pixelDensity,
        COLOR_GRADING_INTENSITY,
    )

    private companion object {
        private const val SHADER_PATH = "shaders/fog_effect.agsl"
        private const val COLOR_GRADING_SHADER_PATH = "shaders/color_grading_lut.agsl"
        private const val LOOKUP_TABLE_TEXTURE_PATH = "textures/fog_lut.png"
        private const val CLOUDS_TEXTURE_PATH = "textures/clouds.png"
        private const val FOG_TEXTURE_PATH = "textures/fog.png"
        private const val COLOR_GRADING_INTENSITY = 0.3f
    }
}
