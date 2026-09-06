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

package com.android.launcher3.popup.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHeight
import kotlin.math.min

/** Represents the currently expanded section within the collapsible popup menu. */
enum class ExpandedSection {
    /** Indicates that the system shortcuts section is expanded. */
    SYSTEM,
    /** Indicates that the deep shortcuts section is expanded. */
    DEEP,
}

/**
 * ViewModel for managing the UI state and interactions of the Launcher3 popup menu.
 *
 * This class holds the [PopupUiState] which determines how the popup content is rendered and
 * manages the expansion state of the sections. It also handles the loading of deep shortcuts
 * asynchronously.
 */
class PopupViewModel {

    /** The current UI state of the popup */
    var state: PopupUiState by mutableStateOf(PopupUiState())
        private set

    /** The currently expanded section in an accordion popup menu, or null if none are expanded. */
    var expandedSection: ExpandedSection? by mutableStateOf(null)
        private set

    /** The maximum number of rows that can be displayed */
    private var maxRows = DEFAULT_MAX_ROWS

    /** The [LauncherPrefs] used to persist the state of the popup menu. */
    private lateinit var launcherPrefs: LauncherPrefs

    /**
     * Initializes the ViewModel with the initial set of system shortcuts and a placeholder count
     * for deep shortcuts. This method determines the initial [PopupUiState].
     *
     * @param systemShortcuts The list of available system shortcuts.
     * @param deepShortcutCount The initial count of deep shortcuts. Actual deep shortcuts are
     *   loaded asynchronously later.
     * @param availableHeightDp The available height of the display in dp.
     * @param launcherPrefs The [LauncherPrefs] to use for persisting UI state.
     */
    fun init(
        systemShortcuts: List<PopupItem>,
        deepShortcutCount: Int,
        availableHeightDp: Float,
        launcherPrefs: LauncherPrefs,
    ) {
        this.launcherPrefs = launcherPrefs
        maxRows = calculateMaxRows(availableHeightDp)
        updateState(systemShortcuts, deepShortcutCount)
    }

    /**
     * Determine how many rows we can fit given the available height of the device.
     *
     * @param availableHeightDp The height available to display the popup menu on the screen.
     */
    private fun calculateMaxRows(availableHeightDp: Float): Int {
        return min(DEFAULT_MAX_ROWS, (availableHeightDp / popupMenuItemHeight.value).toInt())
    }

    /**
     * Updates the [PopupUiState] based on the number of deep shortcuts.
     *
     * This method determines whether the popup should display only a list, an accordion, and if we
     * should show compact system shortcuts.
     *
     * @param systemShortcuts The list of available system shortcuts.
     * @param deepShortcutCount The number of deep shortcuts to consider for the current state.
     */
    private fun updateState(systemShortcuts: List<PopupItem>, deepShortcutCount: Int) {
        val numDeep = min(deepShortcutCount, PopupPopulator.MAX_SHORTCUTS)
        val deepPlaceholder = List(numDeep) { null }
        expandedSection = null

        // Scenario where we don't surpass the max amount of rows with the items we have.
        if (systemShortcuts.size + numDeep <= maxRows) {
            state =
                PopupUiState(
                    standardSystemShortcuts = systemShortcuts,
                    deepShortcuts = deepPlaceholder,
                )
            return
        }

        val maxStandardItemsAccordion = maxRows - 1
        val maxStandardItemsWithTopBar = maxRows - 1
        val maxStandardItemsWithAccordionWithTopBar = maxRows - 2

        // Here we determine if we should show the accordion, which should only show up if
        // there are at least DEEP_SHORTCUTS_ACCORDION_THRESHOLD deep shortcuts. We also determine
        // how many system shortcuts should be compact, and we try to minimize the amount as much
        // as possible.
        val (fixed, compactEligible) =
            systemShortcuts.partition { it.category == PopupCategory.SYSTEM_SHORTCUT_FIXED }
        val isAccordion = numDeep >= DEEP_SHORTCUTS_ACCORDION_THRESHOLD
        val totalSystemItems = fixed.size + compactEligible.size

        // Calculate how many compactEligible items should be promoted to the standard list.
        val numToPromote =
            if (totalSystemItems <= maxStandardItemsAccordion) {
                // If totalSystemItems does not exceed MAX_STANDARD_ITEMS, promote all
                // compactEligible items. This ensures compactSystemShortcuts will be empty.
                compactEligible.size
            } else {
                // totalSystemItems > MAX_STANDARD_ITEMS, so compact items are allowed.
                val targetSize =
                    if (isAccordion) maxStandardItemsWithAccordionWithTopBar
                    else maxStandardItemsWithTopBar - numDeep
                val availableSlots = maxOf(0, targetSize - fixed.size)
                min(compactEligible.size, availableSlots)
            }

        if (isAccordion) {
            expandedSection = launcherPrefs.get(LauncherPrefs.EXPANDED_POPUP_MENU_SECTION)
        }

        state =
            PopupUiState(
                compactSystemShortcuts = compactEligible.drop(numToPromote),
                standardSystemShortcuts = fixed + compactEligible.take(numToPromote),
                deepShortcuts = deepPlaceholder,
                mainSegmentsStyle =
                    if (isAccordion) MainSegmentsStyle.ACCORDION else MainSegmentsStyle.LIST,
            )
    }

    /**
     * Called when the actual deep shortcuts have been loaded asynchronously. This updates the
     * [PopupUiState] with the concrete deep shortcut items.
     *
     * @param deepShortcuts The list of loaded [ItemInfoWithIcon] for deep shortcuts.
     */
    fun onDeepShortcutsLoaded(deepShortcuts: List<ItemInfoWithIcon>) {
        state = state.copy(deepShortcuts = deepShortcuts)
    }

    /**
     * Expands a specific section (System or Deep Shortcuts). This method only has an effect if the
     * current [PopupUiState.mainSegmentsStyle] is ACCORDION and if the [sectionToExpand] is not
     * already expanded.
     *
     * @param sectionToExpand The section to expand.
     */
    fun expandSection(sectionToExpand: ExpandedSection) {
        if (
            state.mainSegmentsStyle == MainSegmentsStyle.ACCORDION &&
                expandedSection != sectionToExpand
        ) {
            expandedSection = sectionToExpand
            launcherPrefs.putSync(LauncherPrefs.EXPANDED_POPUP_MENU_SECTION.to(sectionToExpand))
        }
    }

    companion object {
        /** The default maximum number of rows that can be displayed */
        private const val DEFAULT_MAX_ROWS = 7

        /** The minimum number of deep shortcuts that can trigger the accordion layout. */
        private const val DEEP_SHORTCUTS_ACCORDION_THRESHOLD = 2
    }
}
