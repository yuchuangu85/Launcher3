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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.testing.FakeModelWriter
import com.android.launcher3.model.testing.WriterAction
import com.android.launcher3.ui.testing.FakeLauncherUiStateNotifier
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [FakeModelWriter]. */
@RunWith(AndroidJUnit4::class)
class FakeModelWriterTest {

    @Test
    fun scheduleTransaction_addItemToDatabase_recordsAddItemAction() {
        val fakeModelWriter = FakeModelWriter()
        val item =
            WorkspaceItemInfo().apply {
                id = 1
                container = 0
            }

        fakeModelWriter.scheduleTransaction { context -> context.addItemToDatabase(item) }

        assertThat(fakeModelWriter.actions).hasSize(1)
        val action = fakeModelWriter.actions[0] as WriterAction.AddItem
        assertThat(action.item).isEqualTo(item)
        assertThat(action.container).isEqualTo(0)
    }

    @Test
    fun scheduleTransaction_deleteItemFromDatabase_recordsDeleteItemAction() {
        val fakeModelWriter = FakeModelWriter()
        val item = WorkspaceItemInfo().apply { id = 1 }

        fakeModelWriter.scheduleTransaction { context ->
            context.deleteItemFromDatabase(item, "test_reason")
        }

        assertThat(fakeModelWriter.actions).hasSize(1)
        val action = fakeModelWriter.actions[0] as WriterAction.DeleteItem
        assertThat(action.item).isEqualTo(item)
    }

    @Test
    fun scheduleTransaction_updateItemInDatabase_recordsUpdateItemAction() {
        val fakeModelWriter = FakeModelWriter()
        val item = WorkspaceItemInfo().apply { id = 1 }

        fakeModelWriter.scheduleTransaction { context -> context.updateItemInDatabase(item) }

        assertThat(fakeModelWriter.actions).hasSize(1)
        val action = fakeModelWriter.actions[0] as WriterAction.UpdateItem
        assertThat(action.item).isEqualTo(item)
    }

    @Test
    fun scheduleTransaction_deleteAllItems_recordsDeleteAllItemsAction() {
        val fakeModelWriter = FakeModelWriter()

        fakeModelWriter.scheduleTransaction { context -> context.deleteAllItems() }

        assertThat(fakeModelWriter.actions).hasSize(1)
        assertThat(fakeModelWriter.actions[0]).isEqualTo(WriterAction.DeleteAllItems)
    }

    @Test
    fun scheduleTransaction_multipleActions_recordsAllActionsInOrder() {
        val fakeModelWriter = FakeModelWriter()
        val item1 =
            WorkspaceItemInfo().apply {
                id = 1
                container = 0
            }
        val item2 =
            WorkspaceItemInfo().apply {
                id = 2
                container = 1
            }

        fakeModelWriter.scheduleTransaction { context ->
            context.addItemToDatabase(item1)
            context.updateItemInDatabase(item1)
            context.deleteItemFromDatabase(item2, "reason")
        }

        assertThat(fakeModelWriter.actions).hasSize(3)
        assertThat(fakeModelWriter.actions[0]).isEqualTo(WriterAction.AddItem(item1, 0))
        assertThat(fakeModelWriter.actions[1]).isEqualTo(WriterAction.UpdateItem(item1))
        assertThat(fakeModelWriter.actions[2]).isEqualTo(WriterAction.DeleteItem(item2))
    }

    @Test
    fun deprecatedAddItemToDatabase_recordsAddItemAction() {
        val fakeModelWriter = FakeModelWriter()
        val item =
            WorkspaceItemInfo().apply {
                id = 1
                container = 0
            }

        fakeModelWriter.addItemToDatabase(item, 0, 0, 0, 0)

        assertThat(fakeModelWriter.actions).hasSize(1)
        val action = fakeModelWriter.actions[0] as WriterAction.AddItem
        assertThat(action.item).isEqualTo(item)
        assertThat(action.container).isEqualTo(0)
    }

    @Test
    fun deprecatedDeleteItemFromDatabase_recordsDeleteItemAction() {
        val fakeModelWriter = FakeModelWriter()
        val item = WorkspaceItemInfo().apply { id = 1 }

        fakeModelWriter.deleteItemFromDatabase(item, "test_reason")

        assertThat(fakeModelWriter.actions).hasSize(1)
        val action = fakeModelWriter.actions[0] as WriterAction.DeleteItem
        assertThat(action.item).isEqualTo(item)
    }

    @Test
    fun deprecatedUpdateItemInDatabase_recordsUpdateItemAction() {
        val fakeModelWriter = FakeModelWriter()
        val item = WorkspaceItemInfo().apply { id = 1 }

        fakeModelWriter.updateItemInDatabase(item)

        assertThat(fakeModelWriter.actions).hasSize(1)
        val action = fakeModelWriter.actions[0] as WriterAction.UpdateItem
        assertThat(action.item).isEqualTo(item)
    }

    @Test
    fun getNotifier_returnsFakeNotifier() {
        val fakeModelWriter = FakeModelWriter()
        val notifier = fakeModelWriter.getNotifier()
        assertThat(notifier).isInstanceOf(FakeLauncherUiStateNotifier::class.java)
        assertThat(notifier).isSameInstanceAs(fakeModelWriter.notifier)
    }

    @Test
    fun suspendWrites_queuesTransactions() {
        val fakeModelWriter = FakeModelWriter()
        fakeModelWriter.suspendWrites()

        var completed = false
        fakeModelWriter.scheduleTransaction({ success, _ -> completed = success }) { context ->
            context.addItemToDatabase(WorkspaceItemInfo())
        }

        assertThat(fakeModelWriter.actions).isEmpty()
        assertThat(completed).isFalse()
    }

    @Test
    fun resumeWrites_executesPendingAndQueuedTransactions() {
        val fakeModelWriter = FakeModelWriter()
        fakeModelWriter.suspendWrites()

        val item1 = WorkspaceItemInfo().apply { id = 1 }
        val item2 = WorkspaceItemInfo().apply { id = 2 }

        fakeModelWriter.scheduleTransaction { context -> context.addItemToDatabase(item1) }

        val pending = Consumer<TransactionContext> { context -> context.addItemToDatabase(item2) }

        fakeModelWriter.resumeWrites(pending)

        assertThat(fakeModelWriter.actions).hasSize(2)
        // Pending first
        val action0 = fakeModelWriter.actions[0] as WriterAction.AddItem
        assertThat(action0.item).isEqualTo(item2)
        // Queued second
        val action1 = fakeModelWriter.actions[1] as WriterAction.AddItem
        assertThat(action1.item).isEqualTo(item1)
    }

    @Test
    fun scheduleTransaction_returnsResult() {
        val fakeModelWriter = FakeModelWriter()
        var result: Int? = null

        fakeModelWriter.scheduleTransaction(onComplete = { _, res -> result = res }) { 42 }

        assertThat(result).isEqualTo(42)
    }
}
