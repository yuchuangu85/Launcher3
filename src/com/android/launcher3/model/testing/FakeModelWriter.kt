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

import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.ui.LauncherUiStateNotifier
import com.android.launcher3.ui.testing.FakeLauncherUiStateNotifier
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * A fake implementation of [IModelWriter] for testing purposes.
 *
 * This class records [WriterAction] objects instead of performing actual database operations,
 * allowing tests to verify the exact sequence of mutations a client attempts to execute.
 */
class FakeModelWriter : IModelWriter {

    /** A fake notifier that records UI state changes. */
    val notifier = FakeLauncherUiStateNotifier()

    /** A mutable list to store the [WriterAction] objects recorded during a transaction. */
    val actions = mutableListOf<WriterAction>()

    private interface QueuedTransaction {
        fun execute()
    }

    private var isSuspended = false
    private val transactionQueue = mutableListOf<QueuedTransaction>()

    private val fakeTransactionContext =
        object : TransactionContext {
            override fun addItemToDatabase(item: ItemInfo) {
                actions.add(WriterAction.AddItem(item, item.container))
            }

            override fun addItemsToDatabase(items: List<ItemInfo>) {
                items.forEach { actions.add(WriterAction.AddItem(it, it.container)) }
            }

            override fun moveItemInDatabase(
                item: ItemInfo,
                container: Int,
                screenId: Int,
                cellX: Int,
                cellY: Int,
            ) {
                actions.add(
                    WriterAction.ModifyItem(
                        item,
                        container,
                        screenId,
                        cellX,
                        cellY,
                        item.spanX,
                        item.spanY,
                    )
                )
            }

            override fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int) {
                actions.add(WriterAction.MoveItems(items, container, screen))
            }

            override fun modifyItemInDatabase(
                item: ItemInfo,
                container: Int,
                screenId: Int,
                cellX: Int,
                cellY: Int,
                spanX: Int,
                spanY: Int,
            ) {
                actions.add(
                    WriterAction.ModifyItem(item, container, screenId, cellX, cellY, spanX, spanY)
                )
            }

            override fun updateItemInDatabase(item: ItemInfo) {
                actions.add(WriterAction.UpdateItem(item))
            }

            override fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?) {
                actions.add(WriterAction.DeleteItemsByPredicate(matcher))
            }

            override fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?) {
                items.forEach { actions.add(WriterAction.DeleteItem(it)) }
            }

            override fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo) {
                actions.add(WriterAction.DeleteItem(info))
            }

            override fun deleteWidgetInfo(
                info: LauncherAppWidgetInfo,
                holder: LauncherWidgetHolder?,
                reason: String?,
            ) {
                actions.add(WriterAction.DeleteItem(info))
            }

            override fun deleteAllItems() {
                actions.add(WriterAction.DeleteAllItems)
            }
        }

    /**
     * Overrides the [ModelWriter.scheduleTransaction] method to execute the block immediately and
     * invoke the [onComplete] callback with `true`.
     *
     * This allows tests to directly inspect the [actions] list after calling a client method that
     * uses [scheduleTransaction].
     */
    override fun <T> scheduleTransaction(
        onComplete: ((success: Boolean, result: T?) -> Unit)?,
        block: (TransactionContext) -> T,
    ) {
        if (isSuspended) {
            transactionQueue.add(
                object : QueuedTransaction {
                    override fun execute() {
                        scheduleTransaction(onComplete, block)
                    }
                }
            )
            return
        }
        val result = block(fakeTransactionContext)
        onComplete?.invoke(true, result)
    }

    override fun suspendWrites() {
        isSuspended = true
    }

    override fun resumeWrites(
        pendingTransaction: Consumer<TransactionContext>?,
        discardPending: Boolean,
    ) {
        isSuspended = false
        if (pendingTransaction != null) {
            scheduleTransaction(null) { pendingTransaction.accept(it) }
        }
        val queue = transactionQueue.toList()
        transactionQueue.clear()

        if (!discardPending) {
            queue.forEach { it.execute() }
        }
    }

    override fun getNotifier(): LauncherUiStateNotifier = notifier

    override fun addItemToDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        item.container = container
        item.screenId = screenId
        item.cellX = cellX
        item.cellY = cellY
        execute { it.addItemToDatabase(item) }
    }

    override fun addItemsToDatabase(items: List<ItemInfo>) {
        execute { it.addItemsToDatabase(items) }
    }

    override fun moveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        item.container = container
        item.screenId = screenId
        item.cellX = cellX
        item.cellY = cellY
        execute { it.moveItemInDatabase(item, container, screenId, cellX, cellY) }
    }

    override fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int) {
        items.forEach {
            it.container = container
            it.screenId = screen
        }
        execute { it.moveItemsInDatabase(items, container, screen) }
    }

    override fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {
        item.container = container
        item.screenId = screenId
        item.cellX = cellX
        item.cellY = cellY
        item.spanX = spanX
        item.spanY = spanY
        execute { it.modifyItemInDatabase(item, container, screenId, cellX, cellY, spanX, spanY) }
    }

    override fun updateItemInDatabase(item: ItemInfo) {
        execute { it.updateItemInDatabase(item) }
    }

    override fun deleteItemFromDatabase(item: ItemInfo, reason: String?) {
        execute { it.deleteItemFromDatabase(item, reason) }
    }

    override fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?) {
        execute { it.deleteItemsFromDatabase(matcher, reason) }
    }

    override fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?) {
        execute { it.deleteItemsFromDatabase(items, reason) }
    }

    override fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo) {
        execute { it.deleteCollectionAndContentsFromDatabase(info) }
    }

    override fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: LauncherWidgetHolder?,
        reason: String?,
    ) {
        execute { it.deleteWidgetInfo(info, holder, reason) }
    }

    override fun addOrMoveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        item.container = container
        item.screenId = screenId
        item.cellX = cellX
        item.cellY = cellY
        if (item.id == ItemInfo.NO_ID) {
            execute { it.addItemToDatabase(item) }
        } else {
            execute { it.moveItemInDatabase(item, container, screenId, cellX, cellY) }
        }
    }

    private fun execute(block: (TransactionContext) -> Unit) {
        scheduleTransaction(block = block)
    }
}
