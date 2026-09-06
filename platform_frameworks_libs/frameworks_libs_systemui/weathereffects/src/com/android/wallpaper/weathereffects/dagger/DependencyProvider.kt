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

package com.android.wallpaper.weathereffects.dagger

import android.app.WallpaperManager
import android.content.Context
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

@Module
class DependencyProvider(private val context: Context) {

    @Singleton @Provides fun context() = context

    @Singleton
    @Provides
    @MainScope
    fun mainScope(@Main mainDispatcher: MainCoroutineDispatcher) = CoroutineScope(mainDispatcher)

    @Provides
    @BackgroundScope
    fun backgroundScope(@Background backgroundDispatcher: CoroutineDispatcher) =
        CoroutineScope(backgroundDispatcher)

    @Singleton
    @Provides
    @Main
    fun mainDispatcher(): MainCoroutineDispatcher = Dispatchers.Main.immediate

    @Singleton
    @Provides
    @Background
    fun backgroundDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Singleton @Provides fun resources() = context.resources

    @Singleton
    @Provides
    fun provideWallpaperManager(): WallpaperManager {
        return context.getSystemService(WallpaperManager::class.java)
    }
}
