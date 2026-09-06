/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.quickstep;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.quickstep.NavigationModeSwitchRule.Mode.ALL;
import static com.android.quickstep.NavigationModeSwitchRule.Mode.THREE_BUTTON;
import static com.android.quickstep.NavigationModeSwitchRule.Mode.ZERO_BUTTON;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_3BUTTON_OVERLAY;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_GESTURAL_OVERLAY;

import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.ui.AbstractLauncherUiTest;
import com.android.systemui.shared.system.QuickStepContract;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test rule that allows executing a test with Quickstep on and then Quickstep off.
 * The test should be annotated with @NavigationModeSwitch.
 */
public class NavigationModeSwitchRule implements TestRule {

    static final String TAG = "QuickStepOnOffRule";

    public enum Mode {
        THREE_BUTTON, ZERO_BUTTON, ALL
    }

    // Annotation for tests that need to be run with quickstep enabled and disabled.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface NavigationModeSwitch {
        Mode mode() default ALL;
    }

    private final LauncherInstrumentation mLauncher;

    public NavigationModeSwitchRule(LauncherInstrumentation launcher) {
        mLauncher = launcher;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (description.getAnnotation(NavigationModeSwitch.class) == null) {
            return base;
        }
        Mode mode = description.getAnnotation(NavigationModeSwitch.class).mode();
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mLauncher.enableDebugTracing();
                final String prevOverlayPkg = getCurrentOverlayPackage(
                        LauncherInstrumentation.getCurrentInteractionMode(
                                getInstrumentation().getContext()
                        )
                );
                final LauncherInstrumentation.NavigationModel originalMode =
                        mLauncher.getNavigationModel();
                try {
                    if (mode == ZERO_BUTTON || mode == ALL) {
                        activateZeroButtons(description);
                        base.evaluate();
                    }
                    if (mode == THREE_BUTTON || mode == ALL) {
                        activateThreeButtons(description);
                        base.evaluate();
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Error", e);
                    throw e;
                } finally {
                    Log.d(TAG, "In Finally block");
                    setActiveOverlay(mLauncher, prevOverlayPkg, originalMode, description);
                }
            }
        };
    }

    /**
     * Set gesture nav to be Zero Buttons.
     */
    public void activateZeroButtons(Description description) throws Exception {
        setActiveOverlay(
                mLauncher,
                NAV_BAR_MODE_GESTURAL_OVERLAY,
                LauncherInstrumentation.NavigationModel.ZERO_BUTTON,
                description
        );
    }

    /**
     * Set gesture nav to be Three Buttons.
     */
    public void activateThreeButtons(Description description) throws Exception {
        setActiveOverlay(
                mLauncher,
                NAV_BAR_MODE_3BUTTON_OVERLAY,
                LauncherInstrumentation.NavigationModel.THREE_BUTTON,
                description
        );
    }

    public static String getCurrentOverlayPackage(int currentInteractionMode) {
        return QuickStepContract.isGesturalMode(currentInteractionMode)
                ? NAV_BAR_MODE_GESTURAL_OVERLAY
                : NAV_BAR_MODE_3BUTTON_OVERLAY;
    }

    public static void setActiveOverlay(
            LauncherInstrumentation launcher,
            String overlayPackage,
            LauncherInstrumentation.NavigationModel expectedMode)
            throws Exception {
        setActiveOverlay(launcher, overlayPackage, expectedMode, null);
    }

    public static void setActiveOverlay(
            LauncherInstrumentation launcher,
            String overlayPackage,
            LauncherInstrumentation.NavigationModel expectedMode,
            @Nullable Description description)
            throws Exception {
        if (!packageExists(overlayPackage)) {
            throw new RuntimeException(
                    "setActiveOverlay: " + overlayPackage + " pkg does not exist"
            );
        }

        try {
            Log.d(TAG, "setActiveOverlay: " + overlayPackage + "...");
            UiDevice.getInstance(getInstrumentation())
                    .executeShellCommand(
                            String.format(
                                    "cmd overlay enable-exclusive --user %d --category %s",
                                    Process.myUserHandle().getIdentifier(), overlayPackage
                            )
                    );

            launcher.waitForCondition("Couldn't switch to " + overlayPackage,
                    TestUtil.DEFAULT_UI_TIMEOUT,
                    () -> launcher.getNavigationModel() == expectedMode);

            launcher.waitForCondition(
                    () -> "Switching nav mode: " + launcher.getNavigationModeMismatchError(false),
                    TestUtil.DEFAULT_UI_TIMEOUT,
                    () -> launcher.getNavigationModeMismatchError(false) == null);
            AbstractLauncherUiTest.checkDetectedLeaks(launcher);
        } catch (Throwable e) {
            if (description != null) {
                FailureWatcher.onError(launcher, description);
            }
            throw e;
        }
    }

    private static boolean packageExists(String packageName) {
        try {
            PackageManager pm = getInstrumentation().getContext().getPackageManager();
            if (pm.getApplicationInfo(packageName, 0 /* flags */) == null) {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }
}
