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

package com.android.wallpaper.weathereffects.graphics.utils

/** Contains offsets and scales to position image inside a surface. */
class ImageCrop(
    /**
     * The left start of the image relatively to the left edge of the surface that will contain the
     * image.
     */
    val leftOffset: Float = 0f,
    /**
     * The top distance start of the image relatively to the top edge of the surface that will
     * contain the image.
     */
    val topOffset: Float = 0f,
    /** The horizontal scale applied to the image. */
    val horizontalScale: Float = 1f,
    /** The vertical scale applied to the image. */
    val verticalScale: Float = 1f,
) {

    companion object {

        /**
         * Calculates the [ImageCrop] that would make the image cover the surface (that is, the
         * image will be scaled to the smallest possible size so it fills the container, preserving
         * the aspect ratio and cropping the image vertically or horizontally if necessary) and
         * center its content.
         *
         * @param surfaceWidth the width of the surface where the image will be displayed.
         * @param surfaceWidth the height of the surface where the image will be displayed.
         * @param imageWidth the width of the image that we want to display.
         * @param imageHeight the height of the image that we want to display.
         * @return the [ImageCrop] that will center cover the image into the surface.
         */
        fun centerCoverCrop(
            surfaceWidth: Float,
            surfaceHeight: Float,
            imageWidth: Float,
            imageHeight: Float,
        ): ImageCrop {
            val uvScaleHeight: Float = imageHeight / surfaceHeight
            val uvScaleWidth: Float = imageWidth / surfaceWidth

            val uvScale =
                if (imageWidth / imageHeight > surfaceWidth / surfaceHeight) {
                    uvScaleHeight
                } else {
                    uvScaleWidth
                }

            val horizontalOffset = (imageWidth - surfaceWidth * uvScale) / 2f
            val verticalOffset = (imageHeight - surfaceHeight * uvScale) / 2f

            return ImageCrop(horizontalOffset, verticalOffset, uvScale, uvScale)
        }
    }
}
