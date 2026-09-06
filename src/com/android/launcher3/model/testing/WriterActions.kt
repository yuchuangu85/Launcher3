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
package com.android.launcher3.model.testing

import com.android.launcher3.model.data.ItemInfo
import java.util.function.Predicate

/**
 * A sealed interface representing the actions performed by [ModelWriter.TransactionContext]
 * for testing purposes.
 *
 * This allows for capturing and asserting on the exact operations and arguments
 * a client intended to execute within a transaction.
 */
sealed interface WriterAction {
    /** Represents the action of deleting all items from the database. */
    object DeleteAllItems : WriterAction

    /**
     * Represents the action of adding an item to the database.
     *
     * @property item The [ItemInfo] that was added.
     * @property container The container ID where the item was placed.
     */
    data class AddItem(val item: ItemInfo, val container: Int) : WriterAction

    /**
     * Represents the action of updating an item in the database.
     *
     * @property item The [ItemInfo] that was updated.
     */
    data class UpdateItem(val item: ItemInfo) : WriterAction

    /**
     * Represents the action of deleting an item from the database.
     *
     * @property item The [ItemInfo] that was deleted.
     */
    data class DeleteItem(val item: ItemInfo) : WriterAction

    /**
     * Represents the action of modifying an item in the database.
     *
     * @property item The [ItemInfo] that was modified.
     * @property container The new container ID.
     * @property screenId The new screen ID.
     * @property cellX The new X-coordinate.
     * @property cellY The new Y-coordinate.
     * @property spanX The new span in X-direction.
     * @property spanY The new span in Y-direction.
     */
    data class ModifyItem(
        val item: ItemInfo,
        val container: Int,
        val screenId: Int,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int
    ) : WriterAction

    /**
     * Represents the action of moving multiple items in the database.
     *
     * @property items The list of [ItemInfo] that were moved.
     * @property container The new container ID.
     * @property screen The new screen ID.
     */
    data class MoveItems(val items: List<ItemInfo>, val container: Int, val screen: Int) :
        WriterAction

    /**
     * Represents the action of deleting items from the database using a predicate.
     *
     * @property matcher The [Predicate] used to filter items for deletion.
     */
    data class DeleteItemsByPredicate(val matcher: Predicate<ItemInfo?>) : WriterAction
}
