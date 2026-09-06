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

import com.android.launcher3.model.data.ItemInfo
import javax.inject.Inject
import javax.inject.Named

class PopupDataRepository
@Inject
constructor(@Named(POPUP_DATA_MAPPER) val mappers: Set<@JvmSuppressWildcards PopupDataMapper>) {

    /**
     * Retrieves the popup data for a specific [ItemInfo].
     *
     * @param itemInfo The item to retrieve popup data for.
     * @return the list of [PopupData] if available, or null if the item type is not supported.
     */
    fun getAllSupportedPopupActions(itemInfo: ItemInfo): List<PopupData>? {
        // TODO: Implement some sorting logic
        return mappers.mapNotNull { it.getPopupDataByItemInfo(itemInfo) }.flatten().ifEmpty { null }
    }

    companion object {
        const val POPUP_DATA_MAPPER = "POPUP_DATA_MAPPER"
    }
}
