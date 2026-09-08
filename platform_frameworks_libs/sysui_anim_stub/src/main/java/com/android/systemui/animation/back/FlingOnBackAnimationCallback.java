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

package com.android.systemui.animation.back;

import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;

/**
 * Runtime-compatible subset of SystemUI's predictive-back callback.
 *
 * <p>The standalone Gradle APK compiles against {@code SystemUI.jar}, but that jar belongs to a
 * separate system application and is not available to an ordinary application's class loader.
 * Launcher only requires this type to dispatch predictive-back lifecycle events to the compat
 * callbacks, so package that bridge with the app instead of resolving a private SystemUI class at
 * startup.
 */
public abstract class FlingOnBackAnimationCallback implements OnBackAnimationCallback {

    private final Interpolator mProgressInterpolator;

    public FlingOnBackAnimationCallback() {
        this(new LinearInterpolator());
    }

    public FlingOnBackAnimationCallback(Interpolator progressInterpolator) {
        mProgressInterpolator = progressInterpolator;
    }

    public final Interpolator getProgressInterpolator() {
        return mProgressInterpolator;
    }

    public abstract void onBackStartedCompat(BackEvent backEvent);

    public abstract void onBackProgressedCompat(BackEvent backEvent);

    public abstract void onBackInvokedCompat();

    public abstract void onBackCancelledCompat();

    @Override
    public final void onBackStarted(BackEvent backEvent) {
        onBackStartedCompat(backEvent);
    }

    @Override
    public final void onBackProgressed(BackEvent backEvent) {
        onBackProgressedCompat(backEvent);
    }

    @Override
    public final void onBackInvoked() {
        onBackInvokedCompat();
    }

    @Override
    public final void onBackCancelled() {
        onBackCancelledCompat();
    }
}
