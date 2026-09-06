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

package com.android.launcher3.popup

import android.content.Context
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.util.isEmpty
import androidx.core.util.size
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut.Factory
import com.android.launcher3.views.ActivityContext

/**
 * A single menu item shortcut to allow users to pin an item to the taskbar and unpin an item from
 * the taskbar.
 */
class PinToTaskbarShortcut<T>
@JvmOverloads
constructor(
    target: T,
    itemInfo: ItemInfo?,
    originalView: View,
    @get:VisibleForTesting val isPin: Boolean,
    private val maxPinnableItems: Int,
    private val pinnedInfoList: SparseArray<ItemInfo?>,
    private val onClickCallback: Runnable? = null,
) :
    SystemShortcut<T>(
        if (isPin) R.drawable.ic_pin else R.drawable.ic_unpin,
        if (isPin) R.string.add_to_taskbar else R.string.remove_from_taskbar,
        target,
        itemInfo,
        originalView,
    ) where T : Context?, T : ActivityContext? {

    override fun onClick(v: View?) {
        if (isPin && pinnedInfoList.size >= maxPinnableItems) {
            dismissTaskMenuView()
            showNoSpaceMessage(requireNotNull(mTarget))
            return
        }

        // Create a placeholder callbacks for the writer to notify other launcher model callbacks
        // after update.
        val callbacks: BgDataModel.Callbacks = object : BgDataModel.Callbacks {}

        val writer =
            LauncherAppState.getInstance(mOriginalView.context)
                .model
                .getWriter(true, requireNotNull(mTarget), callbacks)

        if (!isPin) {
            var infoToUnpin = mItemInfo
            // If the shortcut is not triggered from the taskbar, find the info in the taskbar to
            // unpin. Otherwise, directly unpin the info on the taskbar.
            if (mItemInfo.container != Favorites.CONTAINER_HOTSEAT) {
                val targetKey = mItemInfo.componentKey
                for (i in 0 until pinnedInfoList.size) {
                    val taskbarItem = pinnedInfoList.valueAt(i)
                    if (taskbarItem?.componentKey == targetKey) {
                        infoToUnpin = taskbarItem
                        break
                    }
                }
            }
            unpinItem(writer, infoToUnpin)
            onClickCleanUp(v)
            return
        }

        if (maxPinnableItems < 0) return

        val newInfo =
            when (mItemInfo) {
                is com.android.launcher3.model.data.AppInfo ->
                    mItemInfo.makeWorkspaceItem(mOriginalView.context)

                is WorkspaceItemInfo -> mItemInfo.clone().apply { id = ItemInfo.NO_ID }
                else -> return
            }

        // Reorder the taskbar only if we can't find a space that is to the right of all other
        // items.
        if (pinnedInfoList[maxPinnableItems - 1] != null) {
            compactTaskbarItems(writer)
        }

        // Find the first available space that has larger index than all other items.
        var targetIdx = -1
        for (i in maxPinnableItems - 1 downTo 0) {
            if (pinnedInfoList[i] == null) {
                targetIdx = i
            } else {
                break
            }
        }

        if (targetIdx == -1) {
            Log.e(TAG, "No valid space for $mItemInfo to pin to Taskbar")
            return
        }

        val (cellX, cellY) = getCellCoordinates(targetIdx)

        pinItem(writer, newInfo, targetIdx, cellX, cellY)
        onClickCleanUp(v)
    }

    /**
     * Called in [onClick] after the item is pinned/unpinned and right before [onClick] returns to
     * reset the UI.
     */
    private fun onClickCleanUp(shortcutView: View?) {
        sendAccessibilityAnnouncement(shortcutView)
        dismissTaskMenuView()
        onClickCallback?.run()
    }

    private fun sendAccessibilityAnnouncement(shortcutView: View?) {
        if (
            shortcutView == null ||
                mTarget == null ||
                mTarget.getSystemService(AccessibilityManager::class.java)?.isEnabled != true
        ) {
            return
        }
        val announcementText =
            if (isPin) mTarget.getString(R.string.app_added_to_taskbar)
            else mTarget.getString(R.string.app_removed_from_taskbar)

        shortcutView.setContentDescription(announcementText)
        shortcutView.sendAccessibilityEventUnchecked(
            AccessibilityEvent().apply {
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
            }
        )
    }

    /**
     * Moves all the taskbar items to the front so that spaces that don't have a pinned item will be
     * at the end of the taskbar. This can ensure that the newly pinned app will be appended to the
     * end of the taskbar.
     */
    private fun compactTaskbarItems(writer: IModelWriter) {
        if (!isPin || pinnedInfoList.isEmpty()) return

        // Collect existing non-null items in their current order (based on SparseArray keys)
        val nonNullItems = List(maxPinnableItems) { i -> pinnedInfoList[i] }.filterNotNull()

        // Update database for moved items
        for ((newScreenId, itemToUpdate) in nonNullItems.withIndex()) {
            // Calculate new cellX, cellY based on newScreenId
            val (newCellX, newCellY) = getCellCoordinates(newScreenId)
            if (
                itemToUpdate.screenId != newScreenId ||
                    itemToUpdate.cellX != newCellX ||
                    itemToUpdate.cellY != newCellY
            ) {
                itemToUpdate.screenId = newScreenId
                itemToUpdate.cellX = newCellX
                itemToUpdate.cellY = newCellY
                // container remains CONTAINER_HOTSEAT
                writer.updateItemInDatabase(itemToUpdate)
            }
        }

        // Update the mPinnedInfoList in memory to reflect the new state
        pinnedInfoList.clear()
        for ((i, nonNullItem) in nonNullItems.withIndex()) {
            pinnedInfoList[i] = nonNullItem
        }
    }

    /** This should be the same as how Hotseat calculates cellX and cellY from a rank. */
    private fun getCellCoordinates(targetIdx: Int): Pair<Int, Int> {
        val dp: DeviceProfile = requireNotNull(mTarget).deviceProfile
        val cellX = if (dp.isVerticalBarLayout) 0 else targetIdx
        val cellY =
            if (dp.isVerticalBarLayout) (dp.hotseatProfile.numShownIcons - (targetIdx + 1)) else 0

        return Pair(cellX, cellY)
    }

    /* Functions that are non-companion to be easier to spy in tests. */

    @VisibleForTesting
    fun pinItem(
        writer: IModelWriter,
        info: WorkspaceItemInfo,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        writer.addOrMoveItemInDatabase(info, Favorites.CONTAINER_HOTSEAT, screenId, cellX, cellY)
    }

    @VisibleForTesting
    fun unpinItem(writer: IModelWriter, info: ItemInfo) {
        writer.deleteItemFromDatabase(info, "item unpinned through long-press menu")
    }

    @VisibleForTesting
    fun showNoSpaceMessage(context: Context) {
        Toast.makeText(context, R.string.no_room_in_taskbar, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "PinToTaskbarShortcut"

        @JvmStatic
        fun getPinShortcutFactoryFromLauncher(
            maxPinnableItems: Int,
            supportsPinnedAppsOverflow: Boolean,
        ): Factory<Launcher> {
            return Factory { context, itemInfo, originalView ->
                val allPinnedItems = context.pinnedItems

                if (allPinnedItems == null) {
                    Log.e(TAG, "Can not load the valid list of pinned apps")
                    return@Factory null
                }

                // If the target ItemInfo is already pinned on taskbar. Show the unpin option
                // instead.
                var isPinnedInHotseat = false
                for (i in 0 until allPinnedItems.size) {
                    if (allPinnedItems.valueAt(i).getComponentKey() == itemInfo.componentKey) {
                        isPinnedInHotseat = true
                        break
                    }
                }

                if (isPinnedInHotseat) {
                    // As the item is already pinned, return a shortcut to UNPIN it.
                    return@Factory PinToTaskbarShortcut<Launcher>(
                        context,
                        itemInfo,
                        originalView,
                        false,
                        maxPinnableItems,
                        allPinnedItems,
                    )
                }

                if (supportsPinnedAppsOverflow || allPinnedItems.size < maxPinnableItems) {
                    return@Factory PinToTaskbarShortcut<Launcher>(
                        context,
                        itemInfo,
                        originalView,
                        true,
                        maxPinnableItems,
                        allPinnedItems,
                        context::onItemPinnedFromContextMenu,
                    )
                }

                return@Factory null
            }
        }
    }
}
