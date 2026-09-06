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

import android.graphics.Rect
import android.graphics.RectF
import android.util.RotationUtils
import android.view.RemoteAnimationTarget
import android.view.Surface
import com.android.launcher3.views.ActivityContext

class RemoteAnimationCoordinateTransfer(private val activity: ActivityContext) {

    private val tmpResult = Rect()

    /**
     * Transfer the [currentRect] in [source]'s coordinate space to the [activity]'s coordinate
     * space
     */
    fun transferRectToOwnerSurface(
        source: RemoteAnimationTarget,
        currentRect: RectF,
        resultRect: RectF,
    ) =
        transferRectToTargetCoordinate(
            animTarget = source,
            currentRect = currentRect,
            toLauncher = true,
            resultRect = resultRect,
        )

    /**
     * Transfer the [currentRect] in [activity]'s coordinate space to the [animTarget]'s coordinate
     * space
     */
    fun transferRectToAnimTarget(
        animTarget: RemoteAnimationTarget,
        currentRect: RectF,
        resultRect: RectF,
    ) =
        transferRectToTargetCoordinate(
            animTarget = animTarget,
            currentRect = currentRect,
            toLauncher = false,
            resultRect = resultRect,
        )

    /**
     * Transfer the rectangle to another coordinate if needed.
     *
     * @param toLauncher which one is the anchor of this transfer, if true then transfer from
     *   animation target to launcher, false transfer from launcher to animation target.
     */
    private fun transferRectToTargetCoordinate(
        animTarget: RemoteAnimationTarget,
        currentRect: RectF,
        toLauncher: Boolean,
        resultRect: RectF,
    ) {
        val taskRotation = animTarget.windowConfiguration.rotation
        val profile = activity.deviceProfile.deviceProperties
        val rotation = profile.rotationHint
        val widthPx = profile.widthPx
        val heightPx = profile.widthPx

        val rotationDelta =
            if (toLauncher) RotationUtils.deltaRotation(taskRotation, rotation)
            else RotationUtils.deltaRotation(rotation, taskRotation)
        if (rotationDelta != Surface.ROTATION_0) {
            // Get original display size when task is on top but with different rotation
            val parentWidth: Int
            val parentHeight: Int
            if (
                rotationDelta % 2 != 0 &&
                    toLauncher &&
                    (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
            ) {
                parentWidth = heightPx
                parentHeight = widthPx
            } else {
                parentWidth = widthPx
                parentHeight = heightPx
            }
            currentRect.round(tmpResult)
            RotationUtils.rotateBounds(tmpResult, parentWidth, parentHeight, rotationDelta)
            resultRect.set(tmpResult)
        } else {
            resultRect.set(currentRect)
        }
    }
}
