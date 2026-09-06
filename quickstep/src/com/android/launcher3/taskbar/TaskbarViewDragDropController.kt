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

import android.graphics.Rect
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.util.size
import com.android.launcher3.Alarm
import com.android.launcher3.DropTarget
import com.android.launcher3.Flags.enableTaskbarDragToRemove
import com.android.launcher3.LauncherModel.Companion.useModelRepositoryBinding
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.OnAlarmListener
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfo.NO_ID
import com.android.launcher3.model.data.TaskItemInfo.Companion.isSameItem
import com.android.launcher3.model.data.WorkspaceItemFactory
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.ItemInfoMatcher
import com.android.launcher3.views.Snackbar
import java.util.Collections

/**
 * Manages the [DropTarget] implementations that handle drag and drop events over the taskbarView
 * location
 */
class TaskbarViewDragDropController(
    private val activityContext: TaskbarActivityContext,
    private val taskbarView: TaskbarView,
) {
    companion object {
        private const val OPEN_OVERFLOW_DELAY_MS = 800L
        private const val CLOSE_OVERFLOW_DELAY_MS = OPEN_OVERFLOW_DELAY_MS
    }

    private val taskbarPinDelegate = taskbarView
    @VisibleForTesting val taskbarPinningDropTarget = PinningDropTarget(taskbarPinDelegate, false)
    private var overflowPinDelegate: PinnedAppsContainerDelegate? = null
    @VisibleForTesting var overflowPinningDropTarget: PinningDropTarget? = null
    @VisibleForTesting val unpinDropTarget = UnpinDropTarget()
    @VisibleForTesting var targetPinIndex = -1
    private var modelCallbacks: TaskbarModelCallbacks? = null
    @VisibleForTesting val tooltipController = TaskbarDragViewTooltip(activityContext)
    @VisibleForTesting val overflowContainerAlarm = Alarm(activityContext.mainLooper)
    private var dragUpdatedModel = false

    private enum class AlarmState {
        RUNNING_OPEN,
        RUNNING_CLOSE,
        IDLE,
    }

    private var overflowAlarmState = AlarmState.IDLE
    private val openOnAlarmListener = OnAlarmListener {
        overflowAlarmState = AlarmState.IDLE
        activityContext.controllers.taskbarViewController.openOverflowContainer()
    }
    private val closeOnAlarmListener = OnAlarmListener {
        overflowAlarmState = AlarmState.IDLE
        activityContext.controllers.taskbarViewController.closeOverflowContainer()
    }

    fun setUpCallbacks(callbacks: TaskbarModelCallbacks) {
        modelCallbacks = callbacks
    }

    /**
     * Moves the given item to the left or right in hotseat and updates the database.
     *
     * @param item the item to move
     * @param moveLeft true if moving left, false if moving right
     * @return true if item is moved
     */
    fun moveHotseatItem(item: ItemInfo, moveLeft: Boolean): Boolean {
        val hotseatItems = modelCallbacks?.hotseatItems ?: return false
        val currentIndex =
            activityContext.controllers.taskbarViewController.getHotseatItemIndex(item)
        if (currentIndex == -1) return false

        targetPinIndex = if (moveLeft) currentIndex - 1 else currentIndex + 1
        if (targetPinIndex < 0 || targetPinIndex >= hotseatItems.size) {
            targetPinIndex = -1
            return false
        }

        val updates = addOrMoveItemInDatabase(item)
        targetPinIndex = -1
        return if (updates != null) {
            modelCallbacks?.updateItemsForDragAndDrop(updates)
            true
        } else {
            false
        }
    }

    fun addDropTargets(dragController: DragController) {
        dragController.addDropTarget(taskbarPinningDropTarget)
        if (enableTaskbarDragToRemove()) {
            dragController.addDropTarget(unpinDropTarget)
        }
    }

    fun removeDropTargets(dragController: DragController) {
        dragController.removeDropTarget(taskbarPinningDropTarget)
        if (enableTaskbarDragToRemove()) {
            dragController.removeDropTarget(unpinDropTarget)
        }
    }

    fun onTaskbarItemViewDragStart(itemView: View) {
        dragUpdatedModel = false
        if (
            taskbarPinDelegate.updateItemViewVisibilityForDragState(itemView, /*isDragged */ true)
        ) {
            return
        }
        overflowPinDelegate?.updateItemViewVisibilityForDragState(itemView, /*isDragged */ true)
    }

    fun onTaskbarItemViewDragEnd(itemView: View) {
        taskbarView.cleanUpOverflowDragState(dragUpdatedModel)
        dragUpdatedModel = false
        if (
            !taskbarPinDelegate.updateItemViewVisibilityForDragState(itemView, /*isDragged */ false)
        ) {
            overflowPinDelegate?.updateItemViewVisibilityForDragState(
                itemView, /*isDragged */
                false,
            )
        }
    }

    fun addOverflowDropTarget(
        dragController: DragController,
        delegate: PinnedAppsContainerDelegate,
    ) {
        overflowPinDelegate = delegate
        overflowPinningDropTarget = PinningDropTarget(delegate, true)
        dragController.addDropTarget(overflowPinningDropTarget)
    }

    fun removeOverflowDropTarget(dragController: DragController) {
        dragController.removeDropTarget(overflowPinningDropTarget)
        overflowPinningDropTarget = null
        overflowPinDelegate = null
    }

    private fun startOpenOverflowAlarm() {
        if (overflowAlarmState == AlarmState.RUNNING_OPEN) return

        startOverflowAlarm(overflowContainerAlarm, openOnAlarmListener, OPEN_OVERFLOW_DELAY_MS)
        overflowAlarmState = AlarmState.RUNNING_OPEN
    }

    private fun startCloseOverflowAlarm() {
        if (overflowAlarmState == AlarmState.RUNNING_CLOSE) return

        startOverflowAlarm(overflowContainerAlarm, closeOnAlarmListener, CLOSE_OVERFLOW_DELAY_MS)
        overflowAlarmState = AlarmState.RUNNING_CLOSE
    }

    private fun startOverflowAlarm(alarm: Alarm, callback: OnAlarmListener, delay: Long) {
        cancelOverflowAlarm()
        alarm.setOnAlarmListener(callback)
        alarm.setAlarm(delay)
    }

    private fun cancelOverflowAlarm() {
        if (!overflowContainerAlarm.alarmPending()) return

        overflowContainerAlarm.cancelAlarm()
        overflowAlarmState = AlarmState.IDLE
    }

    private fun endDrag(delegate: PinnedAppsContainerDelegate) {
        startCloseOverflowAlarm()
        targetPinIndex = -1
    }

    /**
     * Returns [targetScreenId] where the dragObject is dropped at, and [shouldShiftLeft] which is
     * true if the dragged item's space can be made by shifting items before the dropped index to
     * the left in the hotseat.
     */
    private fun getDropTargetState(
        hotSeatItems: IntSparseArrayMap<ItemInfo>,
        draggedInfo: ItemInfo,
    ): Pair<Int, Boolean> {
        var currentPinIndex = 0
        var lastScreenId = -1
        var shouldShiftLeft = false

        for (i in 0 until hotSeatItems.size) {
            val item = hotSeatItems.valueAt(i) ?: continue
            if (item.isSameItem(draggedInfo)) {
                // The dragged item is already at the target position, nothing need to change.
                if (currentPinIndex == targetPinIndex) {
                    return Pair(item.screenId, true)
                }
                continue
            }

            if (item.screenId - lastScreenId > 1) {
                shouldShiftLeft = true
            }

            lastScreenId = item.screenId

            if (currentPinIndex == targetPinIndex) {
                return Pair(
                    if (shouldShiftLeft) lastScreenId - 1 else lastScreenId,
                    shouldShiftLeft,
                )
            }

            ++currentPinIndex
        }

        return Pair(if (shouldShiftLeft) lastScreenId else lastScreenId + 1, shouldShiftLeft)
    }

    /** Returns the list of items that need to shift left after reordering. */
    private fun getItemsToShiftLeft(
        hotseatItems: IntSparseArrayMap<ItemInfo>,
        draggedInfo: ItemInfo,
        targetScreenId: Int,
    ): List<ItemInfo> {
        val itemsToShift = mutableListOf<ItemInfo>()
        var nextScreenIdToShift = targetScreenId
        for (i in hotseatItems.size - 1 downTo 0) {
            val item = hotseatItems.valueAt(i) ?: continue
            if (item.screenId > targetScreenId) {
                continue
            }
            if (!item.isSameItem(draggedInfo) && item.screenId == nextScreenIdToShift) {
                --nextScreenIdToShift
                itemsToShift.add(item)
            } else {
                break
            }
        }

        return itemsToShift
    }

    /** Returns the list of items that need to shift right after reordering. */
    private fun getItemsToShiftRight(
        hotseatItems: IntSparseArrayMap<ItemInfo>,
        draggedInfo: ItemInfo,
        targetScreenId: Int,
    ): List<ItemInfo> {
        val itemsToShift = mutableListOf<ItemInfo>()
        var nextScreenIdToShift = targetScreenId
        for (i in 0..<hotseatItems.size) {
            val item = hotseatItems.valueAt(i) ?: continue

            if (item.screenId < targetScreenId) {
                continue
            }

            if (!item.isSameItem(draggedInfo) && item.screenId == nextScreenIdToShift) {
                itemsToShift.add(item)
                ++nextScreenIdToShift
            } else {
                break
            }
        }
        return itemsToShift
    }

    private fun addOrMoveItemInDatabase(draggedItem: ItemInfo): Set<ItemInfo>? {
        val hotseatItems = modelCallbacks?.hotseatItems ?: return null

        var hotseatItemsContainDraggedInfo = false
        var itemToUpdate = draggedItem
        // Check if the dragged item already exists in the model.
        // If it does, use the one from the Model's instance, to avoid failing the ModelWriter
        // itemInfo check.
        if (isDraggedInfoFromHotseat(draggedItem)) {
            for (i in 0 until hotseatItems.size) {
                val item = hotseatItems.valueAt(i) ?: continue
                if (item.id != NO_ID && item.id == draggedItem.id) {
                    itemToUpdate = item
                    hotseatItemsContainDraggedInfo = true
                    break
                }
            }
        }

        val (targetScreenId, shouldShiftLeft) = getDropTargetState(hotseatItems, itemToUpdate)
        if (hotseatItemsContainDraggedInfo && itemToUpdate.screenId == targetScreenId) return null

        val itemsToShift =
            if (shouldShiftLeft) getItemsToShiftLeft(hotseatItems, itemToUpdate, targetScreenId)
            else getItemsToShiftRight(hotseatItems, itemToUpdate, targetScreenId)

        val writer = activityContext.modelWriter
        for (item in itemsToShift) {
            val newPosition = item.screenId + if (shouldShiftLeft) -1 else 1
            writer.addOrMoveItemInDatabase(item, CONTAINER_HOTSEAT, newPosition, newPosition, 0)
        }

        writer.addOrMoveItemInDatabase(
            itemToUpdate,
            CONTAINER_HOTSEAT,
            targetScreenId,
            targetScreenId,
            0,
        )

        return itemsToShift.toSet() + hashSetOf(itemToUpdate)
    }

    /** Returns the [ItemInfo] from the dragged object. */
    private fun extractItemInfoFromDragObject(dragObject: DropTarget.DragObject?): ItemInfo? {
        return when (val dragItemInfo = dragObject?.dragInfo) {
            is WorkspaceItemInfo -> dragItemInfo
            is WorkspaceItemFactory -> dragItemInfo.makeWorkspaceItem(activityContext)
            else -> null
        }
    }

    private fun isDraggedInfoFromHotseat(draggedInfo: ItemInfo): Boolean {
        return draggedInfo.id != ItemInfo.NO_ID && draggedInfo.container == CONTAINER_HOTSEAT
    }

    /**
     * Implementation of the [DropTarget] that handles drag and drop events over the recent apps
     * area.
     */
    inner class UnpinDropTarget() : DropTarget {

        override fun isDropEnabled(): Boolean {
            return true
        }

        override fun getDropView(): View? {
            return null
        }

        override fun onDrop(dragObject: DropTarget.DragObject?, options: DragOptions?) {
            tooltipController.hide()
            if (dragObject == null) return

            dragUpdatedModel = true
            val itemToUnpin = extractItemInfoFromDragObject(dragObject) ?: return
            // Remove dragged views immediately - model update will end up removing the dragged item
            // views too, but may do so with a delay, and cause an item removal animation to run.
            taskbarPinDelegate.removeDraggedView()
            overflowPinDelegate?.removeDraggedView()

            deleteItemFromModel(itemToUnpin)
        }

        override fun onDragEnter(dragObject: DropTarget.DragObject?) {
            dragObject ?: return
            val draggedInfo = extractItemInfoFromDragObject(dragObject) ?: return
            if (isDraggedInfoFromHotseat(draggedInfo)) {
                if (tooltipController.isActive()) {
                    tooltipController.hide()
                }
                tooltipController.show(calculateTooltipTargetPosition(dragObject))
            }
        }

        override fun onDragOver(dragObject: DropTarget.DragObject?) {
            dragObject ?: return
            tooltipController.updatePosition(calculateTooltipTargetPosition(dragObject))
        }

        override fun onDragExit(dragObject: DropTarget.DragObject?) {
            tooltipController.hide()
        }

        override fun acceptDrop(dragObject: DropTarget.DragObject?): Boolean {
            return true
        }

        override fun prepareAccessibilityDrop() {
            TODO("Not yet implemented")
        }

        override fun getHitRectRelativeToDragLayer(outRect: Rect?) {
            if (overflowPinningDropTarget == null) {
                taskbarPinDelegate.getHitRectForPinRelativeToDragLayer(outRect)
                outRect?.offset(0, -activityContext.deviceProfile.taskbarProfile.height)
            }
        }

        /** Calculates the tooltip target position based on visual center coordinates. */
        private fun calculateTooltipTargetPosition(dragObject: DropTarget.DragObject): FloatArray {
            val targetLocation = FloatArray(2)
            dragObject.getVisualCenter(targetLocation)
            val yOffset =
                activityContext.resources.getDimensionPixelSize(R.dimen.taskbar_tooltip_y_offset)
            targetLocation[1] -= (dragObject.dragView.measuredHeight / 2f) + yOffset

            return targetLocation
        }

        /** Shows the snackbar after removing a pinned item from hotseat with undo action. */
        private fun deleteItemFromModel(item: ItemInfo) {
            val undoDeleteController = activityContext.undoDeleteController
            undoDeleteController.prepareToUndoDelete()

            if (
                activityContext.controllers.taskbarRecentAppsController.setItemMarkedForDeletion(
                    item,
                    true,
                )
            ) {
                modelCallbacks?.commitRunningAppsToUI()
            }
            undoDeleteController.deleteItem(item, "Unpin by taskbar drag and drop")

            // If model repository bindings are disabled, source of updates will not receive model
            // change events. Update the model state directly, so the changes get picked up by
            // taskbar.
            // When model repository bindings are enabled, model callbacks decide whether to handle
            // updates coming from their own context, and taskbar model callbacks let removal
            // updates through.
            if (!useModelRepositoryBinding()) {
                modelCallbacks?.bindWorkspaceComponentsRemoved(
                    ItemInfoMatcher.ofItems(Collections.singleton(item))
                )
            }

            val onUndoClicked = Runnable {
                undoDeleteController.abort()

                if (
                    activityContext.controllers.taskbarRecentAppsController
                        .setItemMarkedForDeletion(item, false)
                ) {
                    modelCallbacks?.commitRunningAppsToUI()
                }
            }

            val onDismissed = Runnable { undoDeleteController.commit() }

            val overlayContext =
                activityContext.controllers.taskbarOverlayController.requestWindow()
            activityContext.getMainThreadHandler().post {
                Snackbar.show(
                    overlayContext,
                    activityContext.getString(R.string.app_removed_from_taskbar),
                    R.string.undo,
                    onDismissed,
                    onUndoClicked,
                )
            }
        }
    }

    /**
     * Implementation of the [DropTarget] that handles drag and drop events over the hotseat items
     * area.
     */
    inner class PinningDropTarget(
        private val delegate: PinnedAppsContainerDelegate,
        private val isOverflowDropTarget: Boolean,
    ) : DropTarget {
        private var draggedInfo: ItemInfo? = null
        private val dragObjectVisualCenter = FloatArray(2)

        private val startingIndex: Int
            get() =
                if (isOverflowDropTarget) {
                    val pinnedCount = taskbarView.getNumOfVisibleIconsInPinnedSection()
                    val overflowAdjustment =
                        if (taskbarView.getTaskbarPinnedOverflowView() != null) 1 else 0
                    pinnedCount - overflowAdjustment
                } else 0

        private val canPinMoreItems: Boolean
            get() {
                val hotseatItems = modelCallbacks?.hotseatItems ?: return false
                if (draggedInfo !== null && isDraggedInfoFromHotseat(draggedInfo!!)) return true
                return hotseatItems.size < activityContext.taskbarSpecsEvaluator.maxPinnableCount
            }

        override fun isDropEnabled(): Boolean {
            return true
        }

        override fun getDropView(): View? {
            return null
        }

        override fun onDrop(dragObject: DropTarget.DragObject?, options: DragOptions?) {
            val newInfo = extractItemInfoFromDragObject(dragObject) ?: return

            val createdNewItem = delegate.updateForDroppedItem(newInfo)

            val updates = addOrMoveItemInDatabase(newInfo)
            dragUpdatedModel = updates != null
            if (updates != null) {
                if (createdNewItem) {
                    if (delegate != taskbarPinDelegate) {
                        taskbarPinDelegate.removeDraggedView()
                    }
                    if (delegate != overflowPinDelegate) {
                        overflowPinDelegate?.removeDraggedView()
                    }
                }
                modelCallbacks?.updateItemsForDragAndDrop(updates)
            }
            endDrag(delegate)
        }

        override fun onDragEnter(dragObject: DropTarget.DragObject?) {
            if (isOverflowDropTarget) {
                cancelOverflowAlarm()
            }

            dragObject ?: return
            draggedInfo = extractItemInfoFromDragObject(dragObject)
            if (!canPinMoreItems) return

            dragObject.getVisualCenter(dragObjectVisualCenter)

            delegate.reserveDropSlotForDragLocation(dragObjectVisualCenter[0].toInt())
        }

        override fun onDragOver(dragObject: DropTarget.DragObject?) {
            dragObject ?: return
            dragObject.getVisualCenter(dragObjectVisualCenter)
            if (!canPinMoreItems) return

            if (isOverflowDropTarget) {
                delegate.reserveDropSlotForDragLocation(dragObjectVisualCenter[0].toInt())
                targetPinIndex = delegate.getPinIndex(startingIndex)
            } else if (delegate.isPointOnOverflowIcon(dragObjectVisualCenter)) {
                startOpenOverflowAlarm()
            } else {
                startCloseOverflowAlarm()
                delegate.reserveDropSlotForDragLocation(dragObjectVisualCenter[0].toInt())
                targetPinIndex = delegate.getPinIndex(startingIndex)
            }
        }

        override fun onDragExit(dragObject: DropTarget.DragObject?) {
            if (dragObject?.dragComplete != true || dragObject.cancelled) {
                delegate.releaseDropSlot()
                endDrag(delegate)
            }
        }

        override fun acceptDrop(dragObject: DropTarget.DragObject?): Boolean {
            // TODO(b/447444838): For now, only accept drops when the number of pinned items has
            // not reached limit. This will probably be modified after dropping to hotseat overflow
            // folder UX finalized.
            return targetPinIndex >= 0 && canPinMoreItems
        }

        override fun prepareAccessibilityDrop() {
            TODO("Not yet implemented")
        }

        override fun getHitRectRelativeToDragLayer(outRect: Rect?) {
            delegate.getHitRectForPinRelativeToDragLayer(outRect)
        }
    }

    /**
     * A delegate for a container that manages pinned apps, used for drag-and-drop operations.
     *
     * This interface is implemented by a view, such as [TaskbarView], that can have apps pinned to
     * it. It provides the necessary boundaries (hit rectangles) to a drag controller, allowing the
     * controller to determine valid drop zones for pinning or unpinning an item.
     */
    interface PinnedAppsContainerDelegate {
        /**
         * Calculates the hit rectangle for the primary interactive area of the pinned icons,
         * relative to the DragLayer. This area includes the All Apps button and all visible icons.
         * Returns a Rect object to be populated with the calculated coordinates.
         */
        fun getHitRectForPinRelativeToDragLayer(outRect: Rect?)

        /** Returns true if the given point is on the pinned overflow icon. */
        fun isPointOnOverflowIcon(point: FloatArray): Boolean

        /** Reserves the location with a placeholder indicating where the icon to be dropped. */
        fun reserveDropSlotForDragLocation(onScreenLocationX: Int)

        /** Clears the reserved drop slot. */
        fun releaseDropSlot()

        /**
         * Updates the UI to reflect [item] being dropped into the current drop slot, creating a new
         * view for the item as necessary. Returns whether a new view for the item was created, as
         * opposed to reusing the existing "draging" view for the item.
         */
        fun updateForDroppedItem(item: ItemInfo): Boolean

        /**
         * Removes the view that's being dragged (i.e. view that's been set as being dragged using
         * [updateItemViewVisibilityForDragState]) from the container. Called when the dragged item
         * gets unpinned during drop operation, and is expected to be followed by a model update
         * removing the dragged item.
         */
        fun removeDraggedView()

        /**
         * Returns the index in the taskbar where the dragged item would be pinned if dropped at the
         * current location.
         */
        fun getPinIndex(startingIndex: Int): Int

        /**
         * Updates the visibility of the dragged Taskbar item view based on its drag state. Return
         * true if [itemView] should be and was successfully handled by this delegate.
         */
        fun updateItemViewVisibilityForDragState(itemView: View, isDragged: Boolean): Boolean
    }
}
