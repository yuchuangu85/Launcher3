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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.wallpaper.weathereffects.provider.WallpaperInfoContract.WallpaperGenerationData
import com.android.wallpaper.weathereffects.provider.WallpaperInfoContract.WeatherEffect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WeatherEffectsContentProviderTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var weatherEffectsContentProvider: WeatherEffectsContentProvider

    @Before
    fun setup() {
        weatherEffectsContentProvider = WeatherEffectsContentProvider()
        weatherEffectsContentProvider.onCreate()
    }

    @Test
    fun query_updateWallpaper_returnsCorrectData() {
        testScope.runTest {
            val expectedForegroundPath = "fake_directory/foreground.png"
            val expectedBackgroundPath = "fake_directory/background.png"
            val expectedWeatherEffect = WeatherEffect.SNOW.value
            val uri =
                WallpaperInfoContract.getUpdateWallpaperUri()
                    .appendQueryParameter(
                        WallpaperInfoContract.FOREGROUND_TEXTURE_PARAM,
                        expectedForegroundPath,
                    )
                    .appendQueryParameter(
                        WallpaperInfoContract.BACKGROUND_TEXTURE_PARAM,
                        expectedBackgroundPath,
                    )
                    .appendQueryParameter(
                        WallpaperInfoContract.WEATHER_EFFECT_PARAM,
                        expectedWeatherEffect,
                    )
                    .build()

            val cursor =
                weatherEffectsContentProvider.query(
                    uri,
                    projection = null,
                    selection = null,
                    selectionArgs = null,
                    sortOrder = null,
                )

            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndex(WallpaperGenerationData.FOREGROUND_TEXTURE)
                    )
                )
                .isEqualTo(expectedForegroundPath)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndex(WallpaperGenerationData.BACKGROUND_TEXTURE)
                    )
                )
                .isEqualTo(expectedBackgroundPath)
            assertThat(
                    cursor.getString(cursor.getColumnIndex(WallpaperGenerationData.WEATHER_EFFECT))
                )
                .isEqualTo(expectedWeatherEffect)
            assertThat(cursor.columnNames).isEqualTo(WallpaperGenerationData.DEFAULT_PROJECTION)
        }
    }

    @Test
    fun query_updateWallpaper_withNoParams_returnsCorrectData() {
        testScope.runTest {
            val uri = WallpaperInfoContract.getUpdateWallpaperUri().build()

            val cursor =
                weatherEffectsContentProvider.query(
                    uri,
                    projection = null,
                    selection = null,
                    selectionArgs = null,
                    sortOrder = null,
                )

            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndex(WallpaperGenerationData.FOREGROUND_TEXTURE)
                    )
                )
                .isNull()
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndex(WallpaperGenerationData.BACKGROUND_TEXTURE)
                    )
                )
                .isNull()
            assertThat(
                    cursor.getString(cursor.getColumnIndex(WallpaperGenerationData.WEATHER_EFFECT))
                )
                .isNull()
            assertThat(cursor.columnNames).isEqualTo(WallpaperGenerationData.DEFAULT_PROJECTION)
        }
    }
}
