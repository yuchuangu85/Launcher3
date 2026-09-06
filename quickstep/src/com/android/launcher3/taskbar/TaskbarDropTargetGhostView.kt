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

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.util.MultiTranslateDelegate

/** Placeholder view to indicates the location of the drop target during a drag-and-drop. */
class TaskbarDropTargetGhostView(context: Context, size: Int) : View(context), Reorderable {

    private val mTranslateDelegate = MultiTranslateDelegate(this)
    private var mScaleForReorderBounce = 1f

    init {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE

        val placeholderColor = context.resources.getColor(R.color.taskbar_stroke, context.theme)

        drawable.setColor(Color.TRANSPARENT)
        val strokeWidthPx = (2f * context.resources.displayMetrics.density).toInt()
        drawable.setStroke(strokeWidthPx, placeholderColor)
        drawable.cornerRadius = size / 2f

        background = drawable
    }

    override fun getTranslateDelegate(): MultiTranslateDelegate {
        return mTranslateDelegate
    }

    override fun setReorderBounceScale(scale: Float) {
        mScaleForReorderBounce = scale
    }

    override fun getReorderBounceScale(): Float {
        return mScaleForReorderBounce
    }
}
