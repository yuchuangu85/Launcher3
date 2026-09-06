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

package com.google.android.torus.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Size
import android.view.View
import java.io.IOException

/**
 * Bitmap utils.
 */
object BitmapUtils {
    @JvmStatic
    @Throws(IOException::class)
    fun loadBitmap(context: Context, bitmapResourceId: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inScaled = false
        return BitmapFactory.decodeResource(context.resources, bitmapResourceId, options)
            ?: throw IOException("Bitmap has not been decoded properly.")
    }

    @JvmStatic
    fun getBitmapSize(context: Context, bitmapResourceId: Int): Size {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        val bitmap = BitmapFactory.decodeResource(context.resources, bitmapResourceId, options)
        val size = Size(options.outWidth, options.outHeight)
        bitmap?.recycle()
        return size
    }

    /**
     * Generates a Bitmap from a view.
     *
     * @param view The view that we want to create a [Bitmap].
     * @param config The [Bitmap.Config] of how we load the view. By default uses
     * an ARGB 8888 format.
     *
     * @return The generated [Bitmap]
     */
    @JvmStatic
    fun generateBitmapFromView(
        view: View,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, config)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        view.draw(canvas)
        return bitmap
    }
}