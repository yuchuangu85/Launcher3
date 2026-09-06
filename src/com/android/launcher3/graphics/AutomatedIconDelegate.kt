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

package com.android.launcher3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.ValueAnimator
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.app.animation.InterpolatorsAndroidX
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.AnimationState.ENTER
import com.android.launcher3.graphics.AnimationState.EXIT
import com.android.launcher3.graphics.AnimationState.NORMAL
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawableDelegate
import com.android.launcher3.icons.GraphicsUtils.resizeToContentSize
import com.android.launcher3.icons.GraphicsUtils.transformed
import com.android.launcher3.icons.IconShape
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.util.LooperExecutor
import java.util.concurrent.Executor

/** Delegate to handle automated icon animations and glow effects. */
class AutomatedIconDelegate(
    private val outlineStartColor: Int,
    private val outlineMiddleColor: Int,
    private val outlineEndColor: Int,
    private val iconShape: IconShape,
    private val host: FastBitmapDrawable,
    private val parentDelegate: FastBitmapDrawableDelegate,
    private val glowMaskCache: GlowMaskCache,
) : FastBitmapDrawableDelegate by parentDelegate {

    private var animationState: AnimationState = ENTER
    private var currentRotation = 0f
    private val currentIconScale: FloatValueHolder = FloatValueHolder(MAX_ICON_SCALE)

    private val fixedDelegateBounds =
        Rect(0, 0, iconShape.pathSize, iconShape.pathSize).also {
            parentDelegate.onBoundsChange(it)
        }

    private val colors = intArrayOf(outlineStartColor, outlineMiddleColor, outlineEndColor)

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = iconShape.pathSize * STROKE_RATIO
            strokeCap = Paint.Cap.ROUND
        }

    private val platePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            alpha = PLATE_ALPHA
        }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowShader: RuntimeShader? = null
    private var glowMasks: GlowMasks? = null
    private var paddingOffset = 0f
    private var firstRotationOffset = 0f
    private var uiLooper: Looper? = null
    private var uiExecutor: Executor? = null
    private lateinit var linearGradient: LinearGradient

    private val shaderMatrix = Matrix()

    private val firstRotationAnimator =
        ValueAnimator.ofFloat(-360f, 0f).apply {
            duration = FIRST_ROTATION_DURATION
            interpolator = InterpolatorsAndroidX.EMPHASIZED_DECELERATE
            repeatCount = 0
            addUpdateListener { animator ->
                firstRotationOffset = (animator as ValueAnimator).animatedValue as Float
                safelyInvalidateHost()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationStart(animation: Animator) {
                        cancelled = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) {
                            startNormalAnimation()
                        }
                    }
                }
            )
        }

    private val expressiveDefaultSpatialSpring =
        SpringForce().apply {
            dampingRatio = EXPRESSIVE_DEFAULT_SPATIAL_DAMPING
            stiffness = EXPRESSIVE_DEFAULT_SPATIAL_STIFFNESS
        }

    private val scaleAnimation: SpringAnimation =
        SpringAnimation(currentIconScale).apply {
            spring = expressiveDefaultSpatialSpring
            setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
            addUpdateListener { _, _, _ -> safelyInvalidateHost() }
        }

    init {
        updateShaders()
        if (animationState == ENTER) startEnterAnimation()
    }

    private fun subscribeToGlowMasksUpdate() {
        val executor = uiExecutor ?: return
        if (glowMasks != null) return
        if (Flags.disableAppAutomationBlur() || !Utilities.ATLEAST_T) return

        glowMaskCache
            .getMasks(iconShape)
            .thenAcceptAsync(
                { masks ->
                    if (masks != null) {
                        glowMasks = masks
                        paddingOffset = masks.paddingOffset
                        updateGlowShader()
                        safelyInvalidateHost()
                    }
                },
                executor,
            )
    }

    private fun startEnterAnimation() {
        animationState = ENTER
        cancelAllAnimations()
        scaleAnimation.spring.finalPosition = MIN_ICON_SCALE_WITH_STROKE
        firstRotationAnimator.start()
        scaleAnimation.start()
    }

    private fun startNormalAnimation() {
        animationState = NORMAL
        cancelAllAnimations()
        safelyInvalidateHost()
    }

    fun startExitAnimation(onEnd: Runnable? = null) {
        if (animationState == EXIT) return
        animationState = EXIT
        cancelAllAnimations()
        scaleAnimation.apply {
            val listener =
                object : DynamicAnimation.OnAnimationEndListener {
                    override fun onAnimationEnd(
                        animation: DynamicAnimation<*>?,
                        canceled: Boolean,
                        value: Float,
                        velocity: Float,
                    ) {
                        onEnd?.run()
                        removeEndListener(this)
                    }
                }
            addEndListener(listener)
            spring.finalPosition = MAX_ICON_SCALE
            start()
        }
    }

    private fun updateShaders() {
        val size = iconShape.pathSize.toFloat()
        val center = size / 2f
        // Expansion ensures full gradient layer coverage for rotating corners and the glow spread.
        val expansion = size * GRADIENT_EXPANSION_RATIO
        linearGradient =
            LinearGradient(
                /* x0 = */ -expansion,
                /* y0 = */ -expansion,
                /* x1 = */ size + expansion,
                /* y1 = */ size + expansion,
                /* colors = */ colors,
                /* positions = */ gradientPositions,
                /* tile = */ Shader.TileMode.CLAMP,
            )

        val matrix = Matrix()
        matrix.setRotate(START_ANGLE, center, center)
        linearGradient.setLocalMatrix(matrix)
        strokePaint.shader = linearGradient
        updateGlowShader()
    }

    private fun updateGlowShader() {
        if (!Utilities.ATLEAST_T) return

        val masks = glowMasks ?: return
        val shader = RuntimeShader(GLOW_AGSL_STRING)
        shader.setInputShader("uOuterBlur", masks.outerMask)
        shader.setInputShader("uInnerBlur", masks.innerMask)
        shader.setInputShader("uSilhouetteMask", masks.silhouetteMask)
        shader.setInputShader("uGradient", linearGradient)
        glowShader = shader
        glowPaint.shader = shader
    }

    override fun drawContent(
        info: BitmapInfo,
        iconShape: IconShape,
        canvas: Canvas,
        bounds: Rect,
        paint: Paint,
    ) {
        // On first draw: store the correct UI thread (e.g., Workspace vs Taskbar)
        // to ensure updates run on the correct thread for this icon.
        if (uiLooper == null) {
            val looper = Looper.myLooper()!!
            uiLooper = looper
            uiExecutor = LooperExecutor(looper, Process.THREAD_PRIORITY_DEFAULT)
            subscribeToGlowMasksUpdate()
        }

        canvas.resizeToContentSize(bounds, iconShape.pathSize.toFloat()) {
            val time = SystemClock.uptimeMillis()
            val baseRotation = ((time % ROTATION_DURATION) / ROTATION_DURATION.toFloat()) * 360f
            currentRotation = baseRotation + firstRotationOffset + START_ANGLE

            val center = iconShape.pathSize / 2f
            shaderMatrix.setRotate(currentRotation, center, center)
            linearGradient.setLocalMatrix(shaderMatrix)

            canvas.drawPath(iconShape.path, platePaint)

            if (glowShader != null && !Flags.disableAppAutomationBlur() && Utilities.ATLEAST_T) {
                glowShader?.setInputShader("uGradient", linearGradient)
                canvas.drawRect(
                    -paddingOffset,
                    -paddingOffset,
                    iconShape.pathSize + paddingOffset,
                    iconShape.pathSize + paddingOffset,
                    glowPaint,
                )
            }

            transformed {
                scale(currentIconScale.value, currentIconScale.value, center, center)
                parentDelegate.drawContent(info, iconShape, canvas, fixedDelegateBounds, paint)
            }
            canvas.drawPath(iconShape.path, strokePaint)

            // For the rotation animation - trigger the next frame while the icon is still visible.
            if (host.isVisible && host.callback != null) {
                safelyInvalidateHost()
            }
        }
    }

    override fun onVisibilityChanged(isVisible: Boolean) {
        super.onVisibilityChanged(isVisible)
        if (isVisible) {
            subscribeToGlowMasksUpdate()
            when (animationState) {
                ENTER ->
                    if (!firstRotationAnimator.isRunning && !scaleAnimation.isRunning) {
                        startEnterAnimation()
                    }
                NORMAL -> startNormalAnimation()
                EXIT -> {
                    // no-op
                }
            }
        } else {
            cancelAllAnimations()
        }
    }

    private fun cancelAllAnimations() {
        firstRotationAnimator.cancel()
        scaleAnimation.cancel()
        firstRotationOffset = 0f
    }

    private fun safelyInvalidateHost() {
        uiLooper?.let {
            if (Looper.myLooper() == it) {
                host.invalidateSelf()
            } else {
                (uiExecutor ?: LooperExecutor(it, Process.THREAD_PRIORITY_DEFAULT)).execute {
                    host.invalidateSelf()
                }
            }
        }
    }

    companion object {
        private const val START_ANGLE = 13f
        private const val ROTATION_DURATION = 5000L
        private const val FIRST_ROTATION_DURATION = 1000L
        private const val EXPRESSIVE_DEFAULT_SPATIAL_DAMPING = 0.8f
        private const val EXPRESSIVE_DEFAULT_SPATIAL_STIFFNESS = 380f
        private const val MIN_ICON_SCALE_WITH_STROKE = 0.85f
        private const val MAX_ICON_SCALE = 1f
        private const val PLATE_ALPHA = (76.5).toInt() // 30% alpha
        private const val STROKE_RATIO = 0.05f // scale stroke to 3dp for 60dp icon
        private const val GRADIENT_EXPANSION_RATIO = 0.6f
        private const val GLOW_AGSL_STRING =
            """
            uniform shader uOuterBlur;
            uniform shader uInnerBlur;
            uniform shader uSilhouetteMask;
            uniform shader uGradient;

            half4 main(float2 fragCoord) {
                float outerAlpha = uOuterBlur.eval(fragCoord).a;
                float silhouetteAlpha = uSilhouetteMask.eval(fragCoord).a;
                // Inner halo math: subtract inner blur alpha from solid silhouette to get halo
                float innerAlpha = max(0.0, silhouetteAlpha - uInnerBlur.eval(fragCoord).a);
                half4 color = uGradient.eval(fragCoord);
                half4 outerLayer = half4(color.rgb * outerAlpha, color.a * outerAlpha);
                half4 innerLayer = half4(color.rgb * innerAlpha, color.a * innerAlpha);
                return outerLayer + innerLayer;
            }
        """

        private val gradientPositions = floatArrayOf(0.2f, 0.5f, 0.8f)

        @JvmStatic
        fun newAutomatedIcon(
            context: Context,
            info: ItemInfoWithIcon,
            @DrawableCreationFlags creationFlags: Int = 0,
        ): FastBitmapDrawable {
            val originalState = info.newIcon(context, creationFlags).constantState
            val resources = context.resources

            val outlineStartColor =
                boostChroma(context.getColor(R.color.materialColorTertiaryContainer))
            val outlineMiddleColor =
                boostChroma(context.getColor(R.color.materialColorPrimaryFixedDim))
            val outlineEndColor = boostChroma(context.getColor(R.color.materialColorPrimary))

            val glowMaskCache = GlowMaskCache.INSTANCE.get(context)

            val newState =
                originalState.copy(
                    delegateFactory =
                        AutomatedIconDelegateFactory(
                            outlineStartColor,
                            outlineMiddleColor,
                            outlineEndColor,
                            originalState.delegateFactory,
                            glowMaskCache,
                        )
                )
            return newState.newDrawable()
        }

        @JvmStatic
        fun boostChroma(color: Int): Int {
            val hctColor = FloatArray(3)
            ColorUtils.colorToM3HCT(color, hctColor)
            val chroma = hctColor[1]
            return if (chroma < 5) {
                color
            } else {
                ColorUtils.M3HCTToColor(hctColor[0], 70f, hctColor[2])
            }
        }
    }
}

private enum class AnimationState {
    ENTER,
    EXIT,
    NORMAL,
}

class AutomatedIconDelegateFactory(
    private val outlineStartColor: Int,
    private val outlineMiddleColor: Int,
    private val outlineEndColor: Int,
    private val parentFactory: FastBitmapDrawableDelegate.DelegateFactory,
    private val glowMaskCache: GlowMaskCache,
) : FastBitmapDrawableDelegate.DelegateFactory {

    override fun newDelegate(
        bitmapInfo: BitmapInfo,
        iconShape: IconShape,
        paint: Paint,
        host: FastBitmapDrawable,
    ): FastBitmapDrawableDelegate {
        val parent = parentFactory.newDelegate(bitmapInfo, iconShape, paint, host)
        return AutomatedIconDelegate(
            outlineStartColor,
            outlineMiddleColor,
            outlineEndColor,
            iconShape,
            host,
            parent,
            glowMaskCache,
        )
    }
}
