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

package com.android.systemui.surfaceeffects.view.glowboxeffect

import android.animation.ValueAnimator
import android.graphics.Paint
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import com.android.systemui.surfaceeffects.core.glowboxeffect.GlowBoxConfig
import com.android.systemui.surfaceeffects.core.glowboxeffect.GlowBoxShader
import com.android.systemui.surfaceeffects.core.utils.MathUtils
import com.android.systemui.surfaceeffects.view.PaintDrawCallback

/** Glow box effect where the box moves from start to end positions defined in the [config]. */
class GlowBoxEffect(
    private var config: GlowBoxConfig,
    private val paintDrawCallback: PaintDrawCallback,
    private val stateChangedCallback: AnimationStateChangedCallback? = null,
) {
    private val glowBoxShader =
        GlowBoxShader().apply {
            setSize(config.width, config.height)
            setCenter(config.startCenterX, config.startCenterY)
            setBlur(config.blurAmount)
            setColor(config.color)
        }
    private var animator: ValueAnimator? = null
    @VisibleForTesting var state: AnimationState = AnimationState.NOT_PLAYING
    private val paint = Paint().apply { shader = glowBoxShader }

    fun updateConfig(newConfig: GlowBoxConfig) {
        this.config = newConfig

        with(glowBoxShader) {
            setSize(config.width, config.height)
            setCenter(config.startCenterX, config.startCenterY)
            setBlur(config.blurAmount)
            setColor(config.color)
        }
    }

    fun play() {
        if (state != AnimationState.NOT_PLAYING) {
            return
        }

        playFadeIn()
    }

    /** Finishes the animation with fade out. */
    fun finish(force: Boolean = false) {
        // If it's playing fade out, cancel immediately.
        if (force && state == AnimationState.FADE_OUT) {
            animator?.cancel()
            return
        }

        // If it's playing either fade in or main, fast-forward to fade out.
        if (state == AnimationState.FADE_IN || state == AnimationState.MAIN) {
            animator?.pause()
            playFadeOut()
        }

        // At this point, animation state should be fade out. Cancel it if force is true.
        if (force) {
            animator?.cancel()
        }
    }

    private fun playFadeIn() {
        if (state == AnimationState.FADE_IN) {
            return
        }
        state = AnimationState.FADE_IN
        stateChangedCallback?.onStart()

        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = config.fadeInDuration
                addUpdateListener {
                    val progress = it.animatedValue as Float
                    glowBoxShader.setCenter(
                        MathUtils.lerp(config.startCenterX, config.endCenterX, progress),
                        MathUtils.lerp(config.startCenterY, config.endCenterY, progress),
                    )

                    draw()
                }

                doOnEnd {
                    animator = null
                    playMain()
                }

                start()
            }
    }

    private fun playMain() {
        if (state == AnimationState.MAIN) {
            return
        }
        state = AnimationState.MAIN

        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = config.duration
                addUpdateListener { draw() }

                doOnEnd {
                    animator = null
                    playFadeOut()
                }

                start()
            }
    }

    private fun playFadeOut() {
        if (state == AnimationState.FADE_OUT) return
        state = AnimationState.FADE_OUT

        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = config.fadeOutDuration
                addUpdateListener {
                    val progress = it.animatedValue as Float
                    glowBoxShader.setCenter(
                        MathUtils.lerp(config.endCenterX, config.startCenterX, progress),
                        MathUtils.lerp(config.endCenterY, config.startCenterY, progress),
                    )

                    draw()
                }

                doOnEnd {
                    animator = null
                    state = AnimationState.NOT_PLAYING
                    stateChangedCallback?.onEnd()
                }

                start()
            }
    }

    private fun draw() {
        paintDrawCallback.onDraw(paint)
    }

    /**
     * The animation state of the effect. The animation state transitions as follows: [FADE_IN] ->
     * [MAIN] -> [FADE_OUT] -> [NOT_PLAYING].
     */
    enum class AnimationState {
        FADE_IN,
        MAIN,
        FADE_OUT,
        NOT_PLAYING,
    }

    interface AnimationStateChangedCallback {
        /**
         * Triggered when the animation starts, specifically when the states goes from
         * [AnimationState.NOT_PLAYING] to [AnimationState.FADE_IN].
         */
        fun onStart()

        /**
         * Triggered when the animation ends, specifically when the states goes from
         * [AnimationState.FADE_OUT] to [AnimationState.NOT_PLAYING].
         */
        fun onEnd()
    }
}
