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

package com.android.wallpaper.weathereffects.graphics

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import androidx.annotation.VisibleForTesting
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** A wrapper that handles drawing into a [HardwareBuffer] and releasing resources. */
class FrameBuffer(width: Int, height: Int, format: Int = HardwareBuffer.RGBA_8888) {

    private val buffer =
        HardwareBuffer.create(
            width,
            height,
            format,
            /* layers = */ 1,
            // USAGE_GPU_SAMPLED_IMAGE: buffer will be read by the GPU
            // USAGE_GPU_COLOR_OUTPUT: buffer will be written by the GPU
            /* usage= */ HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
    private val renderer = HardwareBufferRenderer(buffer)
    private val node =
        RenderNode("content").also {
            it.setPosition(0, 0, width, height)
            renderer.setContentRoot(it)
        }

    private val executor = Executors.newFixedThreadPool(/* nThreads= */ 1)
    @VisibleForTesting val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

    /**
     * Recording drawing commands.
     *
     * @return RecordingCanvas
     */
    fun beginDrawing(): RecordingCanvas {
        return node.beginRecording()
    }

    /** Ends drawing. Must be paired with [beginDrawing]. */
    fun endDrawing() {
        node.endRecording()
    }

    /** Closes the [FrameBuffer]. */
    fun close() {
        buffer.close()
        renderer.close()
        executor.shutdown()
    }

    /**
     * Invokes the [onImageReady] callback when the new image is acquired, which is associated with
     * the frame buffer.
     *
     * @param onImageReady callback that will be called once the image is ready.
     * @param callbackExecutor executor to use to trigger the callback. Likely to be the main
     *   executor.
     */
    fun tryObtainingImage(onImageReady: (image: Bitmap) -> Unit, callbackExecutor: Executor) {
        if (renderer.isClosed) return
        renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(executor) { result ->
            if (result.status == HardwareBufferRenderer.RenderResult.SUCCESS) {
                result.fence.await(Duration.ofMillis(RESULT_FENCE_TIME_OUT))
                if (!buffer.isClosed) {
                    if (!buffer.isClosed) {
                        Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.let {
                            callbackExecutor.execute { onImageReady.invoke(it) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Configure the [FrameBuffer] to apply to this RenderNode. This will apply a visual effect to
     * the end result of the contents of this RenderNode before it is drawn into the destination.
     *
     * @param renderEffect to be applied to the [FrameBuffer]. Passing null clears all previously
     *   configured RenderEffects.
     */
    fun setRenderEffect(renderEffect: RenderEffect?) = node.setRenderEffect(renderEffect)

    companion object {
        const val RESULT_FENCE_TIME_OUT = 3000L
    }
}
