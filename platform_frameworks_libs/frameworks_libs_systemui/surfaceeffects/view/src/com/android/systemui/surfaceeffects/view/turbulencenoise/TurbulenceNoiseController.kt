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
package com.android.systemui.surfaceeffects.view.turbulencenoise

import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseAnimationConfig
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseShader

/** Plays [TurbulenceNoiseView] in fade-in, main (no fading), and fade-out order. */
class TurbulenceNoiseController(private val turbulenceNoiseView: TurbulenceNoiseView) {

    companion object {
        /**
         * States of the turbulence noise animation.
         *
         * <p>The state is designed to be follow the order below: [AnimationState.FADE_IN],
         * [AnimationState.MAIN], [AnimationState.FADE_OUT].
         */
        enum class AnimationState {
            FADE_IN,
            MAIN,
            FADE_OUT,
            NOT_PLAYING,
        }
    }

    /** Current state of the animation. */
    @VisibleForTesting
    var state: AnimationState = AnimationState.NOT_PLAYING
        set(value) {
            field = value
            if (state == AnimationState.NOT_PLAYING) {
                turbulenceNoiseView.visibility = View.INVISIBLE
                turbulenceNoiseView.clearConfig()
            } else {
                turbulenceNoiseView.visibility = View.VISIBLE
            }
        }

    init {
        turbulenceNoiseView.visibility = View.INVISIBLE
    }

    /** Updates the color of the noise. */
    fun updateNoiseColor(color: Int) {
        if (state == AnimationState.NOT_PLAYING) {
            return
        }
        turbulenceNoiseView.updateColor(color)
    }

    /**
     * Plays [TurbulenceNoiseView] with the given config.
     *
     * <p>It plays fade-in, main, and fade-out animations in sequence.
     */
    fun play(
        baseType: TurbulenceNoiseShader.Companion.Type,
        config: TurbulenceNoiseAnimationConfig,
    ) {
        if (state != AnimationState.NOT_PLAYING) {
            return // Ignore if any of the animation is playing.
        }

        turbulenceNoiseView.initShader(baseType, config)
        playFadeInAnimation()
    }

    // TODO(b/237282226): Support force finish.
    /** Finishes the main animation, which triggers the fade-out animation. */
    fun finish() {
        if (state == AnimationState.MAIN) {
            turbulenceNoiseView.finish(nextAnimation = this::playFadeOutAnimation)
        }
    }

    private fun playFadeInAnimation() {
        if (state != AnimationState.NOT_PLAYING) {
            return
        }
        state = AnimationState.FADE_IN

        turbulenceNoiseView.playFadeIn(this::playMainAnimation)
    }

    private fun playMainAnimation() {
        if (state != AnimationState.FADE_IN) {
            return
        }
        state = AnimationState.MAIN

        turbulenceNoiseView.play(this::playFadeOutAnimation)
    }

    private fun playFadeOutAnimation() {
        if (state != AnimationState.MAIN) {
            return
        }
        state = AnimationState.FADE_OUT

        turbulenceNoiseView.playFadeOut(onAnimationEnd = { state = AnimationState.NOT_PLAYING })
    }
}
