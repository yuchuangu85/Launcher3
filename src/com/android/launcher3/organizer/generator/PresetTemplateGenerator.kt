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
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.util.CellAndSpan

/** Generate templates using a preset set of rules. */
class PresetTemplateGenerator : TemplateGenerator {
    override suspend fun generateTemplates(n: Int, gridSize: Point): List<Template> {
        if (gridSize.x <= 0 || gridSize.y <= 0) return emptyList()

        val strategies =
            listOf(
                generateIconHeavyTemplate(gridSize),
                generateWidgetHeavyTemplate(gridSize),
                generateMinimalTemplate(gridSize),
            )

        return strategies.take(n.coerceAtMost(strategies.size))
    }

    private fun generateIconHeavyTemplate(gridSize: Point): Template {
        val items = mutableListOf<TemplateItem>()
        // Top widget (full width, 2 rows)
        if (gridSize.y >= 2) {
            items.add(TemplateItem(CellAndSpan(0, 0, gridSize.x, 2), ITEM_TYPE_APPWIDGET))
        }

        // Fill everything else with icons
        val startY = if (gridSize.y >= 2) 2 else 0
        for (y in startY until gridSize.y) {
            for (x in 0 until gridSize.x) {
                items.add(TemplateItem(CellAndSpan(x, y, 1, 1), ITEM_TYPE_APPLICATION))
            }
        }
        return Template(items)
    }

    private fun generateWidgetHeavyTemplate(gridSize: Point): Template {
        val items = mutableListOf<TemplateItem>()
        var currentY = 0
        // Stack as many 2-row widgets as possible, leaving at least one row for icons
        while (currentY + 2 < gridSize.y) {
            items.add(TemplateItem(CellAndSpan(0, currentY, gridSize.x, 2), ITEM_TYPE_APPWIDGET))
            currentY += 2
        }
        // Fill the remaining row(s) with icons
        for (y in currentY until gridSize.y) {
            for (x in 0 until gridSize.x) {
                items.add(TemplateItem(CellAndSpan(x, y, 1, 1), ITEM_TYPE_APPLICATION))
            }
        }
        return Template(items)
    }

    private fun generateMinimalTemplate(gridSize: Point): Template {
        val items = mutableListOf<TemplateItem>()
        // One centered-ish widget if space allows
        if (gridSize.y >= 3) {
            val widgetWidth = (gridSize.x - 1).coerceAtLeast(1)
            val widgetX = (gridSize.x - widgetWidth) / 2
            items.add(TemplateItem(CellAndSpan(widgetX, 1, widgetWidth, 2), ITEM_TYPE_APPWIDGET))
        }

        // Fill the bottom row
        val iconCount = gridSize.x
        items.add(TemplateItem(CellAndSpan(0, gridSize.y - 1, 1, 1), ITEM_TYPE_FOLDER))
        val startX = (gridSize.x - iconCount).coerceAtLeast(1).coerceAtMost(gridSize.x - 1)
        for (x in 0 until (iconCount - 1).coerceAtLeast(0)) {
            if (startX + x < gridSize.x) {
                items.add(
                    TemplateItem(
                        CellAndSpan(startX + x, gridSize.y - 1, 1, 1),
                        ITEM_TYPE_APPLICATION,
                    )
                )
            }
        }
        return Template(items)
    }
}
