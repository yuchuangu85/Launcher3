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
package com.android.launcher3.taskbar

import android.graphics.Point
import android.graphics.PointF
import android.view.LayoutInflater
import android.view.View
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.DeepShortcutDragHandler
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.shortcuts.DeepShortcutView

/** Handles dragging deep shortcuts from the taskbar. */
class TaskbarDeepShortcutDragHandler(private val context: BaseTaskbarContext) :
    DeepShortcutDragHandler {
    override fun onDeepShortcutLongPress(itemInfo: ItemInfoWithIcon, touchPoint: PointF) {
        if (context.dragController.isDragging) {
            return
        }

        if ((itemInfo.runtimeStatusFlags and (ItemInfoWithIcon.FLAG_NOT_PINNABLE)) != 0) {
            return
        }

        val container = PopupContainer.getOpen(context) ?: return

        val iconSize = context.deviceProfile.taskbarProfile.iconSize
        val iconShift =
            Point((touchPoint.x - iconSize / 2).toInt(), (touchPoint.y - iconSize).toInt())

        // Create a dummy DeepShortcutView to use the existing drag logic in TaskbarDragController.
        val shortcutView =
            LayoutInflater.from(context).inflate(R.layout.deep_shortcut, container, false)
                as DeepShortcutView
        shortcutView.bubbleText.applyFromWorkspaceItem(itemInfo as WorkspaceItemInfo)
        shortcutView.iconView.background = shortcutView.bubbleText.icon

        shortcutView.visibility = View.INVISIBLE
        container.addView(shortcutView)

        (context.dragController as TaskbarDragController).startDragOnLongClick(
            shortcutView,
            iconShift,
        )
    }
}
