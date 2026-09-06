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

package com.android.launcher3.model.tasks

import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceChangeEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.RemoveEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.tasks.ModelRepoTestEx.TrackedUpdates
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.ListenableDiffAwareRef
import com.android.launcher3.util.ListenableStream
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ExecutorService

typealias TrackedWorkspaceUpdates = TrackedUpdates<WorkspaceData, WorkspaceChangeEvent>

object ModelRepoTestEx {

    fun <T> ListenableStream<T>.trackUpdate(executor: ExecutorService = MODEL_EXECUTOR) =
        mutableListOf<T>().let { updates ->
            TestUtil.runOnExecutorSync(executor) {}
            TrackedChanges(updates, forEach(executor) { updates.add(it) })
        }

    fun <T, R> ListenableDiffAwareRef<T, R>.trackUpdateAndChanges(
        executor: ExecutorService = MODEL_EXECUTOR
    ) = TrackedUpdates(updates = trackUpdate(executor), changes = changes.trackUpdate(executor))

    /** Verifies that the update list contains an update operation, and returns the updated items */
    fun TrackedWorkspaceUpdates.verifyAndGetItemsUpdated(
        updateIndex: Int = 1,
        totalUpdates: Int = 2,
    ): Set<ItemInfo> {
        assertThat(updates).hasSize(totalUpdates)
        assertThat(changes).hasSize(totalUpdates - 1)
        val updates = changes[updateIndex - 1] as UpdateEvent
        return updates.items
    }

    /** Verifies that the update list contains a delete operation */
    fun TrackedWorkspaceUpdates.verifyDelete(deleteIndex: Int = 1, totalUpdates: Int = 2) {
        assertThat(updates).hasSize(totalUpdates)
        assertThat(changes).hasSize(totalUpdates - 1)
        assertThat(changes[deleteIndex - 1]).isInstanceOf(RemoveEvent::class.java)
    }

    data class TrackedUpdates<T, R>(
        val updates: TrackedChanges<T>,
        val changes: TrackedChanges<R>,
    ) {
        fun end() {
            updates.end()
            changes.end()
        }
    }

    data class TrackedChanges<T>(
        private val list: MutableList<T>,
        private val endAction: SafeCloseable,
    ) : List<T> by list {

        fun end() = endAction.close()
    }
}
