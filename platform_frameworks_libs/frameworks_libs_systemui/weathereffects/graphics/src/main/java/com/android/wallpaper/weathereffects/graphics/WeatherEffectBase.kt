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

package com.android.wallpaper.weathereffects.graphics

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.SizeF
import com.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils
import com.android.wallpaper.weathereffects.graphics.utils.MatrixAndValues
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.calculateTransformDifference
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.centerCropMatrix
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.getScaleFromMatrixValues
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.invertAndTransposeMatrix
import kotlin.random.Random

/** Give default implementation of some functions in WeatherEffect */
abstract class WeatherEffectBase(
    protected var foreground: Bitmap,
    protected var background: Bitmap,
    /**
     * Should be screen size with horizontal buffer to support parallax when pan and zoom are
     * supported.
     */
    protected var surfaceSize: SizeF,
    // TODO(b/435215686): change to Map<Size, Matrix> to support different surfaceSize
    protected val initialCropMatrix: Matrix,
    protected val isPanAndZoomInExtendedWallpaperEffectsEnabled: Boolean,
) : WeatherEffect {
    protected val cropMatrix = MatrixAndValues(initialCropMatrix)

    protected val positionMatrix = MatrixAndValues(Matrix(cropMatrix.matrix))
    // Currently, we use same transform for both foreground and background
    protected open val transformMatrixPosition: FloatArray = FloatArray(9)
    protected open val transformMatrixCustomCrop: FloatArray = FloatArray(9)
    // Apply to components not rely on image textures
    // Should be identity matrix in editor and preview, and only change when parallax applied in
    // homescreen
    protected val transformMatrixParallax: FloatArray = FloatArray(9)
    protected var bitmapScale = getScaleFromMatrixValues(cropMatrix.values)
    protected var elapsedTime: Float = 0f

    abstract val shader: RuntimeShader
    abstract val colorGradingShader: RuntimeShader
    abstract val lut: Bitmap?
    abstract val colorGradingIntensity: Float

    override fun setCustomCropMatrix(matrix: Matrix) {
        if (matrix == cropMatrix.matrix) {
            return
        }
        cropMatrix.set(matrix)
        adjustCropping()
    }

    override fun setPositionMatrix(matrix: Matrix) {
        if (matrix == positionMatrix.matrix) {
            return
        }
        positionMatrix.set(matrix)
        if (!isPanAndZoomInExtendedWallpaperEffectsEnabled) {
            // When image is always center aligned, we have scale and translation info in
            // positionMatrix
            bitmapScale = getScaleFromMatrixValues(positionMatrix.values)
        }
        adjustCropping()
    }

    /** This function will be called every time parallax changes, don't do heavy things here */
    open fun adjustCropping() {
        invertAndTransposeMatrix(positionMatrix.matrix, transformMatrixPosition)
        invertAndTransposeMatrix(cropMatrix.matrix, transformMatrixCustomCrop)
        // the difference includes horizontal translation and wallpaper scale <= 1.1f
        // In editor and preview, cropMatrix and positionMatrix is the same
        calculateTransformDifference(
            cropMatrix.matrix,
            positionMatrix.matrix,
            transformMatrixParallax,
        )

        shader.setFloatUniform("transformMatrixBitmap", transformMatrixPosition)
        shader.setFloatUniform("transformMatrixWeather", transformMatrixParallax)
    }

    open fun updateGridSize(newSurfaceSize: SizeF) {}

    override fun resize(newSurfaceSize: SizeF) {
        if (surfaceSize == newSurfaceSize) {
            return
        }
        surfaceSize = newSurfaceSize
        // TODO(b/435215686): we need to rely on map: surfaceSize -> cropMatrix to update cropMatrix
        cropMatrix.set(
            centerCropMatrix(
                surfaceSize,
                SizeF(background.width.toFloat(), background.height.toFloat()),
            )
        )
        shader.setFloatUniform("screenSize", newSurfaceSize.width, newSurfaceSize.height)
        shader.setFloatUniform("screenAspectRatio", GraphicsUtils.getAspectRatio(newSurfaceSize))
        adjustCropping()
        updateGridSize(newSurfaceSize)
    }

    abstract override fun update(deltaMillis: Long, frameTimeNanos: Long)

    abstract override fun draw(canvas: Canvas)

    override fun reset() {
        elapsedTime = Random.nextFloat() * 90f
    }

    override fun release() {
        lut?.recycle()
    }

    override fun setIntensity(intensity: Float) {
        shader.setFloatUniform("intensity", intensity)
        colorGradingShader.setFloatUniform("intensity", colorGradingIntensity * intensity)
    }

    override fun setBitmaps(foreground: Bitmap?, background: Bitmap): Boolean {
        if (this.foreground == foreground && this.background == background) {
            return false
        }
        this.foreground = foreground ?: background
        this.background = background

        // When the user changes bitmap, we should fall back to default center crop matrix
        cropMatrix.set(
            centerCropMatrix(
                surfaceSize,
                SizeF(background.width.toFloat(), background.height.toFloat()),
            )
        )
        positionMatrix.set(cropMatrix.matrix)
        bitmapScale = getScaleFromMatrixValues(cropMatrix.values)
        shader.setInputBuffer(
            "background",
            BitmapShader(this.background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
        )
        shader.setInputBuffer(
            "foreground",
            BitmapShader(this.foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
        )
        adjustCropping()
        return true
    }

    open fun updateTextureUniforms() {
        shader.setInputBuffer(
            "foreground",
            BitmapShader(foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
        )

        shader.setInputBuffer(
            "background",
            BitmapShader(background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR),
        )

        shader.setFloatUniform("screenSize", surfaceSize.width, surfaceSize.height)
        shader.setFloatUniform("screenAspectRatio", GraphicsUtils.getAspectRatio(surfaceSize))
    }

    private fun Matrix.setAndUpdateFloatArray(src: Matrix, targetFloatArray: FloatArray) {
        set(src)
        src.getValues(targetFloatArray)
    }

    companion object {
        // When extracting the scale from the parallax matrix, there will be a very small difference
        // due to floating-point precision.
        // We use FLOAT_TOLERANCE to avoid triggering actions on these insignificant scale changes.
        const val FLOAT_TOLERANCE = 0.0001F
    }
}
