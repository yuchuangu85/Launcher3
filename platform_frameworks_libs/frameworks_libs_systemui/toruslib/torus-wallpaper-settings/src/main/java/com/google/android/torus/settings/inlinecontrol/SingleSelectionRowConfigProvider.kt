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

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.slice.Slice
import androidx.slice.builders.ListBuilder
import com.google.android.torus.R

/**
 * SingleSelectionRowConfigProvider provides the most common used Slice UI among the wallpapers.
 * It will get ColorOptions from wallpaper engines and build a row of color chips for user
 * to choose. It can be extended and overridden the onBindSlice for providing different Slice UI.
 */
open class SingleSelectionRowConfigProvider(
    val colorOptions: Array<ColorChipsRowBuilder.ColorOption>?,
    override val sharedPrefKey: String,
    @StringRes override val uriStringId: Int,
    @StringRes open val wallpaperNameResId: Int,
    @StringRes val sliceTitleStringId: Int = R.string.color_chips_title,
    val minSpaces: Int = MIN_SPACES,
    private val zoomOnSelection: Boolean = false
) : BaseSliceConfigProvider() {
    companion object {
        private const val MIN_SPACES = 5
        const val PREF_COLOR_INDEX = "COlOR_INDEX"
    }

    protected var colorIndex = 0

    override fun onBindSlice(sliceUri: Uri): Slice? {
        context?.let { context ->
            val title: CharSequence = context.getString(wallpaperNameResId)
            val subtitleResId: Int =
                colorOptions?.get(colorIndex)?.description ?: sliceTitleStringId
            listBuilder = ListBuilder(context, sliceUri, ListBuilder.INFINITY)
            buildColorChips(
                context,
                title,
                context.getString(subtitleResId),
                sliceUri
            )

            return listBuilder.build()
        } ?: run {
            return null
        }
    }

    override fun onConfigChange() {
        colorIndex = preferences.getInt(
            PREF_COLOR_INDEX,
            ColorChipsRowBuilder.DEFAULT_COLOR_INDEX
        )
    }

    protected fun buildColorChips(
        context: Context,
        title: CharSequence,
        subtitle: CharSequence,
        sliceUri: Uri
    ) {
        val gridRowBuilder = colorOptions?.let {
            ColorChipsRowBuilder.create(
                context = context,
                colorOptions = it,
                selectedItem = colorIndex,
                title = title,
                minSpaces = minSpaces,
                wallpaperUriString = sliceUri.toString(),
                wallpaperName = context.getString(wallpaperNameResId),
                zoomOnSelection = zoomOnSelection
            )
        }
        gridRowBuilder?.let {
            listBuilder
                .setHeader(
                    ListBuilder.HeaderBuilder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                )
                .addGridRow(it)
        }
    }
}
