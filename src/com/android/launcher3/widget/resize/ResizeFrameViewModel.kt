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

package com.android.launcher3.widget.resize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * An ephemeral view model for the [ResizeFrame] that lives as long as its view (unlike that
 * androidx lifecycle viewmodel that outlives the view).
 *
 * Responsibilities:
 * - Holds the state necessary to show the frame UI
 * - Handle UI events and trigger necessary resize changes using [resizeManager].
 */
class ResizeFrameViewModel(
    private val resizeManager: ResizeManager,
    private val touchTargetSize: Int,
) {
    /** State of the dots (handles) shown over the resize frame. */
    var handlesState by mutableStateOf(HandlesState())
        private set

    /**
     * State of the + / - buttons if shown over a handle. By default, they aren't shown, so, the
     * state is null.
     */
    var resizeButtonsState by mutableStateOf<ResizeButtonState?>(null)
        private set

    /**
     * Handles that are currently being dragged by the user. Null if user is not dragging. When user
     * drags from a corner, this will hold both horizontal and vertical handles.
     */
    var draggingHandles by mutableStateOf<DraggingHandles?>(null)
        private set

    private var totalDragAmount: Offset = Offset.Zero

    init {
        refreshHandlesAndButtonsState()
    }

    /**
     * Recalculates the state for each handle based on the current span and constraints. This
     * function is sole decision maker on whether a handle has a dot and if can expand or shrink.
     */
    private fun refreshHandlesAndButtonsState(tappedEdge: Edge? = null) {
        val horizontalEnabled = resizeManager.resizeConstraints.horizontalResizeModeEnabled
        val verticalEnabled = resizeManager.resizeConstraints.verticalResizeModeEnabled

        val resizeButtonStatesPerEdge =
            Edge.entries.associateWith { handle ->
                val isHorizontal = handle.isHorizontal()
                if ((horizontalEnabled && isHorizontal) || (verticalEnabled && !isHorizontal)) {
                    ResizeButtonState(
                        tappedEdge = handle,
                        canShrink =
                            resizeManager.canShrink(inHorizontalDirection = handle.isHorizontal()),
                        canExpand =
                            resizeManager.canExpandInDirection(
                                dirX = handle.expandDirX,
                                dirY = handle.expandDirY,
                            ),
                    )
                } else {
                    null
                }
            }

        handlesState =
            HandlesState(
                left = resizeButtonStatesPerEdge[Edge.Left]?.isResizeable() ?: false,
                top = resizeButtonStatesPerEdge[Edge.Top]?.isResizeable() ?: false,
                right = resizeButtonStatesPerEdge[Edge.Right]?.isResizeable() ?: false,
                bottom = resizeButtonStatesPerEdge[Edge.Bottom]?.isResizeable() ?: false,
            )

        if (tappedEdge != null) {
            resizeButtonsState = resizeButtonStatesPerEdge[tappedEdge]
        }
    }

    /**
     * Called when a user taps on a drag handle (i.e. dot). This populates the state required to
     * show the (+) and (-) buttons.
     */
    fun onDotHandleTapped(edge: Edge) = refreshHandlesAndButtonsState(edge)

    fun onShrink(edge: Edge) {
        resizeManager.runAtomicResizeSession(edge, spanDelta = -1) {
            refreshHandlesAndButtonsState(edge)
        }
    }

    fun onExpand(edge: Edge) {
        resizeManager.runAtomicResizeSession(edge, spanDelta = 1) {
            refreshHandlesAndButtonsState(edge)
        }
    }

    fun onDragStart(edge: Edge, offset: Offset, size: IntSize) {
        val horizontal: Edge?
        val vertical: Edge?
        val overlapSize = touchTargetSize + touchTargetSize / 2

        if (edge.isHorizontal()) {
            horizontal = edge
            vertical =
                when {
                    handlesState.top && offset.y <= overlapSize -> Edge.Top
                    handlesState.bottom && offset.y >= size.height - overlapSize -> Edge.Bottom
                    else -> null
                }
        } else {
            vertical = edge
            horizontal =
                when {
                    handlesState.left && offset.x <= overlapSize -> Edge.Left
                    handlesState.right && offset.x >= size.width - overlapSize -> Edge.Right
                    else -> null
                }
        }

        resizeButtonsState = null // hide buttons
        totalDragAmount = Offset.Zero
        draggingHandles = DraggingHandles(horizontal, vertical)
        resizeManager.beginResizeSession(horizontal, vertical)
    }

    fun onDrag(dragAmount: Offset) {
        val handles = draggingHandles ?: return
        totalDragAmount += dragAmount

        resizeManager.visualizeResizeForDelta(
            horizontal = handles.horizontal,
            vertical = handles.vertical,
            totalDragAmount = totalDragAmount,
        )
    }

    fun onDragEnd() {
        val handles = draggingHandles ?: return
        resizeManager.endResizeSession(handles.horizontal, handles.vertical, totalDragAmount)
        draggingHandles = null
        refreshHandlesAndButtonsState()
    }
}

/**
 * Describes which handles are involved in the current drag. When resizing from corner, two handles
 * are involved.
 */
data class DraggingHandles(val horizontal: Edge?, val vertical: Edge?)

/** Represents the visibility state of the four drag handles. */
data class HandlesState(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
)

/**
 * Represents the UI state for the resize (+/-) buttons that appear on a handle tap.
 *
 * @param tappedEdge The handle that was tapped to show the buttons.
 * @param canExpand Whether the widget can be made larger from this edge.
 * @param canShrink Whether the widget can be made smaller from this edge.
 */
data class ResizeButtonState(val tappedEdge: Edge, val canExpand: Boolean, val canShrink: Boolean) {
    fun isResizeable() = canExpand || canShrink
}
