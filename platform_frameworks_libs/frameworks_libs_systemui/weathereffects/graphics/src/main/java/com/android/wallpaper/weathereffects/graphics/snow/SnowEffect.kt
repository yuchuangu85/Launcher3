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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.util.Log
import android.util.SizeF
import androidx.core.graphics.createBitmap
import com.android.wallpaper.weathereffects.graphics.FrameBuffer
import com.android.wallpaper.weathereffects.graphics.WeatherEffect.Companion.DEFAULT_INTENSITY
import com.android.wallpaper.weathereffects.graphics.WeatherEffectBase
import com.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils
import com.android.wallpaper.weathereffects.graphics.utils.MathUtils
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.centerCropMatrix
import com.android.wallpaper.weathereffects.graphics.utils.TimeUtils
import java.util.concurrent.Executor
import kotlin.math.abs

/** Defines and generates the rain weather effect animation. */
class SnowEffect(
    /** The config of the snow effect. */
    private val snowConfig: SnowEffectConfig,
    foreground: Bitmap,
    background: Bitmap,
    private var intensity: Float = DEFAULT_INTENSITY,
    /** The initial size of the surface where the effect will be shown. */
    initialSurfaceSize: SizeF,
    /** App main executor. */
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

    private var snowSpeed: Float = 0.8f
    private val snowPaint = Paint().also { it.shader = snowConfig.colorGradingShader }

    // Use outlineFrameBuffer and outlineFrameBufferPaint to get foreground outline
    // its process requires blur effects
    private var outlineFrameBuffer = FrameBuffer(background.width, background.height)
    private val outlineFrameBufferPaint =
        Paint().also { it.shader = snowConfig.accumulatedSnowOutlineShader }
    // accumulationFrameBuffer and accumulationFrameBufferPaint will get the result from
    // outlineFrameBuffer and add noise to snow fluffiness
    private var accumulationFrameBuffer =
        if (isPanAndZoomInExtendedWallpaperEffectsEnabled) {
            FrameBuffer(background.width, background.height)
        } else {
            FrameBuffer(
                (background.width * bitmapScale).toInt(),
                (background.height * bitmapScale).toInt(),
            )
        }

    private val accumulationFrameBufferPaint =
        Paint().also { it.shader = snowConfig.accumulatedSnowResultShader }

    private val snowFlakeSamplesBuffer: FrameBuffer =
        if (
            HardwareBuffer.isSupported(
                SNOW_FLAKE_SAMPLES_BUFFER_WIDTH,
                SNOW_FLAKE_SAMPLES_BUFFER_HEIGHT,
                HardwareBuffer.RGB_888,
                1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
            )
        ) {
            FrameBuffer(
                SNOW_FLAKE_SAMPLES_BUFFER_WIDTH,
                SNOW_FLAKE_SAMPLES_BUFFER_HEIGHT,
                HardwareBuffer.RGB_888,
            )
        } else {
            FrameBuffer(SNOW_FLAKE_SAMPLES_BUFFER_WIDTH, SNOW_FLAKE_SAMPLES_BUFFER_HEIGHT)
        }

    private val snowFlakeSamplesPaint = Paint().also { it.shader = snowConfig.snowFlakeSamples }

    init {
        outlineFrameBuffer.setRenderEffect(
            RenderEffect.createBlurEffect(
                BLUR_RADIUS / bitmapScale,
                BLUR_RADIUS / bitmapScale,
                Shader.TileMode.CLAMP,
            )
        )

        updateTextureUniforms()
        adjustCropping()
        prepareColorGrading()
        updateGridSize(initialSurfaceSize)
        setIntensity(intensity)

        // Generate accumulated snow at the end after we updated all the uniforms.
        generateAccumulatedSnow()
        generateSnowFlakeSamples()
    }

    override fun adjustCropping() {
        super.adjustCropping()
        if (isPanAndZoomInExtendedWallpaperEffectsEnabled) {
            snowConfig.shader.setFloatUniform(
                "transformMatrixAccumulation",
                transformMatrixPosition,
            )
        } else {
            snowConfig.shader.setFloatUniform(
                "transformMatrixAccumulation",
                transformMatrixParallax,
            )
        }
    }

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += snowSpeed * TimeUtils.millisToSeconds(deltaMillis)
        snowConfig.shader.setFloatUniform("time", elapsedTime)
        snowConfig.colorGradingShader.setInputShader("texture", snowConfig.shader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(snowPaint)
    }

    override fun release() {
        super.release()
        outlineFrameBuffer.close()
        accumulationFrameBuffer.close()
        snowFlakeSamplesBuffer.close()
    }

    override fun setIntensity(intensity: Float) {
        super.setIntensity(intensity)
        /**
         * Increase effect speed as weather intensity decreases. This compensates for the floaty
         * appearance when there are fewer particles at the original speed.
         */
        if (this.intensity != intensity) {
            snowSpeed = MathUtils.map(intensity, 0f, 1f, 2.5f, 1.7f)
            this.intensity = intensity
        }
    }

    override fun setBitmaps(foreground: Bitmap?, background: Bitmap): Boolean {
        if (!super.setBitmaps(foreground, background)) {
            return false
        }

        recreateFrameBuffers()
        // GenerateAccumulatedSnow needs foreground for accumulatedSnowShader, and needs frameBuffer
        // which is also changed with background
        generateAccumulatedSnow()
        return true
    }

    override val shader: RuntimeShader
        get() = snowConfig.shader

    override val colorGradingShader: RuntimeShader
        get() = snowConfig.colorGradingShader

    override val lut: Bitmap?
        get() = snowConfig.lut

    override val colorGradingIntensity: Float
        get() = snowConfig.colorGradingIntensity

    override fun setCustomCropMatrix(matrix: Matrix) {
        if (!isPanAndZoomInExtendedWallpaperEffectsEnabled) {
            Log.w(
                TAG,
                "setCustomCropMatrix should not be called when Flag panAndZoomInExtendedWallpaperEffects is disabled ",
            )
            return
        }
        if (matrix == cropMatrix.matrix) {
            return
        }
        super.setCustomCropMatrix(matrix)
    }

    override fun setPositionMatrix(matrix: Matrix) {
        if (matrix == positionMatrix.matrix) {
            return
        }
        val oldScale = bitmapScale
        super.setPositionMatrix(matrix)
        // Blur radius should change with scale because it decides the fluffiness of snow
        if (!isPanAndZoomInExtendedWallpaperEffectsEnabled) {
            if (abs(bitmapScale - oldScale) > FLOAT_TOLERANCE) {
                recreateFrameBuffers()
                snowConfig.shader.setInputShader(
                    "accumulatedSnow",
                    BitmapShader(blankBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                )
                generateAccumulatedSnow()
            }
        }
    }

    override fun updateTextureUniforms() {
        super.updateTextureUniforms()
        snowConfig.accumulatedSnowResultShader.setInputBuffer(
            "noise",
            BitmapShader(snowConfig.noiseTexture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT),
        )
        snowConfig.shader.setFloatUniform("snowFlakeSamplesSize", SNOW_FLAKE_SAMPLES_SIZE.toFloat())
        snowConfig.snowFlakeSamples.setFloatUniform(
            "snowFlakeSamplesSize",
            SNOW_FLAKE_SAMPLES_SIZE.toFloat(),
        )
    }

    private fun prepareColorGrading() {
        snowConfig.colorGradingShader.setInputShader("texture", snowConfig.shader)
        snowConfig.lut?.let {
            snowConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
            )
        }
    }

    // Generate accumulated snow requires two passes, first is to generate blurred foreground
    // outline, second is to add snow fluffiness to it.
    // It should only be called when bitmaps or screensize change, and should not be called
    // per frame.
    private fun generateAccumulatedSnow() {
        val renderingCanvas = outlineFrameBuffer.beginDrawing()
        snowConfig.accumulatedSnowOutlineShader.setFloatUniform("scale", bitmapScale)
        snowConfig.accumulatedSnowOutlineShader.setFloatUniform(
            "snowThickness",
            SNOW_THICKNESS / bitmapScale,
        )
        snowConfig.accumulatedSnowOutlineShader.setFloatUniform("screenWidth", surfaceSize.width)
        snowConfig.accumulatedSnowOutlineShader.setInputBuffer(
            "foreground",
            BitmapShader(foreground, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
        )
        renderingCanvas.drawPaint(outlineFrameBufferPaint)
        outlineFrameBuffer.endDrawing()

        outlineFrameBuffer.tryObtainingImage(
            this::generateAccumulatedSnowWithBlurredOutline,
            mainExecutor,
        )
    }

    /** @param outlineImage is generated by outlineShader */
    private fun generateAccumulatedSnowWithBlurredOutline(outlineImage: Bitmap) {
        val renderingCanvas = accumulationFrameBuffer.beginDrawing()
        snowConfig.accumulatedSnowResultShader.setInputBuffer(
            "foregroundOutline",
            BitmapShader(outlineImage, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
        )

        // Keep boilerplate to avoid branches in shader codes
        if (isPanAndZoomInExtendedWallpaperEffectsEnabled) {
            snowConfig.accumulatedSnowResultShader.setFloatUniform(
                "transformMatrixBitmapScaleOnly",
                identityMatrixFloatArray,
            )
        } else {
            // Actually, we should not generate it with bitmap
            snowConfig.accumulatedSnowResultShader.setFloatUniform(
                "transformMatrixBitmapScaleOnly",
                transformMatrixCustomCrop,
            )
        }

        renderingCanvas.drawPaint(accumulationFrameBufferPaint)
        accumulationFrameBuffer.endDrawing()

        accumulationFrameBuffer.tryObtainingImage(
            { image ->
                snowConfig.shader.setInputBuffer(
                    "accumulatedSnow",
                    BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                )
                if (!isPanAndZoomInExtendedWallpaperEffectsEnabled) {
                    outlineFrameBuffer.close()
                }
            },
            mainExecutor,
        )
    }

    override fun updateGridSize(newSurfaceSize: SizeF) {
        val gridSize = GraphicsUtils.computeDefaultGridSize(newSurfaceSize, snowConfig.pixelDensity)
        val gridSizeColumns = 7f * gridSize
        val gridSizeRows = 2f * gridSize
        snowConfig.shader.setFloatUniform("gridSize", gridSizeColumns, gridSizeRows)
        snowConfig.shader.setFloatUniform("cellAspectRatio", gridSizeColumns / gridSizeRows)
    }

    override fun resize(newSurfaceSize: SizeF) {
        if (surfaceSize == newSurfaceSize) {
            return
        }
        super.resize(newSurfaceSize)
        updateGridSize(surfaceSize)
    }

    /**
     * Generates an offscreen bitmap containing pre-rendered snow flake patterns and properties.
     *
     * This bitmap serves as a lookup table for the main snow_effect shader, reducing per-frame
     * calculations.
     */
    private fun generateSnowFlakeSamples() {
        val renderingCanvas = snowFlakeSamplesBuffer.beginDrawing()
        snowConfig.snowFlakeSamples.setFloatUniform(
            "canvasSize",
            SNOW_FLAKE_SAMPLES_BUFFER_WIDTH.toFloat(),
            SNOW_FLAKE_SAMPLES_BUFFER_HEIGHT.toFloat(),
        )
        renderingCanvas.drawPaint(snowFlakeSamplesPaint)
        snowFlakeSamplesBuffer.endDrawing()
        snowFlakeSamplesBuffer.tryObtainingImage(
            { snowFlakeSamples ->
                snowConfig.shader.setInputShader(
                    "snowFlakeSamples",
                    BitmapShader(snowFlakeSamples, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
                )
            },
            mainExecutor,
        )
    }

    private fun recreateFrameBuffers() {
        outlineFrameBuffer.close()
        accumulationFrameBuffer.close()
        outlineFrameBuffer = FrameBuffer(background.width, background.height)
        if (isPanAndZoomInExtendedWallpaperEffectsEnabled) {
            accumulationFrameBuffer = FrameBuffer(background.width, background.height)
        } else {
            accumulationFrameBuffer =
                FrameBuffer(
                    (background.width * bitmapScale).toInt(),
                    (background.height * bitmapScale).toInt(),
                )
        }

        outlineFrameBuffer.setRenderEffect(
            RenderEffect.createBlurEffect(
                BLUR_RADIUS / bitmapScale,
                BLUR_RADIUS / bitmapScale,
                Shader.TileMode.CLAMP,
            )
        )
    }

    companion object {
        private val TAG = SnowEffect::class.simpleName
        const val BLUR_RADIUS = 4f
        // Use blur effect for both blurring the snow accumulation and generating a gradient edge
        // so that intensity can control snow thickness by cut the gradient edge in snow_effect
        // shader.
        const val SNOW_THICKNESS = 6f
        // During wallpaper resizing, the updated accumulation texture might not be immediately
        // available.
        // To prevent displaying outdated accumulation, we use a tiny blank bitmap to temporarily
        // clear the rendering area before the new texture is ready.
        private val blankBitmap = createBitmap(1, 1)

        // The `snow_flakes_samples` shader pre-generates diverse snow flake properties
        // (shape mask, intensity, etc.) in a bitmap, reducing per-frame calculations. A higher
        // column count provides more x-based visual variations.
        // The following values balance the visual benefits with memory and shader sampling costs.
        // Number of columns; increases x-based variation.
        const val SNOW_FLAKE_SAMPLES_COLUMN_COUNT = 14
        const val SNOW_FLAKE_SAMPLES_ROW_COUNT = 4
        // Side length of each flake's square bounding box.
        const val SNOW_FLAKE_SAMPLES_SIZE = 100

        const val SNOW_FLAKE_SAMPLES_BUFFER_WIDTH =
            SNOW_FLAKE_SAMPLES_COLUMN_COUNT * SNOW_FLAKE_SAMPLES_SIZE
        const val SNOW_FLAKE_SAMPLES_BUFFER_HEIGHT =
            SNOW_FLAKE_SAMPLES_ROW_COUNT * SNOW_FLAKE_SAMPLES_SIZE

        private val identityMatrixFloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
