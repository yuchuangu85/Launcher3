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

import android.graphics.Point
import com.android.launcher3.LauncherSettings
import com.android.launcher3.util.CellAndSpan

/**
 * Represents a predefined slot within a [Template].
 *
 * @property cellAndSpan The grid position and size of the slot.
 * @property itemTypeId The required item type for this slot (e.g.,
 *   [LauncherSettings.Favorites.ITEM_TYPE_APPLICATION]).
 */
data class TemplateItem(val cellAndSpan: CellAndSpan, val itemTypeId: Int)

/**
 * Defines a layout specification for a single container (like a home screen page).
 *
 * A collection of [TemplateItem]s that act as prioritized slots to be filled by a [Placer].
 *
 * @property items The list of predefined slots available in this template.
 */
data class Template(val items: List<TemplateItem>)

/** Generates templates for screens */
interface TemplateGenerator {
    /**
     * Defines a layout specification for a single container (like a home screen page).
     *
     * It specifies the overall grid dimensions and a collection of [TemplateItem]s that act as
     * prioritized slots to be filled by a [Placer].
     *
     * @param n The number of templates to generate
     * @param gridSize The dimensions (columns x rows) of the container's grid.
     * @return A list of templates.
     */
    suspend fun generateTemplates(n: Int, gridSize: Point): List<Template>
}
