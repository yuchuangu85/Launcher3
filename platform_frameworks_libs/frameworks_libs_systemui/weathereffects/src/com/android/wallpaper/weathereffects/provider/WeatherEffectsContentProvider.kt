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

package com.android.wallpaper.weathereffects.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.android.wallpaper.weathereffects.WallpaperEffectsDebugApplication
import com.android.wallpaper.weathereffects.dagger.MainScope
import com.android.wallpaper.weathereffects.domain.WeatherEffectsInteractor
import com.android.wallpaper.weathereffects.provider.WallpaperInfoContract.WallpaperGenerationData
import com.android.wallpaper.weathereffects.shared.model.WallpaperFileModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WeatherEffectsContentProvider : ContentProvider() {

    @Inject @MainScope lateinit var mainScope: CoroutineScope
    @Inject lateinit var interactor: WeatherEffectsInteractor

    private val uriMatcher =
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(WallpaperInfoContract.AUTHORITY, UPDATE_WALLPAPER, UPDATE_WALLPAPER_ID)
        }

    override fun onCreate(): Boolean {
        WallpaperEffectsDebugApplication.graph.inject(this)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        return when (uriMatcher.match(uri)) {
            UPDATE_WALLPAPER_ID -> updateWallpaper(uri)
            // TODO(b/290939683): Add more URIs including save and load wallpapers.
            else -> MatrixCursor(arrayOf())
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        return 0
    }

    private fun updateWallpaper(uri: Uri): MatrixCursor {
        val foreground = uri.getQueryParameter(WallpaperInfoContract.FOREGROUND_TEXTURE_PARAM)
        val background = uri.getQueryParameter(WallpaperInfoContract.BACKGROUND_TEXTURE_PARAM)
        val weatherType = uri.getQueryParameter(WallpaperInfoContract.WEATHER_EFFECT_PARAM)

        val projection = WallpaperGenerationData.DEFAULT_PROJECTION
        val cursor = MatrixCursor(projection)
        cursor.addRow(
            projection.map { column ->
                when (column) {
                    WallpaperGenerationData.FOREGROUND_TEXTURE -> foreground
                    WallpaperGenerationData.BACKGROUND_TEXTURE -> background
                    WallpaperGenerationData.WEATHER_EFFECT -> weatherType
                    else -> null
                }
            }
        )

        mainScope.launch {
            interactor.updateWallpaper(
                WallpaperFileModel(
                    foreground,
                    background,
                    WallpaperInfoContract.WeatherEffect.fromStringValue(weatherType),
                )
            )
        }

        return cursor
    }

    companion object {
        const val UPDATE_WALLPAPER = "update_wallpaper"
        const val UPDATE_WALLPAPER_ID = 0
        const val TAG = "WeatherEffectsContentProvider"
    }
}
