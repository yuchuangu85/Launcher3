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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.AbstractWorkspaceModelTest
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.testing.FakeModelWriter
import com.android.launcher3.model.testing.WriterAction
import com.android.launcher3.views.ActivityContext
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UndoDeleteControllerTest : AbstractWorkspaceModelTest() {

    private lateinit var modelWriter: FakeModelWriter
    private lateinit var undoDeleteController: UndoDeleteController
    private lateinit var mockAbortAction: ModelReloader

    @Before
    override fun setup() {
        super.setup()
        modelWriter = FakeModelWriter()
        val activityContext = mock<ActivityContext>()
        whenever(activityContext.getModelWriter()).thenReturn(modelWriter)
        mockAbortAction = mock<ModelReloader>()
        undoDeleteController = UndoDeleteController(activityContext, mockAbortAction)
    }

    @Test
    fun deleteItem_enqueuesTransaction() {
        val item = WorkspaceItemInfo()
        undoDeleteController.deleteItem(item, "test reason")

        assertThat(modelWriter.actions).isEmpty()
    }

    @Test
    fun commit_executesEnqueuedTransactions() {
        val item = WorkspaceItemInfo()
        undoDeleteController.deleteItem(item, "test reason")

        undoDeleteController.commit()

        assertThat(modelWriter.actions).containsExactly(WriterAction.DeleteItem(item))
    }

    @Test
    fun commit_emptyQueue_doesNothing() {
        undoDeleteController.commit()
        assertThat(modelWriter.actions).isEmpty()
    }

    @Test
    fun abort_clearsQueueAndReloadsModel() {
        val item = WorkspaceItemInfo()
        undoDeleteController.deleteItem(item, "test reason")

        undoDeleteController.abort()

        verify(mockAbortAction).reloadIfActive()

        assertThat(modelWriter.actions).isEmpty()

        undoDeleteController.commit()
        assertThat(modelWriter.actions).isEmpty()
    }

    @Test
    fun prepareToUndoDelete_clearsPreviousQueue() {
        val item1 = WorkspaceItemInfo()
        undoDeleteController.deleteItem(item1, "reason 1")

        undoDeleteController.prepareToUndoDelete()

        undoDeleteController.commit()
        assertThat(modelWriter.actions).isEmpty()
    }

    @Test
    fun deleteMultipleItems_andCommit_executesAll() {
        val item1 = WorkspaceItemInfo()
        val item2 = WorkspaceItemInfo()

        undoDeleteController.deleteItem(item1, "r1")
        undoDeleteController.deleteItem(item2, "r2")

        undoDeleteController.commit()

        assertThat(modelWriter.actions)
            .containsExactly(WriterAction.DeleteItem(item1), WriterAction.DeleteItem(item2))
    }

    @Test
    fun deleteItems_commitsTransaction() {
        val item1 = WorkspaceItemInfo()
        val item2 = WorkspaceItemInfo()
        undoDeleteController.deleteItems(listOf(item1, item2), "test reason")

        assertThat(modelWriter.actions).isEmpty()

        undoDeleteController.commit()

        assertThat(modelWriter.actions)
            .containsExactly(WriterAction.DeleteItem(item1), WriterAction.DeleteItem(item2))
    }

    @Test
    fun deleteCollection_commitsTransaction() {
        val collection = FolderInfo()
        undoDeleteController.deleteCollection(collection)

        assertThat(modelWriter.actions).isEmpty()

        undoDeleteController.commit()

        assertThat(modelWriter.actions).containsExactly(WriterAction.DeleteItem(collection))
    }

    @Test
    fun deleteWidget_commitsTransaction() {
        val widget = LauncherAppWidgetInfo()
        undoDeleteController.deleteWidget(widget, null, "test reason")

        assertThat(modelWriter.actions).isEmpty()

        undoDeleteController.commit()

        assertThat(modelWriter.actions).containsExactly(WriterAction.DeleteItem(widget))
    }

    @Test
    fun prepareToUndoDelete_suspendsWrites() {
        // 1. Prepare to undo (should suspend writes)
        undoDeleteController.prepareToUndoDelete()

        // 2. Perform a separate model write (e.g. background install)
        val backgroundItem = WorkspaceItemInfo().apply { id = 999 }
        modelWriter.addItemToDatabase(backgroundItem, 0, 0, 0, 0)

        // 3. Verify it is NOT yet executed (blocked)
        assertThat(modelWriter.actions).isEmpty()
    }

    @Test
    fun commit_resumesWrites_executingBlockedTransactions() {
        undoDeleteController.prepareToUndoDelete()

        // 1. Queue a user action
        val userItem = WorkspaceItemInfo().apply { id = 1 }
        undoDeleteController.deleteItem(userItem, "user action")

        // 2. Queue a background action
        val backgroundItem = WorkspaceItemInfo().apply { id = 999 }
        modelWriter.addItemToDatabase(backgroundItem, 0, 0, 0, 0)

        // Verify nothing executed yet
        assertThat(modelWriter.actions).isEmpty()

        // 3. Commit
        undoDeleteController.commit()

        // 4. Verify order: Pending Transaction (Delete) -> Queued Transaction (Add)
        assertThat(modelWriter.actions).hasSize(2)
        assertThat(modelWriter.actions[0]).isEqualTo(WriterAction.DeleteItem(userItem))
        val action1 = modelWriter.actions[1] as WriterAction.AddItem
        assertThat(action1.item).isEqualTo(backgroundItem)
    }

    @Test
    fun abort_discardsBlockedTransactions() {
        undoDeleteController.prepareToUndoDelete()

        val userItem = WorkspaceItemInfo().apply { id = 1 }
        undoDeleteController.deleteItem(userItem, "user action")

        val backgroundItem = WorkspaceItemInfo().apply { id = 999 }
        modelWriter.addItemToDatabase(backgroundItem, 0, 0, 0, 0)

        assertThat(modelWriter.actions).isEmpty()

        undoDeleteController.abort()

        assertThat(modelWriter.actions).isEmpty()
    }
}