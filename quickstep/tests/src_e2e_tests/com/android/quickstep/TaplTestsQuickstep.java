/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep;

import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.ui.ActivityStartUtils.resolveSystemApp;
import static com.android.launcher3.util.ui.ActivityStartUtils.startImeTestActivity;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.content.res.Configuration;
import android.platform.systemui_tapl.ui.Root;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability;
import com.android.launcher3.util.ui.BaseLauncherTaplTest.AllowInRecentsWindowTests;
import com.android.launcher3.util.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@AllowInRecentsWindowTests
@RunWith(AndroidJUnit4.class)
public class TaplTestsQuickstep extends AbstractQuickStepTest {

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);
    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        executeOnOverview(recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(true));
        clearAllRecentTasks();
    }

    @After
    public void tearDown() {
        executeOnOverview(/* forTearDown= */ true, recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(false));
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testWorkspaceSwitchToAllApps() {
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @ScreenRecordRule.ScreenRecord  // b/415877373
    @DesktopStability(flavors = LOCAL, bug = 489809350)
    public void testQuickSwitchFromApp() throws Exception {
        startTestActivity(2);
        startTestActivity(3);
        startTestActivity(4);

        quickSwitchToPreviousAppAndAssert(true /* toRight */);
        assertTestActivityIsRunning(3,
                "The first app we should have quick switched to is not running");

        quickSwitchToPreviousAppAndAssert(true /* toRight */);
        if (mLauncher.getNavigationModel() == NavigationModel.THREE_BUTTON) {
            // 3-button mode toggles between 2 apps, rather than going back further.
            assertTestActivityIsRunning(4,
                    "Second quick switch should have returned to the first app.");
        } else {
            assertTestActivityIsRunning(2,
                    "The second app we should have quick switched to is not running");
        }

        quickSwitchToPreviousAppAndAssert(false /* toRight */);
        assertTestActivityIsRunning(3,
                "The 2nd app we should have quick switched to is not running");

        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
        launchedAppState.switchToOverview();
    }

    @Test
    @TaskbarModeSwitch
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    public void testQuickSwitchToPreviousAppForTablet() throws Exception {
        assumeTrue("Ignoring test because device is not a tablet",
            mLauncher.isTablet());
        startTestActivity(2);
        startImeTestActivity();

        // Set ignoreTaskbarVisibility to true to verify the task bar visibility explicitly.
        mLauncher.setIgnoreTaskbarVisibility(true);


        try {
            boolean isTransientTaskbar = mLauncher.isTransientTaskbar();
            // Expect task bar invisible when the launched app was the IME activity.
            LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
            if (!isTransientTaskbar && isHardwareKeyboard() && !mLauncher.isImeDocked()) {
                launchedAppState.assertTaskbarVisible();
            } else {
                launchedAppState.assertTaskbarHidden();
            }

            // Quick-switch to the test app with swiping to right.
            quickSwitchToPreviousAppAndAssert(true /* toRight */);

            assertTestActivityIsRunning(2,
                    "The first app we should have quick switched to is not running");
            launchedAppState = getAndAssertLaunchedApp();
            if (isTransientTaskbar) {
                launchedAppState.assertTaskbarHidden();
            } else {
                // Expect taskbar visible when the launched app was the test activity.
                launchedAppState.assertTaskbarVisible();
            }
        } finally {
            // Reset ignoreTaskbarVisibility to ensure other tests still verify it.
            mLauncher.setIgnoreTaskbarVisibility(false);
        }
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489786011)
    public void testQuickSwitchFromHome() throws Exception {
        startTestActivity(2);
        mLauncher.goHome().quickSwitchToPreviousApp();
        assertTestActivityIsRunning(2,
                "The most recent task is not running after quick switching from home");
        getAndAssertLaunchedApp();
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    @DesktopStability(flavors = LOCAL, bug = 489812017)
    public void testPressBack() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                READ_DEVICE_CONFIG_PERMISSION);
        mLauncher.getWorkspace().switchToAllApps().pressBackToWorkspace();
        waitForLauncherCondition("Launcher internal state didn't switch to Home", launcher ->
                launcher.getStateManager().getCurrentStableState() == LauncherState.NORMAL);

        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.getLaunchedAppState().pressBackToWorkspace();
        waitForLauncherCondition("Launcher internal state didn't switch to Home", launcher ->
                launcher.getStateManager().getCurrentStableState() == LauncherState.NORMAL);
    }

    @Test
    public void testDisableRotationCheckForPhone() throws Exception {
        assumeFalse("Ignoring test because device is not a phone",
            mLauncher.isTablet());
        try {
            mLauncher.setExpectedRotationCheckEnabled(false);
            mLauncher.setEnableRotation(false);
            mLauncher.getDevice().setOrientationLeft();
            startTestActivity(7);
            mLauncher.waitForCondition("Device should not be in natural orientation",
                    TestUtil.DEFAULT_UI_TIMEOUT, () -> !mDevice.isNaturalOrientation());
            mLauncher.goHome();
        } finally {
            mLauncher.setExpectedRotationCheckEnabled(true);
            mLauncher.setEnableRotation(true);
            mLauncher.getDevice().setOrientationNatural();
        }
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489811602)
    public void testNotificationsFromHome() {
        Root.get().openNotificationShadeViaSwipe();
    }

    private void quickSwitchToPreviousAppAndAssert(boolean toRight) {
        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
        if (toRight) {
            launchedAppState.quickSwitchToPreviousApp();
        } else {
            launchedAppState.quickSwitchToPreviousAppSwipeLeft();
        }

        // While enable shell transition, Launcher can be resumed due to transient launch.
        waitForLauncherCondition("Launcher shouldn't stay in resume forever",
                this::isInLaunchedApp, 3000 /* timeout */);
    }

    private boolean isHardwareKeyboard() {
        return Configuration.KEYBOARD_QWERTY
                == mTargetContext.getResources().getConfiguration().keyboard;
    }
}
