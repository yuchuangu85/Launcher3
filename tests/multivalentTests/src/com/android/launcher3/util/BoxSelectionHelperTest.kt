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
package com.android.launcher3.util

import android.app.Instrumentation
import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.BoxSelectionHelper
import com.android.launcher3.FakeWorkspaceSelectionManager
import com.android.launcher3.Launcher
import com.android.launcher3.WorkspaceSelectionManager
import com.android.launcher3.dagger.ActivityContextComponent
import com.android.launcher3.dragndrop.DragLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class BoxSelectionHelperTest {

    private lateinit var targetView: View
    private lateinit var workspaceSelectionManager: WorkspaceSelectionManager
    private lateinit var dragLayer: DragLayer
    private lateinit var helper: BoxSelectionHelper
    private lateinit var instrumentation: Instrumentation
    private lateinit var context: Context
    private lateinit var activityContext: Launcher

    private val targetViewWidth = 1080
    private val targetViewHeight = 1920

    // Test state
    private var capturedRect: Rect? = null
    private var isAppending: Boolean? = null

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext

        // All setup needs to run on the UI thread because Views are created.
        instrumentation.runOnMainSync {
            // Prepare a looper if the test thread doesn't have one.
            if (Looper.myLooper() == null) {
                Looper.prepare()
            }
            MockitoAnnotations.openMocks(this)

            activityContext = mock()
            val activityComponent: ActivityContextComponent = mock()
            dragLayer = mock()

            // Faking WorkspaceSelectionManager
            workspaceSelectionManager =
                FakeWorkspaceSelectionManager({ isAppending = it }, { capturedRect = it })

            // Reset state before each test
            capturedRect = Rect()
            isAppending = false

            // A real View is used to avoid mocking final methods and constructors.
            targetView = View(context)
            targetView.layout(0, 0, targetViewWidth, targetViewHeight)

            // Stubbing the context and its properties to allow View creation
            whenever(activityContext.applicationContext).thenReturn(context.applicationContext)
            whenever(activityContext.packageName).thenReturn(context.packageName)
            whenever(activityContext.applicationInfo).thenReturn(context.applicationInfo)
            whenever(activityContext.resources).thenReturn(context.resources)

            // Stubbing Launcher/ActivityContext specific methods
            whenever(activityContext.activityComponent).thenReturn(activityComponent)
            whenever(activityComponent.workspaceSelectionManager)
                .thenReturn(workspaceSelectionManager)
            whenever(activityContext.getDragLayer()).thenReturn(dragLayer)
            whenever(dragLayer.context).thenReturn(activityContext)

            helper = BoxSelectionHelper(activityContext, targetView)
        }
    }

    private fun obtainMouseEvent(action: Int, x: Float, y: Float, metaState: Int = 0): MotionEvent {
        val event = MotionEvent.obtain(0, 0, action, x, y, metaState)
        event.source = InputDevice.SOURCE_MOUSE
        return event
    }

    @Test
    fun onTouchEvent_fromNonMouseSource_shouldNotStartSelection() {
        instrumentation.runOnMainSync {
            val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(!helper.onTouchEvent(downEvent))
            assertEquals(Rect(), capturedRect)
        }
    }

    @Test
    fun onTouchEvent_secondaryButtonPressed_shouldNotStartSelection() {
        instrumentation.runOnMainSync {
            val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, 10f, 10f)
            // BUTTON_SECONDARY is not public, so we use its value 2.
            downEvent.buttonState = MotionEvent.BUTTON_SECONDARY
            assertTrue(!helper.onTouchEvent(downEvent))
            assertEquals(Rect(), capturedRect)
        }
    }

    @Test
    fun onTouchEvent_shouldStartAndEndSelection() {
        instrumentation.runOnMainSync {
            val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f)
            assertTrue(helper.onTouchEvent(downEvent))
            assertNotNull(capturedRect)

            val moveEvent = obtainMouseEvent(MotionEvent.ACTION_MOVE, 100f, 120f)
            assertTrue(helper.onTouchEvent(moveEvent))

            val upEvent = obtainMouseEvent(MotionEvent.ACTION_UP, 100f, 120f)
            assertTrue(helper.onTouchEvent(upEvent))

            val expectedRect = Rect(10, 20, 100, 120)
            assertEquals(expectedRect, capturedRect)
        }
    }

    @Test
    fun onTouchEvent_shiftKeyPressed_shouldStartAppendingSelection() {
        instrumentation.runOnMainSync {
            val downEvent =
                obtainMouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f, KeyEvent.META_SHIFT_ON)
            helper.onTouchEvent(downEvent)
            assertEquals(true, isAppending)
        }
    }

    @Test
    fun onTouchEvent_reverseDrag_shouldCreateCorrectRect() {
        instrumentation.runOnMainSync {
            val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, 100f, 120f)
            helper.onTouchEvent(downEvent)

            val moveEvent = obtainMouseEvent(MotionEvent.ACTION_MOVE, 10f, 20f)
            helper.onTouchEvent(moveEvent)

            val expectedRect = Rect(10, 20, 100, 120)
            assertEquals(expectedRect, capturedRect)
        }
    }

    @Test
    fun onTouchEvent_dragOutOfBounds_shouldClipSelectionBox() {
        instrumentation.runOnMainSync {
            val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, 50f, 50f)
            helper.onTouchEvent(downEvent)

            // Drag out of top-left bounds
            helper.onTouchEvent(obtainMouseEvent(MotionEvent.ACTION_MOVE, -50f, -50f))
            assertEquals(Rect(0, 0, 50, 50), capturedRect)

            // Drag out of bottom-right bounds
            val rightBound = targetViewWidth.toFloat()
            val bottomBound = targetViewHeight.toFloat()
            helper.onTouchEvent(
                obtainMouseEvent(MotionEvent.ACTION_MOVE, rightBound + 50f, bottomBound + 50f)
            )
            assertEquals(Rect(50, 50, rightBound.toInt(), bottomBound.toInt()), capturedRect)
        }
    }

    @Test
    fun onTouchEvent_actionCancel_shouldEndSelectionAndRemoveView() {
        instrumentation.runOnMainSync {
            val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f)
            helper.onTouchEvent(downEvent)
            val viewCaptor = argumentCaptor<View>()
            verify(dragLayer).addView(viewCaptor.capture())

            val cancelEvent = obtainMouseEvent(MotionEvent.ACTION_CANCEL, 100f, 120f)
            helper.onTouchEvent(cancelEvent)

            verify(dragLayer).removeView(eq(viewCaptor.firstValue))
            // On cancel, the rect should be cleared
            assertEquals(Rect(), capturedRect)
        }
    }

    @Test
    fun onTouchEvent_selectionAddsAndRemovesView() {
        instrumentation.runOnMainSync {
            val downEvent = obtainMouseEvent(MotionEvent.ACTION_DOWN, 10f, 20f)
            helper.onTouchEvent(downEvent)
            val viewCaptor = argumentCaptor<View>()
            verify(dragLayer).addView(viewCaptor.capture())
            assertNotNull(viewCaptor.firstValue)

            val upEvent = obtainMouseEvent(MotionEvent.ACTION_UP, 100f, 120f)
            helper.onTouchEvent(upEvent)
            verify(dragLayer).removeView(eq(viewCaptor.firstValue))
        }
    }
}
