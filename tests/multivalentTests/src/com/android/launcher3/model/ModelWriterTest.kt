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
package com.android.launcher3.model

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.model.IModelWriter.ChangeLog
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.ui.LauncherUiStateNotifier
import com.android.launcher3.util.ModelTestExtensions.bgDataModel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ModelWriterTest : AbstractWorkspaceModelTest() {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private lateinit var modelWriter: ModelWriter
    private val mockNotifier: LauncherUiStateNotifier = mock()
    private val mockCallbacks: BgDataModel.Callbacks = mock()
    private val directExecutor = MoreExecutors.directExecutor()

    private val bgDataModel: BgDataModel
        get() = mTargetContext.appComponent.testableModelState.dataModel

    private val item1 =
        WorkspaceItemInfo().apply {
            container = Favorites.CONTAINER_DESKTOP
            screenId = 0
            cellX = 0
            cellY = 0
        }
    private val item2 =
        WorkspaceItemInfo().apply {
            container = Favorites.CONTAINER_DESKTOP
            screenId = 0
            cellX = 1
            cellY = 0
        }

    @Before
    override fun setup() {
        super.setup()
        modelWriter = createWriter(directExecutor)
    }

    private fun createWriter(executor: Executor): ModelWriter {
        val spiedModel = spy(model)
        doReturn(CompletableFuture.completedFuture(Unit)).whenever(spiedModel).forceReload(any())
        return ModelWriter(
            context = mTargetContext,
            model = spiedModel,
            bgDataModel = bgDataModel,
            cellPosMapper = CellPosMapper.DEFAULT,
            modificationSource = BgDataModel.ModificationSource.ModelTask,
            launcherStateNotifier = mockNotifier,
            owner = mockCallbacks,
            modelExecutor = executor,
        )
    }

    private fun addToModel(vararg items: ItemInfo) {
        modelWriter.scheduleTransaction { it.addItemsToDatabase(items.toList()) }
    }

    @Test
    fun scheduleTransaction_multipleOps_shouldBatchNotifications() {
        addToModel(item2)
        verify(mockNotifier).notifyModelChanged(any(), any())
        reset(mockNotifier)

        modelWriter.scheduleTransaction {
            it.addItemToDatabase(item1)
            it.deleteItemFromDatabase(item2, "reason")
        }

        val changeLogCaptor = argumentCaptor<ChangeLog>()
        verify(mockNotifier).notifyModelChanged(changeLogCaptor.capture(), any())

        val changeLog = changeLogCaptor.firstValue
        assertThat(changeLog.itemsAdded).containsExactly(item1)
        assertThat(changeLog.itemsRemoved).containsExactly(item2)
        assertThat(changeLog.itemsModified).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRANSACTIONAL_MODEL_WRITER)
    fun addOrMoveItemInDatabase_newItem_usesAddItemToDatabase() {
        val newItem = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        modelWriter.addOrMoveItemInDatabase(newItem, Favorites.CONTAINER_DESKTOP, 0, 0, 0)

        assertThat(newItem.id).isNotEqualTo(ItemInfo.NO_ID)
        assertThat(bgDataModel.itemsIdMap[newItem.id]).isNotNull()

        val changeLogCaptor = argumentCaptor<ChangeLog>()
        verify(mockNotifier).notifyModelChanged(changeLogCaptor.capture(), any())
        assertThat(changeLogCaptor.firstValue.itemsAdded).containsExactly(newItem)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRANSACTIONAL_MODEL_WRITER)
    fun addOrMoveItemInDatabase_existingItem_usesMoveItemInDatabase() {
        addToModel(item1)
        reset(mockNotifier)

        val newScreen = 5
        modelWriter.addOrMoveItemInDatabase(item1, Favorites.CONTAINER_DESKTOP, newScreen, 0, 0)

        assertThat(item1.screenId).isEqualTo(newScreen)
        assertThat(bgDataModel.itemsIdMap[item1.id]?.screenId).isEqualTo(newScreen)

        val changeLogCaptor = argumentCaptor<ChangeLog>()
        verify(mockNotifier).notifyModelChanged(changeLogCaptor.capture(), any())
        assertThat(changeLogCaptor.firstValue.itemsModified).containsExactly(item1)
    }

    @Test
    fun addItemToDatabase_updatesPropsSynchronously() {
        val newItem = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        modelWriter.addItemToDatabase(newItem, Favorites.CONTAINER_DESKTOP, 2, 3, 4)

        // Verify props are updated immediately
        assertThat(newItem.container).isEqualTo(Favorites.CONTAINER_DESKTOP)
        assertThat(newItem.screenId).isEqualTo(2)
        assertThat(newItem.cellX).isEqualTo(3)
        assertThat(newItem.cellY).isEqualTo(4)
    }

    @Test
    fun legacyApi_addItemToDatabase_assignsIdSynchronously() {
        val manualExecutor = Executor {}
        val testWriter = createWriter(manualExecutor)

        val newItem = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        testWriter.addItemToDatabase(newItem, Favorites.CONTAINER_DESKTOP, 2, 3, 4)

        // This proves the ID is assigned on the calling thread before the method returns,
        // even if the background task hasn't started yet.
        assertWithMessage("Item ID should be assigned synchronously on calling thread")
            .that(newItem.id)
            .isNotEqualTo(ItemInfo.NO_ID)
    }

    @Test
    fun legacyApi_addItemsToDatabase_assignsIdsSynchronously() {
        val manualExecutor = Executor {}
        val testWriter = createWriter(manualExecutor)

        val newItem1 = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        val newItem2 = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        testWriter.addItemsToDatabase(listOf(newItem1, newItem2))

        assertThat(newItem1.id).isNotEqualTo(ItemInfo.NO_ID)
        assertThat(newItem2.id).isNotEqualTo(ItemInfo.NO_ID)
    }

    @Test
    fun legacyApi_moveItemInDatabase_updatesPropsSynchronously() {
        addToModel(item1)

        modelWriter.moveItemInDatabase(item1, Favorites.CONTAINER_HOTSEAT, 1, 2, 3)

        assertThat(item1.container).isEqualTo(Favorites.CONTAINER_HOTSEAT)
        assertThat(item1.screenId).isEqualTo(2)
        assertThat(item1.cellX).isEqualTo(2)
        assertThat(item1.cellY).isEqualTo(3)
    }

    @Test
    fun legacyApi_moveItemsInDatabase_updatesPropsSynchronously() {
        addToModel(item1, item2)

        val items = listOf(item1, item2)
        modelWriter.moveItemsInDatabase(items, Favorites.CONTAINER_HOTSEAT, 1)

        assertThat(item1.container).isEqualTo(Favorites.CONTAINER_HOTSEAT)
        assertThat(item1.screenId).isEqualTo(0)
        assertThat(item2.container).isEqualTo(Favorites.CONTAINER_HOTSEAT)
        assertThat(item2.screenId).isEqualTo(1)
    }

    @Test
    fun legacyApi_modifyItemInDatabase_updatesPropsSynchronously() {
        addToModel(item1)

        modelWriter.modifyItemInDatabase(item1, Favorites.CONTAINER_DESKTOP, 2, 3, 4, 5, 6)

        assertThat(item1.container).isEqualTo(Favorites.CONTAINER_DESKTOP)
        assertThat(item1.screenId).isEqualTo(2)
        assertThat(item1.cellX).isEqualTo(3)
        assertThat(item1.cellY).isEqualTo(4)
        assertThat(item1.spanX).isEqualTo(5)
        assertThat(item1.spanY).isEqualTo(6)
    }

    @Test
    fun legacyApi_addOrMoveItemInDatabase_updatesPropsSynchronously() {
        // Test add path
        val newItem = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        modelWriter.addOrMoveItemInDatabase(newItem, Favorites.CONTAINER_DESKTOP, 2, 3, 4)

        assertThat(newItem.container).isEqualTo(Favorites.CONTAINER_DESKTOP)
        assertThat(newItem.screenId).isEqualTo(2)
        assertThat(newItem.cellX).isEqualTo(3)
        assertThat(newItem.cellY).isEqualTo(4)

        // Test move path
        addToModel(item1)
        modelWriter.addOrMoveItemInDatabase(item1, Favorites.CONTAINER_HOTSEAT, 1, 2, 3)
        assertThat(item1.container).isEqualTo(Favorites.CONTAINER_HOTSEAT)
        assertThat(item1.screenId).isEqualTo(2)
        assertThat(item1.cellX).isEqualTo(2)
        assertThat(item1.cellY).isEqualTo(3)
    }

    @Test
    fun legacyApi_moveItem_persistsCorrectValues() {
        addToModel(item1)

        // Move the item using legacy API
        val newContainer = Favorites.CONTAINER_HOTSEAT
        val newScreenId = 1
        val newCellX = 2
        val newCellY = 3
        modelWriter.moveItemInDatabase(item1, newContainer, newScreenId, newCellX, newCellY)

        // Verify the background model (simulating DB state) has the correct values
        val persistedItem = bgDataModel.itemsIdMap[item1.id]
        assertWithMessage("Item should exist in model").that(persistedItem).isNotNull()
        assertWithMessage("Persisted container incorrect")
            .that(persistedItem?.container)
            .isEqualTo(newContainer)
        // For hotseat items, screenId is repurposed to indicate rank and must match cellX.
        assertWithMessage("Persisted screenId incorrect")
            .that(persistedItem?.screenId)
            .isEqualTo(newCellX)
        assertWithMessage("Persisted cellX incorrect")
            .that(persistedItem?.cellX)
            .isEqualTo(newCellX)
        assertWithMessage("Persisted cellY incorrect")
            .that(persistedItem?.cellY)
            .isEqualTo(newCellY)
    }

    @Test
    fun legacyApi_ModifyItem_persistsCorrectValues() {
        addToModel(item1)

        // Modify item using legacy API
        val newContainer = Favorites.CONTAINER_DESKTOP
        val newScreenId = 2
        val newCellX = 3
        val newCellY = 4
        val newSpanX = 2
        val newSpanY = 2
        modelWriter.modifyItemInDatabase(
            item1,
            newContainer,
            newScreenId,
            newCellX,
            newCellY,
            newSpanX,
            newSpanY,
        )

        // Verify background model
        val persistedItem = bgDataModel.itemsIdMap[item1.id]
        assertWithMessage("Item should exist in model").that(persistedItem).isNotNull()
        assertWithMessage("Persisted container incorrect")
            .that(persistedItem?.container)
            .isEqualTo(newContainer)
        assertWithMessage("Persisted screenId incorrect")
            .that(persistedItem?.screenId)
            .isEqualTo(newScreenId)
        assertWithMessage("Persisted cellX incorrect")
            .that(persistedItem?.cellX)
            .isEqualTo(newCellX)
        assertWithMessage("Persisted cellY incorrect")
            .that(persistedItem?.cellY)
            .isEqualTo(newCellY)
        assertWithMessage("Persisted spanX incorrect")
            .that(persistedItem?.spanX)
            .isEqualTo(newSpanX)
        assertWithMessage("Persisted spanY incorrect")
            .that(persistedItem?.spanY)
            .isEqualTo(newSpanY)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRANSACTIONAL_MODEL_WRITER)
    fun legacyApi_withTransactionalFlagEnabled_updatesSynchronously() {
        val newItem = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        modelWriter.addItemToDatabase(newItem, Favorites.CONTAINER_DESKTOP, 2, 3, 4)

        // Verify props are updated immediately
        assertThat(newItem.container).isEqualTo(Favorites.CONTAINER_DESKTOP)
        assertThat(newItem.screenId).isEqualTo(2)
        assertThat(newItem.cellX).isEqualTo(3)
        assertThat(newItem.cellY).isEqualTo(4)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TRANSACTIONAL_MODEL_WRITER)
    fun legacyApi_withTransactionalFlagDisabled_updatesSynchronously() {
        val newItem = WorkspaceItemInfo().apply { id = ItemInfo.NO_ID }
        modelWriter.addItemToDatabase(newItem, Favorites.CONTAINER_DESKTOP, 2, 3, 4)

        // Verify props are updated immediately
        assertThat(newItem.container).isEqualTo(Favorites.CONTAINER_DESKTOP)
        assertThat(newItem.screenId).isEqualTo(2)
        assertThat(newItem.cellX).isEqualTo(3)
        assertThat(newItem.cellY).isEqualTo(4)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRANSACTIONAL_MODEL_WRITER)
    fun deleteItemFromDatabase_withFlagEnabled_usesTransactionalPath() {
        addToModel(item1)
        reset(mockNotifier)

        modelWriter.deleteItemFromDatabase(item1, "reason")

        assertThat(bgDataModel.itemsIdMap[item1.id]).isNull()
        val changeLogCaptor = argumentCaptor<ChangeLog>()
        verify(mockNotifier).notifyModelChanged(changeLogCaptor.capture(), any())
        assertThat(changeLogCaptor.firstValue.itemsRemoved).containsExactly(item1)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRANSACTIONAL_MODEL_WRITER)
    fun updateItemInDatabase_withFlagEnabled_usesTransactionalPath() {
        addToModel(item1)
        reset(mockNotifier)

        item1.title = "New Title"
        modelWriter.updateItemInDatabase(item1)

        assertThat(bgDataModel.itemsIdMap[item1.id]?.title).isEqualTo("New Title")
        val changeLogCaptor = argumentCaptor<ChangeLog>()
        verify(mockNotifier).notifyModelChanged(changeLogCaptor.capture(), any())
        assertThat(changeLogCaptor.firstValue.itemsModified).containsExactly(item1)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRANSACTIONAL_MODEL_WRITER)
    fun modifyItemInDatabase_withFlagEnabled_usesTransactionalPath() {
        addToModel(item1)
        reset(mockNotifier)

        val newContainer = Favorites.CONTAINER_DESKTOP
        val newScreenId = 2
        val newCellX = 3
        val newCellY = 4
        val newSpanX = 5
        val newSpanY = 6

        modelWriter.modifyItemInDatabase(
            item1,
            newContainer,
            newScreenId,
            newCellX,
            newCellY,
            newSpanX,
            newSpanY,
        )

        assertThat(item1.container).isEqualTo(newContainer)
        assertThat(item1.screenId).isEqualTo(newScreenId)
        assertThat(item1.cellX).isEqualTo(newCellX)
        assertThat(item1.cellY).isEqualTo(newCellY)
        assertThat(item1.spanX).isEqualTo(newSpanX)
        assertThat(item1.spanY).isEqualTo(newSpanY)

        assertThat(bgDataModel.itemsIdMap[item1.id]).isNotNull()
        val changeLogCaptor = argumentCaptor<ChangeLog>()
        verify(mockNotifier).notifyModelChanged(changeLogCaptor.capture(), any())
        assertThat(changeLogCaptor.firstValue.itemsModified).containsExactly(item1)
    }

    @Test
    fun resumeWrites_discardPending_clearsQueue() {
        modelWriter.suspendWrites()

        var transactionExecuted = false
        modelWriter.scheduleTransaction { transactionExecuted = true }

        modelWriter.resumeWrites(discardPending = true)

        assertThat(transactionExecuted).isFalse()

        var nextTransactionExecuted = false
        modelWriter.scheduleTransaction { nextTransactionExecuted = true }
        assertThat(nextTransactionExecuted).isTrue()
    }

    @Test
    fun scheduleTransaction_deleteAllItems_clearsModelAndDb() {
        addToModel(item1, item2)
        assertThat(bgDataModel.itemsIdMap.count()).isEqualTo(2)

        modelWriter.scheduleTransaction { it.deleteAllItems() }

        assertThat(bgDataModel.itemsIdMap.count()).isEqualTo(0)
    }
}
