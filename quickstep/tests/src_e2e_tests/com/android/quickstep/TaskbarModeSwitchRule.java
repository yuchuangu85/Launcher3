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

import static com.android.quickstep.TaskbarModeSwitchRule.Mode.ALL;
import static com.android.quickstep.TaskbarModeSwitchRule.Mode.PERSISTENT;
import static com.android.quickstep.TaskbarModeSwitchRule.Mode.TRANSIENT;

import android.util.Log;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.launcher3.util.ui.AbstractLauncherUiTest;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test rule that allows executing a test multiple times with different conditions
 * ie. with transient taskbar enabled and disabled.
 * The test should be annotated with @TaskbarModeSwitch.
 */
public class TaskbarModeSwitchRule implements TestRule {

    static final String TAG = "TaskbarModeSwitchRule";

    public static final int WAIT_TIME_MS = 10000;

    public enum Mode {
        TRANSIENT, PERSISTENT, ALL
    }

    // Annotation for tests that need to be run with quickstep enabled and disabled.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface TaskbarModeSwitch {
        Mode mode() default ALL;
    }

    private final LauncherInstrumentation mLauncher;

    public TaskbarModeSwitchRule(LauncherInstrumentation launcher) {
        mLauncher = launcher;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (description.getAnnotation(TaskbarModeSwitch.class) == null) {
            return base;
        }
        Mode mode = description.getAnnotation(TaskbarModeSwitch.class).mode();
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mLauncher.enableDebugTracing();

                final boolean wasTransientTaskbarMode = mLauncher.isTransientTaskbar();
                try {
                    if (mode == TRANSIENT || mode == ALL) {
                        evaluateWithTransientTaskbar();
                    }
                    if (mode == PERSISTENT || mode == ALL) {
                        evaluateWithPersistentTaskbar();
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Error", e);
                    throw e;
                } finally {
                    Log.d(TAG, "In Finally block");
                    setTaskbarMode(mLauncher, wasTransientTaskbarMode, description);
                }
            }

            private void evaluateWithPersistentTaskbar() throws Throwable {
                setTaskbarMode(mLauncher, false, description);
                base.evaluate();
            }

            private void evaluateWithTransientTaskbar() throws Throwable {
                setTaskbarMode(mLauncher, true, description);
                base.evaluate();
            }
        };
    }

    /**
     * Forces the taskbar to behave in a different mode.
     */
    public static void setTaskbarMode(
            LauncherInstrumentation launcher,
            boolean expectTransientTaskbar,
            Description description
    ) {
        launcher.enableTransientTaskbar(expectTransientTaskbar);
        launcher.recreateTaskbar();
        assertTrue(
                launcher,
                "Couldn't set taskbar=" + expectTransientTaskbar,
                launcher.isTransientTaskbar() == expectTransientTaskbar,
                description
        );
        AbstractLauncherUiTest.checkDetectedLeaks(launcher);
    }

    private static void assertTrue(LauncherInstrumentation launcher, String message,
            boolean condition, Description description) {
        launcher.checkForAnomaly(true, true);
        if (!condition) {
            if (description != null) {
                FailureWatcher.onError(launcher, description);
            }
            throw new AssertionError(message);
        }
    }
}
