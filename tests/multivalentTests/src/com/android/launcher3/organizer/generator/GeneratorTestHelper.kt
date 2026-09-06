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
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.CellAndSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

object GeneratorTestHelper {

    /**
     * Verifies the layout of a screen against a set of expected placements.
     *
     * This method validates:
     * 1. No items overlap.
     * 2. No items are out of bounds for the given [gridSize].
     * 3. The number of items matches the expected count.
     * 4. Each item's position and span match the expected [CellAndSpan] for its ID.
     *
     * @param screen The list of items on the screen.
     * @param gridSize The dimensions of the grid.
     * @param expected A list of pairs mapping [CellAndSpan] to the expected item ID.
     */
    fun verifyLayout(
        screen: List<ItemInfo>,
        gridSize: Point,
        expected: List<Pair<CellAndSpan, Int>>,
    ) {
        val occupiedCells = mutableSetOf<Point>()

        // 1. Basic validation for every item on the screen
        screen.forEach { item ->
            if (item.cellX < 0 || item.cellY < 0) {
                fail(
                    "Item ${item.id} (${item.title}) has invalid position: (${item.cellX}, ${item.cellY})"
                )
            }

            // Check boundaries
            assertTrue(
                "Item ${item.id} is out of bounds: (${item.cellX}, ${item.cellY}) " +
                    "with span (${item.spanX}, ${item.spanY}) for grid $gridSize",
                item.cellX + item.spanX <= gridSize.x && item.cellY + item.spanY <= gridSize.y,
            )

            // Check overlaps
            for (y in 0 until item.spanY) {
                for (x in 0 until item.spanX) {
                    val pos = Point(item.cellX + x, item.cellY + y)
                    if (!occupiedCells.add(pos)) {
                        fail("Overlapping item at $pos: Item ${item.id}")
                    }
                }
            }
        }

        // 2. Comprehensive matching against expectations
        assertEquals("Total item count mismatch on screen", expected.size, screen.size)

        expected.forEach { (expectedCell, expectedId) ->
            val actualItem = screen.find { it.id == expectedId }
            assertNotNull("Expected item $expectedId was not found on the screen", actualItem)

            assertEquals(
                "cellX mismatch for item $expectedId",
                expectedCell.cellX,
                actualItem!!.cellX,
            )
            assertEquals(
                "cellY mismatch for item $expectedId",
                expectedCell.cellY,
                actualItem.cellY,
            )
            assertEquals(
                "spanX mismatch for item $expectedId",
                expectedCell.spanX,
                actualItem.spanX,
            )
            assertEquals(
                "spanY mismatch for item $expectedId",
                expectedCell.spanY,
                actualItem.spanY,
            )
        }
    }
}
