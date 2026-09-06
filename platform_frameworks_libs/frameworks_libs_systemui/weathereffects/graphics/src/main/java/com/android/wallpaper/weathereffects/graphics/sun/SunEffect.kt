/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wallpaper.weathereffects.graphics.sun

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.SizeF
import com.android.wallpaper.weathereffects.graphics.WeatherEffect
import com.android.wallpaper.weathereffects.graphics.WeatherEffectBase
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.centerCropMatrix
import com.android.wallpaper.weathereffects.graphics.utils.TimeUtils

/** Defines and generates the sunny weather animation. */
class SunEffect(
    /** The config of the sunny effect. */
    private val sunConfig: SunEffectConfig,
    foreground: Bitmap,
    background: Bitmap,
    intensity: Float = WeatherEffect.DEFAULT_INTENSITY,
    /** The initial size of the surface where the effect will be shown. */
    initialSurfaceSize: SizeF,
    initialMatrix: Matrix =
        centerCropMatrix(
            initialSurfaceSize,
            SizeF(background.width.toFloat(), background.height.toFloat()),
        ),
    isPanAndZoomInExtendedWallpaperEffectsEnabled: Boolean,
) :
    WeatherEffectBase(
        foreground = foreground,
        background = background,
        surfaceSize = initialSurfaceSize,
        initialCropMatrix = initialMatrix,
        isPanAndZoomInExtendedWallpaperEffectsEnabled =
            isPanAndZoomInExtendedWallpaperEffectsEnabled,
    ) {

    private val sunnyPaint = Paint().also { it.shader = sunConfig.colorGradingShader }

    init {
        updateTextureUniforms()
        adjustCropping()
        prepareColorGrading()
        setIntensity(intensity)
    }

    override val shader: RuntimeShader
        get() = sunConfig.shader

    override val colorGradingShader: RuntimeShader
        get() = sunConfig.colorGradingShader

    override val lut: Bitmap?
        get() = sunConfig.lut

    override val colorGradingIntensity: Float
        get() = sunConfig.colorGradingIntensity

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += TimeUtils.millisToSeconds(deltaMillis)
        sunConfig.shader.setFloatUniform("time", elapsedTime)
        sunConfig.colorGradingShader.setInputShader("texture", sunConfig.shader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(sunnyPaint)
    }

    private fun prepareColorGrading() {
        sunConfig.colorGradingShader.setInputShader("texture", sunConfig.shader)
        sunConfig.lut?.let {
            sunConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
            )
        }
        sunConfig.colorGradingShader.setFloatUniform("intensity", sunConfig.colorGradingIntensity)
    }
}
