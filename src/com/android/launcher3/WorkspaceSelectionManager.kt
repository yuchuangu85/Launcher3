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
import android.view.View
import com.android.launcher3.WorkspaceSelectionManager.ItemSelectionType
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject

/**
 * Manages setting the selection state of items within the Workspace.
 *
 * Responsible for:
 * - Handling different selection gestures:
 *     - Single selection (select one item, deselecting others).
 *     - Toggle selection (adding/removing individual items from the selection).
 *     - Range selection (selecting a group of items between an anchor and a new item).
 *     - Box selection (selecting all items within a drawn rectangle).
 * - Applying the selected state to the views.
 */
sealed interface WorkspaceSelectionManager {

    enum class ItemSelectionType {
        // Selects a single item, deselecting any other currently selected items.
        SELECT_SINGLE,
        // Toggles the selection state of a single item, leaving other selections unchanged.
        TOGGLE_SINGLE,
        // Selects a range of items between a previously selected anchor item and the current item.
        RANGE,
    }

    /**
     * Handles a selection event on a single item.
     *
     * @param view The view that was interacted with.
     * @param itemSelectionType The type of selection to perform.
     */
    fun updateItemSelection(view: View, itemSelectionType: ItemSelectionType)

    /**
     * Handles the start of a box selection drag.
     *
     * @param isAppending True if the selection should append to the current selection.
     */
    fun startBoxSelection(isAppending: Boolean)

    /**
     * Handles a box selection event.
     *
     * @param boxBounds The rectangle defining the selection area in window coordinates.
     */
    fun updateBoxSelection(boxBounds: Rect)

    /** Handles the end of a box selection drag. */
    fun endBoxSelection()
}

@ActivityContextSingleton
class WorkspaceSelectionManagerStub @Inject constructor() : WorkspaceSelectionManager {
    override fun updateItemSelection(view: View, itemSelectionType: ItemSelectionType) = Unit

    override fun startBoxSelection(isAppending: Boolean) = Unit

    override fun updateBoxSelection(boxBounds: Rect) = Unit

    override fun endBoxSelection() = Unit
}

@ActivityContextSingleton
class WorkspaceSelectionManagerImpl @Inject constructor(val activityContext: ActivityContext) :
    WorkspaceSelectionManager {

    private fun getSelectableViews(): List<View> {
        val items = ArrayList<View>()
        val op = ItemOperator { info: ItemInfo?, view: View ->
            items.add(view)
            false // Return false to continue iterating through all the items.
        }

        val openFolder = Folder.getOpen(activityContext)
        if (openFolder != null) {
            openFolder.mapOverVisibleItems(op)
        } else {
            activityContext.content.mapOverVisibleItems(op)
        }
        return items
    }

    /**
     * The view that serves as the fixed point for range selections. When a range selection (e.g.,
     * shift-click) occurs, the selection will include all items between this [anchorView] and the
     * newly selected item, based on their row-major order in the grid.
     */
    private var anchorView: View? = null

    private data class BoxSelectionState(
        val preservedSelection: Set<View>,
        val isAppending: Boolean,
    )

    private var boxSelectionState: BoxSelectionState? = null

    override fun startBoxSelection(isAppending: Boolean) {
        check(boxSelectionState == null) { "Box selection is already active" }

        val preserved =
            if (isAppending) {
                getSelectableViews().filter { it.isSelected }.toSet()
            } else {
                emptySet()
            }
        boxSelectionState = BoxSelectionState(preserved, isAppending)
    }

    override fun updateItemSelection(view: View, itemSelectionType: ItemSelectionType) {
        when (itemSelectionType) {
            ItemSelectionType.SELECT_SINGLE -> {
                performSelection(listOf(view), SelectionAction.NEW_SELECTION)
            }
            ItemSelectionType.TOGGLE_SINGLE -> {
                performSelection(listOf(view), SelectionAction.TOGGLE_SELECTION)
            }
            ItemSelectionType.RANGE -> {
                // TODO: Calculate the list of views to select between [view] and [anchorView].
                performSelection(emptyList(), SelectionAction.APPEND_SELECTION)
            }
        }
        anchorView = view
    }

    override fun updateBoxSelection(boxBounds: Rect) {
        val currentState = boxSelectionState
        if (currentState !is BoxSelectionState) return

        val itemsInBox = findIntersectingViews(boxBounds)

        if (currentState.isAppending) {
            handleAppendSelection(itemsInBox, currentState.preservedSelection)
        } else {
            handleNewSelection(itemsInBox)
        }
    }

    override fun endBoxSelection() {
        boxSelectionState = null
    }

    private fun findIntersectingViews(boxBounds: Rect): Set<View> {
        return getSelectableViews()
            .filter { view ->
                val viewLocation = IntArray(2)
                view.getLocationInWindow(viewLocation)
                val viewBounds =
                    Rect(
                        viewLocation[0],
                        viewLocation[1],
                        viewLocation[0] + view.width,
                        viewLocation[1] + view.height,
                    )
                Rect.intersects(boxBounds, viewBounds)
            }
            .toSet()
    }

    private fun handleNewSelection(itemsInBox: Set<View>) {
        performSelection(itemsInBox.toList(), SelectionAction.NEW_SELECTION)
    }

    private fun handleAppendSelection(itemsInBox: Set<View>, preservedSelection: Set<View>) {
        val allSelectableViews = getSelectableViews()
        for (view in allSelectableViews) {
            val isInBox = itemsInBox.contains(view)
            val wasPreserved = preservedSelection.contains(view)

            if (isInBox || wasPreserved) {
                setViewSelected(view, true)
            } else {
                setViewSelected(view, false)
            }
        }
    }

    private enum class SelectionAction {
        // Creates a new selection, deselecting any previously selected items.
        NEW_SELECTION,
        // Adds items to the current selection.
        APPEND_SELECTION,
        // Toggles the selection state of the target items.
        TOGGLE_SELECTION,
    }

    /**
     * Performs the selection action on the given list of views.
     *
     * @param targetViews The list of views to perform the selection action on. This is a subset of
     *   the views provided by [getSelectableViews].
     * @param action The selection action to perform.
     */
    private fun performSelection(targetViews: List<View>, action: SelectionAction) {
        when (action) {
            SelectionAction.NEW_SELECTION -> {
                // Reset all selectable views to no longer be selected.
                for (view in getSelectableViews()) {
                    setViewSelected(view, false)
                }
                // Set only the targetViews as selected for the new selection.
                for (view in targetViews) {
                    setViewSelected(view, true)
                }
            }
            SelectionAction.APPEND_SELECTION -> {
                for (view in targetViews) {
                    setViewSelected(view, true)
                }
            }
            SelectionAction.TOGGLE_SELECTION -> {
                for (view in targetViews) {
                    toggleViewSelected(view)
                }
            }
        }
    }

    private fun toggleViewSelected(view: View) {
        if (isViewSelectable(view)) {
            view.isSelected = !view.isSelected
            view.invalidate()
        }
    }

    private fun setViewSelected(view: View, isSelected: Boolean) {
        if (isViewSelectable(view)) {
            view.isSelected = isSelected
            view.invalidate()
        }
    }

    private fun isViewSelectable(view: View): Boolean {
        return (view is BubbleTextView || view is FolderIcon || view is AppPairIcon)
    }
}
