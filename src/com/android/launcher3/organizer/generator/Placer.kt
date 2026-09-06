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
 * Interface responsible for arranging a list of [TopicClassifiedItem]s into structured containers
 * (like screens or folders).
 *
 * Implementations use [Template]s to define the layout rules and constraints for each container.
 */
interface Placer {
    /**
     * Arranges the given [classifiedItems] into structured containers.
     *
     * @param classifiedItems A list where each element is a prioritized list of items to be
     *   arranged in a single container.
     * @param templates The list of layout templates to follow.
     * @return A list of containers, where each container is a [List] of [ItemInfo]s with their
     *   positional fields (cellX, cellY, screenId, etc.) correctly populated.
     */
    fun place(
        classifiedItems: List<TopicClassifiedItem>,
        templates: List<Template>,
    ): List<List<ItemInfo>>
}
