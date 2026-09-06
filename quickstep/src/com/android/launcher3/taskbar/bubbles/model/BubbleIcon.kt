/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.taskbar.bubbles.model

import android.graphics.Bitmap
import android.widget.ImageView
import com.android.launcher3.icons.BitmapInfo

/**
 * Represents the bubble image which could either be provided by the app developer for chat bubbles,
 * or the app icon for app bubbles.
 */
sealed class BubbleIcon {
    data class AppIcon(val bitmapInfo: BitmapInfo) : BubbleIcon()

    data class Custom(val bitmap: Bitmap) : BubbleIcon()

    /** Sets this [BubbleIcon] as the image for the given [imageView]. */
    fun setOnImageView(imageView: ImageView) {
        when (this) {
            is AppIcon -> imageView.setImageDrawable(bitmapInfo.newIcon(imageView.context))
            is Custom -> imageView.setImageBitmap(bitmap)
        }
    }
}
