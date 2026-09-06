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

package com.android.launcher3.views

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.dragndrop.DragController.SystemDragHandler
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.ReflectionHelpers
import com.android.launcher3.util.TestActivityContext
import com.android.launcher3.util.TouchController
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/** Tests for {@link BaseDragLayer}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class BaseDragLayerTest {

    @get:Rule(order = 0) val flags: SetFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val mockito = MockitoJUnit.rule()
    @get:Rule(order = 2) val context = TestActivityContext()

    @Mock private lateinit var mockDragEvent: DragEvent
    @Mock private lateinit var systemDragHandler: SystemDragHandler
    @Mock private lateinit var touchController1: TouchController
    @Mock private lateinit var touchController2: TouchController
    @Mock private lateinit var mockTouchListener: View.OnTouchListener

    private lateinit var dragLayer: TestDragLayer

    private class TestDragLayer(context: TestActivityContext) :
        BaseDragLayer<TestActivityContext>(context, null, 2) {
        fun setControllers(vararg controllers: TouchController) {
            mControllers = controllers
        }

        // Expose for testing child view dispatch
        override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
            return super.dispatchTouchEvent(ev)
        }
    }

    @Before
    fun setUp() {
        context.getDragController().addSystemDragHandler(systemDragHandler)

        // Initialize drag event.
        // NOTE: Reflection is necessary because `ViewGroup` inspects the `DragEvent.mAction` field
        // during event dispatching rather than using the mockable `DragEvent.getAction()` method.
        ReflectionHelpers.setField(mockDragEvent, "mAction", DragEvent.ACTION_DRAG_STARTED)
        doReturn(DragEvent.ACTION_DRAG_STARTED).whenever(mockDragEvent).action

        dragLayer = TestDragLayer(context)
        dragLayer.setControllers(touchController1, touchController2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testDispatchingDragEventsDelegatesToDragControllerIfPresent() {
        dragLayer.dispatchDragEvent(mockDragEvent)
        verify(systemDragHandler).onDrag(mockDragEvent)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_SYSTEM_DRAG)
    fun testDispatchingDragEventsWontDelegateToDragControllerWhenFlagIsDisabled() {
        dragLayer.dispatchDragEvent(mockDragEvent)
        verifyNoInteractions(systemDragHandler)
    }

    @Test
    fun onInterceptTouchEvent_noControllerHandles_returnsFalse() {
        // Arrange: No controller wants to intercept the event.
        whenever(touchController1.onControllerInterceptTouchEvent(any())).thenReturn(false)
        whenever(touchController2.onControllerInterceptTouchEvent(any())).thenReturn(false)
        val ev = obtainMotionEvent(MotionEvent.ACTION_DOWN)

        // Act: Intercept the touch event.
        val result = dragLayer.onInterceptTouchEvent(ev)

        // Assert: The event is not intercepted.
        assertThat(result).isFalse()
    }

    @Test
    fun onInterceptTouchEvent_controllerHandles_returnsTrue() {
        // Arrange: The second controller wants to intercept the event.
        whenever(touchController1.onControllerInterceptTouchEvent(any())).thenReturn(false)
        whenever(touchController2.onControllerInterceptTouchEvent(any())).thenReturn(true)
        val ev = obtainMotionEvent(MotionEvent.ACTION_DOWN)

        // Act: Intercept the touch event.
        val result = dragLayer.onInterceptTouchEvent(ev)

        // Assert: The event is intercepted.
        assertThat(result).isTrue()
    }

    @Test
    fun onTouchEvent_withActiveController_dispatchesEventToController() {
        // Arrange
        setupActiveController(touchController2)
        val moveEvent = obtainMotionEvent(MotionEvent.ACTION_MOVE, 1f, 1f)
        whenever(touchController2.onControllerTouchEvent(moveEvent)).thenReturn(true)

        // Act
        val result = dragLayer.onTouchEvent(moveEvent)

        // Assert
        assertThat(result).isTrue()
        verify(touchController2).onControllerTouchEvent(moveEvent)
        verify(touchController1, never()).onControllerTouchEvent(any())
    }

    @Test
    fun proxyTouchEvent_allowViewDispatch_dispatchesToChild() {
        // Arrange
        val child = View(context)
        child.setOnTouchListener(mockTouchListener)
        dragLayer.addView(child)
        // Make sure child is laid out and can receive touch events
        dragLayer.layout(0, 0, 100, 100)
        child.layout(0, 0, 100, 100)

        whenever(mockTouchListener.onTouch(any(), any())).thenReturn(true)
        val ev = obtainMotionEvent(MotionEvent.ACTION_DOWN, 50f, 50f)

        // Act: Proxy the event with view dispatch allowed.
        val result = dragLayer.proxyTouchEvent(ev, true)

        // Assert: The event was dispatched to the child and handled.
        assertThat(result).isTrue()
        verify(mockTouchListener).onTouch(child, ev)
    }

    @Test
    fun proxyTouchEvent_noViewDispatch_findsProxyController() {
        // Arrange: The second controller wants to intercept the event.
        whenever(touchController1.onControllerInterceptTouchEvent(any())).thenReturn(false)
        whenever(touchController2.onControllerInterceptTouchEvent(any())).thenReturn(true)
        val ev = obtainMotionEvent(MotionEvent.ACTION_DOWN)

        // Act: Proxy the event without allowing view dispatch.
        val result = dragLayer.proxyTouchEvent(ev, false)

        // Assert: A proxy controller is found and the event is handled.
        assertThat(result).isTrue()
    }

    @Test
    fun proxyTouchEvent_withProxyController_dispatchesEventToController() {
        // Arrange
        setupProxyController(touchController2)
        val moveEvent = obtainMotionEvent(MotionEvent.ACTION_MOVE, 1f, 1f)
        whenever(touchController2.onControllerTouchEvent(moveEvent)).thenReturn(true)

        // Act
        val result = dragLayer.proxyTouchEvent(moveEvent, false)

        // Assert
        assertThat(result).isTrue()
        verify(touchController2).onControllerTouchEvent(moveEvent)
        verify(touchController1, never()).onControllerTouchEvent(any())
    }

    @Test
    fun proxyTouchEvent_upEvent_clearsProxyController() {
        // Arrange: Establish a proxy controller.
        setupProxyController(touchController2)

        // Act: Proxy an UP event, which should clear the controller.
        val upEvent = obtainMotionEvent(MotionEvent.ACTION_UP)
        dragLayer.proxyTouchEvent(upEvent, false)

        // Assert: A subsequent event causes a search for a new controller, proving the
        // old one was cleared.
        reset(touchController1, touchController2)
        val newDownEvent = obtainMotionEvent(MotionEvent.ACTION_DOWN)
        dragLayer.proxyTouchEvent(newDownEvent, false)
        verify(touchController1).onControllerInterceptTouchEvent(newDownEvent)
        verify(touchController2).onControllerInterceptTouchEvent(newDownEvent)
    }

    private fun obtainMotionEvent(action: Int, x: Float = 0f, y: Float = 0f): MotionEvent {
        return MotionEvent.obtain(0, 0, action, x, y, 0)
    }

    private fun setupActiveController(controller: TouchController) {
        // Ensure only the desired controller intercepts the event.
        whenever(touchController1.onControllerInterceptTouchEvent(any())).thenReturn(false)
        whenever(touchController2.onControllerInterceptTouchEvent(any())).thenReturn(false)
        whenever(controller.onControllerInterceptTouchEvent(any())).thenReturn(true)

        val downEvent = obtainMotionEvent(MotionEvent.ACTION_DOWN)
        dragLayer.onInterceptTouchEvent(downEvent)
    }

    private fun setupProxyController(controller: TouchController) {
        // Ensure only the desired controller intercepts the event.
        whenever(touchController1.onControllerInterceptTouchEvent(any())).thenReturn(false)
        whenever(touchController2.onControllerInterceptTouchEvent(any())).thenReturn(false)
        whenever(controller.onControllerInterceptTouchEvent(any())).thenReturn(true)

        val downEvent = obtainMotionEvent(MotionEvent.ACTION_DOWN)
        dragLayer.proxyTouchEvent(downEvent, false)
    }
}
