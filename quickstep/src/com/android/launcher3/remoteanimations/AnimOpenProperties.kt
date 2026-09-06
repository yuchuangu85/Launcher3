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

import android.content.res.Resources
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.testing.shared.ResourceUtils
import kotlin.math.max
import kotlin.math.min

/** Class that holds all the variables for the app open animation. */
class AnimOpenProperties(
    r: Resources,
    windowTargetBounds: Rect,
    launcherIconBounds: RectF,
    view: View,
    dragLayerLeft: Int,
    dragLayerTop: Int,
    hasSplashScreen: Boolean,
    hasDifferentAppIcon: Boolean,
) {
    @JvmField val cropCenterXStart: Int = windowTargetBounds.centerX()

    @JvmField val cropCenterYStart: Int = windowTargetBounds.centerY()

    @JvmField
    val cropWidthStart: Int = ResourceUtils.getDimenByName("starting_surface_icon_size", r, 108)

    @JvmField val cropHeightStart: Int = cropWidthStart

    @JvmField val cropCenterXEnd: Int = windowTargetBounds.centerX()

    @JvmField val cropCenterYEnd: Int = windowTargetBounds.centerY()

    @JvmField val cropWidthEnd: Int = windowTargetBounds.width()

    @JvmField val cropHeightEnd: Int = windowTargetBounds.height()

    // Animate the app icon to the center of the window bounds in screen coordinates.
    @JvmField
    val dX: Float =
        (windowTargetBounds.centerX() - dragLayerLeft).toFloat() - launcherIconBounds.centerX()

    @JvmField
    val dY: Float =
        (windowTargetBounds.centerY() - dragLayerTop).toFloat() - launcherIconBounds.centerY()

    @JvmField
    val initialAppIconScale: Float =
        if (view.parent !is DeepShortcutView) {
            (view as? BubbleTextView)?.icon?.getAnimatedScale() ?: 1f
        } else 1f

    @JvmField
    val finalAppIconScale: Float =
        min(windowTargetBounds.height(), windowTargetBounds.width()).toFloat().let { smallestSize ->
            val maxScaleX = smallestSize / launcherIconBounds.width()
            val maxScaleY = smallestSize / launcherIconBounds.height()
            max(maxScaleX, maxScaleY)
        }

    @JvmField val iconAlphaStart: Float = if (hasSplashScreen && !hasDifferentAppIcon) 0f else 1f
}
