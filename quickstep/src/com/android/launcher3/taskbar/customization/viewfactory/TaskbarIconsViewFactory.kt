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

package com.android.launcher3.taskbar.customization.viewfactory

import android.view.View

/** View factory for each taskbar icons container. */
interface TaskbarIconsViewFactory<T> {

    /** Returns a recycled view for [item] or inflates new one. */
    fun getView(item: T, index: Int): View

    /** Returns expected resource layout ID for [item]. */
    fun getExpectedLayoutResId(item: T): Int

    /** Find a view to recycle for [currentIndex]. */
    fun findViewToRecycle(item: T, expectedLayoutResId: Int, currentIndex: Int): View?

    /** Used to recycle extra/unused views present in parent view. */
    fun removeAndRecycle(view: View)
}
