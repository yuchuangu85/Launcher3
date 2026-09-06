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
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.slice.builders.GridRowBuilder
import androidx.slice.builders.GridRowBuilder.CellBuilder
import androidx.slice.builders.ListBuilder
import androidx.slice.builders.SliceAction
import com.google.android.torus.R
import com.google.android.torus.settings.inlinecontrol.BaseSliceConfigProvider.Companion.EXTRA_WALLPAPER_URI
import kotlin.math.max

/**
 * ColorChipsRowBuilder is used to construct and hold Slice rows data and color configurations
 * for wallpapers that are configurable using the provided params in {@link #create()} method
 */
object ColorChipsRowBuilder {
    var ACTION_PRIMARY = ".action.PRIMARY"
    var ACTION_SET_COLOR = ".action.SET_COLOR"
    var EXTRA_COLOR_INDEX = "extra_color_index"
    var DEFAULT_COLOR_INDEX = 0

    @JvmOverloads
    fun create(
        context: Context,
        colorOptions: Array<ColorOption>,
        selectedItem: Int,
        title: CharSequence? = null,
        minSpaces: Int = 0,  // disabled by default
        wallpaperUriString: String? = "",
        wallpaperName: String,
        zoomOnSelection: Boolean
    ): GridRowBuilder? {
        val packageName = context.packageName
        if (TextUtils.isEmpty(packageName)) return null

        val res = context.resources ?: return null
        val titleAdjusted = title ?: res.getText(R.string.color_chips_title)

        val iconHeight = res.getDimensionPixelSize(R.dimen.slice_icon_height)
        val iconWidth = res.getDimensionPixelSize(R.dimen.slice_icon_width)
        val gridRowBuilder = GridRowBuilder()
        val bmpEmpty = Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888)
        val emptyCell = CellBuilder()
            .addImage(IconCompat.createWithBitmap(bmpEmpty), ListBuilder.SMALL_IMAGE)
        val primaryAction = SliceAction.create(
            PendingIntent.getBroadcast(
                context, 0,
                Intent("$packageName.$wallpaperName$ACTION_PRIMARY").setPackage(packageName)
                    .putExtra(EXTRA_COLOR_INDEX, 0),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ),
            IconCompat.createWithBitmap(bmpEmpty),
            ListBuilder.SMALL_IMAGE, ""
        )

        gridRowBuilder.primaryAction = primaryAction

        // Content description for this grid row.
        gridRowBuilder.setContentDescription(titleAdjusted)

        for (cellIndex in 0..max(colorOptions.size - 1, minSpaces - 1)) {
            if (cellIndex < colorOptions.size) {
                // Add color option
                gridRowBuilder.addCell(
                    makeColorOption(
                        colorOptions,
                        cellIndex,
                        selectedItem,
                        zoomOnSelection,
                        res,
                        context,
                        packageName,
                        wallpaperUriString,
                        wallpaperName
                    )
                )
            } else {
                // Add empty cell for unused spaces
                gridRowBuilder.addCell(emptyCell)
            }
        }
        return gridRowBuilder
    }

    private fun makeColorOption(
        colorOptions: Array<ColorOption>,
        cellIndex: Int,
        selectedItem: Int,
        zoomOnSelection: Boolean,
        res: Resources,
        context: Context,
        packageName: String,
        wallpaperUriString: String?,
        wallpaperName: String
    ) = CellBuilder()
        .addImage(
            getIcon(
                option = colorOptions[cellIndex],
                selected = cellIndex == selectedItem,
                zoomOnSelection = zoomOnSelection,
                res = res
            ),
            ListBuilder.SMALL_IMAGE
        )
        .setContentIntent(
            PendingIntent.getBroadcast(
                context, cellIndex,
                Intent("$packageName.$wallpaperName$ACTION_SET_COLOR")
                    .setPackage(packageName)
                    .putExtra(
                        EXTRA_COLOR_INDEX,
                        cellIndex
                    ).putExtra(EXTRA_WALLPAPER_URI, wallpaperUriString),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setContentDescription(res.getText(colorOptions[cellIndex].description))

    private fun getIcon(
        option: ColorOption,
        selected: Boolean,
        zoomOnSelection: Boolean,
        res: Resources
    ): IconCompat {
        val iconHeight = res.getDimensionPixelSize(R.dimen.slice_icon_height)
        val iconWidth = res.getDimensionPixelSize(R.dimen.slice_icon_width)
        val colorChipHeight = res.getDimensionPixelSize(R.dimen.color_chips_height)
        val colorChipPenWidth = res.getDimensionPixelSize(R.dimen.color_chips_pen_width)
        val iconOnSize = res.getDimensionPixelSize(R.dimen.torus_slice_vector_icon_on_size)
        val iconOffSize = if (zoomOnSelection) {
            res.getDimensionPixelSize(R.dimen.torus_slice_vector_icon_off_size)
        } else {
            res.getDimensionPixelSize(R.dimen.torus_slice_vector_icon_on_size)
        }
        val bmp = Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        if (option.drawable == -1) {
            if (selected) {
                paint.style = Paint.Style.FILL_AND_STROKE
            } else {
                paint.style = Paint.Style.STROKE
            }
            paint.strokeWidth = colorChipPenWidth.toFloat()
            paint.color = option.value
            paint.isAntiAlias = true
            canvas.drawCircle(
                (iconWidth / 2).toFloat(),
                (iconHeight / 2).toFloat(),
                ((colorChipHeight - colorChipPenWidth) / 2).toFloat(),
                paint
            )
        } else {
            val drawableSize = if (selected) iconOnSize else iconOffSize

            val drawable = if (selected && option.drawableSelected != 1) {
                ResourcesCompat.getDrawable(res, option.drawableSelected, null)
            } else {
                ResourcesCompat.getDrawable(res, option.drawable, null)
            }
            drawable?.setBounds(0, 0, drawableSize, drawableSize)
            canvas.translate(
                ((iconWidth - drawableSize) / 2).toFloat(),
                ((iconHeight - drawableSize) / 2).toFloat()
            )
            drawable?.draw(canvas)
        }
        return IconCompat.createWithBitmap(bmp)
    }

    /**
     * A color option to present in an inline control menu
     */
    open class ColorOption {
        @ColorInt
        var value: Int

        @StringRes
        val description: Int

        @DrawableRes
        val drawable: Int

        @DrawableRes
        val drawableSelected: Int

        constructor(
            @StringRes description: Int,
            @DrawableRes drawable: Int,
            @DrawableRes drawableSelected: Int
        ) {
            this.description = description
            this.value = -1
            this.drawable = drawable
            this.drawableSelected = drawableSelected
        }

        constructor(@StringRes description: Int, @ColorInt value: Int) {
            this.description = description
            this.value = value
            drawable = -1
            drawableSelected = -1
        }
    }
}
