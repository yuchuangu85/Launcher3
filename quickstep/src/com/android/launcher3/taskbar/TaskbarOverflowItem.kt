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

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.Context
import android.graphics.drawable.Drawable
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags
import com.android.launcher3.icons.IconShape
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.quickstep.util.SingleTask

interface TaskbarOverflowItem {
    /** The drawable icon for the item. */
    fun getDrawableIcon(@DrawableCreationFlags creationFlags: Int, iconShape: IconShape?): Drawable?

    /** The unique identifier for the item. */
    val itemId: Int
}

// Wrapper for [Task]
internal class TaskWrapper(private val context: Context, private val singleTask: SingleTask?) :
    TaskbarOverflowItem {
    override fun getDrawableIcon(
        @DrawableCreationFlags creationFlags: Int,
        iconShape: IconShape?,
    ): Drawable? =
        singleTask?.task?.icon
            ?: singleTask?.bitmapInfos?.firstOrNull()?.newIcon(context, creationFlags, iconShape)

    override val itemId: Int
        get() = singleTask?.task?.key?.id ?: INVALID_TASK_ID
}

// Wrapper for [ItemInfo]
internal class ItemInfoWrapper(val itemInfo: ItemInfo?, private val context: Context) :
    TaskbarOverflowItem {
    override fun getDrawableIcon(
        @DrawableCreationFlags creationFlags: Int,
        iconShape: IconShape?,
    ): Drawable? = (itemInfo as? ItemInfoWithIcon)?.newIcon(context, creationFlags)

    override val itemId: Int
        get() = itemInfo?.id ?: ItemInfo.NO_ID
}
