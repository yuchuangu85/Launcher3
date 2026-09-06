/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View

/**
 * A view which draws a drawable stretched to fit its size. Unlike ImageView, it avoids relayout
 * when the drawable changes.
 */
class IconView : View {
    var drawable: Drawable? = null
        private set

    var drawableWidth = 0
        private set

    var drawableHeight = 0
        private set

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    /** Sets a [Drawable] to be displayed. */
    fun setDrawable(d: Drawable?) {
        drawable?.callback = null

        // Copy drawable so that mutations below do not affect other users of the drawable
        drawable = d?.constantState?.newDrawable()?.mutate()
        drawable?.let {
            it.callback = this
            setDrawableSizeInternal(width, height)
        }
        invalidate()
    }

    /** Sets the size of the icon drawable. */
    fun setDrawableSize(iconWidth: Int, iconHeight: Int) {
        drawableWidth = iconWidth
        drawableHeight = iconHeight
        drawable?.let { setDrawableSizeInternal(width, height) }
    }

    private fun setDrawableSizeInternal(selfWidth: Int, selfHeight: Int) {
        val selfRect = Rect(0, 0, selfWidth, selfHeight)
        val drawableRect = Rect()
        Gravity.apply(Gravity.CENTER, drawableWidth, drawableHeight, selfRect, drawableRect)
        drawable?.bounds = drawableRect
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawable?.let { setDrawableSizeInternal(w, h) }
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        super.verifyDrawable(who) || who === drawable

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        drawable?.let {
            if (it.isStateful && it.setState(drawableState)) {
                invalidateDrawable(it)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun hasOverlappingRendering(): Boolean = false
}
