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

package com.android.launcher3.popup

import android.view.View
import com.android.launcher3.logging.StatsLogManager.EventEnum
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext

/**
 * Enum for the category of popup we have, as we handle different categories of shortcuts
 * differently depending on the category.
 */
enum class PopupCategory {
    SYSTEM_SHORTCUT,
    SYSTEM_SHORTCUT_FIXED,
}

/** Data class which stores all the values we need to create a long press menu shortcut. */
data class PopupData(
    val iconResId: Int,
    val labelResId: Int,
    val category: PopupCategory,
    val eventId: EventEnum = IGNORE,
    val popupAction: (context: ActivityContext, itemInfo: ItemInfo, view: View) -> Unit,
)

/** Mapper to get all the popup data needed for the long press menu. */
interface PopupDataMapper {
    /**
     * Get the popup data for a specific item.
     *
     * @param itemInfo is linked to a popupData list specific to it.
     * @return a list of popup data belonging to that item.
     */
    fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>?
}
