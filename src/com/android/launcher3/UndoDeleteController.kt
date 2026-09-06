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
package com.android.launcher3

import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.function.Consumer
import javax.inject.Inject

/**
 * Controller class to manage "Undo" delete operations.
 *
 * buffers delete operations locally until they are either committed (executed against the database)
 * or aborted (reverting the UI state).
 */
@ActivityContextSingleton
class UndoDeleteController
@Inject
constructor(
    private val activityContext: ActivityContext,
    private val modelReloader: ModelReloader,
) {
    private val pendingDeletes = mutableListOf<Consumer<TransactionContext>>()

    /**
     * Prepares for a new delete operation by clearing any previously pending ones. This corresponds
     * to the start of a user interaction that might be undone.
     */
    fun prepareToUndoDelete() {
        pendingDeletes.clear()
        activityContext.modelWriter.suspendWrites()
    }

    /** Enqueues a raw transaction operation to be executed upon commit. */
    fun enqueueTransaction(op: Consumer<TransactionContext>) {
        pendingDeletes.add(op)
    }

    /**
     * Schedules the buffered transactions to be executed in the ModelWriter. Use this when the
     * "Undo" window has expired or the user explicitly dismissed it.
     */
    fun commit() {
        if (pendingDeletes.isEmpty()) {
            activityContext.modelWriter.resumeWrites(null)
            return
        }

        val deletesToCommit = pendingDeletes.toList()
        pendingDeletes.clear()

        activityContext.modelWriter.resumeWrites(
            Consumer { context -> deletesToCommit.forEach { it.accept(context) } }
        )
    }

    /**
     * Aborts the pending delete operations and forces a model reload to restore the UI to its state
     * before the optimistic deletions.
     */
    fun abort() {
        pendingDeletes.clear()
        activityContext.modelWriter.resumeWrites(null, discardPending = true)
        modelReloader.reloadIfActive()
    }

    // Type-safe helper methods matching IModelWriter interfaces

    fun deleteItem(item: ItemInfo, reason: String?) {
        enqueueTransaction { it.deleteItemFromDatabase(item, reason) }
    }

    fun deleteItems(items: List<ItemInfo>, reason: String?) {
        enqueueTransaction { it.deleteItemsFromDatabase(items, reason) }
    }

    fun deleteCollection(info: CollectionInfo) {
        enqueueTransaction { it.deleteCollectionAndContentsFromDatabase(info) }
    }

    fun deleteWidget(info: LauncherAppWidgetInfo, holder: LauncherWidgetHolder?, reason: String?) {
        enqueueTransaction { it.deleteWidgetInfo(info, holder, reason) }
    }

    private companion object {
        const val TAG = "UndoDeleteController"
    }
}
