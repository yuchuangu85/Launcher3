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

package com.android.quickstep.desktop

import android.app.WindowConfiguration
import android.content.Intent
import android.os.RemoteException
import android.util.Log
import android.view.Display
import android.view.WindowManagerGlobal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.rule.SetPropRule
import com.android.launcher3.util.rule.TestStabilityRule
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability
import com.android.launcher3.util.rule.TestStabilityRule.LOCAL
import com.android.quickstep.integration.BaseTaskbarIntegrationTest
import com.android.quickstep.taskbar.util.IntegrationNavigationModeSwitchRule
import com.android.quickstep.taskbar.util.IntegrationNavigationModeSwitchRule.NavigationModeSwitch
import com.android.quickstep.taskbar.util.IntegrationTaskbarModeSwitchRule.Mode
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TestsDesktopFirstTaskbar : BaseTaskbarIntegrationTest() {

    val TAG: String = "TaplTestsDesktopFirstTaskbar"

    @get:Rule
    var mSetPropRule: SetPropRule =
        SetPropRule(DesktopModeStatus.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP, "true")

    @get:Rule val testStabilityRule = TestStabilityRule()

    var mOriginalWindowingMode = WindowConfiguration.WINDOWING_MODE_UNDEFINED

    var originalTaskbarMode: Mode = Mode.TRANSIENT

    @Before
    fun setUp() {
        // Default-to-desktop feature requires the display to be freeform mode.
        originalTaskbarMode = taskbarModeSwitchRule.currentMode()
        taskbarModeSwitchRule.setTaskbarMode(Mode.PERSISTENT)
        mOriginalWindowingMode =
            setDisplayWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM)
        super.setup()
        Assume.assumeTrue(
            "Ignoring test because device does not support desktop mode",
            DesktopModeStatus.canEnterDesktopMode(
                InstrumentationRegistry.getInstrumentation().targetContext
            ),
        )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        if (!deviceProfile.deviceProperties.isLargeScreen) return
        if (mOriginalWindowingMode != WindowConfiguration.WINDOWING_MODE_UNDEFINED) {
            setDisplayWindowingMode(mOriginalWindowingMode)
        }
        // We are not using the annotation and using the rule directly to avoid race conditions
        // with setDisplayWindowingMode
        taskbarModeSwitchRule.setTaskbarMode(originalTaskbarMode)
    }

    override fun startCalendarAppDuringSetup(): Boolean {
        return false
    }

    private fun startActivityFast(activity: String) {
        interactions.startAppFast(
            activity,
            Intent().setClassName(LauncherModelHelper.TEST_PACKAGE, activity),
        )
    }

    @Test
    @NavigationModeSwitch
    @DesktopStability(flavors = LOCAL, bug = 486279458)
    fun testTaskbarOnHome() {
        // Go home - taskbar should be visible in desktop-first display context.
        uiDevice.pressHome()
        launcherActivity.waitForResumed()
        waitForTaskbarVisible()

        // Open an app, and go back home.
        interactions.startAppFast(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
        uiDevice.pressHome()
        launcherActivity.waitForResumed()

        // Verify that taskbar is still visible, and contains an icon associated with a running app,
        // which is expected in desktop-first display context.
        waitForTaskbarVisible()

        clickTaskbarAppIcon("Calculator")

        // Activating the running app icon on desktop-fist taskbar opens the app in desktop.
        assertAppInDesktop(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
    }

    @Test
    @NavigationModeSwitch(mode = IntegrationNavigationModeSwitchRule.Mode.THREE_BUTTON)
    @DesktopStability(flavors = LOCAL, bug = 486279458)
    fun testTaskbarOnHome_three_buttons() {
        testTaskbarOnHome()
    }

    @Test
    @NavigationModeSwitch
    fun testTaskbarForFullscreenApp() {
        clearAllRecentTasks()
        uiDevice.pressHome()
        launcherActivity.waitForResumed()

        // Open two apps - they are both expected to open in desktop windowing on desktop-first
        // display, then move the second one into fullscreen.
        interactions.startAppFast(CALCULATOR_APP_PACKAGE)
        startActivityFast(LauncherModelHelper.TEST_ACTIVITY2)
        waitForIcons("TestActivity3", "Calculator")

        moveFocusedActivityToFullscreen(LauncherModelHelper.TEST_PACKAGE, "TestActivity3")

        // Verify that taskbar is still visible, and contains an icon associated with a running app,
        // which is expected in desktop-first display context.
        waitForTaskbarVisible()
        clickTaskbarAppIcon("Calculator")

        // Activating the running app icon on desktop-fist taskbar opens the app in desktop.
        assertAppInDesktop(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
    }

    @Test
    @NavigationModeSwitch(mode = IntegrationNavigationModeSwitchRule.Mode.THREE_BUTTON)
    fun testTaskbarForFullscreenApp_three_buttons() {
        testTaskbarForFullscreenApp()
    }

    @Test
    @NavigationModeSwitch(mode = IntegrationNavigationModeSwitchRule.Mode.ZERO_BUTTON)
    fun testTaskbarForDesktopMode() {
        clearAllRecentTasks()
        uiDevice.pressHome()
        launcherActivity.waitForResumed()

        // Start two apps - they are both expected to open in desktop windowing on desktop-first
        // display.
        interactions.startAppFast(CALCULATOR_APP_PACKAGE)
        startActivityFast(LauncherModelHelper.TEST_ACTIVITY2)
        waitForTaskbarVisible()
        waitForIcons("TestActivity3", "Calculator")

        // Verify that taskbar is still visible, and contains an icon associated with a running app,
        // which is expected in desktop-first display context.
        waitForTaskbarVisible()
        clickTaskbarAppIcon("TestActivity3")

        // Activating the running app icon on desktop-fist taskbar opens the app in desktop.
        assertAppInDesktop(CALCULATOR_APP_PACKAGE)
        waitForTaskbarVisible()
    }

    @Test
    @NavigationModeSwitch(mode = IntegrationNavigationModeSwitchRule.Mode.THREE_BUTTON)
    fun testTaskbarForDesktopMode_three_buttons() {
        testTaskbarForDesktopMode()
    }

    private fun setDisplayWindowingMode(windowingMode: Int): Int {
        try {
            val originalWindowingMode =
                WindowManagerGlobal.getWindowManagerService()!!.getWindowingMode(
                    Display.DEFAULT_DISPLAY
                )
            WindowManagerGlobal.getWindowManagerService()!!.setWindowingMode(
                Display.DEFAULT_DISPLAY,
                windowingMode,
            )
            uiDevice.pressHome()
            uiDevice.waitForIdle()
            return originalWindowingMode
        } catch (e: RemoteException) {
            Log.e(TAG, "error setting windowing mode", e)
            throw RuntimeException(e)
        }
    }
}
