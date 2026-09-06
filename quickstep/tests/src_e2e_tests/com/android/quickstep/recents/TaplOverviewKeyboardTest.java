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

import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.SelectModeButtons;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability;
import com.android.launcher3.util.ui.BaseLauncherTaplTest.AllowInRecentsWindowTests;
import com.android.quickstep.AbstractQuickStepTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@AllowInRecentsWindowTests
@RunWith(AndroidJUnit4.class)
public class TaplOverviewKeyboardTest extends AbstractQuickStepTest {

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        executeOnOverview(recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(true));
        clearAllRecentTasks();
        mLauncher.markOverviewSelectTipSeen();
    }

    @After
    public void tearDown() {
        executeOnOverview(/* forTearDown= */ true, recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(false));
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 489811503)
    public void testOpenOverviewWithActionPlusTabKeys() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertIsInState("Launcher state is not Home", LauncherState.NORMAL);

        Overview overview = home.openOverviewFromActionPlusTabKeyboardShortcut();

        assertIsInState("Launcher state is not Overview", LauncherState.OVERVIEW);
        overview.launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE); // Assert app is focused.
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 489811503)
    public void testOpenOverviewWithRecentsKey() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertIsInState("Launcher state is not Home", LauncherState.NORMAL);

        Overview overview = home.openOverviewFromRecentsKeyboardShortcut();

        assertIsInState("Launcher state is not Overview", LauncherState.OVERVIEW);
        overview.launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE); // Assert app is focused.
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 489811503)
    public void testDismissOverviewWithEscKey() throws Exception {
        startTestAppsWithCheck();
        final Overview overview =
                mLauncher.goHome().openOverviewFromActionPlusTabKeyboardShortcut();
        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);

        overview.dismissByEscKey();
        assertIsInState("Launcher internal state is not Home", LauncherState.NORMAL);
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 489799688)
    public void testDismissModalTaskAndOverviewWithEscKey() throws Exception {
        startTestAppsWithCheck();
        final Overview overview =
                mLauncher.goHome().openOverviewFromActionPlusTabKeyboardShortcut();

        final SelectModeButtons selectModeButtons =
                overview.getCurrentTask().tapMenu().tapSelectMenuItem();

        assertIsInState(
                "Launcher internal state is not Overview Modal Task",
                LauncherState.OVERVIEW_MODAL_TASK);

        selectModeButtons.dismissByEscKey();

        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);
        overview.dismissByEscKey();
        assertIsInState("Launcher internal state is not Home", LauncherState.NORMAL);
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
