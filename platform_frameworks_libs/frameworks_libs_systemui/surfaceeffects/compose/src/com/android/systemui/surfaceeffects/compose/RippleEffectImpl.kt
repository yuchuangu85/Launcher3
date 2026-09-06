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

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import com.android.systemui.surfaceeffects.core.ripple.RippleAnimationConfig
import com.android.systemui.surfaceeffects.core.ripple.RippleShader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Core internal function that applies the ripple shader effect to a Composable.
 *
 * @param shaderConfig The immutable configuration ([RippleAnimationConfig]) defining size, fade,
 *   and color.
 * @param isEnabled Transitioning this from `false` to `true` starts the ripple sequence. To allow
 *   for re-triggering, the caller must reset this value to `false` after the animation concludes.
 * @param onAnimationFinished A callback executed immediately upon the completion of the ripple
 *   animation. This is the recommended hook for the caller to reset [isEnabled] to `false`,
 *   readying the node for its next invocation.
 */
internal fun Modifier.rippleEffectImpl(
    shaderConfig: RippleAnimationConfig,
    isEnabled: Boolean,
    onAnimationFinished: () -> Unit,
) = this then RippleEffectNodeElement(shaderConfig, isEnabled, onAnimationFinished)

/**
 * [DrawModifierNode] implementation for the ripple effect.
 *
 * @property shaderConfig The static configuration for the ripple.
 * @property isEnabled Transitioning this from `false` to `true` starts the ripple sequence. To
 *   allow for re-triggering, the caller must reset this value to `false` after the animation
 *   concludes.
 * @property onAnimationFinished A callback executed immediately upon the completion of the ripple
 *   animation. This is the recommended hook for the caller to reset [isEnabled] to `false`,
 *   readying the node for its next invocation.
 */
@VisibleForTesting
class RippleEffectNode(
    var shaderConfig: RippleAnimationConfig,
    var isEnabled: Boolean,
    val onAnimationFinished: () -> Unit,
) : DrawModifierNode, Modifier.Node() {
    val shaderType = shaderConfig.rippleShape
    val runtimeShader = RippleShader(shaderConfig.rippleShape)
    private val shaderBrush = ShaderBrush(runtimeShader)

    @VisibleForTesting var rawProgress by mutableFloatStateOf(0f)
    private var progressAnimatableJob: Job? = null

    /**
     * Draws the content of the modified Composable first, then overlays the shader effect, updating
     * shader uniforms based on animation progress immediately before drawing.
     */
    override fun ContentDrawScope.draw() {
        // If consumer doesn't define the size explicitly, we'll use the canvas size
        val targetSize =
            if (shaderConfig.maxWidth != 0f && shaderConfig.maxHeight != 0f) {
                Size(shaderConfig.maxWidth, shaderConfig.maxHeight)
            } else {
                size
            }
        val width = targetSize.width
        val height = targetSize.height
        val maxEdgeRadius =
            maxOf(
                shaderConfig.centerX,
                shaderConfig.centerY,
                width - shaderConfig.centerX,
                height - shaderConfig.centerY,
            )
        runtimeShader.rippleSize.setMaxSize(maxEdgeRadius * 2, maxEdgeRadius * 2)
        runtimeShader.time = rawProgress * shaderConfig.duration
        runtimeShader.pixelDensity = density
        runtimeShader.rawProgress = rawProgress
        runtimeShader.distortionStrength =
            if (shaderConfig.shouldDistort) {
                1f - rawProgress
            } else {
                0f
            }
        drawContent()
        drawRect(shaderBrush, topLeft = Offset.Zero, size = size)
    }

    override fun onAttach() {
        runtimeShader.applyConfig(shaderConfig)
        if (isEnabled) {
            startProgressAnimatableJob()
        }
    }

    /**
     * Starts or restarts the progress animation job, resetting progress to 0% and running for the
     * duration specified in [shaderConfig].
     */
    internal fun startProgressAnimatableJob() {
        progressAnimatableJob?.cancel()
        rawProgress = 0f
        progressAnimatableJob =
            coroutineScope.launch {
                Animatable(0f).animateTo(
                    1f,
                    animationSpec =
                        tween(durationMillis = shaderConfig.duration.toInt(), easing = LinearEasing),
                ) {
                    rawProgress = value
                }
                onAnimationFinished()
            }
    }
}

@VisibleForTesting
data class RippleEffectNodeElement(
    val shaderConfig: RippleAnimationConfig,
    var isEnabled: Boolean,
    val onAnimationFinished: () -> Unit,
) : ModifierNodeElement<RippleEffectNode>() {
    @VisibleForTesting lateinit var node: RippleEffectNode

    override fun create() =
        RippleEffectNode(shaderConfig, isEnabled, onAnimationFinished).also { node = it }

    override fun update(node: RippleEffectNode) {
        val shaderTypeChanged = node.shaderType != shaderConfig.rippleShape
        if (shaderTypeChanged) {
            throw IllegalStateException(
                "Changing shaderType on an existing RippleEffect" +
                    " is not supported. This requires the surrounding Composable to to force " +
                    "detaching/re-attaching the effect. Old: ${node.shaderType}, " +
                    "New: ${shaderConfig.rippleShape}"
            )
        }

        val configChanged = node.shaderConfig != shaderConfig
        val isEnabledChanged = node.isEnabled != isEnabled

        node.shaderConfig = shaderConfig
        node.isEnabled = isEnabled

        if (configChanged) {
            node.runtimeShader.applyConfig(shaderConfig)
        }
        if (isEnabledChanged && isEnabled) {
            node.startProgressAnimatableJob()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "RippleEffect"
        properties["config"] = shaderConfig
        properties["isEnabled"] = isEnabled
    }

    companion object {
        private val TAG = RippleEffectNodeElement::class.simpleName
    }
}
