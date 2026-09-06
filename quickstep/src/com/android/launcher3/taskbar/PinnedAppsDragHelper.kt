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

package com.android.launcher3.taskbar

import android.content.Context
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.LinearLayout.INVISIBLE
import com.android.launcher3.BubbleTextView
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.taskbar.TaskbarViewDragDropController.PinnedAppsContainerDelegate

/** A helper class to handle drag and drop logic for pinned apps container. */
abstract class PinnedAppsDragHelper(
    private val context: Context,
    private val container: ViewGroup,
    protected val iconSize: Int,
) : PinnedAppsContainerDelegate {

    protected var dropSpotIndex = -1
    private var dropTargetGhostView: View? = null
    private var indexOfChildHiddenForDrag = -1

    /**
     * Calculates the index where the "ghost" view (placeholder for the drop target) should be
     * inserted based on the drag's X coordinate.
     *
     * @param onScreenLocationX The raw X coordinate of the drag event on the screen.
     * @return The calculated index within the pinned apps list where the ghost view should appear,
     *   or -1 if it cannot be calculated or the default implementation is used.
     */
    open fun calculateGhostViewIndex(onScreenLocationX: Int): Int = -1

    /**
     * Calculates the actual index where the drop ghost view should be inserted into the container's
     * child list.
     *
     * @param dropIndex The logical index among the *visible* items where the drop should occur.
     * @param hiddenChildIndex The actual index in the container of the child currently hidden for
     *   the drag operation (or -1 if none).
     * @return The actual index to pass to [ViewGroup.addView].
     */
    open fun calculateDropIndexInContainer(dropIndex: Int, hiddenChildIndex: Int): Int {
        if (hiddenChildIndex in 0 until dropIndex) {
            return dropIndex + 1
        }
        return dropIndex
    }

    open fun createGhostViewLayoutParams(iconSize: Int): ViewGroup.LayoutParams =
        TaskbarView.TaskbarLayoutParams(0, 0)

    abstract fun createViewForItem(item: ItemInfo): BubbleTextView?

    open fun onDragStateChanged() {}

    open fun hasHiddenChild(): Boolean = indexOfChildHiddenForDrag >= 0

    override fun getPinIndex(startingIndex: Int): Int = startingIndex + dropSpotIndex

    override fun updateForDroppedItem(item: ItemInfo): Boolean {
        if (dropSpotIndex == -1) {
            return false
        }
        dropTargetGhostView?.let {
            container.removeView(it)
            dropTargetGhostView = null
        }

        val draggedView =
            if (indexOfChildHiddenForDrag >= 0) container.getChildAt(indexOfChildHiddenForDrag)
            else null
        draggedView?.let {
            container.removeView(it)
            // Cancel any pending drag view disappearing animation - the dragged view is not visible
            // at this time and will be readded to the container immediately.
            container.layoutTransition?.cancel()
            // Keep drag view invisible, but make it take up space during layout - it will be
            // changed to visible when resetting the drag state.
            it.visibility = INVISIBLE
        }

        val itemView = draggedView ?: createViewForItem(item)
        if (itemView != null) {
            container.addView(itemView, calculateDropIndexInContainer(dropSpotIndex, -1))
            container.layoutTransition?.cancel()
        }
        indexOfChildHiddenForDrag = -1
        dropSpotIndex = -1

        // Adding item view may trigger layout animations. Given that the item view is replacing
        // drop ghost item, the position of non-dragged views should not change, but the added
        // dragged view may end up animating from its current location if it's reused (either as
        // an original drag view, or as a recycled item view) - cancel changing animation, and
        // appearing animation when they get started on next layout to avoid unwanted motion.
        container.addOnLayoutChangeListener(
            object : OnLayoutChangeListener {
                override fun onLayoutChange(
                    view: View?,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    view?.removeOnLayoutChangeListener(this)
                    container.layoutTransition?.cancel()
                    container.layoutTransition?.endChangingAnimations()
                }
            }
        )

        onDragStateChanged()
        return itemView != draggedView
    }

    override fun releaseDropSlot() {
        dropSpotIndex = -1
        dropTargetGhostView?.let {
            container.removeView(it)
            dropTargetGhostView = null
            onDragStateChanged()
        }
    }

    override fun removeDraggedView() {
        if (indexOfChildHiddenForDrag < 0 || indexOfChildHiddenForDrag >= container.childCount) {
            return
        }
        container.removeViewAt(indexOfChildHiddenForDrag)
        container.clearDisappearingChildren()
        indexOfChildHiddenForDrag = -1
    }

    override fun updateItemViewVisibilityForDragState(itemView: View, isDragged: Boolean): Boolean {
        val index = container.indexOfChild(itemView)
        if (index == -1) {
            indexOfChildHiddenForDrag = -1
            return false
        }
        indexOfChildHiddenForDrag = if (isDragged) index else -1
        itemView.visibility = if (isDragged) View.GONE else View.VISIBLE
        return true
    }

    override fun reserveDropSlotForDragLocation(onScreenLocationX: Int) {
        val newDropIndex = calculateGhostViewIndex(onScreenLocationX)
        if (dropTargetGhostView != null && dropSpotIndex == newDropIndex) return

        releaseDropSlot()
        dropSpotIndex = newDropIndex

        if (dropTargetGhostView == null) {
            dropTargetGhostView = TaskbarDropTargetGhostView(context, iconSize)
        }

        val lp = createGhostViewLayoutParams(iconSize)
        val insertIndex = calculateDropIndexInContainer(newDropIndex, indexOfChildHiddenForDrag)

        container.addView(requireNotNull(dropTargetGhostView), insertIndex, lp)
        onDragStateChanged()
    }
}
