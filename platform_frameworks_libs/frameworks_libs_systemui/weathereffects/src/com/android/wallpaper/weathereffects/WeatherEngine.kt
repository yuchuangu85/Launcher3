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

package com.android.wallpaper.weathereffects

import android.animation.ValueAnimator
import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.SurfaceHolder
import androidx.annotation.FloatRange
import com.android.wallpaper.weathereffects.domain.WeatherEffectsInteractor
import com.android.wallpaper.weathereffects.graphics.WeatherEffect
import com.android.wallpaper.weathereffects.graphics.clouds.CloudsEffect
import com.android.wallpaper.weathereffects.graphics.clouds.CloudsEffectConfig
import com.android.wallpaper.weathereffects.graphics.fog.FogEffect
import com.android.wallpaper.weathereffects.graphics.fog.FogEffectConfig
import com.android.wallpaper.weathereffects.graphics.none.NoEffect
import com.android.wallpaper.weathereffects.graphics.rain.RainEffect
import com.android.wallpaper.weathereffects.graphics.rain.RainEffectConfig
import com.android.wallpaper.weathereffects.graphics.snow.SnowEffect
import com.android.wallpaper.weathereffects.graphics.snow.SnowEffectConfig
import com.android.wallpaper.weathereffects.graphics.sun.SunEffect
import com.android.wallpaper.weathereffects.graphics.sun.SunEffectConfig
import com.android.wallpaper.weathereffects.graphics.utils.MatrixUtils.centerCropMatrix
import com.android.wallpaper.weathereffects.provider.WallpaperInfoContract
import com.android.wallpaper.weathereffects.shared.model.WallpaperImageModel
import com.google.android.torus.canvas.engine.CanvasWallpaperEngine
import com.google.android.torus.core.wallpaper.listener.LiveWallpaperEventListener
import com.google.android.torus.core.wallpaper.listener.LiveWallpaperKeyguardEventListener
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WeatherEngine(
    defaultHolder: SurfaceHolder,
    private val applicationScope: CoroutineScope,
    private val interactor: WeatherEffectsInteractor,
    private val context: Context,
    private val isDebugActivity: Boolean = false,
    hardwareAccelerated: Boolean = true,
) :
    CanvasWallpaperEngine(defaultHolder, hardwareAccelerated),
    LiveWallpaperKeyguardEventListener,
    LiveWallpaperEventListener {

    private var lockStartTime: Long = 0
    private var unlockAnimator: ValueAnimator? = null

    private var backgroundColor: WallpaperColors? = null
    private var currentAssets: WallpaperImageModel? = null
    private var activeEffect: WeatherEffect? = null
        private set(value) {
            field = value
            if (shouldTriggerUpdate()) {
                startUpdateLoop()
            } else {
                stopUpdateLoop()
            }
        }

    private var collectWallpaperImageJob: Job? = null
    private var effectTargetIntensity: Float = 1f
    private var effectIntensity: Float = 0f

    private val isPanAndZoomInExtendedWallpaperEffectsEnabled = false

    init {
        /* Load assets. */
        if (interactor.wallpaperImageModel.value == null) {
            applicationScope.launch { interactor.loadWallpaper() }
        }
    }

    override fun onCreate(isFirstActiveInstance: Boolean) {
        Log.d(TAG, "Engine created.")
        /*
         * Initialize `effectIntensity` to `effectTargetIntensity` so we show the weather effect
         * on preview and when `isDebugActivity` is true.
         *
         * isPreview() is only reliable after `onCreate`. Thus update the initial value of
         * `effectIntensity` in case it is not 0.
         */
        if (shouldSkipIntensityOutAnimation()) {
            updateCurrentIntensity(effectTargetIntensity)
        }
    }

    override fun onResize(size: Size) {
        activeEffect?.resize(size.toSizeF())
        if (activeEffect is NoEffect) {
            render { canvas -> activeEffect!!.draw(canvas) }
        }
    }

    override fun onResume() {
        collectWallpaperImageJob =
            applicationScope.launch {
                interactor.wallpaperImageModel.collect { asset ->
                    if (asset == null || asset == currentAssets) return@collect
                    currentAssets = asset
                    createWeatherEffect(asset.foreground, asset.background, asset.weatherEffect)
                    updateWallpaperColors(asset.background)
                }
            }
        if (activeEffect != null) {
            if (shouldTriggerUpdate()) startUpdateLoop()
        }
    }

    override fun onUpdate(deltaMillis: Long, frameTimeNanos: Long) {
        activeEffect?.update(deltaMillis, frameTimeNanos)

        renderWithFpsLimit(frameTimeNanos) { canvas -> activeEffect?.draw(canvas) }
    }

    override fun onPause() {
        stopUpdateLoop()
        collectWallpaperImageJob?.cancel()
        activeEffect?.reset()
    }

    override fun onDestroy(isLastActiveInstance: Boolean) {
        activeEffect?.release()
        activeEffect = null
    }

    override fun onKeyguardGoingAway() {
        // No-op.
    }

    override fun onKeyguardAppearing() {}

    override fun onOffsetChanged(xOffset: Float, xOffsetStep: Float) {
        // No-op.
    }

    override fun onZoomChanged(zoomLevel: Float) {
        // No-op.
    }

    override fun onWallpaperReapplied() {
        // No-op.
    }

    override fun shouldZoomOutWallpaper(): Boolean = true

    override fun computeWallpaperColors(): WallpaperColors? = backgroundColor

    override fun onWake(extras: Bundle) {
        // No-op.
    }

    override fun onSleep(extras: Bundle) {
        // No-op.
    }

    fun setTargetIntensity(@FloatRange(from = 0.0, to = 1.0) intensity: Float) {
        effectTargetIntensity = intensity

        /* If we don't want to animate, update the target intensity as it happens. */
        if (shouldSkipIntensityOutAnimation()) {
            updateCurrentIntensity(effectTargetIntensity)
        }
    }

    private fun createWeatherEffect(
        foreground: Bitmap,
        background: Bitmap,
        weatherEffect: WallpaperInfoContract.WeatherEffect? = null,
    ) {
        activeEffect?.release()
        activeEffect = null
        val initialMatrix =
            centerCropMatrix(
                screenSize.toSizeF(),
                SizeF(background.width.toFloat(), background.height.toFloat()),
            )
        when (weatherEffect) {
            WallpaperInfoContract.WeatherEffect.RAIN -> {
                val rainConfig =
                    RainEffectConfig(context.assets, context.resources.displayMetrics.density)
                activeEffect =
                    RainEffect(
                        rainConfig,
                        foreground,
                        background,
                        effectIntensity,
                        screenSize.toSizeF(),
                        context.mainExecutor,
                        initialMatrix,
                        isPanAndZoomInExtendedWallpaperEffectsEnabled,
                    )
            }
            WallpaperInfoContract.WeatherEffect.FOG -> {
                val fogConfig =
                    FogEffectConfig(context.assets, context.resources.displayMetrics.density)

                activeEffect =
                    FogEffect(
                        fogConfig,
                        foreground,
                        background,
                        effectIntensity,
                        screenSize.toSizeF(),
                        initialMatrix,
                        isPanAndZoomInExtendedWallpaperEffectsEnabled,
                    )
            }

            WallpaperInfoContract.WeatherEffect.CLOUDS -> {
                val cloudsConfig =
                    CloudsEffectConfig(context.assets, context.resources.displayMetrics.density)

                activeEffect =
                    CloudsEffect(
                        cloudsConfig,
                        foreground,
                        background,
                        effectIntensity,
                        screenSize.toSizeF(),
                        initialMatrix,
                        isPanAndZoomInExtendedWallpaperEffectsEnabled,
                    )
            }

            WallpaperInfoContract.WeatherEffect.SNOW -> {
                val snowConfig =
                    SnowEffectConfig(context.assets, context.resources.displayMetrics.density)
                activeEffect =
                    SnowEffect(
                        snowConfig,
                        foreground,
                        background,
                        effectIntensity,
                        screenSize.toSizeF(),
                        context.mainExecutor,
                        initialMatrix,
                        isPanAndZoomInExtendedWallpaperEffectsEnabled,
                    )
            }
            WallpaperInfoContract.WeatherEffect.SUN -> {
                val snowConfig =
                    SunEffectConfig(context.assets, context.resources.displayMetrics.density)
                activeEffect =
                    SunEffect(
                        snowConfig,
                        foreground,
                        background,
                        effectIntensity,
                        screenSize.toSizeF(),
                        initialMatrix,
                        isPanAndZoomInExtendedWallpaperEffectsEnabled,
                    )
            }
            else -> {
                activeEffect = NoEffect(foreground, background, screenSize.toSizeF())
            }
        }

        updateCurrentIntensity()

        render { canvas -> activeEffect?.draw(canvas) }
    }

    private fun shouldTriggerUpdate(): Boolean {
        return activeEffect != null && activeEffect !is NoEffect
    }

    private fun Size.toSizeF(): SizeF = SizeF(width.toFloat(), height.toFloat())

    private fun updateCurrentIntensity(intensity: Float = effectIntensity) {
        if (effectIntensity != intensity) {
            effectIntensity = intensity
        }
        activeEffect?.setIntensity(effectIntensity)
    }

    private fun playIntensityFadeOutAnimation(animationType: AnimationType) {
        when (animationType) {
            AnimationType.WAKE -> {
                unlockAnimator?.cancel()
                updateCurrentIntensity(effectTargetIntensity)
                lockStartTime = SystemClock.elapsedRealtime()
                animateWeatherIntensityOut(AUTO_FADE_DELAY_FROM_AWAY_MILLIS)
            }
            AnimationType.UNLOCK -> {
                // If already running, don't stop it.
                if (unlockAnimator?.isRunning == true) {
                    return
                }

                /*
                 * When waking up the device (from AWAY), we normally wait for a delay
                 * (AUTO_FADE_DELAY_FROM_AWAY_MILLIS) before playing the fade out animation.
                 * However, there is a situation where this might be interrupted:
                 *     AWAY -> LOCKED -> LOCKED -> ACTIVE.
                 * If this happens, we might have already waited for sometime (between
                 * AUTO_FADE_DELAY_MILLIS and AUTO_FADE_DELAY_FROM_AWAY_MILLIS). We compare how long
                 * we've waited with AUTO_FADE_DELAY_MILLIS, and if we've waited longer than
                 * AUTO_FADE_DELAY_MILLIS, we play the animation immediately. Otherwise, we wait
                 * the rest of the AUTO_FADE_DELAY_MILLIS delay.
                 */
                var delayTime = AUTO_FADE_DELAY_MILLIS
                if (unlockAnimator?.isStarted == true) {
                    val deltaTime = (SystemClock.elapsedRealtime() - lockStartTime)
                    delayTime = max(delayTime - deltaTime, 0)
                    lockStartTime = 0
                }
                unlockAnimator?.cancel()
                updateCurrentIntensity()
                animateWeatherIntensityOut(delayTime, AUTO_FADE_SHORT_DURATION_MILLIS)
            }
            AnimationType.NONE -> {
                // No-op.
            }
        }
    }

    private fun shouldSkipIntensityOutAnimation(): Boolean = isPreview() || isDebugActivity

    private fun animateWeatherIntensityOut(
        delayMillis: Long,
        durationMillis: Long = AUTO_FADE_DURATION_MILLIS,
    ) {
        unlockAnimator =
            ValueAnimator.ofFloat(effectIntensity, 0f).apply {
                duration = durationMillis
                startDelay = delayMillis
                addUpdateListener { updatedAnimation ->
                    effectIntensity = updatedAnimation.animatedValue as Float
                    updateCurrentIntensity()
                }
                start()
            }
    }

    private fun updateWallpaperColors(background: Bitmap) {
        backgroundColor =
            WallpaperColors.fromBitmap(
                Bitmap.createScaledBitmap(
                    background,
                    256,
                    (background.width / background.height.toFloat() * 256).roundToInt(),
                    /* filter = */ true,
                )
            )
    }

    /**
     * Types of animations. Currently we animate when we wake the device (from screen off to lock
     * screen or home screen) or when whe unlock device (from lock screen to home screen).
     */
    private enum class AnimationType {
        UNLOCK,
        WAKE,
        NONE,
    }

    private companion object {
        private val TAG = WeatherEngine::class.java.simpleName

        private const val AUTO_FADE_DURATION_MILLIS: Long = 3000
        private const val AUTO_FADE_SHORT_DURATION_MILLIS: Long = 3000
        private const val AUTO_FADE_DELAY_MILLIS: Long = 1000
        private const val AUTO_FADE_DELAY_FROM_AWAY_MILLIS: Long = 2000
    }
}
