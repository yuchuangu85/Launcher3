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

package com.google.android.torus.settings.inlinecontrol

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.slice.builders.ListBuilder
import com.google.android.torus.settings.inlinecontrol.BaseSliceConfigProvider.Companion.EXTRA_WALLPAPER_URI

/**
 * InputRangeRowBuilder is used to construct and hold Slice rows data, color options,
 * and range value for wallpaper configurations that have an input range slider
 */
object InputRangeRowBuilder {
    var ACTION_SET_RANGE = ".action.SET_RANGE"
    var DEFAULT_RANGE_VALUE = 40

    fun createInputRangeBuilder(
        context: Context,
        rangeIntent: Intent,
        currentRangeValue: Int,
        minRangeValue: Int,
        maxRangeValue: Int,
        title: String
    ): ListBuilder.InputRangeBuilder {
        val rangePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            rangeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return ListBuilder.InputRangeBuilder()
            .setTitle(title)
            .setInputAction(rangePendingIntent)
            .setMin(minRangeValue)
            .setMax(maxRangeValue)
            .setValue(currentRangeValue)
    }

    fun getInputRangeRowIntent(
        context: Context,
        wallpaper: Uri,
        wallpaperName: String
    ): Intent {
        val packageName = context.packageName
        return Intent("$packageName.$wallpaperName$ACTION_SET_RANGE")
            .setPackage(packageName)
            .putExtra(
                EXTRA_WALLPAPER_URI,
                wallpaper.toString()
            )
    }
}
