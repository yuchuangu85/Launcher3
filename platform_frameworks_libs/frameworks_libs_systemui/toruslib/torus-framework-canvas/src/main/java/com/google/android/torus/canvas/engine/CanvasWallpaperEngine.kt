/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.torus.canvas.engine

import android.graphics.Canvas
import android.graphics.RuntimeShader
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.annotation.VisibleForTesting
import com.google.android.torus.core.engine.TorusEngine
import com.google.android.torus.core.power.FpsThrottler
import com.google.android.torus.core.time.TimeController
import com.google.android.torus.core.wallpaper.LiveWallpaper
import java.io.PrintWriter

/**
 * Class that implements [TorusEngine] using Canvas and can be used in a [LiveWallpaper]. This class
 * also inherits from [LiveWallpaper.LiveWallpaperConnector] which allows to do some calls related
 * to Live Wallpapers, like the method [isPreview] or [notifyWallpaperColorsChanged].
 *
 * By default it won't start [startUpdateLoop]. To run animations and update logic per frame, call
 * [startUpdateLoop] and [stopUpdateLoop] when it's no longer needed.
 *
 * This class also can be used with the new RuntimeShader.
 */
abstract class CanvasWallpaperEngine(
    /** The default [SurfaceHolder] to be used. */
    private val defaultHolder: SurfaceHolder,

    /**
     * Defines if the surface should be hardware accelerated or not. If you are using
     * [RuntimeShader], this value should be set to true. When setting it to true, some functions
     * might not be supported. Please refer to the documentation:
     * https://developer.android.com/guide/topics/graphics/hardware-accel#unsupported
     */
    private val hardwareAccelerated: Boolean = false,
) : LiveWallpaper.LiveWallpaperConnector(), TorusEngine {

    private val choreographer = Choreographer.getInstance()
    private val timeController =
        TimeController().also { it.resetDeltaTime(SystemClock.uptimeMillis()) }
    private val frameScheduler = FrameCallback()
    private val fpsThrottler = FpsThrottler()

    protected var screenSize = Size(0, 0)
        private set

    private var resizeCalled: Boolean = false

    private var isWallpaperEngineVisible = false
    /**
     * Indicates whether the engine#onCreate is called.
     *
     * TODO(b/277672928): These two booleans were introduced as a workaround where
     *   [onSurfaceRedrawNeeded] called after an [onSurfaceDestroyed], without [onCreate]/
     *   [onSurfaceCreated] being called between those. Remove these once it's fixed in
     *   [WallpaperService].
     */
    private var isCreated = false
    private var shouldInvokeResume = false

    /** Callback to handle when the [TorusEngine] has been created. */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun onCreate(isFirstActiveInstance: Boolean) {
        // No-op. Ready for being overridden by children.
    }

    /** Callback to handle when the [TorusEngine] has been resumed. */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun onResume() {
        // No-op. Ready for being overridden by children.
    }

    /** Callback to handle when the [TorusEngine] has been paused. */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun onPause() {
        // No-op. Ready for being overridden by children.
    }

    /**
     * Callback to handle when the surface holding the [TorusEngine] has changed its size.
     *
     * @param size The new size of the surface holding the [TorusEngine].
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun onResize(size: Size) {
        // No-op. Ready for being overridden by children.
    }

    /**
     * Callback to handle when the [TorusEngine] needs to be updated. Call [startUpdateLoop] to
     * initiate the frame loop; call [stopUpdateLoop] to end the loop. The client is supposed to
     * update logic and render in this loop.
     *
     * @param deltaMillis The time in millis since the last time [onUpdate] was called.
     * @param frameTimeNanos The time in nanoseconds when the frame started being rendered, in the
     *   [System.nanoTime] timebase.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun onUpdate(deltaMillis: Long, frameTimeNanos: Long) {
        // No-op. Ready for being overridden by children.
    }

    /**
     * Callback to handle when we need to destroy the surface.
     *
     * @param isLastActiveInstance Whether this was the last wallpaper engine instance (until the
     *   next [onCreate]).
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun onDestroy(isLastActiveInstance: Boolean) {
        // No-op. Ready for being overridden by children.
    }

    final override fun create(isFirstActiveInstance: Boolean) {
        screenSize =
            Size(
                getCurrentSurfaceHolder().surfaceFrame.width(),
                getCurrentSurfaceHolder().surfaceFrame.height(),
            )

        onCreate(isFirstActiveInstance)

        isCreated = true

        if (shouldInvokeResume) {
            Log.e(
                TAG,
                "Force invoke resume. onVisibilityChanged must have been called" +
                    "before onCreate.",
            )
            resume()
            shouldInvokeResume = false
        }
    }

    final override fun pause() {
        if (!isCreated) {
            Log.e(
                TAG,
                "Engine is not yet created but pause is called. Set a flag to invoke" +
                    " resume on next create.",
            )
            shouldInvokeResume = true
            return
        }

        if (isWallpaperEngineVisible) {
            onPause()
            isWallpaperEngineVisible = false
        }
    }

    final override fun resume() {
        if (!isCreated) {
            Log.e(
                TAG,
                "Engine is not yet created but resume is called. Set a flag to " +
                    "invoke resume on next create.",
            )
            shouldInvokeResume = true
            return
        }

        if (!isWallpaperEngineVisible) {
            onResume()
            isWallpaperEngineVisible = true
        }
    }

    final override fun resize(width: Int, height: Int) {
        resizeCalled = true

        screenSize = Size(width, height)
        onResize(screenSize)
    }

    final override fun destroy(isLastActiveInstance: Boolean) {
        choreographer.removeFrameCallback(frameScheduler)
        timeController.resetDeltaTime(SystemClock.uptimeMillis())

        // Always detach the surface before destroying the engine
        onDestroy(isLastActiveInstance)
    }

    /**
     * Renders to canvas. Use this in [onUpdate] loop. This will automatically throttle (or limit)
     * FPS that was set via [setFpsLimit].
     *
     * @param frameTimeNanos The time in nanoseconds when the frame started being rendered, in the
     *   [System.nanoTime] timebase.
     * @param onRender The callback triggered when the canvas is ready for render.
     * @return Whether it is rendered.
     */
    fun renderWithFpsLimit(frameTimeNanos: Long, onRender: (canvas: Canvas) -> Unit): Boolean {
        if (resizeCalled) {
            /**
             * Skip rendering a frame to a buffer with potentially-outdated dimensions, and request
             * redraw in the next frame.
             */
            resizeCalled = false

            fpsThrottler.requestRendering()
            return renderWithFpsLimit(frameTimeNanos, onRender)
        }

        return fpsThrottler.tryRender(frameTimeNanos) { renderToCanvas(onRender) }
    }

    /**
     * Renders to canvas.
     *
     * @param onRender The callback triggered when the canvas is ready for render.
     * @return Whether it is rendered.
     */
    fun render(onRender: (canvas: Canvas) -> Unit): Boolean {
        if (resizeCalled) {
            /**
             * Skip rendering a frame to a buffer with potentially-outdated dimensions, and request
             * redraw in the next frame.
             */
            resizeCalled = false
            return render(onRender)
        }

        return renderToCanvas(onRender)
    }

    /**
     * Sets the FPS limit. See [FpsThrottler] for the FPS constants. The max FPS will be the screen
     * refresh (VSYNC) rate.
     *
     * @param fps Desired mas FPS.
     */
    protected fun setFpsLimit(fps: Float) {
        fpsThrottler.updateFps(fps)
    }

    /** Starts the update loop. */
    protected fun startUpdateLoop() {
        if (!frameScheduler.running) {
            frameScheduler.running = true
            choreographer.postFrameCallback(frameScheduler)
        }
    }

    /** Stops the update loop. */
    protected fun stopUpdateLoop() {
        if (frameScheduler.running) {
            frameScheduler.running = false
            choreographer.removeFrameCallback(frameScheduler)
        }
    }

    private fun renderToCanvas(onRender: (canvas: Canvas) -> Unit): Boolean {
        val surfaceHolder = getCurrentSurfaceHolder()
        if (!surfaceHolder.surface.isValid) return false
        var canvas: Canvas? = null

        try {
            canvas =
                if (hardwareAccelerated) {
                    surfaceHolder.lockHardwareCanvas()
                } else {
                    surfaceHolder.lockCanvas()
                } ?: return false

            onRender(canvas)
        } catch (e: java.lang.Exception) {
            Log.e("canvas_exception", "canvas exception", e)
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }
        return true
    }

    private fun getCurrentSurfaceHolder(): SurfaceHolder = getEngineSurfaceHolder() ?: defaultHolder

    /** Implementation of [Choreographer.FrameCallback] which triggers [onUpdate]. */
    inner class FrameCallback : Choreographer.FrameCallback {
        internal var running: Boolean = false

        override fun doFrame(frameTimeNanos: Long) {
            if (running) choreographer.postFrameCallback(this)
            // onUpdate should be called for every V_SYNC.
            val frameTimeMillis = frameTimeNanos / 1000_000
            timeController.updateDeltaTime(frameTimeMillis)
            onUpdate(timeController.deltaTimeMillis, frameTimeNanos)
            timeController.resetDeltaTime(frameTimeMillis)
        }
    }

    /**
     * Override this for dumpsys.
     *
     * You still need to have your WallpaperService overriding [dump] and call
     * [CanvasWallpaperEngine.dump].
     *
     * Usage: adb shell dumpsys activity service ${your_wallpaper_service_name}.
     */
    @Deprecated(
        message =
            "Implement TorusEngine.dump instead, which will be called automatically with the" +
                "default implementation of WallpaperService.dump",
        replaceWith = ReplaceWith("TorusEngine.dump"),
    )
    open fun dump(out: PrintWriter) = Unit

    private companion object {
        private val TAG: String = CanvasWallpaperEngine::class.java.simpleName
    }
}
