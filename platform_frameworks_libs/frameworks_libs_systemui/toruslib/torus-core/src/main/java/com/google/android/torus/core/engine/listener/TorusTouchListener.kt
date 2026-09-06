/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.torus.core.engine.listener

import android.view.MotionEvent
import com.google.android.torus.core.engine.TorusEngine

/**
 * Allows to receive Touch events. The Interface must be implemented by a [TorusEngine] instance,
 */
interface TorusTouchListener {
    /**
     * Called when a touch event has been triggered. If the engine is set as a wallpaper and the
     * device is locked, touch events may be restricted. Only taps on the lock screen's focal area
     * will be delivered via [onLockscreenFocalAreaTap]. See [onLockscreenFocalAreaTap] for details.
     *
     * @param event The new [MotionEvent].
     */
    fun onTouchEvent(event: MotionEvent)

    /**
     * Called when a short tap occurs on the wallpaper's focal area on the lock screen.
     *
     * The wallpaper's focal area is the interactive region of the wallpaper that is not obscured by
     * other lock screen elements. The wallpaper is scaled on the lock screen. These coordinates are
     * relative to the unscaled wallpaper.
     *
     * @param x The x-coordinate of the tap, relative to the unscaled wallpaper dimensions.
     * @param y The y-coordinate of the tap, relative to the unscaled wallpaper dimensions.
     */
    fun onLockscreenFocalAreaTap(x: Int, y: Int) {}
}
