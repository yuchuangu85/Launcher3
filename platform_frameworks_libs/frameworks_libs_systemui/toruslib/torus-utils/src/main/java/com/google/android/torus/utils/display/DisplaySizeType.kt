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

import android.content.res.Configuration
import android.view.Display

/**
 * Class that defines the type of display based on its size. The types or displays align with
 * the Window size classes and thresholds defined in:
 * https://developer.android.com/guide/topics/large-screens/support-different-screen-sizes.
 */
enum class DisplaySizeType {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        private const val MEDIUM_WIDTH_DP_THRESHOLD: Float = 600f
        private const val EXPANDED_WIDTH_DP_THRESHOLD: Float = 840f
        private const val MEDIUM_HEIGHT_DP_THRESHOLD: Float = 480f
        private const val EXPANDED_HEIGHT_DP_THRESHOLD: Float = 900f

        /**
         * Returns the [DisplaySizeType] based on the display's width.
         *
         * @param width the current width of the display (in dp).
         *
         * @return The [DisplaySizeType] based on the display [width].
         */
        @JvmStatic
        fun fromWidth(width: Float): DisplaySizeType {
            return when {
                width < MEDIUM_WIDTH_DP_THRESHOLD -> COMPACT
                width < EXPANDED_WIDTH_DP_THRESHOLD -> MEDIUM
                else -> EXPANDED
            }
        }

        /**
         * Returns the [DisplaySizeType] based the display's height.
         *
         * @param height the current height of the display (in dp).
         *
         * @return The [DisplaySizeType] based on the display [height].
         */
        @JvmStatic
        fun fromHeight(height: Float): DisplaySizeType {
            return when {
                height < MEDIUM_HEIGHT_DP_THRESHOLD -> COMPACT
                height < EXPANDED_HEIGHT_DP_THRESHOLD -> MEDIUM
                else -> EXPANDED
            }
        }

        /**
         * Returns the smallest [DisplaySizeType] available (that means for any orientation
         * of the device) for the current Screen associated to the [config]. This can help
         * understand what kind of display we have (i.e., if the returned value is
         * [DisplaySizeType.MEDIUM], we know that this display, independently of its
         * orientation will have a screen width (in dp) >= MEDIUM_WIDTH_DP_THRESHOLD.
         *
         * @param config the current [Configuration].
         *
         * @return The smallest [DisplaySizeType] that the current display will have (of all
         * possible orientations).
         */
        @JvmStatic
        fun smallestAvailableFromDisplay(display: Display): DisplaySizeType {
            /*
             * if we were using a Configuration object and we only wanted the display/screen
             * associated with the current configuration, we could use
             * config.smallestScreenWidthDp.toFloat() instead.
             */
            return fromWidth(DisplayUtils.getSmallestDisplayWidthDp(display))
        }
    }
}

