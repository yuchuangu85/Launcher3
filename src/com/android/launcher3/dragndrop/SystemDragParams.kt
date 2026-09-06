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

package com.android.launcher3.dragndrop

import android.content.ClipData
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.android.launcher3.DragSource
import com.android.launcher3.model.data.ItemInfo

/**
 * Parameters for a system-level drag-and-drop sequence.
 *
 * NOTE: These values are passed on to the launcher drag controller when starting a drag-and-drop
 * sequence. See {@link DragController#startDrag()} for additional documentation.
 */
data class SystemDragParams(
    val clipData: ClipData?,
    val extraDragFlags: Int,
    val closeAllOpenViews: Boolean,
    val dragImage: Drawable,
    val dragInfo: ItemInfo,
    val dragLayerX: Int,
    val dragLayerY: Int,
    val dragOptions: DragOptions,
    val dragRegion: Rect,
    val dragSource: DragSource,
    val dragViewScaleOnDrop: Float,
    val draggableView: DraggableView,
    val initialDragViewScale: Float,
)
