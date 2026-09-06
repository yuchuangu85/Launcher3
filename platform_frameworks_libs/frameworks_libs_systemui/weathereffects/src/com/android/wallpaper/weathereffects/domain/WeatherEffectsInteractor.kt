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

package com.android.wallpaper.weathereffects.domain

import com.android.wallpaper.weathereffects.data.repository.WeatherEffectsRepository
import com.android.wallpaper.weathereffects.shared.model.WallpaperFileModel
import com.android.wallpaper.weathereffects.shared.model.WallpaperImageModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class WeatherEffectsInteractor
@Inject
constructor(private val repository: WeatherEffectsRepository) {
    val wallpaperImageModel: StateFlow<WallpaperImageModel?> = repository.wallpaperImage

    suspend fun updateWallpaper(wallpaper: WallpaperFileModel) {
        repository.updateWallpaper(wallpaper)
    }

    suspend fun loadWallpaper() {
        repository.loadWallpaperFromLocalStorage()
    }

    suspend fun saveWallpaper() {
        repository.saveWallpaper()
    }
}
