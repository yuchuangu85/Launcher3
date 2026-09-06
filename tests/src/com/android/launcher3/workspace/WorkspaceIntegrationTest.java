/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.workspace;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.rule.SkipOnDesktop;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.ModelTestExtensions;
import com.android.launcher3.util.WorkspaceDragHelper;

import org.junit.Before;
import org.junit.Test;

/**
 * Test the basic interactions of the Workspace, adding pages, moving the pages and removing pages.
 */
public class WorkspaceIntegrationTest extends BaseLauncherActivityTest<Launcher> {

    private static final String TEST_ACTIVITY = LauncherModelHelper.TEST_ACTIVITY;
    private static final String TEST_PACKAGE = LauncherModelHelper.TEST_PACKAGE;

    private int getWorkspacePageCount() {
        return getLauncherActivity().getFromLauncher(l -> l.getWorkspace().getPageCount());
    }

    private int pagesPerScreen() {
        return getLauncherActivity().getFromLauncher(l -> l.getWorkspace().getPanelCount());
    }

    private static boolean isWorkspaceScrollable(Launcher launcher) {
        return launcher.getWorkspace().getPageCount() > launcher.getWorkspace().getPanelCount();
    }

    private int getCurrentWorkspacePage(Launcher launcher) {
        return launcher.getWorkspace().getCurrentPage();
    }

    @Before
    public void setUp() throws Exception {
        // Set layout that includes Maps/Play on workspace, and Messaging/Chrome on hotseat.
        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atHotseat(0).putApp(TEST_PACKAGE, TEST_ACTIVITY);
        ModelTestExtensions.setModelLayout(targetContext(), builder);
        loadLauncherSync();

        // Pre verifying the screens
        getLauncherActivity().executeOnLauncher(launcher -> {
            launcher.enableHotseatEdu(false);
        });
    }

    /**
     * Add an icon and add a page to ensure the Workspace is scrollable and also make sure we can
     * move between workspaces. After, make sure we can launch an app from the Workspace.
     *
     * @throws Exception if we can't set the defaults icons that will appear at the beginning.
     */
    @Test
    public void testWorkspace() {
        WorkspaceDragHelper workspaceDragHelper = new WorkspaceDragHelper(getLauncherActivity());
        final DeviceProfile dp = getLauncherActivity().getFromLauncher(Launcher::getDeviceProfile);

        // Test that ensureWorkspaceIsScrollable adds a page by dragging an icon there.
        getLauncherActivity().executeOnLauncher(
                launcher -> assertFalse(
                        "Initial workspace state is scrollable",
                        isWorkspaceScrollable(launcher)
                )
        );
        assertEquals(
                "Initial workspace doesn't have the correct page",
                dp.getPanelCount(),
                getWorkspacePageCount()
        );

        assertFalse(
                "Chrome app was found on empty workspace",
                workspaceDragHelper.appIconExists(CONTAINER_DESKTOP, TEST_ACTIVITY)
        );
        workspaceDragHelper.dragIcon(
                workspaceDragHelper.getHotseatAppIcon(TEST_ACTIVITY),
                pagesPerScreen(),
                CONTAINER_DESKTOP
        );

        getLauncherActivity().executeOnLauncher(
                launcher -> assertTrue(
                        "Ensuring workspace scrollable didn't switch to next screen",
                        getCurrentWorkspacePage(launcher) <= pagesPerScreen()
                )
        );
        getLauncherActivity().executeOnLauncher(
                launcher -> assertTrue(
                        "ensureScrollable didn't make workspace scrollable",
                        isWorkspaceScrollable(launcher)
                )
        );
        assertNotNull(
                "ensureScrollable didn't add Chrome app",
                workspaceDragHelper.getWorkspaceAppIcon(TEST_ACTIVITY)
        );

        // Test flinging workspace.
        workspaceDragHelper.flingBackward();
        assertTrue(
                "Launcher internal state is not Home",
                getLauncherActivity().isInState(() -> LauncherState.NORMAL)
        );
        getLauncherActivity().executeOnLauncher(
                launcher -> assertEquals(
                        "Flinging back didn't switch workspace to page #0",
                        0, getCurrentWorkspacePage(launcher)
                )
        );

        workspaceDragHelper.flingForward();
        getLauncherActivity().executeOnLauncher(
                launcher -> assertTrue(
                        "Flinging forward didn't switch workspace to next screen",
                        getCurrentWorkspacePage(launcher) <= pagesPerScreen()
                )
        );
        assertTrue("Launcher internal state is not Home",
                getLauncherActivity().isInState(() -> LauncherState.NORMAL));

        // Test app in workspace.
        assertTrue(
                "No Chrome app in workspace",
                workspaceDragHelper.appIconExists(CONTAINER_DESKTOP, TEST_ACTIVITY)
        );
    }


