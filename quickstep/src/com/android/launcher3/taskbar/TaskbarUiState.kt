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

import android.graphics.Rect
import android.view.MotionEvent
import androidx.annotation.Px
import com.android.launcher3.DeviceProfile
import com.android.launcher3.util.ImmutableRect
import com.android.launcher3.util.NavigationMode
import javax.annotation.concurrent.ThreadSafe

/** Data class that represents taskbar's UI states. This state is shared to launcher and recents. */
@ThreadSafe
class TaskbarUiState {

    // Misc ui state
    @Volatile var isPrimaryDisplay = false
    @Volatile var isTaskbarOrBubbleBarDraggingItem = false

    @Volatile private var _deviceProfile = DeviceProfile.DEFAULT_DEVICE_PROFILE
    @Volatile private var _navigationMode = NavigationMode.THREE_BUTTONS

    // Taskbar ui states
    @Volatile var isTaskbarStashed = false
    @Volatile var isTaskbarAllAppsOpen = false
    @Volatile var showTaskbarEduOnAppLaunch = false
    @Volatile var showDesktopTaskbarForFreeformDisplay = false
    @Volatile var taskbarUnstashAreaSizePx: Int = 0
    @Volatile var taskbarActionCornerPaddingPx: Int = 0
    @Volatile var taskbarNavThreshold: Int = 0
    @Volatile var taskbarSlowVelocityYThreshold: Int = 0
    @Volatile var taskbarStashedScreenEdgeHoverDeadzoneHeightPx: Int = 0
    @Volatile var taskbarStashedBelowHoverDeadzoneHeightPx: Int = 0

    @Volatile private var _isTaskbarTransient = false
    @Volatile private var _isTaskbarViewShown = false
    @Volatile private var _isTaskbarDragging = false
    @Volatile private var _taskbarStashState = 0L
    @Volatile private var _taskbarIconsActualBounds = ImmutableRect.EMPTY_RECT

    // Bubble bar ui states
    @Volatile var hasBubbles = false
    @Volatile var isBubbleStashed = false
    @Volatile var isBubbleBarExpanded = false

    @Volatile private var _isBubbleDragging = false
    @Volatile private var _isBubbleBarViewVisible = true
    @Volatile private var _isBubbleBarStashedHandlerViewVisible = true
    @Volatile private var _stashedBubbleBarHeightPx = Int.MAX_VALUE
    @Volatile private var _bubbleBarViewRect = ImmutableRect.EMPTY_RECT
    @Volatile private var _bubbleBarStashedHandleViewRect = ImmutableRect.EMPTY_RECT

    // Navbar ui state
    @Volatile private var _navbarFloatingRotationButtonsBounds = ImmutableRect.EMPTY_RECT

    fun setDeviceProfile(dp: DeviceProfile) {
        _deviceProfile = dp
    }

    fun getDeviceProfile(): DeviceProfile = _deviceProfile

    fun setNavigationMode(navigationMode: NavigationMode) {
        _navigationMode = navigationMode
    }

    fun isThreeButtonNav() = _navigationMode == NavigationMode.THREE_BUTTONS

    fun setIsTransient(isTransient: Boolean) {
        _isTaskbarTransient = isTransient
    }

    fun isTransientTaskbar() =
        _isTaskbarTransient && isPrimaryDisplay && !_deviceProfile.deviceProperties.isPhone

    fun setIsTaskbarViewShown(isShown: Boolean) {
        _isTaskbarViewShown = isShown
    }

    fun setIsTaskbarDragging(isTaskbarDragging: Boolean) {
        _isTaskbarDragging = isTaskbarDragging
        isTaskbarOrBubbleBarDraggingItem = _isBubbleDragging or _isTaskbarDragging
    }

    fun setTaskbarStashState(state: Long) {
        _taskbarStashState = state
    }

    fun setTaskbarIconsActualBounds(rect: Rect) {
        _taskbarIconsActualBounds = ImmutableRect.from(rect)
    }

    private fun isEventOnTaskbarView(ev: MotionEvent) =
        _isTaskbarViewShown and _taskbarIconsActualBounds.contains(ev.rawX, ev.rawY)

    fun setIsBubbleDragging(isBubbleDragging: Boolean) {
        _isBubbleDragging = isBubbleDragging
        isTaskbarOrBubbleBarDraggingItem = _isBubbleDragging or _isTaskbarDragging
    }

    fun setIsBubbleBarViewVisible(visible: Boolean) {
        _isBubbleBarViewVisible = visible
    }

    private fun isEventOverBubbleBarView(e: MotionEvent) =
        if (!_isBubbleBarViewVisible) {
            false
        } else {
            _bubbleBarViewRect.contains(e.x, e.y)
        }

    fun setIsBubbleBarStashedHandlerViewVisible(isVisible: Boolean) {
        _isBubbleBarStashedHandlerViewVisible = isVisible
    }

    fun setStashedBubbleBarHeightPx(@Px height: Int) {
        _stashedBubbleBarHeightPx = height
    }

    fun setBubbleBarRect(rect: Rect) {
        _bubbleBarViewRect = ImmutableRect.from(rect)
    }

    fun setBubbleBarStashedHandleViewRect(rect: Rect) {
        _bubbleBarStashedHandleViewRect = ImmutableRect.from(rect)
    }

    private fun isEventOverBubbleBarStashedHandle(ev: MotionEvent): Boolean {
        if (!_isBubbleBarStashedHandlerViewVisible) {
            return false
        }
        val top = _deviceProfile.deviceProperties.heightPx - _stashedBubbleBarHeightPx
        val x = ev.rawX
        val y = ev.rawY
        return y >= top &&
            x >= _bubbleBarStashedHandleViewRect.left &&
            x <= _bubbleBarStashedHandleViewRect.right
    }

    fun isEventOverBubbleBarViews(ev: MotionEvent): Boolean {
        return isEventOverBubbleBarView(ev) || isEventOverBubbleBarStashedHandle(ev)
    }

    fun setNavbarFloatingRotationButtonsBounds(rect: Rect) {
        _navbarFloatingRotationButtonsBounds = ImmutableRect.from(rect)
    }

    private fun isEventOnNavbar(ev: MotionEvent) =
        _navbarFloatingRotationButtonsBounds.contains(ev.x, ev.y)

    // Combined event checks
    fun isEventOverAnyTaskbarItem(ev: MotionEvent) = isEventOnTaskbarView(ev) || isEventOnNavbar(ev)
}
