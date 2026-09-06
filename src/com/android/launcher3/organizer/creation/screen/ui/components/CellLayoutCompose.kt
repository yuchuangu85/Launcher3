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

package com.android.launcher3.organizer.creation.screen.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Represents the location and span of an item in the grid.
 *
 * @property cellX The x coordinate of the item in cells.
 * @property cellY The y coordinate of the item in cells.
 * @property spanX The number of cells the item spans horizontally.
 * @property spanY The number of cells the item spans vertically.
 */
data class ItemLocation(val cellX: Int, val cellY: Int, val spanX: Int = 1, val spanY: Int = 1) {

    /**
     * Calculates the position of the item in dps.
     *
     * @param itemSpacing The spacing between items.
     * @param cellSize The size of a single cell.
     */
    fun calculatePosition(
        itemSpacing: CellLayoutComposeItemSpacing,
        cellSize: CellSize,
    ): CellPositionDp {
        return CellPositionDp(
            x = cellSize.width.times(cellX) + itemSpacing.x.times(cellX),
            y = cellSize.height.times(cellY) + itemSpacing.y.times(cellY),
        )
    }

    /**
     * Calculates the size of the item.
     *
     * @param itemSpacing The spacing between items.
     * @param cellSize The size of a single cell.
     */
    fun calculateSize(
        itemSpacing: CellLayoutComposeItemSpacing,
        cellSize: CellSize,
    ): CellLayoutComposeItemSize {
        return CellLayoutComposeItemSize(
            width = cellSize.width.times(spanX) + itemSpacing.x.times(spanX - 1),
            height = cellSize.height.times(spanY) + itemSpacing.y.times(spanY - 1),
        )
    }
}

/**
 * Represents an item in the grid, containing its location and content.
 *
 * @property cellAndSpan The location and span of the item.
 * @property content The composable content of the item.
 */
private data class GridItem(val cellAndSpan: ItemLocation, val content: @Composable () -> Unit)

/** Scope for the [CellLayoutCompose] DSL. */
interface CellLayoutScope {
    /**
     * Adds an item to the layout.
     *
     * @param cellAndSpan The location and span of the item.
     * @param content The content of the item.
     */
    fun item(cellAndSpan: ItemLocation, content: @Composable (CellLayoutComposeItemSize) -> Unit)
}

/**
 * Implementation of [CellLayoutScope].
 *
 * @property itemSpacing The spacing between items.
 * @property cellSize The size of a single cell.
 */
private class CellLayoutScopeImpl(
    val itemSpacing: CellLayoutComposeItemSpacing,
    val cellSize: CellSize,
) : CellLayoutScope {
    val items = mutableListOf<GridItem>()

    override fun item(
        cellAndSpan: ItemLocation,
        content: @Composable (CellLayoutComposeItemSize) -> Unit,
    ) {
        val size = cellAndSpan.calculateSize(itemSpacing, cellSize)
        items.add(GridItem(cellAndSpan) { content(size) })
    }
}

/**
 * Represents the size of the grid in terms of number of cells.
 *
 * @property x The number of columns.
 * @property y The number of rows.
 */
data class CellLayoutComposeSize(val x: Int, val y: Int)

/**
 * Represents the spacing between items in the grid.
 *
 * @property x The horizontal spacing.
 * @property y The vertical spacing.
 */
data class CellLayoutComposeItemSpacing(val x: Dp = 0.dp, val y: Dp = 0.dp)

/**
 * Represents the size of a single cell in the grid.
 *
 * @property width The width of the cell.
 * @property height The height of the cell.
 */
data class CellSize(val width: Dp, val height: Dp)

/**
 * Represents a position in dps.
 *
 * @property x The x coordinate in dp.
 * @property y The y coordinate in dp.
 */
data class CellPositionDp(val x: Dp, val y: Dp)

/**
 * Represents the calculated size of an item in the layout. Use this in your compose [Modifier].
 *
 * @property width The width of the item.
 * @property height The height of the item.
 */
data class CellLayoutComposeItemSize(val width: Dp, val height: Dp)

/**
 * Grid for items with fixed sizes. The width and height must be known, the grid computes the size
 * of each cell and the position of each item. And it's provided by using the {fun item(cellAndSpan:
 * ItemLocation, content: @Composable (CellLayoutComposeItemSize) -> Unit)} function. You need to
 * provide the cell size of each item.
 *
 * @param width The total width of the layout.
 * @param height The total height of the layout.
 * @param gridSize The size of the grid (rows and columns).
 * @param spacing The spacing between items.
 * @param modifier The modifier to be applied to the layout.
 * @param content The content of the layout.
 */
@Composable
fun CellLayoutCompose(
    width: Dp,
    height: Dp,
    gridSize: CellLayoutComposeSize,
    spacing: CellLayoutComposeItemSpacing = CellLayoutComposeItemSpacing(),
    modifier: Modifier = Modifier,
    content: CellLayoutScope.() -> Unit,
) {
    val cellSize =
        calculateCellSize(
            width = width,
            height = height,
            gridSize = gridSize,
            itemSpacing = spacing,
        )

    val scope = CellLayoutScopeImpl(itemSpacing = spacing, cellSize = cellSize).apply(content)
    val items = scope.items

    Layout(modifier = modifier, contents = items.map { it.content }) {
        measurables: List<List<Measurable>>,
        constraints: Constraints ->
        val placeables = measurables.flatten().map { it.measure(constraints) }

        layout(width.roundToPx(), height.roundToPx()) {
            placeables.forEachIndexed { index, placeable ->
                val item = items[index]
                val position =
                    item.cellAndSpan.calculatePosition(itemSpacing = spacing, cellSize = cellSize)
                placeable.place(x = position.x.roundToPx(), y = position.y.roundToPx())
            }
        }
    }
}

/**
 * Calculates the size of a single cell.
 *
 * @param width The total width of the layout.
 * @param height The total height of the layout.
 * @param gridSize The size of the grid.
 * @param itemSpacing The spacing between items.
 */
private fun calculateCellSize(
    width: Dp,
    height: Dp,
    gridSize: CellLayoutComposeSize,
    itemSpacing: CellLayoutComposeItemSpacing,
): CellSize {
    return CellSize(
        width = (width - itemSpacing.x.times(gridSize.x - 1)).div(gridSize.x),
        height = (height - itemSpacing.y.times(gridSize.y - 1)).div(gridSize.y),
    )
}
