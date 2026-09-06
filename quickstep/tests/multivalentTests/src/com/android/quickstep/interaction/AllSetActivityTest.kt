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

package com.android.quickstep.interaction

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify

@SkipOnDeviceless // Skipping because of need for shell commands and spying
@RunWith(AndroidJUnit4::class)
class AllSetActivityTest {
    @Rule @JvmField val limitDevicesRule = LimitDevicesRule()

    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private lateinit var scenario: ActivityScenario<AllSetActivity>
    private var callback: Application.ActivityLifecycleCallbacks? = null

    @Before
    fun setUp() {
        uiAutomation.executeShellCommand("setprop setupwizard.theme glif_expressive")

        val app = ApplicationProvider.getApplicationContext<Application>()

        // 1. Attach the spy BEFORE the Activity actually runs its onCreate logic
        callback =
            object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityPreCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?,
                    ) {
                        if (activity is AllSetActivity) {
                            // spyOn hooks into the existing window instance immediately
                            activity.window.convertToSpy()
                        }
                    }

                    // Empty implementations for the rest
                    override fun onActivityCreated(a: Activity, s: Bundle?) {}

                    override fun onActivityStarted(a: Activity) {}

                    override fun onActivityResumed(a: Activity) {}

                    override fun onActivityPaused(a: Activity) {}

                    override fun onActivityStopped(a: Activity) {}

                    override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}

                    override fun onActivityDestroyed(a: Activity) {}
                }
                .also { app.registerActivityLifecycleCallbacks(it) }

        val intent = Intent(app, AllSetActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()

        val app = ApplicationProvider.getApplicationContext<Application>()
        callback?.let { app.unregisterActivityLifecycleCallbacks(it) }
        callback = null

        uiAutomation.executeShellCommand("setprop setupwizard.theme \"\"")
    }

    @Test
    fun swipeUp_blurRadiusUnchanged() {
        verifyBlurRadius()

        scenario.onActivity { it.mSwipeProgress.updateValue(0.3f) }
        verifyBlurRadius()

        scenario.onActivity { it.mSwipeProgress.updateValue(1.2f) }
        verifyBlurRadius()
    }

    private fun verifyBlurRadius() {
        scenario.onActivity { activity ->
            // Standard Mockito verify works on the window now
            verify(activity.window, atLeastOnce()).setBackgroundBlurRadius(WALLPAPER_BLUR_RADIUS)
        }
    }

    private companion object {
        private const val WALLPAPER_BLUR_RADIUS = 30
    }
}
