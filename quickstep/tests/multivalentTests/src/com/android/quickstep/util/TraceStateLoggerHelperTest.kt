/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.util

import android.content.Context
import android.view.Display.DEFAULT_DISPLAY
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.app.tracing.TraceStateLogger
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StateManager.StateListener
import com.android.launcher3.statemanager.StatefulContainer
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.util.TraceStateLoggerHelper.Companion.DESTROYED_STATE
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TraceStateLoggerHelperTest {
    private val traceStateLogger: TraceStateLogger = mock()
    private val stateManager: StateManager<TestState, StatefulContainer<TestState>> = mock()
    private val lifecycle: Lifecycle = mock()
    private val context: Context =
        spy(getInstrumentation().targetContext).apply {
            whenever(displayId).thenReturn(DEFAULT_DISPLAY)
        }
    private val statefulContainer: StatefulContainer<TestState> = mock {
        on { it.asContext() } doReturn context
        on { it.stateManager } doReturn stateManager
        on { it.lifecycle } doReturn lifecycle
    }

    @Test
    fun onStateTransitionStart_loggerLogs() {
        val systemUnderTest = createTraceStateLoggerHelper()
        val listenerCaptor = argumentCaptor<StateListener<TestState>>()
        systemUnderTest.startTraceStateLogger()
        getInstrumentation().waitForIdleSync()

        verify(stateManager).addStateListener(listenerCaptor.capture())
        val capturedListener = listenerCaptor.firstValue

        capturedListener.onStateTransitionStart(TestState())

        verify(traceStateLogger).log(TEST_STATE_NAME)
    }

    @Test
    fun onContainerDestroy_loggerRemoves() {
        val systemUnderTest = createTraceStateLoggerHelper()
        val listenerCaptor = argumentCaptor<StateListener<TestState>>()
        val observerCaptor = argumentCaptor<DefaultLifecycleObserver>()
        systemUnderTest.startTraceStateLogger()
        getInstrumentation().waitForIdleSync()

        verify(stateManager).addStateListener(listenerCaptor.capture())
        verify(lifecycle).addObserver(observerCaptor.capture())

        observerCaptor.firstValue.onDestroy(mock())

        verify(stateManager).removeStateListener(listenerCaptor.firstValue)
    }

    @Test
    fun onContainerDestroy_logsDestroyed() {
        whenever(context.displayId).thenReturn(EXTERNAL_DISPLAY)
        val systemUnderTest = createTraceStateLoggerHelper()
        val listenerCaptor = argumentCaptor<StateListener<TestState>>()
        val observerCaptor = argumentCaptor<DefaultLifecycleObserver>()
        systemUnderTest.startTraceStateLogger()
        getInstrumentation().waitForIdleSync()

        verify(stateManager).addStateListener(listenerCaptor.capture())
        verify(lifecycle).addObserver(observerCaptor.capture())

        observerCaptor.firstValue.onDestroy(mock())

        verify(traceStateLogger).log(DESTROYED_STATE)
    }

    private fun createTraceStateLoggerHelper() =
        TraceStateLoggerHelper(statefulContainer, traceStateLogger)

    private class TestState(private val name: String = TEST_STATE_NAME) : BaseState<TestState> {
        override fun toString() = name

        override fun getTransitionDuration(context: ActivityContext?, isToState: Boolean): Int = 1

        override fun hasFlag(flagMask: Int): Boolean = false

        override fun getDepth(context: ActivityContext?): Float = 1f

        override fun getHistoryForState(previousState: TestState?): TestState = previousState!!
    }

    private companion object {
        const val TEST_STATE_NAME = "Test State"

        const val EXTERNAL_DISPLAY = 5
    }
}
