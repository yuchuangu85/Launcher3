/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.Utilities.shouldReduceWorkspaceBlurUsage;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;

import android.graphics.Color;

import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherUiState;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.util.concurrent.TimeUnit;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final int STATE_FLAGS =
            FLAG_WORKSPACE_INACCESSIBLE | FLAG_CLOSE_POPUPS | FLAG_HOTSEAT_INACCESSIBLE;
    private static final long BACK_CUJ_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);


    public AllAppsState(int id) {
        super(id, LAUNCHER_STATE_ALLAPPS, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        if (Flags.allAppsSurface() && !isToState) {
            // TODO(b/414847564): Temporary workaround for no state transition during the swipe.
            return 100;
        }
        return isToState
                ? context.getDeviceProfile().getAllAppsProfile().getOpenDuration()
                : context.getDeviceProfile().getAllAppsProfile().getCloseDuration();
    }

    @Override
    public void onBackStarted(Launcher launcher) {
        // Because the back gesture can take longer time depending on when user release the finger,
        // we pass BACK_CUJ_TIMEOUT_MS as timeout to the jank monitor.
        InteractionJankMonitorWrapper.begin(launcher.getAppsView(),
                Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK, BACK_CUJ_TIMEOUT_MS);
        super.onBackStarted(launcher);
    }

    @Override
    public void onBackInvoked(Launcher launcher) {
        // In predictive back swipe, onBackInvoked() will be called after onBackStarted().
        // In 3 button mode, onBackStarted() is not called but onBackInvoked() will be called.
        // Thus In onBackInvoked(), we should only begin instrumenting if we didn't call
        // onBackStarted() to start instrumenting CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK.
        if (!InteractionJankMonitorWrapper.isInstrumenting(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK)) {
            InteractionJankMonitorWrapper.begin(
                    launcher.getAppsView(), Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
        }
        super.onBackInvoked(launcher);
    }

    /** Called when predictive back swipe is cancelled. */
    @Override
    public void onBackCancelled(Launcher launcher) {
        super.onBackCancelled(launcher);
        InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
    }

    @Override
    protected void onBackAnimationCompleted(boolean success) {
        if (success) {
            // Animation was successful.
            InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
        } else {
            // Animation was canceled.
            InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
        }
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getAppsView().getDescription();
    }

    @Override
    public int getTitle() {
        return R.string.all_apps_list_label;
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        return 0f;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        final float scale = shouldReduceWorkspaceBlurUsage(launcher)
                ? NO_SCALE
                : launcher.getDeviceProfile().getWorkspaceProfile().getWorkspaceContentScale();
        return new ScaleAndTranslation(scale, NO_OFFSET, NO_OFFSET);
    }

    @Override
    protected float getDepthUnchecked(ActivityContext context) {
        return shouldReduceWorkspaceBlurUsage(context.asContext())
                ? 0f
                : context.getDeviceProfile().getBottomSheetProfile().getBottomSheetDepth();
    }

    @Override
    public boolean shouldBlurWorkspace(Launcher launcher, LauncherState targetState) {
        return !shouldReduceWorkspaceBlurUsage(launcher) && (targetState == ALL_APPS
                || targetState == NORMAL);
    }

    @Override
    public int getVisibleElements(LauncherUiState launcherUiState) {
        return Flags.allAppsSurface() ? HOTSEAT_ICONS
                : ALL_APPS_CONTENT | FLOATING_SEARCH_BAR | HOTSEAT_ICONS;
    }

    @Override
    public int getFloatingSearchBarRestingMarginBottom(Launcher launcher) {
        return 0;
    }

    @Override
    public int getFloatingSearchBarRestingMarginStart(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.getAllAppsProfile().getLeftRightMargin() + dp.getAllAppsIconStartMargin(launcher);
    }

    @Override
    public int getFloatingSearchBarRestingMarginEnd(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.getAllAppsProfile().getLeftRightMargin() + dp.getAllAppsIconStartMargin(launcher);
    }

    @Override
    public boolean shouldFloatingSearchBarUsePillWhenUnfocused(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.getDeviceProperties().isPhone() && !dp.getDeviceProperties().isLandscape();
    }

    @Override
    public ScrimColors getWorkspaceScrimColor(Launcher launcher) {
        if (Flags.allAppsSurface()) {
            // No scrim.
            return super.getWorkspaceScrimColor(launcher);
        }
        int backgroundColor = Themes.getAttrColor(launcher, R.attr.allAppsScrimColor);
        return new ScrimColors(backgroundColor, /* foregroundColor */ Color.TRANSPARENT);
    }
}
