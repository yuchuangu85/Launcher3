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

package com.android.launcher3.taskbar

import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.touch.BaseItemCustomActionsListener
import com.android.launcher3.touch.isNotPinnable

/**
 * Implementation of [BaseItemCustomActionsListener] for items appearing on the Taskbar or
 * TaskbarAllApps.
 *
 * This listener tailors the response to custom actions (like right-clicks, drags, taps) to the
 * specific functionalities and context of the Taskbar, such as showing a popup menu or initiating a
 * drag from the Taskbar.
 */
class TaskbarCustomActionsListener(private val taskbarContext: BaseTaskbarContext) :
    BaseItemCustomActionsListener() {
    override fun onOpenPopupMenu(target: View, btv: BubbleTextView?) {
        // Allow the view to handle its own popup menu if it has a custom implementation.
        if (btv?.showPopup() != null) return

        if (btv != null || target is FolderIcon) {
            taskbarContext.showPopupMenuForIcon(btv ?: target)
        }
    }

    override fun onStartDrag(target: View, btv: BubbleTextView?) {
        if (btv == null) return

        val info = btv.tag as? ItemInfoWithIcon
        if (info?.isNotPinnable == true) return

        (taskbarContext.dragController as? TaskbarDragController)?.startDragWithMouse(btv)
    }
}
