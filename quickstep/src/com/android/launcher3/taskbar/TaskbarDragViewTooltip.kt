/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.taskbar

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.launcher3.R
import com.android.launcher3.views.ArrowTipView

/** Manages the tooltip shown when dragging a taskbar item over the unpin target. */
class TaskbarDragViewTooltip(private val context: TaskbarActivityContext) {

    @VisibleForTesting var activeTooltipView: ArrowTipView? = null

    /** Shows the "Remove" tooltip based on the target location. */
    fun show(targetLocation: FloatArray) {
        val contextThemeWrapper = ContextThemeWrapper(context, R.style.ArrowTipTaskbarStyle)
        val toolTipView =
            ArrowTipView(
                contextThemeWrapper,
                /* isPointingUp= */ false,
                R.layout.taskbar_drag_view_tooltip,
            )

        val openAnimator = AnimatorSet()
        openAnimator.play(ObjectAnimator.ofFloat(toolTipView, View.ALPHA, 0f, 1f))
        openAnimator.duration = OPEN_ANIMATION_DURATION
        toolTipView.setCustomOpenAnimation(openAnimator)

        toolTipView.showAtLocation(
            context.getString(R.string.taskbar_drag_to_unpin_title),
            targetLocation[0].toInt(),
            targetLocation[1].toInt(),
            /* shouldAutoClose= */ false,
        )

        activeTooltipView = toolTipView
    }

    /** Updates the position of the tooltip based on the target location. */
    fun updatePosition(targetLocation: FloatArray) {
        val tooltip = activeTooltipView ?: return

        tooltip.translationX = targetLocation[0] - (tooltip.width / 2f)
        tooltip.translationY = targetLocation[1] - tooltip.height
    }

    /** Closes the current tooltip. */
    fun hide() {
        activeTooltipView?.close(/* animate= */ false)
        activeTooltipView = null
    }

    /** Closes the current tooltip. */
    fun isActive(): Boolean {
        return activeTooltipView != null
    }

    companion object {
        private const val OPEN_ANIMATION_DURATION = 15L
    }
}
