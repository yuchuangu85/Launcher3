/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3.remoteanimations

import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.view.RemoteAnimationTarget
import com.android.app.animation.Interpolators
import com.android.launcher3.Utilities
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.util.RectFSpringAnim.OnUpdateListener
import com.android.quickstep.util.SurfaceTransaction
import com.android.quickstep.util.SurfaceTransactionApplier

/**
 * RectFSpringAnim update listener to be used for app to home animation.
 *
 * @param appTargets the list of opening/closing apps
 * @param targetRect target rectangle
 * @param closingWindowStartRect start position of the window when the spring animation is started.
 *   In the predictive back to home case this will be smaller than closingWindowOriginalRect because
 *   the window is already scaled by the user gesture
 * @param startRadius corner radius of window at the start position
 */
open class SpringAnimRunner
@JvmOverloads
constructor(
    private val appTargets: Array<RemoteAnimationTarget>?,
    targetRect: RectF,
    closingWindowStartRect: RectF,
    activityContext: ActivityContext,
    private val startRadius: Float,
    private val alphaEndProgress: Float = ALPHA_END_PROGRESS,
) : OnUpdateListener {

    private val matrix = Matrix()
    private val tmpPos = Point()
    private val currentAnimTargetRectF = RectF()
    private val endRadius = targetRect.width().coerceAtLeast(1f) / 2f
    private val surfaceApplier = SurfaceTransactionApplier(activityContext.dragLayer)
    private val windowStartBounds = RectF(closingWindowStartRect)
    private val windowOriginalBounds =
        RectF(
            0f,
            0f,
            activityContext.deviceProfile.deviceProperties.widthPx.toFloat(),
            activityContext.deviceProfile.deviceProperties.heightPx.toFloat(),
        )

    private val tmpRect = Rect()
    private val tmpRectF = RectF()
    private val coordinateTransfer: RemoteAnimationCoordinateTransfer =
        RemoteAnimationCoordinateTransfer(activityContext)

    private var windowCornerRadius: Float? = null

    init {
        // transfer the coordinate based on animation target.
        appTargets
            ?.find { it.mode == RemoteAnimationTarget.MODE_CLOSING }
            ?.let {
                val transferRect = RectF(windowStartBounds)
                coordinateTransfer.transferRectToAnimTarget(it, transferRect, windowStartBounds)

                transferRect.set(windowOriginalBounds)
                coordinateTransfer.transferRectToAnimTarget(it, transferRect, windowOriginalBounds)
            }
    }

    fun setWindowCornerRadius(radius: Float?) {
        windowCornerRadius = radius
    }

    fun getCornerRadius(progress: Float): Float =
        Utilities.mapRange(progress, startRadius, endRadius)

    override fun onUpdate(currentRectF: RectF, progress: Float) {
        appTargets ?: return
        val transaction = SurfaceTransaction()
        for (i in appTargets.indices.reversed()) {
            val target = appTargets[i]
            val builder = transaction.forSurface(target.leash)

            if (target.localBounds != null) {
                tmpPos[target.localBounds.left] = target.localBounds.top
            } else {
                tmpPos[target.position.x] = target.position.y
            }

            if (target.mode == RemoteAnimationTarget.MODE_CLOSING) {
                coordinateTransfer.transferRectToAnimTarget(
                    target,
                    currentRectF,
                    currentAnimTargetRectF,
                )

                // Scale the target window to match the currentRectF.
                val scale: Float

                // We need to infer the crop (we crop the window to match the currentRectF).
                if (windowStartBounds.height() > windowStartBounds.width()) {
                    scale =
                        (currentAnimTargetRectF.width() / windowOriginalBounds.width())
                            .coerceAtMost(1f)
                    val unscaledHeight = currentAnimTargetRectF.height() / scale
                    tmpRectF.set(0f, 0f, windowOriginalBounds.width(), unscaledHeight)
                } else {
                    scale =
                        (currentAnimTargetRectF.height() / windowOriginalBounds.height())
                            .coerceAtMost(1f)

                    val unscaledWidth = currentAnimTargetRectF.width() / scale
                    tmpRectF.set(0f, 0f, unscaledWidth, windowOriginalBounds.height())
                }

                // Match size and position of currentRect.
                matrix.setScale(scale, scale)
                matrix.postTranslate(currentAnimTargetRectF.left, currentAnimTargetRectF.top)

                tmpRectF.round(tmpRect)
                builder
                    .setMatrix(matrix)
                    .setWindowCrop(tmpRect)
                    .setAlpha(getWindowAlpha(progress))
                    .setCornerRadius(windowCornerRadius ?: (getCornerRadius(progress) / scale))
            } else if (target.mode == RemoteAnimationTarget.MODE_OPENING) {
                matrix.setTranslate(tmpPos.x.toFloat(), tmpPos.y.toFloat())
                builder.setMatrix(matrix).setAlpha(1f)
            }
        }
        surfaceApplier.scheduleApply(transaction)
    }

    /** Alpha interpolates between [1, 0] between progress values [start, end] */
    private fun getWindowAlpha(progress: Float): Float =
        when {
            progress <= ALPHA_START_PROGRESS -> 1f
            progress >= alphaEndProgress -> 0f
            else ->
                Utilities.mapToRange(
                    progress,
                    ALPHA_START_PROGRESS,
                    alphaEndProgress,
                    1f,
                    0f,
                    Interpolators.ACCELERATE_1_5,
                )
        }

    companion object {
        private const val ALPHA_START_PROGRESS = 0f
        private const val ALPHA_END_PROGRESS = 0.85f
    }
}
