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

package com.google.android.torus.core.power

/**
 * This class determines ready-to-render conditions for the engine's main loop in order to target a
 * requested frame rate.
 */
class FpsThrottler {
    companion object {
        const val NANO_TO_MILLIS = 1 / 1E6

        const val FPS_120 = 120f
        const val FPS_60 = 60f
        const val FPS_30 = 30f
        const val FPS_18 = 18f

        @Deprecated(message = "Use FPS_60 instead.") const val HIGH_FPS = 60f
        @Deprecated(message = "Use FPS_30 instead.") const val MED_FPS = 30f
        @Deprecated(message = "Use FPS_18 instead.") const val LOW_FPS = 18f

        /** Small tolerance (ms) for float precision in frame timing. */
        const val TOLERANCE_MILLIS = 1L
    }

    private var fps: Float = FPS_60

    @Volatile private var frameTimeMillis: Double = 1000.0 / fps.toDouble()
    private var lastFrameTimeNanos: Long = -1

    @Volatile private var continuousRenderingMode: Boolean = true

    @Volatile private var requestRendering: Boolean = false

    private fun updateFrameTime() {
        frameTimeMillis = 1000.0 / fps.toDouble()
    }

    /**
     * If [fps] is non-zero, update the requested FPS and calculate the frame time for the requested
     * FPS. Otherwise disable continuous rendering (on demand rendering) without changing the frame
     * rate.
     *
     * @param fps The requested FPS value.
     */
    fun updateFps(fps: Float) {
        if (fps <= 0f) {
            setContinuousRenderingMode(false)
        } else {
            setContinuousRenderingMode(true)
            this.fps = fps
            updateFrameTime()
        }
    }

    /**
     * Sets rendering mode to continuous or on demand.
     *
     * @param continuousRenderingMode When true enable continuous rendering. When false disable
     *   continuous rendering (on demand).
     */
    fun setContinuousRenderingMode(continuousRenderingMode: Boolean) {
        this.continuousRenderingMode = continuousRenderingMode
    }

    /** Request a new render frame (in on demand rendering mode). */
    fun requestRendering() {
        requestRendering = true
    }

    /**
     * Calculates whether we can render the next frame. In continuous mode return true only if
     * enough time has passed since the last render to maintain requested FPS. In on demand mode,
     * return true only if [requestRendering] was called to render the next frame.
     *
     * @param frameTimeNanos The time in nanoseconds when the current frame started.
     * @return true if we can render the next frame.
     */
    fun canRender(frameTimeNanos: Long): Boolean {
        return if (continuousRenderingMode) {
            // continuous rendering
            if (lastFrameTimeNanos == -1L) {
                true
            } else {
                val deltaMillis = (frameTimeNanos - lastFrameTimeNanos) * NANO_TO_MILLIS
                return (deltaMillis >= frameTimeMillis - TOLERANCE_MILLIS) && (fps > 0f)
            }
        } else {
            // on demand rendering
            requestRendering
        }
    }

    /**
     * Attempt to render a frame, if throttling permits it at this time. The delegate
     * [onRenderPermitted] will be called to handle the rendering if so. The delegate may decide to
     * skip the frame for any other reason, and then should return false. If the frame is actually
     * rendered, the delegate must return true to ensure that the next frame will be scheduled for
     * the correct time.
     *
     * @param frameTimeNanos The time in nanoseconds when the current frame started.
     * @param onRenderPermitted The client delegate to dispatch if rendering is permitted at this
     *   time.
     * @return true if a frame is permitted and then actually rendered.
     */
    fun tryRender(frameTimeNanos: Long, onRenderPermitted: () -> Boolean): Boolean {
        if (canRender(frameTimeNanos) && onRenderPermitted()) {
            // For pacing, record the time when the frame *started*, not when it finished rendering.
            lastFrameTimeNanos = frameTimeNanos
            requestRendering = false
            return true
        }
        return false
    }
}
