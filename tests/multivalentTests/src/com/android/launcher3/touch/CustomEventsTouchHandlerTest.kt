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

package com.android.launcher3.touch

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_CURSOR_DRIVEN_WORKFLOWS
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_LAUNCH
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_POPUP_MENU
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_START_DRAG
import com.android.launcher3.util.RoboApiWrapper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SmallTest
@EnableFlags(FLAG_ENABLE_CURSOR_DRIVEN_WORKFLOWS)
@RunWith(AndroidJUnit4::class)
class CustomEventsTouchHandlerTest {

    private lateinit var context: Context
    private lateinit var view: View
    private lateinit var touchHandler: CustomEventsTouchHandler
    private val mockListener: CustomActionsListener = mock()
    private var downTime: Long = SystemClock.uptimeMillis()

    @get:Rule val flags: SetFlagsRule = SetFlagsRule()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        view = View(context)
        touchHandler = CustomEventsTouchHandler(view, { false }, { false })
        touchHandler.customActionsListener = mockListener
    }

    @After
    fun tearDown() {
        RoboApiWrapper.waitForLooperSync(Looper.getMainLooper())
    }

    private fun obtainMouseEvent(
        action: Int,
        buttonState: Int,
        x: Float = 10f,
        y: Float = 10f,
        customDownTime: Long = downTime,
    ): MotionEvent {
        return MotionEvent.obtain(customDownTime, customDownTime, action, x, y, 0).apply {
            source = InputDevice.SOURCE_MOUSE
            setButtonState(buttonState)
        }
    }

    private fun obtainTouchEvent(action: Int, customDownTime: Long = downTime): MotionEvent {
        return MotionEvent.obtain(customDownTime, customDownTime, action, 10f, 10f, 0)
    }

    @Test
    fun onTouchEvent_withListener_handled() {
        assertTrue(touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun onTouchEvent_withoutListener_callsDefaultHandler() {
        val mockDefaultHandler = mock<(MotionEvent) -> Boolean>()
        whenever(mockDefaultHandler.invoke(any())).thenReturn(false)

        touchHandler = CustomEventsTouchHandler(view, mockDefaultHandler) { false }
        touchHandler.customActionsListener = null

        val event = obtainTouchEvent(MotionEvent.ACTION_DOWN)
        touchHandler.onDelegateTouchEvent(event)
        verify(mockDefaultHandler).invoke(event)
    }

    @Test
    fun onTouchEvent_shouldIgnoreTouchDownTrue_eventIgnored() {
        touchHandler = CustomEventsTouchHandler(view, { true }, { true }) // Ignore the event
        touchHandler.customActionsListener = mockListener

        val downEvent = obtainTouchEvent(MotionEvent.ACTION_DOWN)
        val result = touchHandler.onDelegateTouchEvent(downEvent)

        assertFalse(result)
        verify(mockListener, never()).performActions(any(), any())
    }

    @Test
    fun onTouchEvent_shouldIgnoreTouchDownTrue_subsequentEventsIgnored() {
        touchHandler = CustomEventsTouchHandler(view, { true }, { true }) // Ignore the event
        touchHandler.customActionsListener = mockListener

        // Simulate a complete gesture stream
        touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_DOWN))
        touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_MOVE))
        touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_UP))

        verify(mockListener, never()).performActions(any(), any())
    }

    @Test
    fun onTouchEvent_shouldIgnoreTouchDownFalse_eventProcessed() {
        touchHandler = CustomEventsTouchHandler(view, { true }, { false }) // Don't ignore the event
        touchHandler.customActionsListener = mockListener

        // Simulate a complete tap
        touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_DOWN))
        touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_UP))

        verify(mockListener).performActions(view, ACTION_LAUNCH)
    }

    @Test
    fun onTouchEvent_mouseRightClickDown_triggersPopupMenu() {
        val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY)
        touchHandler.onDelegateTouchEvent(downEvent)
        verify(mockListener).performActions(view, ACTION_POPUP_MENU)

        // Verify that subsequent move and up events don't perform additional actions after right
        // clicking.
        val moveEvent = obtainMouseEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_SECONDARY)
        touchHandler.onDelegateTouchEvent(moveEvent)

        val upEvent = obtainMouseEvent(MotionEvent.ACTION_UP, 0) // Button state is 0 on UP
        touchHandler.onDelegateTouchEvent(upEvent)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun onTouchEvent_mouseDragOutsideSlop_triggersStartDrag() {
        val downEvent =
            obtainMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, 10f, 10f)
        touchHandler.onDelegateTouchEvent(downEvent)

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val moveEvent =
            obtainMouseEvent(
                MotionEvent.ACTION_MOVE,
                MotionEvent.BUTTON_PRIMARY,
                10f + touchSlop + 1,
                10f,
            )
        touchHandler.onDelegateTouchEvent(moveEvent)
        verify(mockListener).performActions(view, ACTION_START_DRAG)
    }

    @Test
    fun onTouchEvent_mouseDragWithinSlop_doesNotTriggerStartDrag() {
        val downEvent =
            obtainMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, 10f, 10f)
        touchHandler.onDelegateTouchEvent(downEvent)

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val moveEvent =
            obtainMouseEvent(
                MotionEvent.ACTION_MOVE,
                MotionEvent.BUTTON_PRIMARY,
                10f + touchSlop - 1,
                10f,
            )
        touchHandler.onDelegateTouchEvent(moveEvent)
        verify(mockListener, never()).performActions(view, ACTION_START_DRAG)
    }

    @Test
    fun onSingleTapUp_mouse_triggersLaunch() {
        // Simulate a complete tap
        touchHandler.onDelegateTouchEvent(
            obtainMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)
        )
        touchHandler.onDelegateTouchEvent(obtainMouseEvent(MotionEvent.ACTION_UP, 0))

        verify(mockListener).performActions(view, ACTION_LAUNCH)
    }

    @Test
    fun onSingleTapUp_touch_triggersLaunch() {
        // Simulate a complete tap
        touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_DOWN))
        touchHandler.onDelegateTouchEvent(obtainTouchEvent(MotionEvent.ACTION_UP))

        verify(mockListener).performActions(view, ACTION_LAUNCH)
    }

    @Test
    fun onLongPress_touch_triggersPopupAndDrag() {
        // Start the down event at a time long enough ago to schedule a long press. Then wait for
        // the long press to trigger.
        val longPressDownTime = downTime - ViewConfiguration.getLongPressTimeout() - 100

        val downEvent =
            obtainTouchEvent(MotionEvent.ACTION_DOWN, customDownTime = longPressDownTime)
        touchHandler.onDelegateTouchEvent(downEvent)
        RoboApiWrapper.waitForLooperSync(Looper.getMainLooper())

        verify(mockListener).performActions(view, ACTION_POPUP_MENU or ACTION_START_DRAG)
    }

    @Test
    fun onLongPress_mouse_isIgnored() {
        // Start the down event at a time long enough ago to schedule a long press. Then wait for
        // the long press to trigger, to ensure no action is performed.
        val longPressDownTime = downTime - ViewConfiguration.getLongPressTimeout() - 100
        val downEvent =
            obtainMouseEvent(
                MotionEvent.ACTION_DOWN,
                MotionEvent.BUTTON_PRIMARY,
                customDownTime = longPressDownTime,
            )
        touchHandler.onDelegateTouchEvent(downEvent)
        RoboApiWrapper.waitForLooperSync(Looper.getMainLooper())

        verify(mockListener, never()).performActions(any(), any())
    }

    @Test
    fun onLongPress_mouse_withLongPressForDragEnabled_triggersDrag() {
        touchHandler.enableMouseLongPressForDrag = true

        // Start the down event at a time long enough ago to schedule a long press. Then wait for
        // the long press to trigger.
        val longPressDownTime = downTime - ViewConfiguration.getLongPressTimeout() - 100
        val downEvent =
            obtainMouseEvent(
                MotionEvent.ACTION_DOWN,
                MotionEvent.BUTTON_PRIMARY,
                customDownTime = longPressDownTime,
            )
        touchHandler.onDelegateTouchEvent(downEvent)
        RoboApiWrapper.waitForLooperSync(Looper.getMainLooper())

        verify(mockListener).performActions(view, ACTION_START_DRAG)
    }

    @Test
    fun onTouchEvent_mouseDragOutsideSlop_withLongPressForDragEnabled_doesNotTriggerStartDrag() {
        touchHandler.enableMouseLongPressForDrag = true

        val downEvent =
            obtainMouseEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, 10f, 10f)
        touchHandler.onDelegateTouchEvent(downEvent)

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val moveEvent =
            obtainMouseEvent(
                MotionEvent.ACTION_MOVE,
                MotionEvent.BUTTON_PRIMARY,
                10f + touchSlop + 1,
                10f,
            )
        touchHandler.onDelegateTouchEvent(moveEvent)
        verify(mockListener, never()).performActions(view, ACTION_START_DRAG)
    }
}
