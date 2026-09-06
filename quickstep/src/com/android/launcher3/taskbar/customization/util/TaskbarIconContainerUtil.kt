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

package com.android.launcher3.taskbar.customization.util

import androidx.annotation.VisibleForTesting
import com.android.launcher3.taskbar.customization.enums.OverflowIconPosition
import kotlin.math.max

object TaskbarIconContainerUtil {
    const val DEFAULT_BOUNCE_SCALE = 1f
    @VisibleForTesting const val MINIMUM_ICONS_TO_SHOW_WITH_OVERFLOW = 3

    /** Returns maximum amount of icons we show in a container */
    fun getMaxIconCount(itemCount: Int, overflowingItems: Int, isOverflowEnabled: Boolean): Int {
        return if (itemCount <= MINIMUM_ICONS_TO_SHOW_WITH_OVERFLOW || !isOverflowEnabled) {
            itemCount
        } else if (overflowingItems > 1) {
            max(itemCount - overflowingItems, MINIMUM_ICONS_TO_SHOW_WITH_OVERFLOW)
        } else if (overflowingItems == 1) {
            // we need to subtract one since we need minimum of two icons in overflow.
            max(itemCount - overflowingItems - 1, MINIMUM_ICONS_TO_SHOW_WITH_OVERFLOW)
        } else {
            itemCount
        }
    }

    /** Returns lists of icons to show in taskbar and in overflow */
    fun <T> getOverflowAndNonOverflowLists(
        itemList: List<T>,
        overflowIconPosition: OverflowIconPosition,
        numMaxIcons: Int,
    ): TaskbarContainerIconsBySection<T> {
        return when (overflowIconPosition) {
            OverflowIconPosition.END -> {
                val startIdx = 0
                val endIdx = if (itemList.size > numMaxIcons) numMaxIcons - 1 else itemList.size
                TaskbarContainerIconsBySection(
                    itemList.subList(startIdx, endIdx),
                    itemList.subList(endIdx, itemList.size),
                )
            }

            OverflowIconPosition.START -> {
                if (itemList.size <= numMaxIcons)
                    return TaskbarContainerIconsBySection(itemList, emptyList())

                val startIdx = itemList.size - numMaxIcons + 1
                val endIdx = itemList.size
                itemList.subList(startIdx, endIdx)
                TaskbarContainerIconsBySection(
                    itemList.subList(startIdx, endIdx),
                    itemList.subList(0, startIdx),
                )
            }
        }
    }
}
