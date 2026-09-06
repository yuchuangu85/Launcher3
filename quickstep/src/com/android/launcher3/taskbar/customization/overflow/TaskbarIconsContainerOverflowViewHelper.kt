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

package com.android.launcher3.taskbar.customization.overflow

import android.view.ViewGroup
import androidx.core.view.contains
import androidx.core.view.setPadding
import com.android.launcher3.taskbar.TaskbarOverflowView
import com.android.launcher3.taskbar.TaskbarOverflowView.OverflowType
import com.android.launcher3.taskbar.customization.enums.OverflowIconPosition
import com.android.launcher3.taskbar.customization.listeners.TaskbarIconsContainerHoverListener
import com.android.launcher3.taskbar.customization.listeners.TaskbarIconsContainerOverflowClickListeners
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerLayoutParams

/** Helper class for taskbar icon container to show overflow view. */
class TaskbarIconsContainerOverflowViewHelper<T>
private constructor(
    private val taskbarIconViewSize: Int,
    private val taskbarIconPadding: Int,
    private val overflowIconPosition: OverflowIconPosition,
    private val taskbarOverflowIconWrapper: TaskbarOverflowIconWrapper<T>,
    private val parentView: ViewGroup,
    val taskbarContainerOverflowView: TaskbarOverflowView,
) {
    private val isOverflowViewShowing: Boolean
        get() = taskbarContainerOverflowView in parentView

    // Index to remove view from parent view to add overflow view in place.
    private val removeIndexForAddingOverflowView: Int
        get() =
            // -1 means add view at the end of parent view
            if (overflowIconPosition == OverflowIconPosition.END) parentView.childCount - 1 else 0

    private val addIndexForOverflowView: Int
        get() =
            // -1 means add view at the end of parent view
            if (overflowIconPosition == OverflowIconPosition.END) -1 else 0

    fun setUpOverflowView(items: List<T>, itemMarginLeftRight: Int) {
        if (items.isNotEmpty()) {
            taskbarContainerOverflowView.setItems(
                items.map { item -> taskbarOverflowIconWrapper.getTaskbarOverflowItemWrapper(item) }
            )
            maybeAddPinOverflowView(itemMarginLeftRight)
        } else if (isOverflowViewShowing) {
            parentView.removeView(taskbarContainerOverflowView)
            taskbarContainerOverflowView.clearItems()
        }
    }

    fun setUpCallbacks(
        hoverListener: TaskbarIconsContainerHoverListener,
        overflowClickListeners: TaskbarIconsContainerOverflowClickListeners,
    ) {
        taskbarContainerOverflowView.setOnClickListener(
            overflowClickListeners.overflowIconClickListener
        )
        taskbarContainerOverflowView.onLongClickListener =
            overflowClickListeners.overflowIconLongClickListener
        taskbarContainerOverflowView.setOnHoverListener(
            hoverListener.getHoverListener(taskbarContainerOverflowView)
        )
    }

    private fun maybeAddPinOverflowView(itemMarginLeftRight: Int) {
        if (isOverflowViewShowing) {
            return
        }
        val lp = TaskbarIconContainerLayoutParams(taskbarIconViewSize, taskbarIconViewSize)
        if (overflowIconPosition == OverflowIconPosition.END) {
            lp.marginStart = itemMarginLeftRight
        } else {
            lp.marginEnd = itemMarginLeftRight
        }

        taskbarContainerOverflowView.setPadding(taskbarIconPadding)

        parentView.addView(taskbarContainerOverflowView, addIndexForOverflowView, lp)
    }

    companion object {

        /**
         * We don't want to expose overflowViewType to [TaskbarIconsContainerOverflowViewHelper].
         * So, create function create [TaskbarOverflowView] and passes it into
         * [TaskbarIconsContainerOverflowViewHelper].
         */
        fun <T> create(
            taskbarIconViewSize: Int,
            taskbarIconPadding: Int,
            overflowIconPosition: OverflowIconPosition,
            taskbarOverflowIconWrapper: TaskbarOverflowIconWrapper<T>,
            parentView: ViewGroup,
            overflowViewType: OverflowType,
        ): TaskbarIconsContainerOverflowViewHelper<T> {

            val taskbarContainerOverflowView =
                TaskbarOverflowView.inflateIcon(
                    overflowViewType,
                    parentView,
                    taskbarIconViewSize,
                    taskbarIconPadding,
                )

            return TaskbarIconsContainerOverflowViewHelper(
                taskbarIconViewSize,
                taskbarIconPadding,
                overflowIconPosition,
                taskbarOverflowIconWrapper,
                parentView,
                taskbarContainerOverflowView,
            )
        }
    }
}
