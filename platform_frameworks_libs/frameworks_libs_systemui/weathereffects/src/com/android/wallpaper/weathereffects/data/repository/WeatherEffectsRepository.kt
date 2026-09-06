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

package com.android.wallpaper.weathereffects.data.repository

import android.content.Context
import android.util.Log
import com.android.wallpaper.weathereffects.shared.model.WallpaperFileModel
import com.android.wallpaper.weathereffects.shared.model.WallpaperImageModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class WeatherEffectsRepository @Inject constructor(private val context: Context) {
    private val _wallpaperImage = MutableStateFlow<WallpaperImageModel?>(null)
    val wallpaperImage: StateFlow<WallpaperImageModel?> = _wallpaperImage.asStateFlow()

    /** Generates or updates a wallpaper from the provided [wallpaperFileModel]. */
    suspend fun updateWallpaper(wallpaperFileModel: WallpaperFileModel) {
        try {
            // Use the existing images if the foreground and background are not supplied.
            var fgBitmap = _wallpaperImage.value?.foreground
            var bgBitmap = _wallpaperImage.value?.background

            wallpaperFileModel.foregroundAbsolutePath?.let {
                WallpaperFileUtils.importBitmapFromAbsolutePath(it)?.let { newFg ->
                    fgBitmap = newFg
                }
            }

            wallpaperFileModel.backgroundAbsolutePath?.let {
                WallpaperFileUtils.importBitmapFromAbsolutePath(it)?.let { newBg ->
                    bgBitmap = newBg
                }
            }

            if (fgBitmap == null || bgBitmap == null) {
                Log.w(TAG, "Cannot update wallpaper. asset: $wallpaperFileModel")
                return
            }

            val foreground = fgBitmap!!
            val background = bgBitmap!!

            var success = true
            // TODO: Only persist assets when the wallpaper is applied.
            success =
                success and
                    WallpaperFileUtils.exportBitmap(
                        context,
                        WallpaperFileUtils.FG_FILE_NAME,
                        foreground,
                    )
            success =
                success and
                    WallpaperFileUtils.exportBitmap(
                        context,
                        WallpaperFileUtils.BG_FILE_NAME,
                        background,
                    )
            if (!success) {
                Log.e(TAG, "Failed to export assets during wallpaper generation")
                return
            }

            // We always respect the new weather.
            val weather = wallpaperFileModel.weatherEffect
            if (weather != null) WallpaperFileUtils.exportLastKnownWeather(weather, context)

            _wallpaperImage.value = WallpaperImageModel(foreground, background, weather)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to load wallpaper: ", e)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Unable to load wallpaper: ", e)
        }
    }

    /**
     * Loads wallpaper from the persisted files in local storage. This assumes wallpaper assets
     * exist in local storage under fixed names.
     */
    suspend fun loadWallpaperFromLocalStorage() {
        try {
            val fgBitmap =
                WallpaperFileUtils.importBitmapFromLocalStorage(
                    WallpaperFileUtils.FG_FILE_NAME,
                    context,
                )
            val bgBitmap =
                WallpaperFileUtils.importBitmapFromLocalStorage(
                    WallpaperFileUtils.BG_FILE_NAME,
                    context,
                )
            if (fgBitmap == null || bgBitmap == null) {
                Log.w(TAG, "Cannot load wallpaper from local storage.")
                return
            }

            val weatherEffect = WallpaperFileUtils.importLastKnownWeather(context)

            _wallpaperImage.value = WallpaperImageModel(fgBitmap, bgBitmap, weatherEffect)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to load wallpaper: ", e)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Unable to load wallpaper: ", e)
            null
        }
    }

    suspend fun saveWallpaper() {
        val foreground = _wallpaperImage.value?.foreground
        val background = _wallpaperImage.value?.background

        var success = true
        success =
            success and
                (foreground?.let {
                    WallpaperFileUtils.exportBitmap(context, WallpaperFileUtils.FG_FILE_NAME, it)
                } == true)
        success =
            success and
                (background?.let {
                    WallpaperFileUtils.exportBitmap(context, WallpaperFileUtils.BG_FILE_NAME, it)
                } == true)
        if (success) {
            Log.d(TAG, "Successfully save wallpaper")
        } else {
            Log.e(TAG, "Failed to save wallpaper")
        }
    }

    companion object {
        private const val TAG = "WeatherEffectsRepository"
    }
}
