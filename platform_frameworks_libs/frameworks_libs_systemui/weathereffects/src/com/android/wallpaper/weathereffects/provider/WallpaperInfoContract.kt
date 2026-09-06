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

import android.content.ContentResolver.SCHEME_CONTENT
import android.net.Uri

object WallpaperInfoContract {

    /**
     * Returns a [Uri.Builder] for updating a wallpaper. This will produce a uri starts with
     * content://com.android.wallpaper.weathereffects.effectprovider/update_wallpaper. Append
     * parameters such as foreground and background images, etc.
     *
     * All the parameters are optional.
     * <ul>
     * <li>For the initial generation, foreground and background images must be provided.
     * <li>When foreground and background images are already provided, but no weather type is
     *   provided, it clears the existing weather effect (foreground & background images composed).
     * </ul>
     *
     * Example uri: content://com.android.wallpaper.weathereffects.effectprovider/
     * update_wallpaper?foreground_texture=<path_to_foreground_texture>&background_texture=
     * <path_to_background_texture>
     */
    fun getUpdateWallpaperUri(): Uri.Builder {
        return Uri.Builder()
            .scheme(SCHEME_CONTENT)
            .authority(AUTHORITY)
            .appendPath(WeatherEffectsContentProvider.UPDATE_WALLPAPER)
    }

    enum class WeatherEffect(val value: String) {
        RAIN("rain"),
        FOG("fog"),
        CLOUDS("clouds"),
        SNOW("snow"),
        SUN("SUN");

        companion object {

            /**
             * Converts the String value to an enum.
             *
             * @param value a String representing the [value] of an enum. Note that this is the
             *   value that we created [value] and it does not refer to the [valueOf] value, which
             *   corresponds to the [name]. i.e.
             * - RAIN("rain"): -> [valueOf] needs [name] ("RAIN"). -> [fromStringValue] needs
             *   [value] ("rain").
             *
             * @return the associated [WeatherEffect].
             */
            fun fromStringValue(value: String?): WeatherEffect? {
                return when (value) {
                    RAIN.value -> RAIN
                    FOG.value -> FOG
                    CLOUDS.value -> CLOUDS
                    SNOW.value -> SNOW
                    SUN.value -> SUN
                    else -> null
                }
            }
        }
    }

    const val AUTHORITY = "com.android.wallpaper.weathereffects.effectprovider"
    const val FOREGROUND_TEXTURE_PARAM = "foreground_texture"
    const val BACKGROUND_TEXTURE_PARAM = "background_texture"
    const val WEATHER_EFFECT_PARAM = "weather_effect"

    object WallpaperGenerationData {

        const val FOREGROUND_TEXTURE = "foreground_texture"
        const val BACKGROUND_TEXTURE = "background_texture"
        const val WEATHER_EFFECT = "weather_effect"

        val DEFAULT_PROJECTION = arrayOf(FOREGROUND_TEXTURE, BACKGROUND_TEXTURE, WEATHER_EFFECT)
    }
}
