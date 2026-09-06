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

package com.android.launcher3.statehandlers

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Flags.FLAG_ENABLE_TASKBAR_UI_THREAD
import com.android.launcher3.LauncherState
import com.android.launcher3.statehandlers.DesktopVisibilityController.DesktopVisibilityListener
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.binder.OneWayBinderList
import com.android.wm.shell.desktopmode.DisplayDeskState
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests the behavior of [DesktopVisibilityController] in regards to multiple desktops and multiple
 * displays.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopVisibilityControllerTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = mock<Context>()
    private val systemUiProxy = mock<SystemUiProxy>()
    private val desktopTaskListeners = mock<OneWayBinderList<IDesktopTaskListener>>()

    private val lifeCycleTracker = mock<DaggerSingletonTracker>()
    private lateinit var desktopVisibilityController: DesktopVisibilityController
    private val listenerCaptor = argumentCaptor<IDesktopTaskListener>()

    @Before
    fun setUp() {
        whenever(context.resources).thenReturn(mock())
        doReturn(desktopTaskListeners).whenever(systemUiProxy).desktopTaskListeners

        desktopVisibilityController =
            DesktopVisibilityController(context, systemUiProxy, lifeCycleTracker)
        verify(desktopTaskListeners).register(listenerCaptor.capture())
    }

    @Test
    fun noCrashWhenCheckingNonExistentDisplay() {
        assertThat(desktopVisibilityController.isInDesktopMode(displayId = 500)).isFalse()
        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(displayId = 300))
            .isFalse()
    }

    @Test
    fun taskListenerConnects() {
        connectTaskListener()
    }

    @Test
    fun getActiveDeskIdIsAreCorrect() {
        connectTaskListener()

        assertThat(desktopVisibilityController.getActiveDeskId(FIRST_DISPLAY_ID))
            .isEqualTo(FIRST_DISPLAY_ACTIVE_DESK_ID)
        assertThat(desktopVisibilityController.getActiveDeskId(SECOND_DISPLAY_ID))
            .isEqualTo(SECOND_DISPLAY_ACTIVE_DESK_ID)
    }

    @Test
    fun isInDesktopModeIsCorrect() {
        connectTaskListener()

        assertThat(desktopVisibilityController.isInDesktopMode(FIRST_DISPLAY_ID)).isTrue()
        assertThat(desktopVisibilityController.isInDesktopMode(SECOND_DISPLAY_ID)).isTrue()
        assertThat(desktopVisibilityController.isInDesktopMode(NON_DESKTOP_DISPLAY_ID)).isFalse()
    }

    @Test
    fun launcherStateChangeUpdatesState() {
        connectTaskListener()

        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(FIRST_DISPLAY_ID))
            .isTrue()

        desktopVisibilityController.onLauncherStateChanged(FIRST_DISPLAY_ID, LauncherState.OVERVIEW)

        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(FIRST_DISPLAY_ID))
            .isFalse()

        desktopVisibilityController.onLauncherStateChanged(
            FIRST_DISPLAY_ID,
            LauncherState.BACKGROUND_APP,
        )

        assertThat(desktopVisibilityController.isInDesktopModeAndNotInOverview(FIRST_DISPLAY_ID))
            .isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_UI_THREAD)
    fun concurrentAccess_whenUiThreadEnabled_doesNotCrash() {
        val listener = listenerCaptor.lastValue!!
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(10)
        val errors = mutableListOf<Throwable>()

        val firstDisplay =
            DisplayDeskState().apply {
                displayId = FIRST_DISPLAY_ID
                activeDeskId = FIRST_DISPLAY_ACTIVE_DESK_ID
                deskIds = FIRST_DISPLAY_DESK_IDS
            }
        val secondDisplay =
            DisplayDeskState().apply {
                displayId = SECOND_DISPLAY_ID
                activeDeskId = SECOND_DISPLAY_ACTIVE_DESK_ID
                deskIds = SECOND_DISPLAY_DESK_IDS
            }
        val states = arrayOf(firstDisplay, secondDisplay)
        val emptyStates = arrayOf<DisplayDeskState>()

        // 1 writer thread, which posts to the main thread.
        executor.submit {
            try {
                repeat(100) {
                    listener.onListenerConnected(states, true)
                    listener.onListenerConnected(emptyStates, false)
                }
            } catch (t: Throwable) {
                synchronized(errors) { errors.add(t) }
            } finally {
                latch.countDown()
            }
        }

        // 9 reader threads
        repeat(9) {
            executor.submit {
                try {
                    repeat(100) {
                        desktopVisibilityController.getActiveDeskId(FIRST_DISPLAY_ID)
                        desktopVisibilityController.isInDesktopMode(SECOND_DISPLAY_ID)
                        desktopVisibilityController.getActiveDeskId(NON_DESKTOP_DISPLAY_ID)
                    }
                } catch (t: Throwable) {
                    synchronized(errors) { errors.add(t) }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        // Wait for main thread execution to finish
        getInstrumentation().waitForIdleSync()

        assertThat(errors).isEmpty()
    }

    fun taskbarCornerRoundingListener_isNotifiedWithCorrectDisplayId() {
        // Arrange
        val taskbarListener = mock<DesktopVisibilityListener>()
        desktopVisibilityController.registerDesktopVisibilityListener(taskbarListener)
        val desktopTaskListener = listenerCaptor.lastValue!!
        val displayId1 = 10
        val displayId2 = 20

        // Act
        desktopTaskListener.onTaskbarCornerRoundingUpdate(true, displayId1)
        desktopTaskListener.onTaskbarCornerRoundingUpdate(false, displayId2)
        getInstrumentation().waitForIdleSync()

        // Assert
        verify(taskbarListener).onTaskbarCornerRoundingUpdate(true, displayId1)
        verify(taskbarListener).onTaskbarCornerRoundingUpdate(false, displayId2)
    }

    @Test
    fun taskbarCornerRoundingListener_isNotCalledAfterUnregister() {
        // Arrange
        val taskbarListener = mock<DesktopVisibilityListener>()
        desktopVisibilityController.registerDesktopVisibilityListener(taskbarListener)
        desktopVisibilityController.unregisterDesktopVisibilityListener(taskbarListener)
        val desktopTaskListener = listenerCaptor.lastValue!!
        val displayId = 10

        // Act
        desktopTaskListener.onTaskbarCornerRoundingUpdate(true, displayId)
        getInstrumentation().waitForIdleSync()

        // Assert
        verify(taskbarListener, never()).onTaskbarCornerRoundingUpdate(any(), any())
    }

    @Test
    fun onTaskAppearingInDeskWithOverviewShowing_notifiesListener() {
        // Arrange: Register a mock listener to observe notifications.
        val desktopVisibilityListener = mock<DesktopVisibilityListener>()
        desktopVisibilityController.registerDesktopVisibilityListener(desktopVisibilityListener)
        val desktopTaskListener = listenerCaptor.lastValue!!
        val taskId = 123
        val displayId = 456
        val deskId = 789

        // Act: Trigger the callback from the shell listener.
        desktopTaskListener.onTaskAppearingInDeskWithOverviewShowing(taskId, displayId, deskId)
        getInstrumentation().waitForIdleSync()

        // Assert: Verify the listener was called with the correct parameters.
        verify(desktopVisibilityListener)
            .onTaskAppearingInDeskWithOverviewShowing(taskId, displayId, deskId)
    }

    private fun connectTaskListener() {
        val firstDisplay =
            DisplayDeskState().apply {
                displayId = FIRST_DISPLAY_ID
                activeDeskId = FIRST_DISPLAY_ACTIVE_DESK_ID
                deskIds = FIRST_DISPLAY_DESK_IDS
            }
        val secondDisplay =
            DisplayDeskState().apply {
                displayId = SECOND_DISPLAY_ID
                activeDeskId = SECOND_DISPLAY_ACTIVE_DESK_ID
                deskIds = SECOND_DISPLAY_DESK_IDS
            }
        val states = arrayOf(firstDisplay, secondDisplay)
        val listener = listenerCaptor.lastValue
        assertThat(listener).isNotNull()
        listener!!.onListenerConnected(states, /* canCreateDesks= */ true)
        getInstrumentation().waitForIdleSync()
        assertThat(desktopVisibilityController.canCreateDesks.value).isTrue()
    }

    companion object {
        private const val FIRST_DISPLAY_ID = 0
        private const val FIRST_DISPLAY_ACTIVE_DESK_ID = 0
        private val FIRST_DISPLAY_DESK_IDS = intArrayOf(0, 1, 2, 3, 4)
        private const val SECOND_DISPLAY_ID = 1
        private const val SECOND_DISPLAY_ACTIVE_DESK_ID = 5
        private val SECOND_DISPLAY_DESK_IDS = intArrayOf(5, 6)
        private const val NON_DESKTOP_DISPLAY_ID = 2
    }
}
