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
package com.android.quickstep

import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.Source
import android.util.Size
import androidx.annotation.VisibleForTesting
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The default image decoder listener for the Launcher process.
 *
 * This listener is used to downscale images that are larger than a certain memory limit.
 */
class LauncherProcessImageListener(
    /*
     * The maximum size in bytes of an image that will be decoded. If the image is
     * larger than this, it will be downscaled, retaining the aspect ratio, until its size
     * is no larger than this value.
     */
    val maxMemoryBytes: Long,

    /*
     * The set of mime types that are allowed to be decoded. If the image's mime type is
     * not in this set, a RuntimeException will be thrown.
     */
    val allowedMimeTypes: Set<String>,
) : ImageDecoder.OnHeaderDecodedListener {

    override fun onHeaderDecoded(decoder: ImageDecoder, info: ImageInfo, source: Source) {
        onHeaderDecoded(AndroidImageDecoderWrapper(decoder, info))
    }

    @VisibleForTesting
    fun onHeaderDecoded(decoder: ImageDecoderWrapper) {
        // First check that the image is a supported type based on our allowlist.
        val mimeType = decoder.mimeType
        if (mimeType == null || mimeType.lowercase(Locale.US) !in allowedMimeTypes) {
            throw IOException("Image mime type ($mimeType) is not allowed.")
        }

        // Remote size may have returned a giant image, so we need to defensively limit it
        // to something reasonable.
        val size = getPreferredSize(decoder.size.width, decoder.size.height, maxMemoryBytes)
        if (size != null) {
            decoder.setTargetSize(size.width, size.height)
        }
    }

    /** Abstraction for [ImageDecoder] to allow testing. */
    interface ImageDecoderWrapper {
        /** The size of the image in pixels. */
        val size: Size

        /** The mime type of the image. */
        val mimeType: String?

        /** Sets the target size for the image. */
        fun setTargetSize(width: Int, height: Int)
    }

    /**
     * A wrapper around [ImageDecoder] that implements [ImageDecoderWrapper].
     *
     * @param decoder The [ImageDecoder] to wrap.
     * @param info The [ImageInfo] of the image to decode.
     */
    private class AndroidImageDecoderWrapper(
        private val decoder: ImageDecoder,
        private val info: ImageInfo,
    ) : ImageDecoderWrapper {
        override val size: Size
            get() = info.size

        override val mimeType: String?
            get() = info.mimeType

        override fun setTargetSize(width: Int, height: Int) {
            decoder.setTargetSize(width, height)
        }
    }

    companion object {
        // Assume worst-case (RGBA_F16) to safely fit in memory.
        private const val BYTES_PER_PIXEL = 8

        /**
         * Returns the preferred size to use for the given image size and max memory.
         *
         * If the image is larger than the max memory, this will return a size that will result in
         * the image size being no larger than the max memory. If the image is smaller than the max
         * memory, this will return null.
         *
         * @param width The width of the image in pixels.
         * @param height The height of the image in pixels.
         * @param maxMemoryBytes The maximum size in bytes of the image.
         * @return The preferred size to use for the image, or null if no downscaling is needed.
         */
        fun getPreferredSize(width: Int, height: Int, maxMemoryBytes: Long): Size? {
            // estimated size is in memory as bytes
            val estimatedSize = width.toLong() * height.toLong() * BYTES_PER_PIXEL

            // If the image is larger than the max memory, we need to downscale it.
            if (estimatedSize > maxMemoryBytes) {
                // Ratio of the estimated size to the max memory.
                val maxPixels = maxMemoryBytes / BYTES_PER_PIXEL
                // Derive the scale factor to apply to the image to get the desired size.
                val scale = sqrt(maxPixels.toDouble() / (width.toLong() * height.toLong()))
                // Ensure at least 1 pixel dimensions
                // follow image decoder rounding behavior
                val targetWidth = max(1, (width * scale + 0.5).toInt())
                val targetHeight = max(1, (height * scale + 0.5).toInt())
                return Size(targetWidth, targetHeight)
            }
            return null
        }
    }
}
