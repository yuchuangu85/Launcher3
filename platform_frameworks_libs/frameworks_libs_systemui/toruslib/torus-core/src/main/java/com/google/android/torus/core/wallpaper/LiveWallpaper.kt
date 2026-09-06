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

package com.google.android.torus.core.wallpaper

import android.app.WallpaperColors
import android.app.wallpaper.WallpaperDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.google.android.torus.core.content.ConfigurationChangeListener
import com.google.android.torus.core.engine.TorusEngine
import com.google.android.torus.core.engine.listener.TorusTouchListener
import com.google.android.torus.core.wallpaper.listener.LiveWallpaperEventListener
import com.google.android.torus.core.wallpaper.listener.LiveWallpaperKeyguardEventListener
import java.io.FileDescriptor
import java.io.PrintWriter
import java.lang.ref.WeakReference

/**
 * Implements [WallpaperService] using Filament to render the wallpaper. An instance of this class
 * should only implement [getWallpaperEngine]
 *
 * Note: [LiveWallpaper] subclasses must include the following attribute/s in the
 * AndroidManifest.xml:
 * - android:configChanges="uiMode"
 */
abstract class LiveWallpaper : WallpaperService() {
    private companion object {
        const val COMMAND_REAPPLY = "android.wallpaper.reapply"
        const val COMMAND_WAKING_UP = "android.wallpaper.wakingup"
        const val COMMAND_KEYGUARD_GOING_AWAY = "android.wallpaper.keyguardgoingaway"
        const val COMMAND_GOING_TO_SLEEP = "android.wallpaper.goingtosleep"
        const val COMMAND_PREVIEW_INFO = "android.wallpaper.previewinfo"
        const val COMMAND_LOCKSCREEN_LAYOUT_CHANGED = "android.wallpaper.lockscreen_layout_changed"
        const val COMMAND_LOCKSCREEN_TAP = "android.wallpaper.lockscreen_tap"
        const val COMMAND_KEYGUARD_APPEARING = "android.wallpaper.keyguardappearing"
        const val WALLPAPER_FLAG_NOT_FOUND = -1
    }

    // Holds the number of concurrent engines.
    private var numEngines = 0

    // We can have multiple ConfigurationChangeListener because we can have multiple engines.
    private val configChangeListeners: ArrayList<WeakReference<ConfigurationChangeListener>> =
        ArrayList()

    // This is only needed for <= android R.
    private val wakeStateChangeListeners: ArrayList<WeakReference<LiveWallpaperEngineWrapper>> =
        ArrayList()
    private lateinit var wakeStateReceiver: BroadcastReceiver