    /**
     * Similar to {@link WorkspaceIntegrationTest#testWorkspace} but here we also make sure we can
     * delete the pages.
     */
    @Test
    public void testAddAndDeletePageAndFling() {
        WorkspaceDragHelper workspaceDragHelper = new WorkspaceDragHelper(getLauncherActivity());
        // Add one page by dragging app to page 1.
        workspaceDragHelper.dragIcon(
                // Get the first app from the hotseat
                workspaceDragHelper.getAppIcon(CONTAINER_HOTSEAT, TEST_ACTIVITY),
                pagesPerScreen(),
                CONTAINER_DESKTOP
        );
        assertEquals(
                "Incorrect Page count Number",
                pagesPerScreen() * 2,
                getWorkspacePageCount()
        );

        // Delete one page by deleting the item from workspace.
        getLauncherActivity().executeOnLauncher(launcher -> {
            final View view = launcher.getWorkspace().mapOverItems(
                    workspaceDragHelper.getWorkspaceAppIcon(TEST_ACTIVITY));
            assertNotNull(view);
            launcher.getAccessibilityDelegate().performAccessibilityAction(view, R.id.action_remove,
                    null);
            AbstractFloatingView.getOpenView(launcher, AbstractFloatingView.TYPE_SNACKBAR).close(
                    false);
        });

        // Refresh workspace to avoid using stale container error.
        assertEquals("Incorrect Page count Number", pagesPerScreen(), getWorkspacePageCount());
    }

    @Test
    // No hotseat on desktop, consider adding a similar test for taskbar once b/466972765 is done.
    @SkipOnDesktop
    public void testDragToAndFromHotseat() {
        WorkspaceDragHelper workspaceDragHelper = new WorkspaceDragHelper(getLauncherActivity());

        assertFalse(workspaceDragHelper.appIconExists(CONTAINER_DESKTOP, TEST_ACTIVITY));
        assertTrue(workspaceDragHelper.appIconExists(CONTAINER_HOTSEAT, TEST_ACTIVITY));

        // Drag from hotseat.
        workspaceDragHelper.dragIcon(
                workspaceDragHelper.getHotseatAppIcon(TEST_ACTIVITY),
                /*pageDelta=*/ 0,
                CONTAINER_DESKTOP);

        assertTrue(workspaceDragHelper.appIconExists(CONTAINER_DESKTOP, TEST_ACTIVITY));
        assertFalse(workspaceDragHelper.appIconExists(CONTAINER_HOTSEAT, TEST_ACTIVITY));

        // Drag to hotseat.
        workspaceDragHelper.dragIcon(
                workspaceDragHelper.getWorkspaceAppIcon(TEST_ACTIVITY),
                /*pageDelta=*/ 0,
                CONTAINER_HOTSEAT);

        assertFalse(workspaceDragHelper.appIconExists(CONTAINER_DESKTOP, TEST_ACTIVITY));
        assertTrue(workspaceDragHelper.appIconExists(CONTAINER_HOTSEAT, TEST_ACTIVITY));
    }
}
