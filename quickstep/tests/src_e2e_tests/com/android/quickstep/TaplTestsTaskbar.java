/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME;
import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability;
import com.android.launcher3.util.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;

import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsTaskbar extends AbstractTaplTestsTaskbar {

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.ALL)
    @DesktopStability(flavors = LOCAL, bug = 486279914)
    public void testLaunchApp() {
        getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
        // We are using parameterized test runner to share code between different test cases with
        // taskbar variants. But, sometimes we only need to assert things for particular Taskbar
        // variants.
        if (mLauncher.isTransientTaskbar()) {
            mLauncher.getLaunchedAppState().assertTaskbarHidden();
        }
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.ALL)
    @DesktopStability(flavors = LOCAL, bug = 486279914)
    public void testLaunchShortcut() {
        getTaskbar().getAppIcon(TEST_APP_NAME)
                .openDeepShortcutMenu()
                .getMenuItem("Shortcut 1")
                .launch(TEST_APP_PACKAGE);
    }


    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.ALL)
    @DesktopStability(flavors = LOCAL, bug = 486279914)
    public void testOpenMenu() {
        getTaskbar().getAppIcon(TEST_APP_NAME).openMenu();
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.ALL)
    @DesktopStability(flavors = LOCAL, bug = 486279914)
    public void testLaunchApp_fromTaskbarAllApps() {
        getTaskbar().openAllApps().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.ALL)
    @DesktopStability(flavors = LOCAL, bug = 486279914)
    public void testOpenMenu_fromTaskbarAllApps() {
        getTaskbar().openAllApps().getAppIcon(TEST_APP_NAME).openMenu();
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.ALL)
    @DesktopStability(flavors = LOCAL, bug = 486279914)
    public void testLaunchShortcut_fromTaskbarAllApps() {
        getTaskbar().openAllApps()
                .getAppIcon(TEST_APP_NAME)
                .openDeepShortcutMenu()
                .getMenuItem("Shortcut 1")
                .launch(TEST_APP_PACKAGE);
    }

    @Test
    @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.ALL)
    @DesktopStability(flavors = LOCAL, bug = 486279914)
    public void testOpenMenuViaRightClick() {
        getTaskbar().getAppIcon(TEST_APP_NAME).openDeepShortcutMenuWithRightClick();
    }

    @Test
    @PortraitLandscape
    public void testDismissAllAppsByTappingOutsideSheet() {
        getTaskbar().openAllApps().dismissByTappingOutsideForTablet(/* tapRight= */ true);
        getTaskbar().openAllApps().dismissByTappingOutsideForTablet(/* tapRight= */ false);
    }

}