    override fun onCreate() {
        super.onCreate()

        val wakeStateChangeIntentFilter = IntentFilter()
        wakeStateChangeIntentFilter.addAction(Intent.ACTION_SCREEN_ON)
        wakeStateChangeIntentFilter.addAction(Intent.ACTION_SCREEN_OFF)

        /*
         * Only For Android R (SDK 30) or lower. Starting from S we can get wake/sleep events
         * through WallpaperService.Engine.onCommand events that should be more accurate.
         */
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            wakeStateReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val positionExtras = Bundle()
                        when (intent.action) {
                            Intent.ACTION_SCREEN_ON -> {
                                positionExtras.putInt(
                                    LiveWallpaperEventListener.WAKE_ACTION_LOCATION_X,
                                    -1,
                                )
                                positionExtras.putInt(
                                    LiveWallpaperEventListener.WAKE_ACTION_LOCATION_Y,
                                    -1,
                                )
                                wakeStateChangeListeners.forEach {
                                    it.get()?.onWake(positionExtras)
                                }
                            }

                            Intent.ACTION_SCREEN_OFF -> {
                                positionExtras.putInt(
                                    LiveWallpaperEventListener.SLEEP_ACTION_LOCATION_X,
                                    -1,
                                )
                                positionExtras.putInt(
                                    LiveWallpaperEventListener.SLEEP_ACTION_LOCATION_Y,
                                    -1,
                                )
                                wakeStateChangeListeners.forEach {
                                    it.get()?.onSleep(positionExtras)
                                }
                            }
                        }
                    }
                }
            registerReceiver(wakeStateReceiver, wakeStateChangeIntentFilter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) unregisterReceiver(wakeStateReceiver)
    }

    /**
     * Must be implemented to return a new instance of [TorusEngine]. If you want it to subscribe to
     * wallpaper interactions (offset, preview, zoom...) the engine should also implement
     * [LiveWallpaperEventListener]. If you want it to subscribe to touch events, it should
     * implement [TorusTouchListener].
     *
     * Note: You might have multiple Engines running at the same time (when the wallpaper is set as
     * the active wallpaper and the user is in the wallpaper picker viewing a preview of it as
     * well). You can track the lifecycle when *any* Engine is active using the
     * is{First/Last}ActiveInstance parameters of the create/destroy methods.
     */
    abstract fun getWallpaperEngine(
        context: Context,
        surfaceHolder: SurfaceHolder,
        wallpaperDescription: WallpaperDescription? = null,
    ): TorusEngine

    /**
     * returns a new instance of [LiveWallpaperEngineWrapper]. Caution: This function should not be
     * override when extending [LiveWallpaper] class.
     */
    override fun onCreateEngine(): Engine {
        val wrapper = LiveWallpaperEngineWrapper()
        wakeStateChangeListeners.add(WeakReference(wrapper))
        return wrapper
    }

    override fun onCreateEngine(description: WallpaperDescription): Engine? {
        val wrapper = LiveWallpaperEngineWrapper(description)
        wakeStateChangeListeners.add(WeakReference(wrapper))
        return wrapper
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        for (reference in configChangeListeners) {
            reference.get()?.onConfigurationChanged(newConfig)
        }
    }

    private fun addConfigChangeListener(configChangeListener: ConfigurationChangeListener) {
        var containsListener = false

        for (reference in configChangeListeners) {
            if (configChangeListener == reference.get()) {
                containsListener = true
                break
            }
        }

        if (!containsListener) {
            configChangeListeners.add(WeakReference(configChangeListener))
        }
    }

    private fun removeConfigChangeListener(configChangeListener: ConfigurationChangeListener) {
        for (reference in configChangeListeners) {
            if (configChangeListener == reference.get()) {
                configChangeListeners.remove(reference)
                break
            }
        }
    }

    /**
     * Class that enables to connect a [TorusEngine] with some [WallpaperService.Engine] functions.
     * The class that you use to render in a [LiveWallpaper] needs to inherit from
     * [LiveWallpaperConnector] and implement [TorusEngine].
     */
    open class LiveWallpaperConnector {
        private var wallpaperServiceEngine: WallpaperService.Engine? = null

        /**
         * Returns the information if the wallpaper is in preview mode. This value doesn't change
         * during a [TorusEngine] lifecycle, so you can know if the wallpaper is set checking that
         * on create isPreview == false.
         */
        fun isPreview(): Boolean {
            this.wallpaperServiceEngine?.let {
                return it.isPreview
            }
            return false
        }

        /** Returns the information if the wallpaper is visible. */
        fun isVisible(): Boolean {
            this.wallpaperServiceEngine?.let {
                return it.isVisible
            }
            return false
        }

        /** Triggers the [WallpaperService] to recompute the Wallpaper Colors. */
        fun notifyWallpaperColorsChanged() {
            this.wallpaperServiceEngine?.notifyColorsChanged()
        }

        /** Returns the current Engine [SurfaceHolder]. */
        fun getEngineSurfaceHolder(): SurfaceHolder? = this.wallpaperServiceEngine?.surfaceHolder

        /** Returns the wallpaper flags indicating which screen this Engine is rendering to. */
        fun getWallpaperFlags(): Int {
            if (Build.VERSION.SDK_INT >= 34) {
                this.wallpaperServiceEngine?.let {
                    return it.wallpaperFlags
                }
            }
            return WALLPAPER_FLAG_NOT_FOUND
        }

        fun setOffsetNotificationsEnabled(enabled: Boolean) {
            this.wallpaperServiceEngine?.setOffsetNotificationsEnabled(enabled)
        }

        internal fun setServiceEngineReference(wallpaperServiceEngine: WallpaperService.Engine) {
            this.wallpaperServiceEngine = wallpaperServiceEngine
        }
    }

    /**
     * Implementation of [WallpaperService.Engine] that works as a wrapper. If we used a
     * [WallpaperService.Engine] instance as the framework engine, we would find the problem that
     * the engine will be created for preview, then destroyed and recreated again when the wallpaper
     * is set. This behavior may cause to load assets multiple time for every time the Rendering
     * engine is created. Also, wrapping our [TorusEngine] inside [WallpaperService.Engine] allow us
     * to reuse [TorusEngine] in other places, like Activities.
     */
    private inner class LiveWallpaperEngineWrapper(
        private val wallpaperDescription: WallpaperDescription? = null
    ) : WallpaperService.Engine() {
        private lateinit var wallpaperEngine: TorusEngine

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Use RGBA_8888 format.
            surfaceHolder.setFormat(PixelFormat.RGBA_8888)

            /*
             * For Android 10 (SDK 29).
             * This is needed for Foldables and multiple display devices.
             */
            val context =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    displayContext ?: this@LiveWallpaper
                } else {
                    this@LiveWallpaper
                }

            wallpaperEngine = getWallpaperEngine(context, surfaceHolder, wallpaperDescription)
            numEngines++

            /*
             * It is important to call setTouchEventsEnabled in onCreate for it to work. Calling it
             * in onSurfaceCreated instead will cause the engine to be stuck in an instantiation
             * loop.
             */
            if (wallpaperEngine is TorusTouchListener) setTouchEventsEnabled(true)
        }

        override fun onApplyWallpaper(which: Int): WallpaperDescription? {
            super.onApplyWallpaper(which)
            return wallpaperEngine.applyWallpaper(which)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)

            if (wallpaperEngine is ConfigurationChangeListener) {
                addConfigChangeListener(wallpaperEngine as ConfigurationChangeListener)
            }

            if (wallpaperEngine is LiveWallpaperConnector) {
                (wallpaperEngine as LiveWallpaperConnector).setServiceEngineReference(this)
            }

            wallpaperEngine.create(numEngines == 1)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            numEngines--

            if (wallpaperEngine is ConfigurationChangeListener) {
                removeConfigChangeListener(wallpaperEngine as ConfigurationChangeListener)
            }

            var isLastInstance = false
            if (numEngines <= 0) {
                numEngines = 0
                isLastInstance = true
            }

            if (isVisible) wallpaperEngine.pause()
            wallpaperEngine.destroy(isLastInstance)
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            wallpaperEngine.resize(width, height)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            super.onOffsetsChanged(
                xOffset,
                yOffset,
                xOffsetStep,
                yOffsetStep,
                xPixelOffset,
                yPixelOffset,
            )

            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onOffsetChanged(
                    xOffset,
                    if (xOffsetStep.compareTo(0f) == 0) {
                        1.0f
                    } else {
                        xOffsetStep
                    },
                )
            }
        }

        override fun onZoomChanged(zoom: Float) {
            super.onZoomChanged(zoom)
            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onZoomChanged(zoom)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                wallpaperEngine.resume()
            } else {
                wallpaperEngine.pause()
            }
        }

        override fun onComputeColors(): WallpaperColors? {
            if (wallpaperEngine is LiveWallpaperEventListener) {
                val colors =
                    (wallpaperEngine as LiveWallpaperEventListener).computeWallpaperColors()

                if (colors != null) {
                    return colors
                }
            }

            return super.onComputeColors()
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean,
        ): Bundle? {
            when (action) {
                COMMAND_REAPPLY -> onWallpaperReapplied()
                COMMAND_WAKING_UP -> {
                    val positionExtras = extras ?: Bundle()
                    positionExtras.putInt(LiveWallpaperEventListener.WAKE_ACTION_LOCATION_X, x)
                    positionExtras.putInt(LiveWallpaperEventListener.WAKE_ACTION_LOCATION_Y, y)
                    onWake(positionExtras)
                }
                COMMAND_GOING_TO_SLEEP -> {
                    val positionExtras = extras ?: Bundle()
                    positionExtras.putInt(LiveWallpaperEventListener.SLEEP_ACTION_LOCATION_X, x)
                    positionExtras.putInt(LiveWallpaperEventListener.SLEEP_ACTION_LOCATION_Y, y)
                    onSleep(positionExtras)
                }
                COMMAND_KEYGUARD_GOING_AWAY -> onKeyguardGoingAway()
                COMMAND_PREVIEW_INFO -> onPreviewInfoReceived(extras)
                COMMAND_LOCKSCREEN_LAYOUT_CHANGED -> {
                    if (extras != null) {
                        onLockscreenLayoutChanged(extras)
                    }
                }
                COMMAND_LOCKSCREEN_TAP -> {
                    if (extras != null) {
                        onLockscreenFocalAreaTap(x, y)
                    }
                }
                COMMAND_KEYGUARD_APPEARING -> {
                    onKeyguardAppearing()
                }
            }

            if (resultRequested) return extras

            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean, animationDuration: Long) {
            super.onAmbientModeChanged(inAmbientMode, animationDuration)

            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onAmbientModeChanged(
                    inAmbientMode,
                    animationDuration,
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)

            if (wallpaperEngine is TorusTouchListener) {
                (wallpaperEngine as TorusTouchListener).onTouchEvent(event)
            }
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            wallpaperEngine.onWallpaperFlagsChanged(which)
        }

        /** This is overriding a hidden API [WallpaperService.shouldZoomOutWallpaper]. */
        override fun shouldZoomOutWallpaper(): Boolean {
            if (wallpaperEngine is LiveWallpaperEventListener) {
                return (wallpaperEngine as LiveWallpaperEventListener).shouldZoomOutWallpaper()
            }
            return false
        }

        override fun dump(
            prefix: String?,
            fd: FileDescriptor?,
            out: PrintWriter?,
            args: Array<out String>?,
        ) {
            super.dump(prefix, fd, out, args)
            if (out == null) return
            wallpaperEngine.dump(prefix, fd, out, args)
        }

        fun onWake(extras: Bundle) {
            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onWake(extras)
            }
        }

        fun onSleep(extras: Bundle) {
            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onSleep(extras)
            }
        }

        fun onWallpaperReapplied() {
            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onWallpaperReapplied()
            }
        }

        fun onKeyguardGoingAway() {
            if (wallpaperEngine is LiveWallpaperKeyguardEventListener) {
                (wallpaperEngine as LiveWallpaperKeyguardEventListener).onKeyguardGoingAway()
            }
        }

        fun onKeyguardAppearing() {
            if (wallpaperEngine is LiveWallpaperKeyguardEventListener) {
                (wallpaperEngine as LiveWallpaperKeyguardEventListener).onKeyguardAppearing()
            }
        }

        fun onPreviewInfoReceived(extras: Bundle?) {
            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onPreviewInfoReceived(extras)
            }
        }

        fun onLockscreenLayoutChanged(extras: Bundle) {
            if (wallpaperEngine is LiveWallpaperEventListener) {
                (wallpaperEngine as LiveWallpaperEventListener).onLockscreenLayoutChanged(extras)
            }
        }

        fun onLockscreenFocalAreaTap(x: Int, y: Int) {
            if (wallpaperEngine is TorusTouchListener) {
                (wallpaperEngine as TorusTouchListener).onLockscreenFocalAreaTap(x, y)
            }
        }
    }
}
