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
package com.google.android.torus.utils.wallpaper

import android.app.WallpaperColors
import android.graphics.Color
import android.os.Build

/** Creates some utils for wallpapers. */
object WallpaperUtils {
    /**
     * Returns a [WallpaperColors] with the color provided and launcher using black text
     * if [darkText] is true.
     *
     * @param primaryColor Primary color.
     * @param secondaryColor Secondary color.
     * @param tertiaryColor Tertiary color.
     * @param darkText If the launcher should use dark text (It won't work for SDK < 31 (S)).
     *
     * @return the wallpaper color with the color hints (if possible).
     */
    @JvmStatic
    fun getWallpaperColors(
        primaryColor: Color,
        secondaryColor: Color,
        tertiaryColor: Color,
        darkText: Boolean = false
    ): WallpaperColors {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkText) {
            WallpaperColors(
                primaryColor,
                secondaryColor,
                tertiaryColor,
                WallpaperColors.HINT_SUPPORTS_DARK_TEXT or WallpaperColors.HINT_SUPPORTS_DARK_THEME
            )
        } else {
            WallpaperColors(
                primaryColor,
                secondaryColor,
                tertiaryColor,
            )
        }
    }
}
