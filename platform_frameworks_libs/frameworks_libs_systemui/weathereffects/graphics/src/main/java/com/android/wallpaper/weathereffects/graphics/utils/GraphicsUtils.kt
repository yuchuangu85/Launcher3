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

package com.android.wallpaper.weathereffects.graphics.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.SizeF
import androidx.annotation.FloatRange

/** Contains functions for rendering. */
object GraphicsUtils {
    /* Default width dp is calculated as default_display_width / default_display_density. */
    private const val DEFAULT_WIDTH_DP = 1080 / 2.625f

    /**
     * Loads a shader from an asset file.
     *
     * @param assetManager an [AssetManager] instance.
     * @param path path to the shader to load.
     * @return returns a [RuntimeShader] object.
     */
    fun loadShader(assetManager: AssetManager, path: String): RuntimeShader {
        val shader = loadRawShader(assetManager, path)
        val finalShader = resolveShaderIncludes(assetManager, shader)
        return RuntimeShader(finalShader)
    }

    /**
     * Loads a Bitmap from an asset file.
     *
     * @param assetManager an [AssetManager] instance.
     * @param path path to the texture bitmap to load.
     * @return returns a Bitmap.
     */
    fun loadTexture(assetManager: AssetManager, path: String): Bitmap? {
        return assetManager.open(path).use {
            BitmapFactory.decodeStream(
                it,
                Rect(),
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE },
            )
        }
    }

    /**
     * Blurs an image and returns it as a new one.
     *
     * @param context the application.
     * @param sourceBitmap the original image that we want to blur.
     * @param blurRadius the amount that we want to blur (only values from 0 to 25).
     * @param config the bitmap config (optional).
     * @return returns a Bitmap.
     */
    fun blurImage(
        context: Context,
        sourceBitmap: Bitmap,
        @FloatRange(from = 0.0, to = 25.0) blurRadius: Float,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ): Bitmap {
        // TODO: This might not be the ideal option, find a better one.
        val blurredImage = Bitmap.createBitmap(sourceBitmap.copy(config, true))
        val renderScript = RenderScript.create(context)
        val blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        val allocationIn = Allocation.createFromBitmap(renderScript, sourceBitmap)
        val allocationOut = Allocation.createFromBitmap(renderScript, blurredImage)
        blur.setRadius(blurRadius)
        blur.setInput(allocationIn)
        blur.forEach(allocationOut)
        allocationOut.copyTo(blurredImage)
        return blurredImage
    }

    /**
     * @return the [Float] representing the aspect ratio of width / height, -1 if either width or
     *   height is equal to or less than 0.
     */
    fun getAspectRatio(size: SizeF): Float {
        val width = size.width
        val height = size.height

        return if (width <= 0 || height <= 0) {
            -1f
        } else width / height
    }

    /**
     * Compute the weather effect default grid size. This takes into consideration the different
     * display densities and aspect ratio so the effect looks good on displays with different sizes.
     *
     * @param surfaceSize the size of the surface where the wallpaper is being rendered.
     * @param density the current display density.
     * @return a [Float] representing the default size.
     */
    fun computeDefaultGridSize(surfaceSize: SizeF, density: Float): Float {
        val displayWidthDp = surfaceSize.width / density
        val adjustedScale =
            when {
                // "COMPACT"
                displayWidthDp < 600 -> 1f
                // "MEDIUM"
                displayWidthDp >= 600 && displayWidthDp < 840 -> 0.9f
                // "EXPANDED"
                else -> 0.8f
            }
        return adjustedScale * displayWidthDp / DEFAULT_WIDTH_DP
    }

    private fun resolveShaderIncludes(assetManager: AssetManager, string: String): String {
        val match = Regex("[ \\t]*#include +\"([\\w\\d./]+)\"")
        return string.replace(match) { m ->
            val (includePath) = m.destructured
            getResolvedShaderPath(assetManager, includePath)
        }
    }

    private fun getResolvedShaderPath(assetManager: AssetManager, includePath: String): String {
        val string = loadRawShader(assetManager, includePath)
        return resolveShaderIncludes(assetManager, string)
    }

    private fun loadRawShader(assetManager: AssetManager, path: String): String {
        return assetManager.open(path).bufferedReader().use { it.readText() }
    }
}
