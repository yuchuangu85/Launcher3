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
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.SizeF
import androidx.annotation.FloatRange

/** Defines a single weather effect with a main shader and a main LUT for color grading. */
interface WeatherEffect {

    /**
     * Resizes the effect.
     *
     * @param newSurfaceSize the new size of the surface where we are showing the effect.
     */
    fun resize(newSurfaceSize: SizeF)

    /**
     * Updates the effect.
     *
     * @param deltaMillis The time in millis since the last time [onUpdate] was called.
     * @param frameTimeNanos The time in nanoseconds from the previous Vsync frame, in the
     *   [System.nanoTime] timebase.
     */
    fun update(deltaMillis: Long, frameTimeNanos: Long)

    /**
     * Draw the effect.
     *
     * @param canvas the canvas where we have to draw the effect.
     */
    fun draw(canvas: Canvas)

    /** Resets the effect. */
    fun reset()

    /** Releases the weather effect. */
    fun release()

    /**
     * Adjusts the intensity of the effect (that means both weather intensity and color grading
     * intensity, if any).
     *
     * @param intensity [0, 1] the intensity of the weather effect.
     */
    fun setIntensity(@FloatRange(from = 0.0, to = 1.0) intensity: Float)

    /**
     * Reuse current shader but change background, foreground
     *
     * @param foreground A bitmap containing the foreground of the image, will be null when
     *   segmentation hasn't finished.
     * @param background A bitmap containing the background of the image
     * @return True if the bitmaps have been updated. False otherwise.
     */
    fun setBitmaps(foreground: Bitmap?, background: Bitmap): Boolean

    /**
     * Applies a transformation matrix for parallax and cropping. In the editor and preview, this
     * matrix handles custom cropping. When the wallpaper is applied on the home screen, this matrix
     * combines both custom cropping and parallax-based transformations.
     */
    fun setPositionMatrix(matrix: Matrix) {}

    /**
     * Sets the custom cropping matrix. This is used in the editor and preview to handle
     * user-defined pan and zoom. The matrix is later incorporated into the parallax matrix for the
     * final rendering.
     */
    fun setCustomCropMatrix(matrix: Matrix) {}

    companion object {
        val DEFAULT_INTENSITY = 1f
    }
}
