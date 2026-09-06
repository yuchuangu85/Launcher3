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
package com.android.launcher3.model

import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.ui.LauncherUiStateNotifier
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.function.Consumer
import java.util.function.Predicate

/** Interface for handling model updates. */
interface IModelWriter {

    /**
     * A log of changes made to the model.
     *
     * @property itemsAdded A list of [ItemInfo] objects representing items added to the model.
     * @property itemsModified A set of [ItemInfo] objects representing items modified in the model.
     * @property itemsRemoved A list of [ItemInfo] objects representing items removed from the
     *   model.
     *
     * TODO(b/457449059): Integrate with HomeScreenRepository changelog in the future.
     */
    data class ChangeLog(
        val itemsAdded: MutableList<ItemInfo> = mutableListOf(),
        val itemsModified: MutableSet<ItemInfo> = mutableSetOf(),
        val itemsRemoved: MutableList<ItemInfo> = mutableListOf(),
    )

    /**
     * Schedules a block of code to be executed within a single database transaction on the
     * background model thread. This is the ONLY public way to mutate the model.
     *
     * This method is strictly for persisting model changes and does not handle UI state. It
     * executes immediately. Clients wishing to support buffering (e.g. for "undo" logic or batched
     * updates) should implement "Client-Side Buffering":
     * 1. Update the UI state immediately.
     * 2. Retain the necessary data in memory.
     * 3. If the operation is confirmed (or the "undo" window expires), call this method to
     *    permanently commit the change to the database.
     * 4. If the operation is cancelled, revert the UI state using the retained data. No database
     *    transaction is needed.
     *
     * @param T The type of the result returned by the [block]. This result will be passed back to
     *   the onComplete callback, allowing the transaction block to communicate state or data back
     *   to the caller.
     * @param onComplete An optional lambda that will be executed on the UI thread after the
     *   transaction has completed, indicating success or failure and the result of the block.
     * @param block The block of code to execute within the transaction, receiving a
     *   [TransactionContext] handle for performing mutations.
     */
    fun <T> scheduleTransaction(
        onComplete: ((success: Boolean, result: T?) -> Unit)? = null,
        block: (TransactionContext) -> T,
    )

    /**
     * Suspends any new database writes.
     *
     * While suspended, any calls to [scheduleTransaction] will be queued and executed only after
     * [resumeWrites] is called.
     */
    fun suspendWrites()

    /**
     * Resumes database writes.
     *
     * @param pendingTransaction An optional transaction to execute immediately before processing
     *   the queued transactions. This is typically the "commit" operation of an undoable action.
     * @param discardPending If true, any transactions that were queued while suspended will be
     *   discarded instead of executed.
     */
    fun resumeWrites(
        pendingTransaction: Consumer<TransactionContext>? = null,
        discardPending: Boolean = false,
    )

    /**
     * Returns the [LauncherUiStateNotifier] used by the model to notify the UI of changes.
     *
     * This is used to notify the UI of changes that do not require a database transaction, such as
     * optimistic updates or UI state synchronization.
     */
    @Deprecated("Temporary interface for ui notification")
    fun getNotifier(): LauncherUiStateNotifier

    // The following methods are deprecated and will be removed once all clients are migrated
    // to the new transactional API. They are kept for now to ensure a safe, incremental rollout.
    fun addOrMoveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    )

    fun moveItemInDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int)

    fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int)

    fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    )

    fun updateItemInDatabase(item: ItemInfo)

    fun addItemToDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int)

    fun addItemsToDatabase(items: List<ItemInfo>)

    fun deleteItemFromDatabase(item: ItemInfo, reason: String?)

    fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?)

    fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?)

    fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo)

    fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: LauncherWidgetHolder?,
        reason: String?,
    )
}

/**
 * A handle provided exclusively to the lambda block of a [scheduleTransaction] call.
 *
 * This interface defines the contract for all mutation methods that can be performed within a
 * managed database transaction. By providing this handle, we ensure that mutations can only occur
 * within a transactional context, promoting data consistency and preventing direct,
 * non-transactional database writes.
 */
interface TransactionContext {

    /**
     * Adds an item to the database.
     *
     * @param item The [ItemInfo] to add.
     */
    fun addItemToDatabase(item: ItemInfo)

    /**
     * Adds multiple items to the database.
     *
     * @param items The list of [ItemInfo] to add.
     */
    fun addItemsToDatabase(items: List<ItemInfo>)

    /**
     * Moves an item in the database to a new container, screen, and cell coordinates.
     *
     * @param item The [ItemInfo] to move.
     * @param container The new container ID.
     * @param screenId The new screen ID.
     * @param cellX The new X-coordinate.
     * @param cellY The new Y-coordinate.
     */
    fun moveItemInDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int)

    /**
     * Moves multiple items in the database to a new container and screen.
     *
     * @param items The list of [ItemInfo] to move.
     * @param container The new container ID.
     * @param screen The new screen ID.
     */
    fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int)

    /**
     * Modifies an item in the database, including its position, size, and container.
     *
     * @param item The [ItemInfo] to modify.
     * @param container The new container ID.
     * @param screenId The new screen ID.
     * @param cellX The new X-coordinate.
     * @param cellY The new Y-coordinate.
     * @param spanX The new span in X-direction.
     * @param spanY The new span in Y-direction.
     */
    fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    )

    /**
     * Updates an existing item in the database.
     *
     * @param item The [ItemInfo] to update.
     */
    fun updateItemInDatabase(item: ItemInfo)

    /**
     * Deletes a specific item from the database.
     *
     * @param item The [ItemInfo] to delete.
     * @param reason An optional string indicating the reason for deletion.
     */
    fun deleteItemFromDatabase(item: ItemInfo, reason: String?) =
        deleteItemsFromDatabase(listOf(item), reason)

    /**
     * Deletes items from the database that match a given predicate.
     *
     * @param matcher A [Predicate] to filter items for deletion.
     * @param reason An optional string indicating the reason for deletion.
     */
    fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?)

    /**
     * Deletes multiple items from the database.
     *
     * @param items The list of [ItemInfo] to delete.
     * @param reason An optional string indicating the reason for deletion.
     */
    fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?)

    /**
     * Deletes a collection (e.g., a folder) and all its contents from the database.
     *
     * @param info The [CollectionInfo] representing the collection to delete.
     */
    fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo)

    /**
     * Deletes a widget info and its associated widget ID.
     *
     * @param info The [LauncherAppWidgetInfo] to delete.
     * @param holder An optional [LauncherWidgetHolder] to delete the app widget ID.
     * @param reason An optional string indicating the reason for deletion.
     */
    fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: LauncherWidgetHolder?,
        reason: String?,
    )

    /** Deletes all items from the database. */
    fun deleteAllItems()
}
