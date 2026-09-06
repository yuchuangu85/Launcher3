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

package com.android.wallpaper.weathereffects.shared.model

import android.graphics.Bitmap
import com.android.wallpaper.weathereffects.provider.WallpaperInfoContract

/**
 * Model representing assets.
 *
 * @param foregroundAbsolutePath Absolute file path of the foreground asset.
 * @param backgroundAbsolutePath Absolute file path of the background asset.
 * @param weatherEffect the weather effect type.
 */
data class WallpaperFileModel(
    val foregroundAbsolutePath: String?,
    val backgroundAbsolutePath: String?,
    val weatherEffect: WallpaperInfoContract.WeatherEffect? = null,
)

/**
 * Model representing wallpapers with images loaded in memory.
 *
 * @param foreground Bitmap of the foreground image.
 * @param background Bitmap of the background image.
 * @param weatherEffect the weather effect type.
 */
data class WallpaperImageModel(
    val foreground: Bitmap,
    val background: Bitmap,
    val weatherEffect: WallpaperInfoContract.WeatherEffect? = null,
)
