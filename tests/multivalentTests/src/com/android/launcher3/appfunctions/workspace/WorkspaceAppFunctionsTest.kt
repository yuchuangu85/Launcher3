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

import android.content.Context
import androidx.appfunctions.AppFunctionContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.appfunctions.workspace.WorkspaceAppFunctions.Proof
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceAppFunctionsTest {
    private val fakeWorkspaceRepository = FakeWorkspaceRepository()
    private val workspaceAppFunctions = WorkspaceAppFunctions(fakeWorkspaceRepository)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun getCurrentWorkspace_returnsWorkspaceSpec(): Unit = runBlocking {
        val workspaceResponse =
            workspaceAppFunctions.getCurrentWorkspace(FakeAppFunctionContext(context))
        assertThat(workspaceResponse.workspace).isEqualTo(fakeWorkspaceRepository.getWorkspace())
        assertThat(workspaceResponse.proof).isEqualTo(Proof.GET_CURRENT_WORKSPACE_PROOF)
    }

    @Test
    fun removeItem_returnsWorkspaceResponse(): Unit = runBlocking {
        val target = RemoveItemParamsSpec(
            item = ItemSelectorSpec(
                label = null,
                screenIndex = 0,
                x = 1,
                y = 2,
                hotseatRank = null,
                packageName = null,
                className = null
            )
        )
        val response = workspaceAppFunctions.removeItem(FakeAppFunctionContext(context), target)

        assertThat(response.success).isTrue()
        assertThat(response.proof).isEqualTo(Proof.REMOVE_ITEM_PROOF)
        val lastTransaction = fakeWorkspaceRepository.lastTransaction as FakeWorkspaceTransaction
        assertThat(lastTransaction.lastRemovedTarget).isEqualTo(target)
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
        var lastTransaction: WorkspaceTransaction = FakeWorkspaceTransaction()

        override suspend fun getWorkspace(): WorkspaceSpec {
            return WorkspaceSpec(
                screens = listOf(
                    WorkspaceScreenSpec(
                        items = listOf(
                            WorkspaceItemSpec(x = 1, y = 2)
                        )
                    )
                ),
                hotseat = HotseatSpec(listOf()),
                rows = null,
                columns = null,
            )
        }

        override fun newTransaction(): WorkspaceTransaction {
            lastTransaction = FakeWorkspaceTransaction()
            return lastTransaction
        }

        override suspend fun getInstalledApps(orderByUsageStats: Boolean): List<UnplacedAppSpec> {
            return emptyList()
        }

        override suspend fun getInstalledWidgets(
            orderByUsageStats: Boolean
        ): List<UnplacedWidgetSpec> {
            return emptyList()
        }
    }

    private class FakeWorkspaceTransaction : WorkspaceTransaction {
        var lastRemovedTarget: RemoveItemParamsSpec? = null

        override fun removeItem(target: RemoveItemParamsSpec): WorkspaceTransaction {
            lastRemovedTarget = target
            return this
        }

        override suspend fun commit(): WorkspaceSpec {
            return WorkspaceSpec(
                screens = listOf(),
                hotseat = HotseatSpec(listOf()),
                rows = null,
                columns = null,
            )
        }
    }

    private class FakeAppFunctionContext(override val context: Context) : AppFunctionContext
}
