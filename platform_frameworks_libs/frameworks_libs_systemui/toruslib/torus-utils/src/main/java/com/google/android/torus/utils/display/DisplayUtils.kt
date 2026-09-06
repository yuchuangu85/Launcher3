/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.torus.utils.display

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import kotlin.math.round

/** Display-related utils. */
object DisplayUtils {

    /** A constant value that should be passed in to get all the screens. */
    private const val DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED =
        "android.hardware.display.category.ALL_INCLUDING_DISABLED"
    /**
     * Returns a list of the ID and size of each Display available on the current device.
     *
     * @param context the application context.
     *
     * @return a [List] composed by [Pair<Int, Size>] of Display ID and Display Size.
     */
    @JvmStatic
    fun getDisplayIdsAndSizes(context: Context): List<Pair<Int, Size>> {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
        val displaySizes = displays.map {
            val size = Point()
            /*
             * Note: this API has been deprecated but currently there isn't a good alternative.
             * The proposed way in the Android Developers site:
             *
             * ```
             * This method was deprecated in API level 31.
             * Use WindowManager#getCurrentWindowMetrics() to identify the current size of
             * the activity window. UI-related work, such as choosing UI layouts,
             * should rely upon WindowMetrics#getBounds().
             * ```
             *
             * Only works to retrieve the DEFAULT/current display but not for any display.
             * Once we have an API that allows to retrieve this information, this code will be
             * updated.
             */
            it.getRealSize(size)
            Pair(it.displayId, Size(size.x, size.y))
        }

        return displaySizes
    }

    /**
     * Returns the number of available displays.
     *
     * @param context the application context.
     *
     * @return the number of available displays.
     */
    @JvmStatic
    fun getNumberOfDisplaysAvailable(context: Context): Int {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED).size
    }

    /**
     * Converts a pixel unit to dp.
     *
     * @param pixels the pixels unit to convert.
     * @param displayMetrics the [DisplayMetrics] we want to use.
     *
     * @return the pixel unit converted to dp.
     */
    @JvmStatic
    fun convertPixelToDp(pixels: Float, displayMetrics: DisplayMetrics): Float {
        val densityRatio = displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
        return pixels / densityRatio
    }

    /**
     * Converts a dp unit to pixels.
     *
     * @param dp the dp unit to convert.
     * @param displayMetrics the [DisplayMetrics] we want to use.
     *
     * @return the dp unit converted to pixels.
     */
    @JvmStatic
    fun convertDpToPixel(dp: Float, displayMetrics: DisplayMetrics): Float {
        val densityRatio = displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
        return round(dp * densityRatio)
    }

    /**
     * Returns the smallest [Display] width in dp (that means for any orientation of the device).
     *
     * @param display the [Display] we want to get the smallest width in dp.
     *
     * @return The smallest width in dp.
     */
    fun getSmallestDisplayWidthDp(display: Display): Float {
        val metrics = DisplayMetrics()
        /*
         * Note: this API has been deprecated but currently there isn't a good alternative.
         * The proposed way in the Android Developers site:
         *
         * ```
         * This method was deprecated in API level 31.
         * Use WindowManager#getCurrentWindowMetrics() to identify the current size of
         * the activity window. UI-related work, such as choosing UI layouts,
         * should rely upon WindowMetrics#getBounds().
         * ```
         *
         * Only works to retrieve the DEFAULT/current display but not for any display.
         * Once we have an API that allows to retrieve this information, this code will be
         * updated.
         */
        display.getRealMetrics(metrics)
        return convertPixelToDp(
            minOf(metrics.widthPixels, metrics.heightPixels).toFloat(),
            metrics
        )
    }
}
