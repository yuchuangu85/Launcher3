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

package com.android.quickstep.taskbar.customization

import com.android.launcher3.taskbar.customization.enums.OverflowIconPosition
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerUtil
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerUtil.MINIMUM_ICONS_TO_SHOW_WITH_OVERFLOW
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class TaskbarIconContainerUtilTest {

    @Test
    fun testGetMaxIconCount_whenOverflowDisabled_shouldReturnItemCount() {
        assertThat(TaskbarIconContainerUtil.getMaxIconCount(10, 4, false)).isEqualTo(10)
    }

    @Test
    fun testGetMaxIconCount_whenZeroOverflowingItems_shouldReturnItemCount() {
        assertThat(TaskbarIconContainerUtil.getMaxIconCount(10, 0, true)).isEqualTo(10)
    }

    @Test
    fun testGetMaxIconCount_whenOverflowingItems_shouldReturnItemCountDueToNotEnoughItemsToOverflow() {
        assertThat(TaskbarIconContainerUtil.getMaxIconCount(3, 1, true)).isEqualTo(3)
    }

    @Test
    fun testGetMaxIconCount_whenOverflowingItems_shouldReturnMinimumIconsToShowInContainer() {
        assertThat(TaskbarIconContainerUtil.getMaxIconCount(4, 2, true))
            .isEqualTo(MINIMUM_ICONS_TO_SHOW_WITH_OVERFLOW)
    }

    @Test
    fun testGetMaxIconCount_whenOverflowingItems_shouldReturnItemCountMinusOverflownItemsAndExtraOne() {
        assertThat(TaskbarIconContainerUtil.getMaxIconCount(10, 1, true)).isEqualTo(8)
    }

    @Test
    fun testGetMaxIconCount_whenOverflowingItems_shouldReturnItemCountMinusOverflownItems() {
        assertThat(TaskbarIconContainerUtil.getMaxIconCount(10, 2, true)).isEqualTo(8)
    }

    @Test
    fun testGetOverflowAndNonOverflowItems_whenNoOverFlowItemsAndOverflowPositionIsAtEnd() {
        val itemList = (1..4).toList()
        val taskbarIconContainerLists =
            TaskbarIconContainerUtil.getOverflowAndNonOverflowLists(
                itemList,
                OverflowIconPosition.END,
                4,
            )

        assertThat(taskbarIconContainerLists.nonOverflownItems).hasSize(itemList.size)
        assertThat(taskbarIconContainerLists.overflownItems).isEmpty()
    }

    @Test
    fun testGetOverflowAndNonOverflowItems_whenNoOverFlowItemsAndOverflowPositionIsAtStart() {
        val itemList = (1..4).toList()
        val taskbarIconContainerLists =
            TaskbarIconContainerUtil.getOverflowAndNonOverflowLists(
                itemList,
                OverflowIconPosition.START,
                4,
            )

        assertThat(taskbarIconContainerLists.nonOverflownItems).hasSize(itemList.size)
        assertThat(taskbarIconContainerLists.overflownItems).isEmpty()
    }

    @Test
    fun testGetOverflowAndNonOverflowItems_whenThereAreOverFlowItemsAndOverflowPositionIsAtEnd() {
        val itemList = (1..10).toList()
        var taskbarIconContainerLists =
            TaskbarIconContainerUtil.getOverflowAndNonOverflowLists(
                itemList,
                OverflowIconPosition.END,
                8,
            )

        assertThat(taskbarIconContainerLists.nonOverflownItems).hasSize(7)
        assertThat(taskbarIconContainerLists.overflownItems).hasSize(3)
    }

    @Test
    fun testGetOverflowAndNonOverflowItems_whenThereAreOverFlowItemsAndOverflowPositionIsAtStart() {
        val itemList = (1..10).toList()
        var taskbarIconContainerLists =
            TaskbarIconContainerUtil.getOverflowAndNonOverflowLists(
                itemList,
                OverflowIconPosition.START,
                8,
            )

        assertThat(taskbarIconContainerLists.nonOverflownItems).hasSize(7)
        assertThat(taskbarIconContainerLists.overflownItems).hasSize(3)
    }
}
