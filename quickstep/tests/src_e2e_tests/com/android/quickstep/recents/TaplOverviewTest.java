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

package com.android.quickstep.recents;

import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.ui.ActivityStartUtils.getAppPackageName;
import static com.android.launcher3.util.ui.ActivityStartUtils.resolveSystemApp;
import static com.android.launcher3.util.ui.ActivityStartUtils.startExcludeFromRecentsTestActivity;
import static com.android.quickstep.TaskbarModeSwitchRule.Mode.TRANSIENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.BaseOverview;
import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.OverviewActions;
import com.android.launcher3.tapl.OverviewTask;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability;
import com.android.launcher3.util.ui.BaseLauncherTaplTest.AllowInRecentsWindowTests;
import com.android.launcher3.util.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.quickstep.AbstractQuickStepTest;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;
import com.android.quickstep.views.RecentsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@AllowInRecentsWindowTests
@RunWith(AndroidJUnit4.class)
public class TaplOverviewTest extends AbstractQuickStepTest {

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

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
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489810466)
    public void testOverview() throws Exception {
        startTestAppsWithCheck();
        // mLauncher.pressHome() also tests an important case of pressing home while in background.
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState(
                "Launcher internal state didn't switch to Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Don't have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        // Test flinging forward and backward.
        executeOnOverview(recentsView -> assertEquals("Current task in Overview is not first",
                recentsView.indexOfChild(recentsView.getFirstTaskView()),
                recentsView.getCurrentPage()));

        overview.flingForward();
        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);
        final Integer currentTaskAfterFlingForward =
                getFromOverview(RecentsView::getCurrentPage);
        executeOnOverview(recentsView -> assertTrue("Current task in Overview is still 0",
                currentTaskAfterFlingForward > recentsView.indexOfChild(
                        recentsView.getFirstTaskView())));

        overview.flingBackward();
        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Flinging back in Overview did nothing",
                recentsView.getCurrentPage() < currentTaskAfterFlingForward));

        // Test opening a task.
        OverviewTask task = mLauncher.goHome().switchToOverview().getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (1)", task);
        assertNotNull("OverviewTask.open returned null", task.open());
        assertTrue("Test activity didn't open from Overview", mDevice.wait(Until.hasObject(
                        By.pkg(getAppPackageName()).text("TestActivity2")),
                TestUtil.DEFAULT_UI_TIMEOUT));
        expectLaunchedAppState();

        // Test dismissing a task.
        overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);
        task.dismiss();
        executeOnOverview(recentsView -> assertEquals(
                "Dismissing a task didn't remove 1 task from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));

        // Test dismissing all tasks.
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        assertIsInState("Launcher internal state is not Home", LauncherState.NORMAL);
        executeOnOverview(recentsView -> assertEquals("Still have tasks after dismissing all",
                0, recentsView.getTaskViewCount()));
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489810466)
    public void testOpenOverviewFromHome() throws Exception {
        startTestAppsWithCheck();
        assertNotNull("Workspace.switchToOverview() returned null",
                mLauncher.goHome().switchToOverview());
        assertIsInState(
                "Launcher internal state didn't switch to Overview", LauncherState.OVERVIEW);
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    public void testOpenOverviewFromApp() throws Exception {
        startAppFast(CALCULATOR_APP_PACKAGE);
        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();

        assertNotNull("Background.switchToOverview() returned null",
                launchedAppState.switchToOverview());
        assertIsInState(
                "Launcher internal state didn't switch to Overview", LauncherState.OVERVIEW);
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    public void testOpenOverviewFromAppWithStashedTaskbar() throws Exception {
        try {
            startTestAppsWithCheck();
            // Set ignoreTaskbarVisibility, as transient taskbar will be stashed after app launch.
            mLauncher.setIgnoreTaskbarVisibility(true);
            mLauncher.getLaunchedAppState().switchToOverview();
        } finally {
            mLauncher.setIgnoreTaskbarVisibility(false);
        }
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 489927668)
    public void testOpenOverviewFromExcludeFromRecentsApps() throws Exception {
        startExcludeFromRecentsTestActivity();
        OverviewTask currentTask = getAndAssertLaunchedApp().switchToOverview().getCurrentTask();
        assertTrue("Can't find ExcludeFromRecentsTestActivity after entering Overview from it",
                currentTask.containsContentDescription("ExcludeFromRecents"));
        // Going home should clear out the excludeFromRecents task.
        BaseOverview overview = mLauncher.goHome().switchToOverview();
        if (overview.hasTasks()) {
            currentTask = overview.getCurrentTask();
            assertFalse("Found ExcludeFromRecentsTestActivity after entering Overview from Home",
                    currentTask.containsContentDescription("ExcludeFromRecents"));
        } else {
            // Presumably the test started with 0 tasks and remains that way after going home.
        }
    }

    @Test
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489811178)
    public void testOverviewDeadzones() throws Exception {
        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Should have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        // It should not dismiss overview when tapping between tasks
        overview.touchBetweenTasks();
        overview = mLauncher.getOverview();
        assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);

        // Dismiss when tapping to the right of the focused task
        overview.touchOutsideFirstTask();
        assertIsInState("Launcher internal state should be Home", LauncherState.NORMAL);
    }

    @Test
    @PortraitLandscape
    @TaskbarModeSwitch
    @DesktopStability(flavors = LOCAL, bug = 489811260)
    public void testTaskbarDeadzonesForTablet() throws Exception {
        assumeTrue("Ignoring test because device is not a tablet", mLauncher.isTablet());

        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Should have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        if (mLauncher.isTransientTaskbar()) {
            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertIsInState("Launcher internal state should be Normal", LauncherState.NORMAL);

            overview = mLauncher.getWorkspace().switchToOverview();

            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertIsInState("Launcher internal state should be Normal", LauncherState.NORMAL);
        } else {
            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);

            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);
        }
    }

    /**
     * Smoke test for action buttons: Presses all the buttons and makes sure no crashes occur.
     */
    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489810466)
    public void testOverviewScreenshotAction() throws Exception {
        assumeFalse("Skipping Overview Actions tests for tablet", mLauncher.isTablet());
        startTestAppsWithCheck();
        OverviewActions actionsView =
                mLauncher.goHome().switchToOverview().getOverviewActions();
        actionsView.clickAndDismissScreenshot();
    }

    /**
     * Smoke test for action buttons: Presses all the buttons and makes sure no crashes occur.
     */
    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489810466)
    public void testOverviewSelectAction() throws Exception {
        assumeFalse("Skipping Overview Actions tests for tablet", mLauncher.isTablet());
        startTestAppsWithCheck();
        OverviewActions actionsView =
                mLauncher.goHome().switchToOverview().getOverviewActions();
        actionsView.clickSelect().clickClose();
    }

    private void startTestAppsWithCheck() throws Exception {
        startTestApps();
        expectLaunchedAppState();
    }

    private void assertIsInState(
            @NonNull String failureMessage, @NonNull LauncherState expectedState) {
        assertTrue(failureMessage, isInState(() -> expectedState));
    }

    private void expectLaunchedAppState() {
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
    }
}
