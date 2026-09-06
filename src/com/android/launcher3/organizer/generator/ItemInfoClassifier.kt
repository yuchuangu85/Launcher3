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

/**
 * Data class representing an [ItemInfo] that has been classified under a specific topic.
 *
 * @property itemInfo information about the item without placement resolved.
 * @property topic The primary topic this item belongs to.
 * @property score A confidence score [0.0 to 1.0] indicating how well the item matches the topic.
 */
data class TopicClassifiedItem(val itemInfo: ItemInfo, val topic: String, val score: Float)

/**
 * Interface for a strategy that analyzes [ItemInfo] objects to determine their best-matching topic.
 */
interface ItemInfoClassifier {
    /**
     * Executes classification on [items].
     *
     * @param items The items to be classified.
     * @param topics The topics on which to classify items.
     * @return A list of [TopicClassifiedItem]s.
     */
    suspend fun classify(items: List<ItemInfo>, topics: List<String>): List<TopicClassifiedItem>
}
