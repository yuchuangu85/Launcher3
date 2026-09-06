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
package com.android.quickstep;

import static androidx.test.InstrumentationRegistry.getTargetContext;

import static com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME;
import static com.android.quickstep.TaskbarModeSwitchRule.Mode.PERSISTENT;
import static com.android.wm.shell.shared.desktopmode.DesktopModeStatus.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.util.rule.SetPropRule;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;
import com.android.quickstep.util.OOPDisplayWindowingModeRule;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test taskbar all apps dragging behavior on desktop devices.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsDesktopTaskbarAllAppsDragTest extends AbstractTaplTestsTaskbar {
    private static final String TAG = "TaplTestsDesktopFirstTaskbar";

    @Rule
    public SetPropRule mSetPropRule =
            new SetPropRule(ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP, "true");

    @Rule
    public OOPDisplayWindowingModeRule mWindowingModeRule = new OOPDisplayWindowingModeRule(
            OOPDisplayWindowingModeRule.MODES.FREEFORM
    );

    @Override
    public void setUp() throws Exception {
        Assume.assumeTrue(
                "Ignoring test because device does not support desktop mode",
                DesktopModeStatus.canEnterDesktopMode(getTargetContext()));
        super.setUp();
    }

    @Override
    protected boolean startCalculatorAppDuringSetup() {
        return false;
    }

    @Override
    protected boolean expectTaskbarIconsMatchHotseat() {
        return false;
    }

    @Test
    @NavigationModeSwitch
    @TaskbarModeSwitch(mode = PERSISTENT)
    public void testDragFromAllAppsToWorspace() {
        mDevice.pressHome();
        waitForResumed("Launcher internal state is still Background");

        final HomeAllApps allApps = getTaskbar().openAllAppsOnHome();
        allApps.freeze();
        try {
            allApps.getAppIcon(TEST_APP_NAME).dragToWorkspace(false, false);
            assertThat(mLauncher.getWorkspace().getWorkspaceAppIcon(TEST_APP_NAME)).isNotNull();
        } finally {
            allApps.unfreeze();
        }
    }
}
