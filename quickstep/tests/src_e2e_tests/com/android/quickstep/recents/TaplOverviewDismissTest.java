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
import static com.android.launcher3.util.ui.ActivityStartUtils.resolveSystemApp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.OverviewTask;
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability;
import com.android.launcher3.util.ui.BaseLauncherTaplTest.AllowInRecentsWindowTests;
import com.android.launcher3.util.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.quickstep.AbstractQuickStepTest;
import com.android.quickstep.views.RecentsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Comparator;
import java.util.Optional;

@LargeTest
@AllowInRecentsWindowTests
@RunWith(AndroidJUnit4.class)
public class TaplOverviewDismissTest extends AbstractQuickStepTest {

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
    public void testDismissCancel() throws Exception {
        startTestAppsWithCheck();
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        OverviewTask task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);

        task.dismissCancel();

        executeOnOverview(recentsView -> assertEquals(
                "Canceling dismissing a task removed a task from Overview",
                numTasks == null ? 0 : numTasks, recentsView.getTaskViewCount()));
    }

    @Test
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489811439)
    public void testDismissBottomRow() throws Exception {
        assumeTrue("Ignoring test because device is not a tablet", mLauncher.isTablet());
        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        Optional<OverviewTask> bottomTask = overview.getCurrentTasksForTablet().stream().max(
                Comparator.comparingInt(OverviewTask::getTaskCenterY));
        assertTrue("bottomTask null", bottomTask.isPresent());

        bottomTask.get().dismiss();
        executeOnOverview(recentsView -> assertEquals(
                "Dismissing a bottomTask didn't remove 1 bottomTask from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));
    }

    @Test
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489810784)
    public void testDismissLastGridRow() throws Exception {
        assumeTrue("Ignoring test because device is not a tablet", mLauncher.isTablet());
        startTestAppsWithCheck();
        startTestActivity(3);
        startTestActivity(4);
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertNotEquals(
                "Grid overview should have unequal row counts",
                recentsView.getTopRowTaskCountForTablet(),
                recentsView.getBottomRowTaskCountForTablet()));

        overview.flingForwardUntilClearAllVisible();
        assertTrue("Clear All not visible.", overview.isClearAllVisible());
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        Optional<OverviewTask> lastGridTask = overview.getCurrentTasksForTablet().stream().min(
                Comparator.comparingInt(OverviewTask::getTaskCenterX));
        assertTrue("lastGridTask null.", lastGridTask.isPresent());

        lastGridTask.get().dismiss();
        executeOnOverview(recentsView -> {
            assertEquals(
                    "Dismissing a lastGridTask didn't remove 1 lastGridTask from Overview",
                    numTasks - 1, recentsView.getTaskViewCount());
            assertEquals(
                    "Grid overview should have equal row counts.",
                    recentsView.getTopRowTaskCountForTablet(),
                    recentsView.getBottomRowTaskCountForTablet());
        });
        assertTrue("Clear All not visible.", overview.isClearAllVisible());
    }

    @Test
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489811542)
    // When dismissing multiple apps, the apps off screen should "re-balance" i.e. re-arrange
    // themselves evenly across both top and bottom rows.
    public void gridRebalancesOffScreenAfterDismissingMultipleApps() throws Exception {
        assumeTrue("Ignoring test because device is not a tablet", mLauncher.isTablet());
        // Launch enough apps so some are offscreen.
        for (int i = 2; i <= 12; i++) {
            startTestActivity(i);
        }
        Overview overview = mLauncher.goHome().switchToOverview();
        executeOnOverview(recentsView -> assertTrue("11 tasks should be open",
                recentsView.getTaskViewCount() >= 11));

        // Dismiss 2 tasks from the top row.
        assertIsInState(
                "Launcher internal state didn't remain in Overview", LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();
        assertIsInState(
                "Launcher internal state didn't remain in Overview", LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();

        // Assert that the two row counts are no more than 1 apart, therefore were re-balanced.
        executeOnOverview(recentsView -> assertTrue(
                "Grid did not re-balance after multiple dismissals",
                (Math.abs(recentsView.getTopRowTaskCountForTablet()
                        - recentsView.getBottomRowTaskCountForTablet()) <= 1)));
    }

    @Test
    @PortraitLandscape
    @DesktopStability(flavors = LOCAL, bug = 489811542)
    // When dismissing multiple apps, the apps on screen should not "re-balance" i.e. dismissing
    // 2 apps from the top row, will move the top row along 2 and so it will not be balanced
    // across the bottom row.
    public void gridDoesNotRebalanceOnScreenAfterDismissingMultipleApps() throws Exception {
        assumeTrue("Ignoring test because device is not a tablet", mLauncher.isTablet());
        // Launch 6 apps so 3 are in each row.
        int appsInBothRowsCount = 6;
        int appsInEachRowCount = appsInBothRowsCount / 2;
        for (int i = 2; i <= appsInBothRowsCount + 1; i++) {
            startTestActivity(i);
        }
        Overview overview = mLauncher.goHome().switchToOverview();
        executeOnOverview(recentsView -> {
            assertEquals(appsInBothRowsCount + " tasks should be open",
                    appsInBothRowsCount, recentsView.getTaskViewCount());
            assertEquals("Grid should have " + appsInEachRowCount + " tasks on the top row",
                    appsInEachRowCount,
                    recentsView.getTopRowTaskCountForTablet());
            assertEquals("Grid should have " + appsInEachRowCount + " tasks on the bottom row",
                    appsInEachRowCount,
                    recentsView.getBottomRowTaskCountForTablet());
        });

        // Dismiss 2 tasks from the top row.
        assertIsInState("Launcher internal state didn't remain in Overview",
                LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();
        assertIsInState("Launcher internal state didn't remain in Overview",
                LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();

        executeOnOverview(recentsView -> {
            int expectedTopRowCount = appsInEachRowCount - 2;
            assertEquals(
                    "Grid should have " + expectedTopRowCount + " tasks on the top row",
                    expectedTopRowCount,
                    recentsView.getTopRowTaskCountForTablet());
            assertEquals("Grid should have " + appsInEachRowCount + " tasks on the bottom row",
                    appsInEachRowCount,
                    recentsView.getBottomRowTaskCountForTablet());
        });
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
