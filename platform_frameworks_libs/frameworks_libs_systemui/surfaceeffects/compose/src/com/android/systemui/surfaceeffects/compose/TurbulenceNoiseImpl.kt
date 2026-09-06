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

package com.android.systemui.surfaceeffects.compose

import android.graphics.RenderEffect
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import com.android.systemui.surfaceeffects.compose.RippleEffectNodeElement.Companion
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseAnimationConfig
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseShader
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseShader.Companion.BACKGROUND_UNIFORM
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Core internal function that applies a [TurbulenceNoiseShader] effect to the modified Composable.
 *
 * @param shaderType The desired type of turbulence noise effect. **This parameter is expected to be
 *   static and should not change after the initial composition.**
 * @param shaderConfig The configuration ([TurbulenceNoiseAnimationConfig]) defining movement,
 *   colors, and grid size etc.
 * @param isEnabled A boolean controlling the fade-in (enabled) or fade-out (disabled) of the
 *   effect.
 * @param onAnimationFinished A callback triggered exclusively when the fade-out animation
 *   completes. This is not invoked during the initial fade-in or while the effect is active.
 */
internal fun Modifier.turbulenceNoiseImpl(
    shaderType: TurbulenceNoiseShader.Companion.Type,
    shaderConfig: TurbulenceNoiseAnimationConfig,
    isEnabled: Boolean,
    onAnimationFinished: () -> Unit,
) = this then TurbulenceNoiseNodeElement(shaderType, shaderConfig, isEnabled, onAnimationFinished)

/**
 * [DrawModifierNode] implementation for the turbulence noise effect.
 *
 * @property shaderType The static type of noise effect being rendered.
 * @property shaderConfig The configuration for the noise.
 * @property isEnabled Controls the visibility fade state.
 * @property onAnimationFinished A callback triggered exclusively when the fade-out animation
 *   completes. This is not invoked during the initial fade-in or while the effect is active.
 */
