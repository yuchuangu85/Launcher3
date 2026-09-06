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

package com.android.launcher3.popup

import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.util.MultiPropertyFactory

/** An interface for an icon view that can be shown in a popup. */
interface IconViewController {
    /** Sets the visibility of the icon. */
    fun setIconVisible(visible: Boolean)

    /** Get the height of the icon. */
    fun getIconHeight(): Int

    /** Sets the visibility of the notification dot. */
    fun setForceHideDot(forceHideDot: Boolean)

    /**
     * Gets the alpha for text inside a BubbleTextView.
     *
     * @return a MultiProperty that has the alpha for the text.
     */
    fun getFloatingViewTextAlpha(): MultiPropertyFactory<AnimatedFloat>.MultiProperty?
}
