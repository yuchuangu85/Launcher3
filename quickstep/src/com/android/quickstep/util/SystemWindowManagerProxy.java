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
package com.android.quickstep.util;

import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Context;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;
import com.android.wm.shell.shared.desktopmode.DesktopState;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Extension of {@link WindowManagerProxy} with some assumption for the default system Launcher
 */
@LauncherAppSingleton
public class SystemWindowManagerProxy extends WindowManagerProxy {

    private final DesktopState mDesktopState;

    @Inject
    public SystemWindowManagerProxy(DesktopState desktopState) {
        super(true);
        mDesktopState = desktopState;
    }

    @Override
    public Rect getCurrentBounds(Context displayInfoContext) {
        return new Rect(getCurrentWindowMetrics(displayInfoContext).getBounds());
    }

    @Override
    public boolean isDisplayDesktopFirst(Context displayInfoContext) {
        // Standalone Launcher does not ship WM Shell desktop-mode support.
        return false;
    }

    @Override
    public boolean showDesktopTaskbarForFreeformDisplay(Context displayInfoContext) {
        if (!DesktopModeStatus.canEnterDesktopMode(displayInfoContext)) {
            return false;
        }

        if (!DesktopModeStatus.enterDesktopByDefaultOnFreeformDisplay(displayInfoContext)) {
            return false;
        }

        return isDisplayDesktopFirst(displayInfoContext);
    }

    @Override
    public int getRotation(Context displayInfoContext) {
        return super.getRotation(displayInfoContext);
    }

    /**
     * Uses the current-window API when it is exposed by the device framework. Some vendor builds
     * omit APIs present in the AOSP framework stubs used to compile Launcher.
     */
    private WindowMetrics getCurrentWindowMetrics(Context context) {
        WindowManager windowManager = context.getSystemService(WindowManager.class);
        try {
            Method method = WindowManager.class.getMethod("getCurrentWindowMetrics");
            Object result = method.invoke(windowManager);
            if (result instanceof WindowMetrics) {
                return (WindowMetrics) result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to the stable API below.
        }
        return windowManager.getMaximumWindowMetrics();
    }

    @Override
    public ArrayMap<CachedDisplayInfo, List<WindowBounds>> estimateInternalDisplayBounds(
            Context displayInfoContext) {
        ArrayMap<CachedDisplayInfo, List<WindowBounds>> result = new ArrayMap<>();
        WindowManager windowManager = displayInfoContext.getSystemService(WindowManager.class);
        for (WindowMetrics windowMetrics : getPossibleMaximumWindowMetrics(windowManager)) {
            CachedDisplayInfo info = getDisplayInfo(windowMetrics, Surface.ROTATION_0);
            List<WindowBounds> bounds = estimateWindowBounds(displayInfoContext, info);
            result.put(info, bounds);
        }
        return result;
    }

    /**
     * Uses the multi-posture API when the device framework provides it. Some vendor Android 17
     * builds omit it despite compiling Launcher against the matching AOSP framework stubs.
     */
    @SuppressWarnings("unchecked")
    private Set<WindowMetrics> getPossibleMaximumWindowMetrics(WindowManager windowManager) {
        try {
            Method method = WindowManager.class.getMethod(
                    "getPossibleMaximumWindowMetrics", int.class);
            Object result = method.invoke(windowManager, DEFAULT_DISPLAY);
            if (result instanceof Set<?>) {
                return (Set<WindowMetrics>) result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to the stable API below.
        }
        return Collections.singleton(windowManager.getMaximumWindowMetrics());
    }

    @Override
    protected DisplayCutout rotateCutout(DisplayCutout original, int startWidth, int startHeight,
            int fromRotation, int toRotation) {
        try {
            Method method = DisplayCutout.class.getMethod("getRotated", int.class, int.class,
                    int.class, int.class);
            return (DisplayCutout) method.invoke(original, startWidth, startHeight, fromRotation,
                    toRotation);
        } catch (ReflectiveOperationException ignored) {
            return super.rotateCutout(original, startWidth, startHeight, fromRotation, toRotation);
        }
    }
}
