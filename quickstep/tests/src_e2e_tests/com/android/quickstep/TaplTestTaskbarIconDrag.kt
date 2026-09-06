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

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability
import com.android.launcher3.util.rule.TestStabilityRule.LOCAL
import com.android.wm.shell.Flags
import org.junit.After
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
class TaplTestTaskbarIconDrag : AbstractQuickStepTest() {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private var launcherLayout: AutoCloseable? = null

    override fun setUp() {
        Assume.assumeTrue("Ignoring test because device is not a tablet", mLauncher.isTablet)
        super.setUp()
        val layoutBuilder =
            LauncherLayoutBuilder()
                .atHotseat(0)
                .putApp(
                    "com.google.android.apps.nexuslauncher.out_of_proc_tests",
                    "com.android.launcher3.testcomponent.TestLauncherActivity",
                )
        launcherLayout = TestUtil.setLauncherDefaultLayout(mTargetContext, layoutBuilder)
        performInitialization()
        mLauncher.enableBlockTimeout(true)
    }

    @After
    fun tearDown() {
        mLauncher.removeAllBubbles()
        mLauncher.enableBlockTimeout(false)
        launcherLayout?.close()
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 490563735)
    fun testAppIconDragOnOverviewFromTaskBarToBubbleBar() {
        val overview = mLauncher.workspace.switchToOverview()
        mLauncher.showTaskbarIfHidden()
        // test left drop target
        overview.taskbar!!
            .getAppIcon(TEST_APP_NAME)
            .dragToBubbleBarLocation(/* isBubbleBarLeftDropTarget= */ true)
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 490563735)
    fun testAppIconDragInRunningAppFromTaskBarToBubbleBar() {
        startAppFast(AbstractTaplTestsTaskbar.CALCULATOR_APP_PACKAGE)
        val launchedAppState = mLauncher.launchedAppState
        mLauncher.showTaskbarIfHidden()
        // test right drop target
        launchedAppState.taskbar
            .getAppIcon(TEST_APP_NAME)
            .dragToBubbleBarLocation(/* isBubbleBarLeftDropTarget= */ false)
    }

    @Test
    @ScreenRecord
    @DesktopStability(flavors = LOCAL, bug = 490563735)
    fun testAppIconDragOnOverviewFromTaskBarAllAppsToBubbleBar() {
        val overview = mLauncher.workspace.switchToOverview()
        mLauncher.showTaskbarIfHidden()
        // test right drop target
        overview.taskbar!!
            .openAllApps()
            .getAppIcon(TEST_APP_NAME)
            .dragToBubbleBarLocation(/* isBubbleBarLeftDropTarget= */ false)
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 490563735)
    fun testAppIconDragInRunningAppFromTaskBarAllAppsToBubbleBar() {
        startAppFast(AbstractTaplTestsTaskbar.CALCULATOR_APP_PACKAGE)
        val launchedAppState = mLauncher.launchedAppState
        mLauncher.showTaskbarIfHidden()
        // test left drop target
        launchedAppState.taskbar
            .openAllApps()
            .getAppIcon(TEST_APP_NAME)
            .dragToBubbleBarLocation(/* isBubbleBarLeftDropTarget= */ true)
    }
}
