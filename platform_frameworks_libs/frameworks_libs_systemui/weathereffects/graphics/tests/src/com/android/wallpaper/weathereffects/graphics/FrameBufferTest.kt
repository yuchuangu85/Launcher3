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
package com.android.wallpaper.weathereffects.graphics

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.wallpaper.weathereffects.graphics.FrameBuffer.Companion.RESULT_FENCE_TIME_OUT
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameBufferTest {

    private val executor = MoreExecutors.directExecutor()

    @Test
    fun onImageReady_invokesCallback() {
        val expectedWidth = 1
        val expectedHeight = 1
        val expectedColor = Color.RED

        val buffer = FrameBuffer(expectedWidth, expectedHeight)
        buffer.beginDrawing().drawColor(expectedColor)
        buffer.endDrawing()

        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null

        buffer.tryObtainingImage(
            {
                bitmap = it
                latch.countDown()
            },
            executor,
        )

        assertThat(latch.await(RESULT_FENCE_TIME_OUT, TimeUnit.MILLISECONDS)).isTrue()

        assertThat(bitmap).isNotNull()
        val resultBitmap = bitmap!!
        assertThat(resultBitmap.width).isEqualTo(expectedWidth)
        assertThat(resultBitmap.height).isEqualTo(expectedHeight)
        assertThat(resultBitmap.colorSpace).isEqualTo(buffer.colorSpace)

        // Color sampling only works on software bitmap.
        val softwareBitmap = resultBitmap.copy(Bitmap.Config.ARGB_8888, false)
        assertThat(softwareBitmap.getPixel(0, 0)).isEqualTo(expectedColor)
    }

    @Test
    fun close_onImageReady_doesNotInvokeCallback() {
        val buffer = FrameBuffer(width = 1, height = 1)
        buffer.beginDrawing().drawColor(Color.RED)
        buffer.endDrawing()

        // Call close before we obtain image.
        buffer.close()

        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null

        buffer.tryObtainingImage(
            {
                bitmap = it
                latch.countDown()
            },
            executor,
        )

        assertThat(latch.await(RESULT_FENCE_TIME_OUT, TimeUnit.MILLISECONDS)).isFalse()
        assertThat(bitmap).isNull()
    }
}
