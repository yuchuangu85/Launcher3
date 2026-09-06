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

package com.android.launcher3.model.data

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.data.TaskItemInfo.Companion.isSameItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskItemInfoTest {

    @Test
    fun isSameItem_sameTaskItems_returnsTrue() {
        assertThat(TEST_TASK_ITEM_1.isSameItem(TEST_TASK_ITEM_1)).isTrue()
    }

    @Test
    fun isSameItem_sameItems_returnsTrue() {
        assertThat(TEST_ITEM_1.isSameItem(TEST_ITEM_1)).isTrue()
    }

    @Test
    fun isSameItem_taskItemToSameWrappedItem_returnsTrue() {
        assertThat(TEST_TASK_ITEM_1.isSameItem(TEST_ITEM_1)).isTrue()
    }

    @Test
    fun isSameItem_itemToTaskItemWrappingIt_returnsTrue() {
        assertThat(TEST_ITEM_1.isSameItem(TEST_TASK_ITEM_1)).isTrue()
    }

    @Test
    fun isSameItem_taskItemAndNewTaskItemInstanceWithSameItem_returnsTrue() {
        val newTaskItem = TaskItemInfo(1, TEST_ITEM_1)
        assertThat(TEST_TASK_ITEM_1.isSameItem(newTaskItem)).isTrue()
    }

    @Test
    fun isSameItem_taskItemsWrappingDifferentItems_returnsFalse() {
        assertThat(TEST_TASK_ITEM_1.isSameItem(TEST_TASK_ITEM_2)).isFalse()
    }

    @Test
    fun isSameItem_differentItems_returnsFalse() {
        assertThat(TEST_ITEM_1.isSameItem(TEST_ITEM_2)).isFalse()
    }

    @Test
    fun isSameItem_taskWrappingItemToDifferentItem_returnsFalse() {
        assertThat(TEST_TASK_ITEM_1.isSameItem(TEST_ITEM_2)).isFalse()
    }

    @Test
    fun isSameItem_itemToTaskWithDifferentWrappedItem_returnsFalse() {
        assertThat(TEST_ITEM_1.isSameItem(TEST_TASK_ITEM_2)).isFalse()
    }

    @Test
    fun isSameItem_taskItemToNullItem_returnsFalse() {
        assertThat(TEST_TASK_ITEM_1.isSameItem(null)).isFalse()
    }

    @Test
    fun isSameItem_taskItemToDifferentObjectType_returnsFalse() {
        assertThat(TEST_TASK_ITEM_1.isSameItem(1)).isFalse()
    }

    private companion object {
        val TEST_ITEM_1 =
            WorkspaceItemInfo().apply {
                title = "Test Item 1"
                intent = Intent()
            }
        val TEST_ITEM_2 =
            WorkspaceItemInfo().apply {
                title = "Test Item 2"
                intent = Intent()
            }
        val TEST_TASK_ITEM_1 = TaskItemInfo(1, TEST_ITEM_1)
        val TEST_TASK_ITEM_2 = TaskItemInfo(2, TEST_ITEM_2)
    }
}
