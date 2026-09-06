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
package com.android.launcher3

import android.graphics.Rect
import android.graphics.RectF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.views.ActivityContext

/**
 * A helper class that enables box selection within a [ViewGroup].
 *
 * This helper creates and manages a view that is overlaid on a host container, allowing users to
 * draw a selection rectangle with a mouse. The helper detects when a selection should begin,
 * handles the drawing of the selection box, and notifies the host of the selected area as it
 * changes.
 *
 * @param host The [BoxSelectionHost] that will respond to selection events.
 */
class BoxSelectionHelper(
    private val activityContext: ActivityContext,
    private val targetView: View,
) {
    private var boxSelectionView: View? = null

    // Coordinates where the touch down event occurred.
    private var downX = 0f
    private var downY = 0f

    private fun isSelecting() = boxSelectionView != null

    private fun workspaceSelectionManager() =
        activityContext.activityComponent.workspaceSelectionManager

    /**
     * Handles touch events for initiating and managing box selection.
     *
     * @param ev The [MotionEvent] to handle.
     * @return `true` if the event was consumed, `false` otherwise.
     */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        // TODO(http://b/465503610): Unify touch delegation logic into CustomEventsTouchHandler
        if (!isSelecting()) {
            // Box selection is not started. Check if we should start it.
            if (ev.action == MotionEvent.ACTION_DOWN && isBoxSelectionEvent(ev)) {
                startSelection(ev)
                return true
            }
            return false
        }

        // Box selection is in progress.
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                updateSelection(ev)
            }
            MotionEvent.ACTION_UP -> {
                updateSelection(ev)
                endSelection()
            }
            MotionEvent.ACTION_CANCEL -> {
                workspaceSelectionManager().updateBoxSelection(Rect())
                endSelection()
            }
            else -> {
                // Ignore other events.
                return false
            }
        }
        return true
    }

    private fun isBoxSelectionEvent(ev: MotionEvent): Boolean {
        return ev.isFromSource(InputDevice.SOURCE_MOUSE) &&
            !ev.isButtonPressed(MotionEvent.BUTTON_SECONDARY) &&
            !ev.isButtonPressed(MotionEvent.BUTTON_TERTIARY) &&
            !MotionEventsUtils.isTrackpadMotionEvent(ev)
    }

    private fun startSelection(ev: MotionEvent) {
        workspaceSelectionManager()
            .startBoxSelection((ev.metaState and KeyEvent.META_SHIFT_ON) != 0)

        boxSelectionView =
            createBoxSelectionView().also {
                activityContext.getDragLayer().addView(it)
                it.visibility = View.VISIBLE
            }
        downX = ev.x
        downY = ev.y

        // Initialize selectionRect as a point at the touch down location.
        updateSelection(ev)
    }

    private fun updateSelection(ev: MotionEvent) {
        boxSelectionView?.let { boxView ->
            // selectionRect is in the local coordinate space of 'view' (the Folder or Workspace)
            val sortedRectF = RectF(downX, downY, ev.x, ev.y)
            sortedRectF.sort()

            // Clip the selection rectangle to the bounds of the view itself.
            sortedRectF.left = sortedRectF.left.coerceAtLeast(0f)
            sortedRectF.top = sortedRectF.top.coerceAtLeast(0f)
            sortedRectF.right = sortedRectF.right.coerceAtMost(targetView.width.toFloat())
            sortedRectF.bottom = sortedRectF.bottom.coerceAtMost(targetView.height.toFloat())

            // Translate the clipped, local coordinates to the host container's (DragLayer)
            // coordinates.
            boxView.x = sortedRectF.left + targetView.x
            boxView.y = sortedRectF.top + targetView.y
            val lp = boxView.layoutParams ?: ViewGroup.LayoutParams(0, 0)
            lp.width = sortedRectF.width().toInt()
            lp.height = sortedRectF.height().toInt()
            boxView.layoutParams = lp

            val resultRect = Rect()
            val screenRectF =
                RectF(boxView.x, boxView.y, boxView.x + lp.width, boxView.y + lp.height)
            screenRectF.round(resultRect)

            // Notify the host about the selection update.
            workspaceSelectionManager().updateBoxSelection(resultRect)
        }
    }

    private fun endSelection() {
        activityContext.getDragLayer().removeView(boxSelectionView)
        boxSelectionView = null
        workspaceSelectionManager().endBoxSelection()
    }

    private fun createBoxSelectionView(): View {
        val context = activityContext as android.content.Context
        return View(context).apply {
            // Set background to opaque white and use view's alpha for opacity.
            setBackgroundColor(context.getColor(R.color.materialColorPrimaryFixed))
            alpha = BACKGROUND_ALPHA

            val radius =
                if (context != null) {
                    android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP,
                        CORNER_RADIUS_DP,
                        context.resources.displayMetrics,
                    )
                } else {
                    // Default to a small radius if context is not available.
                    DEFAULT_CORNER_RADIUS_PX
                }

            outlineProvider =
                object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
            clipToOutline = true
        }
    }

    companion object {
        private const val BACKGROUND_ALPHA = 0.25f
        private const val CORNER_RADIUS_DP = 2f
        private const val DEFAULT_CORNER_RADIUS_PX = 4f
    }
}
