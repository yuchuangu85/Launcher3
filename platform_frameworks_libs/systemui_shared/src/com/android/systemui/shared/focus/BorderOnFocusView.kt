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

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.widget.FrameLayout
import com.android.systemui.shared.R

/**
 * A view that draws a rounded rect border when it is focused.
 *
 * This is intended to wrap around anything that should have a focus ring. [horizontalInset] and
 * [verticalInset] can be used to position the ring around the desired object.
 *
 * Positive insets will shrink the border, making it smaller than the view. Negative insets will
 * expand the border, making it larger than the view.
 *
 * For example, to have a border that is 2dp smaller than the view on all sides, you would use:
 * app:horizontalInset="2dp" app:verticalInset="2dp"
 */
class BorderOnFocusView
@JvmOverloads
constructor(
    context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int = 0,
    private val defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val border = FocusBorderDrawable()

    init {
        readAttributes()

        setWillNotDraw(false)
        setAddStatesFromChildren(true)
        foreground = border
    }

    private fun readAttributes() {
        val attributes =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.BorderOnFocusView,
                defStyleAttr,
                defStyleRes,
            )
        try {
            border.cornerRadius =
                attributes.getDimension(R.styleable.BorderOnFocusView_cornerRadius, 0f)
            border.horizontalInset =
                attributes.getDimension(R.styleable.BorderOnFocusView_horizontalInset, 0f)
            border.verticalInset =
                attributes.getDimension(R.styleable.BorderOnFocusView_verticalInset, 0f)
            border.color = attributes.getColor(R.styleable.BorderOnFocusView_borderColor, 0)
            border.strokeWidth =
                attributes.getDimension(R.styleable.BorderOnFocusView_strokeWidth, 3f)
        } finally {
            attributes.recycle()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readAttributes()
    }
}