@VisibleForTesting
class TurbulenceNoiseNode(
    val shaderType: TurbulenceNoiseShader.Companion.Type,
    var shaderConfig: TurbulenceNoiseAnimationConfig,
    var isEnabled: Boolean,
    val onAnimationFinished: () -> Unit,
) : DelegatingNode() {
    private var lastSize = Size(0f, 0f)
    internal val runtimeShader = TurbulenceNoiseShader(shaderType)
    private val shaderBrush = ShaderBrush(runtimeShader)
    private val isOverlay = shaderType == TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE_SIMPLE

    private var progressAnimatableJob: Job? = null
    private var fadingAnimatableJob: Job? = null

    // Raw, linear animation progress from 0f to 1f over the maxDuration.
    @VisibleForTesting var rawProgress by mutableFloatStateOf(0f)

    // Fading progress from 0f to 1f (Fade-In) or 1f to 0f (Fade-Out).
    @VisibleForTesting var fadingProgress by mutableFloatStateOf(0f)

    // Tracks the automatic fade-out
    private var autoFadeOutProgress by mutableFloatStateOf(0f)

    init {
        delegate(
            CacheDrawModifierNode {
                val noiseGraphicsLayer = obtainGraphicsLayer()
                onDrawWithContent {
                    val targetSize =
                        if (shaderConfig.width != 0f && shaderConfig.height != 0f) {
                            Size(shaderConfig.width, shaderConfig.height)
                        } else {
                            size
                        }
                    if (lastSize != targetSize) {
                        runtimeShader.setSize(targetSize.width, targetSize.height)
                        lastSize = targetSize
                    }
                    runtimeShader.setPixelDensity(density)
                    val maxDuration = shaderConfig.maxDuration
                    val timeElapsed = rawProgress * maxDuration / 1000
                    val noiseMoveX =
                        shaderConfig.noiseMoveSpeedX * timeElapsed + shaderConfig.noiseOffsetX
                    val noiseMoveY =
                        shaderConfig.noiseMoveSpeedY * timeElapsed + shaderConfig.noiseOffsetY
                    val noiseMoveZ =
                        shaderConfig.noiseMoveSpeedZ * timeElapsed + shaderConfig.noiseOffsetZ
                    runtimeShader.setNoiseMove(noiseMoveX, noiseMoveY, noiseMoveZ)
                    // Calculate final opacity factor, which is the minimum of the manual fade
                    // (fadingProgress)
                    // and the automatic end-of-life fade (autoFadeOutProgress).
                    val opacityFactor = min(fadingProgress, autoFadeOutProgress)
                    runtimeShader.setOpacity(opacityFactor * shaderConfig.luminosityMultiplier)

                    if (isOverlay) {
                        drawContent()
                        drawRect(shaderBrush, topLeft = Offset.Zero, size = size)
                    } else {
                        noiseGraphicsLayer.record { this@onDrawWithContent.drawContent() }
                        noiseGraphicsLayer.apply {
                            this.renderEffect =
                                RenderEffect.createRuntimeShaderEffect(
                                        runtimeShader,
                                        BACKGROUND_UNIFORM,
                                    )
                                    .asComposeRenderEffect()
                            this.compositingStrategy = CompositingStrategy.Offscreen
                        }

                        drawLayer(noiseGraphicsLayer)
                    }
                }
            }
        )
    }

    override fun onAttach() {
        runtimeShader.applyConfig(shaderConfig)
        if (isEnabled) {
            startProgressAnimatableJob()
            startFadingAnimatableJob()
        }
    }

    internal fun startProgressAnimatableJob() {
        progressAnimatableJob?.cancel()
        progressAnimatableJob =
            coroutineScope.launch {
                if (isEnabled) {
                    Animatable(0f).animateTo(
                        1f,
                        animationSpec =
                            tween(
                                durationMillis = shaderConfig.maxDuration.toInt(),
                                easing = LinearEasing,
                            ),
                    ) {
                        rawProgress = value
                        autoFadeOutProgress =
                            min(
                                1f,
                                (1 - rawProgress) * shaderConfig.maxDuration /
                                    shaderConfig.fadeOutDuration,
                            )
                    }
                    onAnimationFinished()
                }
            }
    }

    internal fun startFadingAnimatableJob() {
        fadingAnimatableJob?.cancel()
        fadingAnimatableJob =
            coroutineScope.launch {
                if (isEnabled) {
                    Animatable(0f).animateTo(
                        1f,
                        animationSpec = tween(durationMillis = shaderConfig.fadeInDuration.toInt()),
                    ) {
                        fadingProgress = value
                    }
                } else {
                    Animatable(fadingProgress).animateTo(
                        0f,
                        animationSpec = tween(durationMillis = shaderConfig.fadeOutDuration.toInt()),
                    ) {
                        fadingProgress = value
                    }
                    progressAnimatableJob?.cancel()
                    onAnimationFinished()
                }
            }
    }
}

@VisibleForTesting
data class TurbulenceNoiseNodeElement(
    val shaderType: TurbulenceNoiseShader.Companion.Type,
    val shaderConfig: TurbulenceNoiseAnimationConfig,
    val isEnabled: Boolean,
    val onAnimationFinished: () -> Unit,
) : ModifierNodeElement<TurbulenceNoiseNode>() {
    @VisibleForTesting lateinit var node: TurbulenceNoiseNode

    override fun create(): TurbulenceNoiseNode {
        return TurbulenceNoiseNode(shaderType, shaderConfig, isEnabled, onAnimationFinished).also {
            node = it
        }
    }

    override fun update(node: TurbulenceNoiseNode) {
        val isShaderTypeChanged = node.shaderType != shaderType
        if (isShaderTypeChanged) {
            throw IllegalStateException(
                "Changing shaderType on an existing TurbulenceNoiseEffect" +
                    " is not supported. This requires the surrounding Composable to to force " +
                    "detaching/re-attaching the effect. Old: ${node.shaderType}, New: $shaderType"
            )
        }

        val isConfigChanged = node.shaderConfig != shaderConfig
        val isEnabledChanged = node.isEnabled != isEnabled

        node.shaderConfig = shaderConfig
        node.isEnabled = isEnabled

        if (isConfigChanged) {
            node.runtimeShader.applyConfig(shaderConfig)
        }
        // Restart the continuous progress animation only when the effect is enabled
        if (isEnabledChanged && isEnabled) {
            node.startProgressAnimatableJob()
        }
        if (isEnabledChanged) {
            node.startFadingAnimatableJob()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "TurbulenceNoise"
        properties["shaderType"] = shaderType
        properties["config"] = shaderConfig
        properties["isEnabled"] = isEnabled
    }

    companion object {
        private val TAG = TurbulenceNoiseNode::class.simpleName
    }
}
