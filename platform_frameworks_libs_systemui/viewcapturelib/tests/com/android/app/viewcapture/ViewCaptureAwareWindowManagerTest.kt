/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.app.viewcapture

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
import android.window.WindowContext
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidTestingRunner::class)
@SmallTest
class ViewCaptureAwareWindowManagerTest {
    private val mContext: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var mViewCaptureAwareWindowManager: ViewCaptureAwareWindowManager

    private val activityIntent = Intent(mContext, TestActivity::class.java)

    @get:Rule val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)

    @get:Rule val mSetFlagsRule: SetFlagsRule = SetFlagsRule()

    @Test
    fun testAddView_verifyStartCaptureCall() {
        activityScenarioRule.scenario.onActivity { activity ->
            mViewCaptureAwareWindowManager =
                ViewCaptureAwareWindowManager(
                    mContext,
                    mContext.getSystemService(WindowManager::class.java),
                )

            val activityDecorView = activity.window.decorView
            // removing view since it is already added to view hierarchy on declaration
            mViewCaptureAwareWindowManager.removeView(activityDecorView)
            val viewCapture = ViewCaptureFactory.getInstance(mContext)

            mViewCaptureAwareWindowManager.addView(
                activityDecorView,
                activityDecorView.layoutParams as WindowManager.LayoutParams,
            )
            assertTrue(viewCapture.mIsStarted)
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_WINDOW_CONTEXT_OVERRIDE_TYPE)
    @Test
    fun useWithWindowContext_attachWindow_attachToViewCaptureAwareWm() {
        val windowContext =
            mContext.createWindowContext(
                mContext.getSystemService(DisplayManager::class.java).getDisplay(DEFAULT_DISPLAY),
                TYPE_APPLICATION,
                null, /* options */
            ) as WindowContext

        // Obtain ViewCaptureAwareWindowManager with WindowContext.
        mViewCaptureAwareWindowManager =
            ViewCaptureAwareWindowManagerFactory.getInstance(windowContext)
                as ViewCaptureAwareWindowManager

        // Attach to an Activity so that we can add an application parent window.
        val params = WindowManager.LayoutParams()
        activityScenarioRule.scenario.onActivity { activity ->
            params.token = activity.activityToken
        }

        // Create and attach an application window, and listen to OnAttachStateChangeListener.
        // We need to know when the parent window is attached and then we can add the attached
        // dialog.
        val listener = AttachStateListener()
        val parentWindow = View(windowContext)
        parentWindow.addOnAttachStateChangeListener(listener)
        windowContext.attachWindow(parentWindow)

        // Attach the parent window to ViewCaptureAwareWm
        activityScenarioRule.scenario.onActivity {
            mViewCaptureAwareWindowManager.addView(parentWindow, params)
        }

        // Wait for parent window to be attached.
        listener.mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        assertWithMessage("The WindowContext token must be attached.")
            .that(params.mWindowContextToken)
            .isEqualTo(windowContext.windowContextToken)

        val subWindow = View(windowContext)
        val subParams = WindowManager.LayoutParams(TYPE_APPLICATION_ATTACHED_DIALOG)

        // Attach the sub-window
        activityScenarioRule.scenario.onActivity {
            mViewCaptureAwareWindowManager.addView(subWindow, subParams)
        }

        assertWithMessage("The sub-window must be attached to the parent window")
            .that(subParams.token)
            .isEqualTo(parentWindow.windowToken)
    }

    private class AttachStateListener : View.OnAttachStateChangeListener {
        val mLatch: CountDownLatch = CountDownLatch(1)

        override fun onViewAttachedToWindow(v: View) {
            mLatch.countDown()
        }

        override fun onViewDetachedFromWindow(v: View) {}
    }

    companion object {
        private const val TIMEOUT_IN_SECONDS = 4L
    }
}
