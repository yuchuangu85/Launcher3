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

package com.android.quickstep

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.util.TestConstants
import com.android.launcher3.util.rule.ScreenRecordRule
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability
import com.android.launcher3.util.rule.TestStabilityRule.LOCAL
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TaplTaskbarDragging : AbstractTaplTestsTaskbar() {

    fun testLaunchAppInSplitscreen_fromTaskbarAllApps() {
        taskbar
            .openAllApps()
            .getAppIcon(TestConstants.AppNames.TEST_APP_NAME)
            .dragToSplitscreen(TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE)
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.TRANSIENT)
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    fun testLaunchAppInSplitscreen_fromTaskbarAllApps_transient() {
        testLaunchAppInSplitscreen_fromTaskbarAllApps()
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.PERSISTENT)
    @DesktopStability(flavors = LOCAL, bug = 491563553)
    fun testLaunchAppInSplitscreen_fromTaskbarAllApps_persistent() {
        testLaunchAppInSplitscreen_fromTaskbarAllApps()
    }

    fun testLaunchShortcutInSplitscreen_fromTaskbarAllApps() {
        taskbar
            .openAllApps()
            .getAppIcon(TestConstants.AppNames.TEST_APP_NAME)
            .openDeepShortcutMenu()
            .getMenuItem("Shortcut 1")
            .dragToSplitscreen(TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE)
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.TRANSIENT)
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    fun testLaunchShortcutInSplitscreen_fromTaskbarAllApps_transient() {
        testLaunchShortcutInSplitscreen_fromTaskbarAllApps()
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.PERSISTENT)
    @DesktopStability(flavors = LOCAL, bug = 491563553)
    fun testLaunchShortcutInSplitscreen_fromTaskbarAllApps_persistent() {
        testLaunchShortcutInSplitscreen_fromTaskbarAllApps()
    }

    fun testLaunchShortcutInSplitscreen() {
        taskbar
            .getAppIcon(TestConstants.AppNames.TEST_APP_NAME)
            .openDeepShortcutMenu()
            .getMenuItem("Shortcut 1")
            .dragToSplitscreen(TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE)
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/414900465
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.TRANSIENT)
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    fun testLaunchShortcutInSplitscreen_transient() {
        testLaunchShortcutInSplitscreen()
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/414900465
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.PERSISTENT)
    @DesktopStability(flavors = LOCAL, bug = 491563553)
    fun testLaunchShortcutInSplitscreen_persistent() {
        testLaunchShortcutInSplitscreen()
    }

    fun testLaunchAppInSplitscreen() {
        taskbar
            .getAppIcon(TestConstants.AppNames.TEST_APP_NAME)
            .dragToSplitscreen(TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE)
        // We are using parameterized test runner to share code between different test cases with
        // taskbar variants. But, sometimes we only need to assert things for particular Taskbar
        // variants.
        if (mLauncher.isTransientTaskbar) {
            mLauncher.launchedAppState.assertTaskbarHidden()
        }
    }

    @Test
    @ScreenRecordRule.ScreenRecord // b/414900465
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.TRANSIENT)
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    fun testLaunchAppInSplitscreen_transient() {
        testLaunchAppInSplitscreen()
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.PERSISTENT)
    @DesktopStability(flavors = LOCAL, bug = 491563553)
    fun testLaunchAppInSplitscreen_persistent() {
        testLaunchAppInSplitscreen()
    }
}
