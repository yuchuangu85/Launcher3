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

package com.android.systemui.shared.focus

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * A drawable that draws a rounded rectangular border when its view is focused.
 *
 * This drawable is stateful and will only draw when the view's state includes
 * [android.R.attr.state_focused].
 *
 * This is intended to be used with [BorderOnFocusView], but can be used on its own.
 *
 * ### Usage
 *
 * To use this drawable, apply it to the foreground of a view. If the view is a container for other
 * focusable views, you must ensure that the container propagates its children's focused state.
 *
 * Custom FrameLayout example:
 * ```
 * class FocusableView(context: Context) : FrameLayout(context) {
 *     private val focusBorder = FocusBorderDrawable()
 *
 *     init {
 *         // Set properties on the drawable
 *         focusBorder.color = context.getColor(R.color.my_focus_color)
 *         focusBorder.strokeWidth = 3f
 *
 *         // Ensure onDraw is called
 *         setWillNotDraw(false)
 *
 *         // Ensure the focus state of the view's children are passed to the drawable
 *         setAddStatesFromChildren(true)
 *
 *         // Apply the drawable to the view
 *         foreground = focusBorder
 *     }
 * }
 * ```
 *
 * ### Configuration Changes
 *
 * If any of the drawable's properties (like [color] or [strokeWidth]) come from resources that can
 * change, such as theme attributes, the view is responsible for updating them when the
 * configuration changes. A good place to do this is in the view's `onConfigurationChanged` method.
 * See [BorderOnFocusView] for an example implementation.
 */
class FocusBorderDrawable : Drawable() {
    private val rect = RectF()
    private var hasFocus = false

    private val borderPaint: Paint =
        Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

    /** The radius of the corners of the border. */
    var cornerRadius: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    /**
     * The amount to inset the border horizontally.
     *
     * Positive values will shrink the border
     */
    var horizontalInset: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    /**
     * The amount to inset the border vertically.
     *
     * Positive values will shrink the border
     */
    var verticalInset: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    /** The color of the border */
    var color: Int
        get() = borderPaint.color
        set(value) {
            if (borderPaint.color != value) {
                borderPaint.color = value
                invalidateSelf()
            }
        }

    /** The width of the border */
    var strokeWidth: Float
        get() = borderPaint.strokeWidth
        set(value) {
            if (borderPaint.strokeWidth != value) {
                borderPaint.strokeWidth = value
                invalidateSelf()
            }
        }

    override fun setAlpha(alpha: Int) {
        borderPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        borderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (!hasFocus) return

        rect.set(
            bounds.left + horizontalInset,
            bounds.top + verticalInset,
            bounds.right - horizontalInset,
            bounds.bottom - verticalInset,
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    override fun onStateChange(state: IntArray): Boolean {
        val focused = state.any { it == android.R.attr.state_focused }
        if (hasFocus != focused) {
            hasFocus = focused
            return true
        }
        return false
    }

    override fun isStateful() = true

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
