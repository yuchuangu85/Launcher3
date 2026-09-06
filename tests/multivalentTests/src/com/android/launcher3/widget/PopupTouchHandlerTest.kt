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

package com.android.launcher3.widget

import android.graphics.Rect
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.PopupTouchHandler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for {@link PopupTouchHandler}. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupTouchHandlerTest {

    private val popup: AbstractFloatingView = mock()
    private val mPopupTouchHandler = PopupTouchHandler()

    @Test
    fun isEventOverOpenPopup_noPopup_returnsFalse() {
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, EVENT_X, EVENT_Y, 0)
        assertFalse(mPopupTouchHandler.isEventOverOpenPopup(null, ev))
    }

    @Test
    fun isEventOverOpenPopup_eventInsidePopup_returnsTrue() {
        Mockito.doAnswer { invocation ->
                val rect = invocation.arguments[0] as Rect
                rect.set(0, 0, POPUP_WIDTH, POPUP_HEIGHT)
                null
            }
            .whenever(popup)
            .getHitRect(Mockito.any(Rect::class.java))

        val ev =
            MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, EVENT_INSIDE_X, EVENT_INSIDE_Y, 0)
        assertTrue(mPopupTouchHandler.isEventOverOpenPopup(popup, ev))
    }

    @Test
    fun isEventOverOpenPopup_eventOutsidePopup_returnsFalse() {
        Mockito.doAnswer { invocation ->
                val rect = invocation.arguments[0] as Rect
                rect.set(0, 0, POPUP_WIDTH, POPUP_HEIGHT)
                null
            }
            .whenever(popup)
            .getHitRect(Mockito.any(Rect::class.java))

        val ev =
            MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, EVENT_OUTSIDE_X, EVENT_OUTSIDE_Y, 0)
        assertFalse(mPopupTouchHandler.isEventOverOpenPopup(popup, ev))
    }

    @Test
    fun handleTouchEvent_noPopup_returnsFalse() {
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, EVENT_X, EVENT_Y, 0)
        assertFalse(mPopupTouchHandler.handleTouchEvent(null, ev))
    }

    @Test
    fun handleTouchEvent_eventInsidePopup_returnsFalse() {
        Mockito.doAnswer { invocation ->
                val rect = invocation.arguments[0] as Rect
                rect.set(0, 0, POPUP_WIDTH, POPUP_HEIGHT)
                null
            }
            .whenever(popup)
            .getHitRect(Mockito.any(Rect::class.java))

        val ev =
            MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, EVENT_INSIDE_X, EVENT_INSIDE_Y, 0)
        assertFalse(mPopupTouchHandler.handleTouchEvent(popup, ev))
    }

    @Test
    fun handleTouchEvent_eventOutsidePopup_closesPopupAndReturnsTrue() {
        Mockito.doAnswer { invocation ->
                val rect = invocation.arguments[0] as Rect
                rect.set(0, 0, POPUP_WIDTH, POPUP_HEIGHT)
                null
            }
            .whenever(popup)
            .getHitRect(Mockito.any(Rect::class.java))

        val ev =
            MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, EVENT_OUTSIDE_X, EVENT_OUTSIDE_Y, 0)
        assertTrue(mPopupTouchHandler.handleTouchEvent(popup, ev))
        verify(popup).close(true)
    }

    companion object {
        private const val EVENT_X = 10f
        private const val EVENT_Y = 10f
        private const val POPUP_WIDTH = 100
        private const val POPUP_HEIGHT = 100
        private const val EVENT_INSIDE_X = 50f
        private const val EVENT_INSIDE_Y = 50f
        private const val EVENT_OUTSIDE_X = 150f
        private const val EVENT_OUTSIDE_Y = 150f
    }
}
