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

package com.android.launcher3.util.ui;

import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import com.android.launcher3.util.rule.FailureWatcher;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class PortraitLandscapeRunner implements TestRule {
    private static final String TAG = "PortraitLandscapeRunner";
    private final AbstractLauncherUiTest<?, ?> mTest;

    // Annotation for tests that need to be run in portrait and landscape modes.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface PortraitLandscape {
    }

    public PortraitLandscapeRunner(AbstractLauncherUiTest<?, ?> test) {
        mTest = test;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    if (description.getAnnotation(PortraitLandscape.class) == null) {
                        base.evaluate();
                        return;
                    }
                    try {
                        // we expect to begin unlocked...
                        AbstractLauncherUiTest.verifyKeyguardInvisible();
                        mTest.mDevice.pressHome();
                        mTest.mLauncher.waitForLauncherInitialized();
                        mTest.mLauncher.setEnableRotation(true);
                    } catch (Throwable e) {
                        FailureWatcher.onError(mTest.mLauncher, description);
                        throw e;
                    }

                    goToPortrait();
                    base.evaluate();
                    mTest.getDevice().pressHome();
                    goToLandscape();
                    base.evaluate();
                    mTest.getDevice().pressHome();
                } catch (Throwable e) {
                    Log.e(TAG, "Error", e);
                    throw e;
                } finally {
                    mTest.mDevice.setOrientationNatural();
                    mTest.mLauncher.setFixedLandscape(false);
                    mTest.mLauncher.setEnableRotation(false);
                    mTest.mLauncher.setExpectedRotation(Surface.ROTATION_0);

                    // and end unlocked...
                    AbstractLauncherUiTest.verifyKeyguardInvisible();
                }
            }


        };
    }

    /**
     * Makes the phone go into Portrait mode.
     */
    public void goToPortrait() throws RemoteException {
        mTest.mDevice.setOrientationNatural();
        mTest.mLauncher.setExpectedRotation(Surface.ROTATION_0);
        AbstractLauncherUiTest.checkDetectedLeaks(mTest.mLauncher);
    }

    /**
     * Makes the phone go into Landscape mode or FixedLandscape for phones.
     */
    public void goToLandscape() throws RemoteException {
        mTest.mLauncher.setFixedLandscape(true);
        mTest.mDevice.setOrientationLeft();
        AbstractLauncherUiTest.checkDetectedLeaks(mTest.mLauncher);
        mTest.mLauncher.setExpectedRotation(Surface.ROTATION_90);
    }
}
