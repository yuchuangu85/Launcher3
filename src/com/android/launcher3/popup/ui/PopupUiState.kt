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

import android.graphics.Rect
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.PopupCategory

/** Sealed interface for the different types of click events in the popup menu. */
sealed interface PopupClickEvent

/** Represents a click on a system shortcut. */
data class SystemShortcutClickEvent(val item: PopupItem) : PopupClickEvent

/** Represents a click on a deep shortcut. */
data class DeepShortcutClickEvent(val item: ItemInfoWithIcon?, val iconBounds: Rect) :
    PopupClickEvent

/** Data class which stores all the values we need to create a long press menu shortcut. */
data class PopupItem(
    val iconResId: Int,
    val labelResId: Int,
    val popupAction: () -> Unit,
    val category: PopupCategory,
)

/** Defines the display style for the bottom sections of the popup menu. */
enum class MainSegmentsStyle {
    /** Renders each segment as a flat list, with all items visible. */
    LIST,
    /** Renders segments as an accordion, where opening one collapses the other. */
    ACCORDION,
}

/** Represents the UI state of the popup menu. */
data class PopupUiState(
    /** Icon only system shortcuts. */
    val compactSystemShortcuts: List<PopupItem> = emptyList(),
    /** Full size system shortcuts. */
    val standardSystemShortcuts: List<PopupItem> = emptyList(),
    /** App shortcuts. */
    val deepShortcuts: List<ItemInfoWithIcon?> = emptyList(),
    /** Style for displaying the main sections of non-compact shortcuts. */
    val mainSegmentsStyle: MainSegmentsStyle = MainSegmentsStyle.LIST,
)
