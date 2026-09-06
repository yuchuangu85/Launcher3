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

import com.android.launcher3.util.Executors.getTaskbarUiThread

/** Wrap [StashedHandleViewController] and dispatch API calls to taskbar's ui thread. */
class StashedHandleViewControllerProxy(private val delegate: StashedHandleViewController) {

    fun setTranslationYForSwipe(transY: Float) {
        getTaskbarUiThread().execute { delegate.setTranslationYForSwipe(transY) }
    }

    fun setStashedHandleAlpha(alphaIndex: Int, alpha: Float) {
        getTaskbarUiThread().execute { delegate.stashedHandleAlpha.get(alphaIndex).setValue(alpha) }
    }
}
