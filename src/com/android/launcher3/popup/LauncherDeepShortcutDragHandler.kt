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
package com.android.launcher3.popup

import android.graphics.PointF
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toPoint
import com.android.launcher3.AbstractFloatingView.TYPE_FOLDER
import com.android.launcher3.AbstractFloatingView.closeOpenContainer
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider
import com.android.launcher3.touch.ItemLongClickListener

/** Handles dragging deep shortcuts from the launcher. */
class LauncherDeepShortcutDragHandler(
    private val launcher: Launcher,
    private val container: PopupContainer<*>,
) : DeepShortcutDragHandler {

    override fun onDeepShortcutLongPress(itemInfo: ItemInfoWithIcon, touchPoint: PointF) {
        if (!ItemLongClickListener.canStartDrag(launcher)) {
            return
        }
        if ((itemInfo.runtimeStatusFlags and (ItemInfoWithIcon.FLAG_NOT_PINNABLE)) != 0) {
            return
        }

        val iconSize = launcher.deviceProfile.workspaceProfile.iconSizePx
        val iconShift = PointF(touchPoint.x - iconSize / 2, touchPoint.y)

        val dummyIconView = BubbleTextView(launcher)
        dummyIconView.visibility = View.INVISIBLE
        dummyIconView.applyFromItemInfoWithIcon(itemInfo)
        dummyIconView.background = itemInfo.bitmap.icon.toDrawable(launcher.resources)

        val previewProvider = ShortcutDragPreviewProvider(dummyIconView, iconShift.toPoint())
        val draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON)
        launcher.workspace.beginDragShared(
            dummyIconView,
            draggableView,
            container,
            itemInfo,
            previewProvider,
            DragOptions(),
        )

        // TODO: support dragging from within folder without having to close it
        closeOpenContainer(launcher, TYPE_FOLDER)
    }
}
