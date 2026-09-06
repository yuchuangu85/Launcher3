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

import android.graphics.Rect
import android.view.MotionEvent

/** Handles touch events for popups. */
class PopupTouchHandler {

    /**
     * Handles touch events for the given popup.
     *
     * @param popup the popup to handle the touch event for.
     * @param ev the touch event.
     * @return true if the event was handled, false otherwise.
     */
    fun handleTouchEvent(popup: AbstractFloatingView?, ev: MotionEvent): Boolean {
        if (isEventOverOpenPopup(popup, ev)) {
            return false
        } else if (popup != null) {
            popup.close(true)
            return true
        }
        return false
    }

    /**
     * Returns whether the given touch event is over the given popup.
     *
     * @param popup the popup to check.
     * @param ev the touch event.
     * @return true if the event is over the popup, false otherwise.
     */
    fun isEventOverOpenPopup(popup: AbstractFloatingView?, ev: MotionEvent): Boolean {
        val tempRect = Rect()
        if (popup != null) {
            // If tap is on popup, don't consume event and don't close anything.
            popup.getHitRect(tempRect)
            if (tempRect.contains(ev.x.toInt(), ev.y.toInt())) {
                return true
            }
        }
        return false
    }
}
