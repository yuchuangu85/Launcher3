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

import android.util.Size
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class LauncherProcessImageListenerTest {

    private class FakeImageDecoderWrapper(
        width: Int,
        height: Int,
        override val mimeType: String? = "image/png",
    ) : LauncherProcessImageListener.ImageDecoderWrapper {

        var targetSize: Size? = null

        override val size: Size = Size(width, height)

        override fun setTargetSize(width: Int, height: Int) {
            targetSize = Size(width, height)
        }
    }

    @Test
    fun onHeaderDecoded_smallerThanMax_doesNotSetTargetSize() {
        // 10x10 = 100 pixels * 8 bytes = 800 bytes. Limit 1000.
        val decoder = FakeImageDecoderWrapper(10, 10)
        val listener = LauncherProcessImageListener(1000, allowedMimeTypes)

        listener.onHeaderDecoded(decoder)

        assertThat(decoder.targetSize).isNull()
    }

    @Test
    fun onHeaderDecoded_largerThanMax_setsTargetSize() {
        // 10x10 = 100 pixels * 8 bytes = 800 bytes. Limit 200.
        // Ratio 4. Sqrt(4) = 2. Scale 0.5. -> 5x5
        val decoder = FakeImageDecoderWrapper(10, 10)
        val listener = LauncherProcessImageListener(200, allowedMimeTypes)

        listener.onHeaderDecoded(decoder)

        assertThat(decoder.targetSize).isEqualTo(Size(5, 5))
    }

    @Test
    fun onHeaderDecoded_unsupportedMimeType_throwsException() {
        val decoder = FakeImageDecoderWrapper(10, 10, mimeType = "image/unsupported")
        val listener = LauncherProcessImageListener(1000, allowedMimeTypes)

        val exception =
            assertThrows(IOException::class.java) { listener.onHeaderDecoded(decoder) }
        assertThat(exception).hasMessageThat().contains("image/unsupported")
    }

    @Test
    fun onHeaderDecoded_nullMimeType_throwsException() {
        val decoder = FakeImageDecoderWrapper(10, 10, mimeType = null)
        val listener = LauncherProcessImageListener(1000, allowedMimeTypes)

        val exception =
            assertThrows(IOException::class.java) { listener.onHeaderDecoded(decoder) }
        assertThat(exception).hasMessageThat().contains("null")
    }

    @Test
    fun onHeaderDecoded_supportedMimeTypes_doesNotThrow() {
        val listener = LauncherProcessImageListener(1000, allowedMimeTypes)

        for (mimeType in allowedMimeTypes) {
            val decoder = FakeImageDecoderWrapper(10, 10, mimeType = mimeType)
            listener.onHeaderDecoded(decoder)
        }
    }

    @Test
    fun onHeaderDecoded_supportedMimeTypesUpperCase_doesNotThrow() {
        val listener = LauncherProcessImageListener(1000, allowedMimeTypes)
        val decoder = FakeImageDecoderWrapper(10, 10, mimeType = "IMAGE/PNG")
        listener.onHeaderDecoded(decoder)
    }

    @Test
    fun getPreferredSize_smallerThanMax_returnsNull() {
        // 100x100 = 10k pixels * 8 = 80k bytes. Limit 100k.
        assertThat(LauncherProcessImageListener.getPreferredSize(100, 100, 100_000)).isNull()
    }

    @Test
    fun getPreferredSize_larger_returnsCorrectScale() {
        // 100x100 = 80k bytes. Limit 20k.
        // Ratio = 4. Sqrt(4) = 2. Scale 0.5. -> 50x50.
        assertThat(LauncherProcessImageListener.getPreferredSize(100, 100, 20_000))
            .isEqualTo(Size(50, 50))
    }

    @Test
    fun getPreferredSize_huge_returnsCorrectScale() {
        // 100x100 = 80k bytes. Limit 5k.
        // Ratio = 16. Sqrt(16) = 4. Scale 0.25 -> 25x25.
        assertThat(LauncherProcessImageListener.getPreferredSize(100, 100, 5_000))
            .isEqualTo(Size(25, 25))
    }

    @Test
    fun getPreferredSize_fractionalScale_returnsCorrectScale() {
        // 100x100 = 80k bytes. Limit 32k.
        // Ratio = 2.5. Sqrt(2.5) ~= 1.58. Scale = 0.632.
        // 100 * 0.632 = 63.
        assertThat(LauncherProcessImageListener.getPreferredSize(100, 100, 32_000))
            .isEqualTo(Size(63, 63))
    }

    private companion object {
        private val allowedMimeTypes =
            setOf(
                "image/png",
                "image/jpeg",
                "image/webp",
                "image/gif",
                "image/bmp",
                "image/x-ico",
                "image/vnd.wap.wbmp",
                "image/heif",
                "image/heic",
                "image/avif",
            )
    }
}
