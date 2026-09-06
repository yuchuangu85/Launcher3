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

package com.android.launcher3.appfunctions.workspace

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.appfunctions.workspace.WorkspaceAppFunctions.Proof
import com.android.launcher3.workspacefunctions.testing.FakeWorkspaceRepository
import com.android.launcher3.workspacefunctions.testing.FakeWorkspaceTransaction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceMutationManagerTest {

    private val fakeRepository = FakeWorkspaceRepository()
    private val mutationManager = WorkspaceMutationManager(fakeRepository)

    @Test
    fun removeItem_validRequest_callsTransactionAndReturnsSuccess(): Unit = runBlocking {
        // Setup repository with an item that matches the selector
        // Let's use coordinates (0, 1, 1)
        fakeRepository.workspace = WorkspaceSpec(
            screens = listOf(WorkspaceScreenSpec(items = listOf(WorkspaceItemSpec(x = 1, y = 1)))),
            hotseat = HotseatSpec(emptyList()),
            rows = null,
            columns = null
        )

        val params = RemoveItemParamsSpec(item = createSelector(screenIndex = 0, x = 1, y = 1))
        val result = mutationManager.removeItem(params)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Item removed")
        assertThat(result.proof).isEqualTo(Proof.REMOVE_ITEM_PROOF)

        val transaction = fakeRepository.lastTransaction as FakeWorkspaceTransaction
        assertThat(transaction.removeItemCalledWith).isEqualTo(params)
        assertThat(transaction.commitCalled).isTrue()
    }

    @Test
    fun removeItem_invalidSelector_returnsFailureWithInvalidParameters(): Unit = runBlocking {
        // Empty selector is invalid
        val params = RemoveItemParamsSpec(item = createSelector())
        val result = mutationManager.removeItem(params)

        assertThat(result.success).isFalse()
        assertThat(result.errorCode?.code).isEqualTo(ErrorCode.INVALID_PARAMETERS)
        assertThat(fakeRepository.lastTransaction).isNull()
    }

    @Test
    fun removeItem_itemNotFound_returnsFailureWithItemNotFound(): Unit = runBlocking {
        // Empty workspace, selector will not find any item
        fakeRepository.workspace = WorkspaceSpec(
            screens = listOf(WorkspaceScreenSpec(items = emptyList())),
            hotseat = HotseatSpec(emptyList()),
            rows = null,
            columns = null
        )

        val params = RemoveItemParamsSpec(item = createSelector(label = "NonExistent"))
        val result = mutationManager.removeItem(params)

        assertThat(result.success).isFalse()
        assertThat(result.errorCode?.code).isEqualTo(ErrorCode.ITEM_NOT_FOUND)
        assertThat(fakeRepository.lastTransaction).isNull()
    }

    private fun createSelector(
        label: String? = null,
        screenIndex: Int? = null,
        x: Int? = null,
        y: Int? = null,
        hotseatRank: Int? = null,
        packageName: String? = null,
        className: String? = null
    ) = ItemSelectorSpec(
        label = label,
        screenIndex = screenIndex,
        x = x,
        y = y,
        hotseatRank = hotseatRank,
        packageName = packageName,
        className = className
    )
}
