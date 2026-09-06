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

package com.android.wallpaper.weathereffects.graphics.rain

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.SizeF
import com.android.wallpaper.weathereffects.graphics.FrameBuffer
import com.android.wallpaper.weathereffects.graphics.WeatherEffect.Companion.DEFAULT_INTENSITY
import com.android.wallpaper.weathereffects.graphics.WeatherEffectBase
import com.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.centerCropMatrix
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.getScale
import com.android.wallpaper.weathereffects.graphics.utils.TimeUtils
import java.util.concurrent.Executor

/** Defines and generates the rain weather effect animation. */
class RainEffect(
    /** The config of the rain effect. */
    private val rainConfig: RainEffectConfig,
    foreground: Bitmap,
    background: Bitmap,
    intensity: Float = DEFAULT_INTENSITY,
    /** The initial size of the surface where the effect will be shown. */
    initialSurfaceSize: SizeF,
    private val mainExecutor: Executor,
    initialMatrix: Matrix =
        centerCropMatrix(
            initialSurfaceSize,
            SizeF(background.width.toFloat(), background.height.toFloat()),
        ),
    isPanAndZoomInExtendedWallpaperEffectsEnabled: Boolean,
) :
    WeatherEffectBase(
        foreground,
        background,
        initialSurfaceSize,
        initialMatrix,
        isPanAndZoomInExtendedWallpaperEffectsEnabled,
    ) {

    private val rainPaint = Paint().also { it.shader = rainConfig.colorGradingShader }
    // Outline buffer is set with bitmap size, so we need to multiply blur radius by scale to get
    // consistent blur across different surface
    private var outlineBuffer =
        FrameBuffer(background.width, background.height).apply {
            setRenderEffect(
                RenderEffect.createBlurEffect(
                    BLUR_RADIUS / bitmapScale,
                    BLUR_RADIUS / bitmapScale,
                    Shader.TileMode.CLAMP,
                )
            )
        }
    private val outlineBufferPaint = Paint().also { it.shader = rainConfig.outlineShader }

    init {
        updateTextureUniforms()
        adjustCropping()
        prepareColorGrading()
        updateGridSize(initialSurfaceSize)
        setIntensity(intensity)
    }

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += TimeUtils.millisToSeconds(deltaMillis)

        rainConfig.rainShowerShader.setFloatUniform("time", elapsedTime)
        rainConfig.colorGradingShader.setInputShader("texture", rainConfig.rainShowerShader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(rainPaint)
    }

    override fun release() {
        super.release()
        outlineBuffer.close()
    }

    override fun setBitmaps(foreground: Bitmap?, background: Bitmap): Boolean {
        if (!super.setBitmaps(foreground, background)) {
            return false
        }
        outlineBuffer.close()
        outlineBuffer = FrameBuffer(background.width, background.height)

        bitmapScale = getScale(positionMatrix.matrix)
        // Different from snow effects, we only need to change blur radius when bitmaps change
        // it only gives the range of rain splashes and doesn't influence the visual effects
        outlineBuffer.setRenderEffect(
            RenderEffect.createBlurEffect(
                BLUR_RADIUS / bitmapScale,
                BLUR_RADIUS / bitmapScale,
                Shader.TileMode.CLAMP,
            )
        )

        updateTextureUniforms()
        return true
    }

    override val shader: RuntimeShader
        get() = rainConfig.rainShowerShader

    override val colorGradingShader: RuntimeShader
        get() = rainConfig.colorGradingShader

    override val lut: Bitmap?
        get() = rainConfig.lut

    override val colorGradingIntensity: Float
        get() = rainConfig.colorGradingIntensity

    override fun updateTextureUniforms() {
        super.updateTextureUniforms()
        rainConfig.outlineShader.setInputBuffer(
            "texture",
            BitmapShader(super.foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
        )
        createOutlineBuffer()
    }

    /**
     * It's necessary to create outline buffer only when bitmaps change, only intensity change won't
     * create a new one cause we refer to intensity in each cell when drawing splashes.
     */
    private fun createOutlineBuffer() {
        rainConfig.outlineShader.setFloatUniform(
            "thickness",
            MAX_RAIN_OUTLINE_THICKNESS / bitmapScale,
        )
        val canvas = outlineBuffer.beginDrawing()
        canvas.drawPaint(outlineBufferPaint)
        outlineBuffer.endDrawing()

        outlineBuffer.tryObtainingImage(
            { buffer ->
                rainConfig.rainShowerShader.setInputBuffer(
                    "outlineBuffer",
                    BitmapShader(buffer, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
                )
            },
            mainExecutor,
        )
    }

    private fun prepareColorGrading() {
        // Initialize the buffer with black, so that we don't ever draw garbage buffer.
        rainConfig.colorGradingShader.setInputShader("texture", rainConfig.rainShowerShader)
        rainConfig.lut?.let {
            rainConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
            )
        }
    }

    override fun updateGridSize(newSurfaceSize: SizeF) {
        val widthScreenScale =
            GraphicsUtils.computeDefaultGridSize(newSurfaceSize, rainConfig.pixelDensity)
        rainConfig.rainShowerShader.setFloatUniform("gridScale", widthScreenScale)
    }

    companion object {
        const val MAX_RAIN_OUTLINE_THICKNESS = 11f
        const val BLUR_RADIUS = 2f
    }
}
