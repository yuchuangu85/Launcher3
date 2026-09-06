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
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.core.animation.Interpolator
import com.android.systemui.surfaceeffects.core.dwellrippleeffect.DwellEffectConfig
import com.android.systemui.surfaceeffects.core.dwellrippleeffect.DwellRippleShader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Core internal function that applies a [DwellEffectShader] effect to the modified Composable.
 *
 * @param shaderConfig The configuration ([DwellEffectConfig]) defining radius, color, and duration,
 *   etc.
 * @param isExpanding A state trigger that drives the animation; set to `true` to initiate the
 *   expansion and `false` to initiate the retraction.
 * @param onAnimationFinished A callback triggered exclusively upon the completion of the retraction
 *   phase. Note: This callback is not invoked when the expansion phase finishes; it is intended for
 *   cleanup or state resetting after the effect fully disappears.
 */
@VisibleForTesting
fun Modifier.dwellEffectImpl(
    shaderConfig: DwellEffectConfig,
    isExpanding: Boolean,
    onAnimationFinished: () -> Unit,
) = this then DwellEffectNodeElement(shaderConfig, isExpanding, onAnimationFinished)

/**
 * [DrawModifierNode] implementation for the dwell ripple effect.
 *
 * @property shaderConfig The configuration for the dwell ripple effect.
 * @property isExpanding A state trigger that drives the animation; set to `true` to initiate the
 *   expansion and `false` to initiate the retraction.
 * @property onAnimationFinished A callback triggered exclusively upon the completion of the
 *   retraction phase. Note: This callback is not invoked when the expansion phase finishes; it is
 *   intended for cleanup or state resetting after the effect fully disappears.
 */
@VisibleForTesting
class DwellEffectNode(
    var shaderConfig: DwellEffectConfig,
    var isExpanding: Boolean,
    val onAnimationFinished: () -> Unit,
) : DrawModifierNode, Modifier.Node() {
    @VisibleForTesting val runtimeShader = DwellRippleShader()
    var easing = LinearEasing
    private val shaderBrush = ShaderBrush(runtimeShader)
    private var attachTimeMillis: Long = 0L
    private var progressAnimatableJob: Job? = null

    /**
     * The eased animation progress from 0f to 1f when expanding or retracting.
     *
     * This value applies the visual [interpolator] and is passed directly to the shader's progress
     * uniform to control the ripple's visual radius.
     */
    @VisibleForTesting var progress by mutableFloatStateOf(0f)

    override fun onAttach() {
        attachTimeMillis = System.currentTimeMillis()
        runtimeShader.applyConfig(shaderConfig)
        if (isExpanding) {
            startProgressAnimatableJob()
        }
    }

    internal fun startProgressAnimatableJob() {
        progressAnimatableJob?.cancel()
        val target = if (isExpanding) 1f else 0f
        val animationConfig =
            if (isExpanding) shaderConfig.expandingAnimationConfig
            else shaderConfig.retractingAnimationConfig
        val duration = animationConfig.duration
        val interpolator = animationConfig.interpolator
        val easing = convertInterpolatorToEasing(interpolator)
        progressAnimatableJob =
            coroutineScope.launch {
                Animatable(progress).animateTo(
                    target,
                    animationSpec = tween(durationMillis = duration.toInt(), easing = easing),
                ) {
                    progress = value
                }
                if (!isExpanding) {
                    onAnimationFinished()
                }
            }
    }

    override fun ContentDrawScope.draw() {
        val elapsedTimeMillis = System.currentTimeMillis() - attachTimeMillis
        runtimeShader.time = elapsedTimeMillis.toFloat()
        runtimeShader.progress = progress
        // To reduce overdraw, we mask the effect to a circle whose radius is big enough to cover
        // the active effect area. Values here should be kept in sync with the
        // animation implementation in the dwell ripple shader. (Twice bigger)
        drawCircle(
            brush = shaderBrush,
            radius =
                (1 - (1 - progress) * (1 - progress) * (1 - progress)) *
                    shaderConfig.maxRadius *
                    2f,
            center = Offset(shaderConfig.centerX, shaderConfig.centerY),
        )
    }

    companion object {
        /**
         * Creates a Compose Easing function that wraps a traditional Android Interpolator.
         * * @param interpolator The existing Android Interpolator instance to wrap.
         *
         * @return A Compose Easing function.
         */
        internal fun convertInterpolatorToEasing(interpolator: Interpolator): Easing {
            return Easing { fraction ->
                // The Easing function calls the Interpolator's method directly.
                interpolator.getInterpolation(fraction)
            }
        }
    }
}

@VisibleForTesting
data class DwellEffectNodeElement(
    val shaderConfig: DwellEffectConfig,
    val isExpanding: Boolean,
    val onAnimationFinished: () -> Unit,
) : ModifierNodeElement<DwellEffectNode>() {
    @VisibleForTesting lateinit var node: DwellEffectNode

    override fun create(): DwellEffectNode {
        return DwellEffectNode(shaderConfig, isExpanding, onAnimationFinished).also { node = it }
    }

    override fun update(node: DwellEffectNode) {
        val isConfigChanged = node.shaderConfig != shaderConfig
        val isExpandingChanged = node.isExpanding != isExpanding
        node.shaderConfig = shaderConfig
        node.isExpanding = isExpanding

        if (isConfigChanged) {
            node.runtimeShader.applyConfig(shaderConfig)
        }
        if (isExpandingChanged) {
            node.startProgressAnimatableJob()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "DwellEffect"
        properties["config"] = shaderConfig
        properties["isExpanding"] = isExpanding
    }

    companion object {
        private val TAG = DwellEffectNode::class.simpleName
    }
}
