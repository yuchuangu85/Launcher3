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

package com.android.launcher3.util

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.Picture
import android.graphics.RenderNode
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.hardware.HardwareBuffer
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Extension functions for [View] and its subclasses */
object ViewEx {

    /** Goes up the view hierarchy until a view (inclusive) matching [predicate] is found. */
    inline fun View.findInParentTree(predicate: (View) -> Boolean): View? {
        var current: View = this
        while (!predicate(current)) {
            val parent = current.parent
            if (parent is View) current = parent else return null
        }
        return current
    }

    /** Returns a drawable containing a snapshot of the provided view. */
    @JvmStatic
    fun View.captureSnapshotAsDrawable(debugString: String, width: Int, height: Int): Drawable {
        /**
         * Captures the view without retaining the clip outlines, shadows etc. Used as a fallback.
         */
        fun captureAsPicture(): Drawable {
            val picture = Picture()
            draw(picture.beginRecording(width, height))
            picture.endRecording()
            return PictureDrawable(picture)
        }

        // Capture with RenderNode to retain the clip outlines
        if (Build.VERSION.SDK_INT >= 34) {
            val renderNode = RenderNode(debugString)
            renderNode.setPosition(0, 0, width, height)
            draw(renderNode.beginRecording())
            renderNode.endRecording()

            runCatching {
                    HardwareBuffer.create(
                            width,
                            height,
                            HardwareBuffer.RGBA_8888,
                            /*layers*/ 1,
                            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
                        )
                        .use { buffer ->
                            HardwareBufferRenderer(buffer).use { renderer ->
                                renderer.setContentRoot(renderNode)
                                val latch = CountDownLatch(1)
                                var renderResult: HardwareBufferRenderer.RenderResult? = null
                                renderer.obtainRenderRequest().draw(Runnable::run) { result ->
                                    renderResult = result
                                    latch.countDown()
                                }

                                latch.await(VIEW_SNAPSHOT_CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                                if (
                                    renderResult?.status ==
                                        HardwareBufferRenderer.RenderResult.SUCCESS
                                ) {
                                    val bitmap =
                                        Bitmap.wrapHardwareBuffer(
                                                buffer,
                                                ColorSpace.get(ColorSpace.Named.SRGB),
                                            )
                                            ?.copy(Bitmap.Config.ARGB_8888, false)
                                    if (bitmap != null) {
                                        return bitmap.toDrawable(resources)
                                    }
                                }
                            }
                        }
                }
                .onFailure { throwable ->
                    if (BuildConfig.IS_STUDIO_BUILD) {
                        Log.e(TAG, "Failed to capture view snapshot for $debugString: ", throwable)
                    }
                }
        }

        return captureAsPicture()
    }

    /**
     * Recursively check view tag [R.id.perform_a11y_action_on_launcher_state_normal_tag] and call
     * [View.performAccessibilityAction] on view tree. The tag is cleared after this call.
     */
    @JvmStatic
    fun View.performAccessibilityActionOnViewTree() {
        val a11yTag = R.id.perform_a11y_action_on_launcher_state_normal_tag
        (getTag(a11yTag) as? Int)?.run {
            performAccessibilityAction(this, null)
            setTag(a11yTag, null)
        }
        (this as? ViewGroup)?.run {
            (0 until childCount).map(::getChildAt).forEach {
                it.performAccessibilityActionOnViewTree()
            }
        }
    }

    /**
     * Registers the [task] to be called when the view is attached to the window. It is called
     * immediately if the view is already attached. The returned [SafeCloseable] is called when the
     * view is detached.
     */
    @JvmStatic
    fun View.registerLifecycleTask(task: () -> SafeCloseable) {
        val tracker = ViewLifecycleTracker(task)
        addOnAttachStateChangeListener(tracker)
        if (isAttachedToWindow) tracker.onViewAttachedToWindow(this)
    }

    internal class ViewLifecycleTracker(private val task: () -> SafeCloseable) :
        View.OnAttachStateChangeListener {

        private var pendingCleanupTask: SafeCloseable? = null

        override fun onViewAttachedToWindow(v: View) = cleanup(task)

        override fun onViewDetachedFromWindow(v: View) = cleanup { null }

        inline fun cleanup(newValue: () -> SafeCloseable?) {
            pendingCleanupTask?.close()
            pendingCleanupTask = newValue.invoke()
        }
    }

    private const val TAG = "ViewEx"
    private const val VIEW_SNAPSHOT_CAPTURE_TIMEOUT_MS = 1000L
}
