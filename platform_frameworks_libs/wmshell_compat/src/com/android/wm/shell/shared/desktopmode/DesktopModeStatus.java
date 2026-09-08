/*
 * Copyright (C) 2026
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
 * Standalone-APK fallback for the SystemUI-owned desktop-mode feature flags.
 *
 * <p>The corresponding AOSP implementation is supplied by the privileged WM Shell process, not
 * by a user-installed Launcher APK. Keep all desktop-only paths disabled when that implementation
 * is unavailable.
 */
public class DesktopModeStatus {

    public static final String ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP =
            "persist.wm.debug.desktop_mode_default_on_freeform_display";

    public static boolean useRoundedCorners() {
        return false;
    }

    public static boolean enforceDeviceRestrictions() {
        return true;
    }

    public static boolean canShowDesktopModeDevOption(Context context) {
        return false;
    }

    public static boolean canShowDesktopExperienceDevOption(Context context) {
        return false;
    }

    public static boolean shouldDevOptionBeEnabledByDefault(Context context) {
        return false;
    }

    public static boolean canEnterDesktopMode(Context context) {
        return false;
    }

    public static boolean isDesktopModeSupportedOnDisplay(Context context, Display display) {
        return false;
    }

    public static boolean overridesShowAppHandle(Context context) {
        return false;
    }

    public static boolean canEnterDesktopModeOrShowAppHandle(Context context) {
        return false;
    }

    public static boolean isDeviceEligibleForDesktopMode(Context context) {
        return false;
    }

    public static boolean enterDesktopByDefaultOnFreeformDisplay(Context context) {
        return false;
    }
}
