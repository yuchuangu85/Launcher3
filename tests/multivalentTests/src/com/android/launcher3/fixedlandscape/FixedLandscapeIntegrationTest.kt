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

package com.android.launcher3.fixedlandscape

import android.content.Context
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.FIXED_LANDSCAPE_MODE
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.integration.util.events.ActivityTestEvents.createFixedLandscapeSwitchWaiter
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.LauncherModelHelper.WIDGET_CLASS_NAME_NO_CONFIG
import com.android.launcher3.util.LauncherModelHelper.WIDGET_PACKAGE_NAME
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class FixedLandscapeIntegrationTest {

    @Rule @JvmField val limitDevicesRule = LimitDevicesRule()

    private val targetContext: Context = getInstrumentation().targetContext

    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private val idp = InvariantDeviceProfile.INSTANCE.get(targetContext)

    private fun getTotalItems(): Int =
        launcherActivity.getFromLauncher {
            it.workspace.mWorkspaceScreens.sumOf { it.shortcutsAndWidgets.childCount }
        }!!

    private fun switchFixedLandscape(isFixedLandscape: Boolean) {
        val currentIsFixedLandscape =
            launcherActivity.getFromLauncher { it.deviceProfile.inv.isFixedLandscape }!!
        if (currentIsFixedLandscape != isFixedLandscape) {
            val fixedLandscapeSwitchedWaiter =
                launcherActivity.createFixedLandscapeSwitchWaiter(isFixedLandscape)
            LauncherPrefs.get(targetContext).putSync(FIXED_LANDSCAPE_MODE.to(isFixedLandscape))
            fixedLandscapeSwitchedWaiter.waitForSignal()
        }
        launcherActivity.waitUntil("Workspace didn't finished loading") { !it.isWorkspaceLoading }
        if (isFixedLandscape) {
            launcherActivity.waitUntil("Fixed Landscape should have 3 columns") {
                it.deviceProfile.inv.numRows == 3
            }
        }
    }

    @Test
    @SkipOnDeviceless
    fun `can switch to fixed landscape and back`() {
        switchFixedLandscape(true)
        val countItemsInFixedLandscape = getTotalItems()
        switchFixedLandscape(false)
        val countItemsInLauncher = getTotalItems()
        assert(countItemsInFixedLandscape == countItemsInLauncher) {
            "The number of items should be the same in both orientations, the values " +
                "are $countItemsInFixedLandscape in Fixed Landscape and" +
                "$countItemsInLauncher in the regular Launcher "
        }
    }

    @Test
    @SkipOnDeviceless
    fun `can switch to fixed landscape and back from non-default grid`() {
        val gridName = "small"
        val fixedLandscapeGridName = "fixed_landscape_mode"
        idp.setCurrentGrid(gridName)
        switchFixedLandscape(true)
        var currentGridName = LauncherPrefs.get(targetContext).get(LauncherPrefs.GRID_NAME)
        assert(currentGridName.equals(fixedLandscapeGridName)) {
            "When we switch to fixed landscape mode we should go to $fixedLandscapeGridName. " +
                "Instead, we went to $currentGridName"
        }
        switchFixedLandscape(false)
        currentGridName = LauncherPrefs.get(targetContext).get(LauncherPrefs.GRID_NAME)
        assert(currentGridName.equals(gridName)) {
            "The grid that we go back to should be $gridName. Instead, we went to $currentGridName"
        }
    }

    @Test
    @SkipOnDeviceless
    fun `stress test fixed landscape`() {
        // The number should be bigger than 1 but also not too big so that the test doesn't take too
        // long to run
        for (i in 0..7) {
            switchFixedLandscape(true)
            val countItemsInFixedLandscape = getTotalItems()
            switchFixedLandscape(false)
            val countItemsInLauncher = getTotalItems()
            assert(countItemsInFixedLandscape == countItemsInLauncher) {
                "The number of items should be the same in both orientations, the values " +
                    "are $countItemsInFixedLandscape in fixed landscape and" +
                    " $countItemsInLauncher in the regular Launcher "
            }
        }
    }

    @Before
    fun setup() {
        launcherActivity.initializeActivity()
        targetContext.setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(0, 1, 0)
                .putWidget(WIDGET_PACKAGE_NAME, WIDGET_CLASS_NAME_NO_CONFIG, 3, 1)
                .atWorkspace(3, 1, 0)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY)
                .atWorkspace(1, 2, 0)
                .putWidget(WIDGET_PACKAGE_NAME, WIDGET_CLASS_NAME_NO_CONFIG, 3, 1)
                .atWorkspace(0, 2, 0)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY)
                .atWorkspace(2, 3, 0)
                .putApp(TEST_PACKAGE, TEST_ACTIVITY2)
        )

        assumeTrue(
            "Fixed landscape is only supported on phones, skip test for none phone",
            launcherActivity.getFromLauncher { it.deviceProfile.inv.deviceType } == TYPE_PHONE,
        )
    }

    @After
    fun cleanup() {
        LauncherPrefs.get(targetContext).putSync(FIXED_LANDSCAPE_MODE.to(false))
    }
}
