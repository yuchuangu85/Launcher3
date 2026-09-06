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

package com.android.launcher3.views

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SnackbarIntegrationTest {
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private lateinit var launcher: Launcher
    private val dismissTimer = FakeSnackbarDismissTimer()

    @Before
    fun setUp() {
        launcherActivity.initializeActivity()
        launcherActivity.waitAndGet("Get launcher") {
            launcher = it
            true
        }

        // Use a test timer to make tests deterministic and fast.
        Snackbar.setSnackbarTestDismissTimer(dismissTimer)
    }

    @After
    fun tearDown() {
        // Clear test timer.
        Snackbar.setSnackbarTestDismissTimer(null)
    }

    @Test
    fun testSnackbarHover_pausesDismiss() {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Snackbar.show(launcher, R.string.item_removed, R.string.undo, { latch.countDown() }, {})
        }
        val snackbar =
            launcherActivity.waitAndGet("Snackbar not shown") {
                AbstractFloatingView.getOpenView<AbstractFloatingView>(
                    it,
                    AbstractFloatingView.TYPE_SNACKBAR,
                ) as? Snackbar
            }
        assertNotNull(snackbar)

        // Verify timeout was set.
        val expectedTimeout = Snackbar.getDismissTimeout(launcher)
        assertTrue("Dismiss timer not scheduled", dismissTimer.isScheduled)
        assertThat(dismissTimer.lastTimeout).isEqualTo(expectedTimeout)

        // Hover on snackbar. Timer should be cancelled.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            sendHoverEvent(snackbar!!, MotionEvent.ACTION_HOVER_ENTER)
        }
        assertFalse("Dismiss timer should be cancelled while hovered", dismissTimer.isScheduled)

        // Hover exit from snackbar. Timer should be re-scheduled.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            sendHoverEvent(snackbar!!, MotionEvent.ACTION_HOVER_EXIT)
        }
        assertTrue(
            "Dismiss timer should be re-scheduled after hover exit",
            dismissTimer.isScheduled,
        )

        // Trigger the timer. Snackbar should be dismissed.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { dismissTimer.trigger() }
        assertTrue(
            "Snackbar not dismissed after triggering timer",
            latch.await(2, TimeUnit.SECONDS),
        )
        assertNull(
            "Snackbar still visible",
            AbstractFloatingView.getOpenView(launcher, AbstractFloatingView.TYPE_SNACKBAR),
        )
    }

    @Test
    fun testSnackbarAndActionHover_pausesDismiss() {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Snackbar.show(launcher, R.string.item_removed, R.string.undo, { latch.countDown() }, {})
        }
        val snackbar =
            launcherActivity.waitAndGet("Snackbar not shown") {
                AbstractFloatingView.getOpenView<AbstractFloatingView>(
                    it,
                    AbstractFloatingView.TYPE_SNACKBAR,
                ) as? Snackbar
            }
        assertNotNull(snackbar)
        val actionView = snackbar.findViewById<View>(R.id.action)
        assertNotNull(actionView)

        // Hover on snackbar. Timer should be cancelled.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            sendHoverEvent(snackbar!!, MotionEvent.ACTION_HOVER_ENTER)
        }
        assertFalse("Dismiss timer should be cancelled while hovered", dismissTimer.isScheduled)

        // Hover on action view (implies hover exit on snackbar).
        // Hover enter on action view first, then hover exit on snackbar.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            sendHoverEvent(actionView!!, MotionEvent.ACTION_HOVER_ENTER)
            sendHoverEvent(snackbar!!, MotionEvent.ACTION_HOVER_EXIT)
        }
        assertFalse(
            "Dismiss timer should still be cancelled (action hovered)",
            dismissTimer.isScheduled,
        )

        // Hover exit action view (implies hover enter on snackbar).
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            sendHoverEvent(actionView!!, MotionEvent.ACTION_HOVER_EXIT)
            sendHoverEvent(snackbar!!, MotionEvent.ACTION_HOVER_ENTER)
        }
        assertFalse(
            "Dismiss timer should still be cancelled (snackbar hovered)",
            dismissTimer.isScheduled,
        )

        // Hover exit from snackbar. Timer should be re-scheduled.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            sendHoverEvent(snackbar!!, MotionEvent.ACTION_HOVER_EXIT)
        }
        assertTrue(
            "Dismiss timer should be re-scheduled after all hovers exit",
            dismissTimer.isScheduled,
        )

        // Trigger the timer. Snackbar should be dismissed.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { dismissTimer.trigger() }
        assertTrue(
            "Snackbar not dismissed after triggering timer",
            latch.await(2, TimeUnit.SECONDS),
        )
        assertNull(
            "Snackbar still visible",
            AbstractFloatingView.getOpenView(launcher, AbstractFloatingView.TYPE_SNACKBAR),
        )
    }

    private fun sendHoverEvent(view: View, action: Int) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = location[0] + view.width / 2f
        val y = location[1] + view.height / 2f

        val downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, downTime, action, x, y, 0)
        view.dispatchGenericMotionEvent(event)
        event.recycle()
    }

    private class FakeSnackbarDismissTimer : Snackbar.SnackbarDismissTimer {
        var lastRunnable: Runnable? = null
        var lastTimeout: Long = 0
        val isScheduled: Boolean
            get() = lastRunnable != null

        override fun post(snackbar: View, runnable: Runnable, timeout: Long) {
            lastRunnable = runnable
            lastTimeout = timeout
        }

        override fun cancel(snackbar: View, runnable: Runnable) {
            if (lastRunnable === runnable) {
                lastRunnable = null
            }
        }

        fun trigger() {
            val r = lastRunnable
            lastRunnable = null
            r?.run()
        }
    }
}
