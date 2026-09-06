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

package com.android.launcher3

import android.os.Bundle
import android.view.WindowManager
import com.android.app.animation.Interpolators
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.dragndrop.AddItemActivity
import com.android.launcher3.util.WindowBlurState
import com.android.launcher3.widgetpicker.WidgetPickerProgressHandler

/** A variant of [AddItemActivity] to show pin widget sheet with blur background. */
open class QuickstepAddItemActivity : AddItemActivity(), WidgetPickerProgressHandler {
    private var isBlurEnabled = false
    private var blurRadius: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        isBlurEnabled = Flags.enableWidgetPickerBlur() && WindowBlurState.getInstance(this).value
        blurRadius = resources.getDimensionPixelSize(R.dimen.max_depth_blur_radius_enhanced)

        super.onCreate(savedInstanceState)

        if (isBlurEnabled) {
            val lp: WindowManager.LayoutParams? = window?.attributes
            lp?.dimAmount = 0.0f
        }
    }

    override fun onProgress(progress: Float) {
        rootView.windowToken?.let {
            if (isBlurEnabled) {
                updateBlurBackground(progress)
            }
        }
    }

    private fun updateBlurBackground(progress: Float) {
        window?.decorView?.viewRootImpl?.let {
            if (rootView.background == null) {
                val bgDrawable: BackgroundBlurDrawable = it.createBackgroundBlurDrawable()
                rootView.background = bgDrawable
            }
            (rootView.background as BackgroundBlurDrawable).apply {
                setBlurRadius(
                    (Interpolators.clampToProgress(
                            progress,
                            /*lowerBound=*/ MIN_BACKGROUND_BLUR_FRACTION,
                            /*upperBound=*/ MAX_BACKGROUND_BLUR_FRACTION,
                        ) * blurRadius)
                        .toInt()
                )
            }
        }
    }

    companion object {
        private const val MIN_BACKGROUND_BLUR_FRACTION = 0f // At closed state, no blur
        private const val MAX_BACKGROUND_BLUR_FRACTION = 0.3f // blur capped at 30%
    }
}
