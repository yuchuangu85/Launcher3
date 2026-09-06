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

package com.android.launcher3.organizer.creation.screen.ui

import android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
import androidx.activity.ComponentActivity
import com.android.launcher3.R

/** Logic for applying window-level background blurs. */
class BlurController(protected val activity: ComponentActivity) {

    protected val maxBlurRadius =
        activity.resources.getDimensionPixelSize(R.dimen.home_organizer_background_blur_radius)

    fun apply() {
        activity.window.addFlags(FLAG_BLUR_BEHIND)
        activity.window.attributes =
            activity.window.attributes.apply { blurBehindRadius = maxBlurRadius }
    }
}
