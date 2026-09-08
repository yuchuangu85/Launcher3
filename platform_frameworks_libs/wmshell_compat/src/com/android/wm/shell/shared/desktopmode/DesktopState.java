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

package com.android.wm.shell.shared.desktopmode;

import android.content.Context;
import android.view.Display;

/**
 * Standalone-APK fallback for the SystemUI-owned desktop-mode state.
 *
 * <p>AOSP provides this type from the privileged WMShell/SystemUI process. A user-installed
 * Launcher cannot load that private implementation, so disable desktop-mode-only behavior while
 * retaining the public API consumed by Launcher.
 */
public interface DesktopState {

    DesktopState DISABLED = new DisabledDesktopState();

    /** Binary-compatible Kotlin companion used by code compiled against SystemUI.jar. */
    Companion Companion = Holder.INSTANCE;

    boolean canEnterDesktopMode();

    default boolean canEnterDesktopModeOrShowAppHandle() {
        return canEnterDesktopMode() || overridesShowAppHandle();
    }

    boolean canShowDesktopExperienceDevOption();

    boolean canShowDesktopModeDevOption();

    boolean enterDesktopByDefaultOnFreeformDisplay();

    boolean isDeviceEligibleForDesktopMode();

    boolean enableMultipleDesktops();

    boolean isDesktopModeSupportedOnDisplay(int displayId);

    boolean isDesktopModeSupportedOnDisplay(Display display);

    boolean isProjectedMode();

    boolean overridesShowAppHandle();

    boolean isFreeformEnabled();

    boolean getShouldShowHomeBehindDesktop();

    default void destroy() {}

    static DesktopState fromContext(Context context) {
        return DISABLED;
    }

    static DesktopState getInstance(Context context) {
        return DISABLED;
    }

    final class Holder {
        private static final DesktopState.Companion INSTANCE = new DesktopState.Companion();

        private Holder() {}
    }

    final class Companion {
        public static final Companion $$INSTANCE = new Companion();

        private Companion() {}

        public DesktopState fromContext(Context context) {
            return DISABLED;
        }

        public DesktopState getInstance(Context context) {
            return DISABLED;
        }
    }
}

final class DisabledDesktopState implements DesktopState {

    @Override
    public boolean canEnterDesktopMode() {
        return false;
    }

    @Override
    public boolean canShowDesktopExperienceDevOption() {
        return false;
    }

    @Override
    public boolean canShowDesktopModeDevOption() {
        return false;
    }

    @Override
    public boolean enterDesktopByDefaultOnFreeformDisplay() {
        return false;
    }

    @Override
    public boolean isDeviceEligibleForDesktopMode() {
        return false;
    }

    @Override
    public boolean enableMultipleDesktops() {
        return false;
    }

    @Override
    public boolean isDesktopModeSupportedOnDisplay(int displayId) {
        return false;
    }

    @Override
    public boolean isDesktopModeSupportedOnDisplay(Display display) {
        return false;
    }

    @Override
    public boolean isProjectedMode() {
        return false;
    }

    @Override
    public boolean overridesShowAppHandle() {
        return false;
    }

    @Override
    public boolean isFreeformEnabled() {
        return false;
    }

    @Override
    public boolean getShouldShowHomeBehindDesktop() {
        return false;
    }
}
