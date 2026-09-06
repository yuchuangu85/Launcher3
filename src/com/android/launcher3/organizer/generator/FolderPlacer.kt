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

package com.android.launcher3.organizer.generator

import com.android.launcher3.model.data.ItemInfo

/** Arranges items into folders by assigning them sequential ranks. */
class FolderPlacer {
    /**
     * Arranges the given [itemsToPlace] into a single folder.
     *
     * @param itemsToPlace The prioritized list of items to be arranged.
     * @return A list of [ItemInfo]s with their rank field correctly populated.
     */
    fun place(itemsToPlace: List<TopicClassifiedItem>): List<ItemInfo> {
        return itemsToPlace.mapIndexed { index, topicItem ->
            topicItem.itemInfo.makeShallowCopy().apply { this.rank = index }
        }
    }
}
