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

import static com.android.app.animation.Interpolators.DECELERATE_2;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;
import static com.android.launcher3.util.OverviewReleaseFlags.enablePredictiveBackInOverview;

import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemProperties;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherUiState;
import com.android.launcher3.R;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;
import com.android.quickstep.fallback.RecentsStateUtilsKt;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    private static final int OVERVIEW_SLIDE_IN_DURATION = 380;
    private static final int OVERVIEW_POP_IN_DURATION = 250;
    private static final int OVERVIEW_EXIT_DURATION = 250;

    protected static final Rect sTempRect = new Rect();

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE_EXCEPT_UI_MODE_CHANGE
            | FLAG_RECENTS_VIEW_VISIBLE
            | FLAG_WORKSPACE_INACCESSIBLE
            | FLAG_CLOSE_POPUPS;

    protected OverviewState(int id) {
        this(id, STATE_FLAGS);
    }

    protected OverviewState(int id, int stateFlags) {
        this(id, LAUNCHER_STATE_OVERVIEW, stateFlags);
    }

    protected OverviewState(int id, int logContainer, int stateFlags) {
        super(id, logContainer, stateFlags);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        if (isToState) {
            // In gesture modes, overview comes in all the way from the side, so give it more time.
            return DisplayController.getNavigationMode(context.asContext()).hasGestures
                    ? OVERVIEW_SLIDE_IN_DURATION
                    : OVERVIEW_POP_IN_DURATION;
        } else {
            // When exiting Overview, exit quickly.
            return OVERVIEW_EXIT_DURATION;
        }
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        recentsView.getTaskSize(sTempRect);
        float scale;
        DeviceProfile deviceProfile = launcher.getDeviceProfile();
        if (deviceProfile.getDeviceProperties().isTwoPanels()) {
            // In two panel layout, width does not include both panels or space between them, so
            // use height instead. We do not use height for handheld, as cell layout can be
            // shorter than a task and we want the workspace to scale down to task size.
            scale = (float) sTempRect.height() / deviceProfile.getCellLayoutHeight();
        } else {
            scale = (float) sTempRect.width() / deviceProfile.getCellLayoutWidth();
        }
        float parallaxFactor = 0.5f;
        return new ScaleAndTranslation(scale, 0, -getDefaultSwipeHeight(launcher) * parallaxFactor);
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        return new float[] {NO_SCALE, NO_OFFSET};
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return new PageAlphaProvider(DECELERATE_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return 0;
            }
        };
    }

    @Override
    public int getVisibleElements(LauncherUiState launcherUiState) {
        int elements = CLEAR_ALL_BUTTON | OVERVIEW_ACTIONS | ADD_DESK_BUTTON;
        boolean showFloatingSearch;
        DeviceProfile dp = launcherUiState.getDeviceProfileRef().getValue();
        if (dp.getDeviceProperties().isPhone()) {
            // Only show search in phone overview in portrait mode.
            showFloatingSearch = !dp.getDeviceProperties().isLandscape();
        } else {
            // Only show search in tablet overview if taskbar is not visible.
            showFloatingSearch = !dp.getDeviceProperties()
                    .getTaskbarConfiguration()
                    .isTaskbarPresent()
                    || isTaskbarStashed(dp);
        }
        if (showFloatingSearch) {
            elements |= FLOATING_SEARCH_BAR;
        }
        if (launcherUiState.getSplitScreenUiState().isSplitSelectActive()) {
            elements &= ~CLEAR_ALL_BUTTON & ~ADD_DESK_BUTTON;
        }
        return elements;
    }

    @Override
    public float getSplitSelectTranslation(Launcher launcher) {
        if (!launcher.isSplitSelectionActive()) {
            return 0f;
        }
        RecentsView recentsView = launcher.getOverviewPanel();
        return recentsView.getSplitSelectTranslation();
    }

    @Override
    public int getFloatingSearchBarRestingMarginBottom(Launcher launcher) {
        return areElementsVisible(launcher.getLauncherUiState(), FLOATING_SEARCH_BAR) ? 0
                : super.getFloatingSearchBarRestingMarginBottom(launcher);
    }

    @Override
    public boolean shouldFloatingSearchBarUsePillWhenUnfocused(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.getDeviceProperties().isPhone() && !dp.getDeviceProperties().isLandscape();
    }

    @Override
    public boolean isTaskbarAlignedWithHotseat() {
        return false;
    }

    @Override
    public ScrimColors getWorkspaceScrimColor(Launcher launcher) {
        return new ScrimColors(
                /* backgroundColor= */ Themes.getAttrColor(launcher, R.attr.overviewScrimColor),
                /* foregroundColor= */ Color.TRANSPARENT);
    }

    @Override
    public boolean displayOverviewTasksAsGrid(DeviceProfile deviceProfile) {
        return deviceProfile.getDeviceProperties().isLargeScreen();
    }

    @Override
    public boolean isInOverview() {
        return true;
    }

    @Override
    public boolean disallowTaskbarGlobalDrag() {
        // Disable global drag in overview
        return true;
    }

    @Override
    public boolean allowTaskbarInitialSplitSelection() {
        // Allow split select from taskbar items in overview
        return true;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.accessibility_recent_apps);
    }

    @Override
    public int getTitle() {
        return R.string.accessibility_recent_apps;
    }

    public static float getDefaultSwipeHeight(Launcher launcher) {
        return LayoutUtils.getDefaultSwipeHeight(launcher, launcher.getDeviceProfile());
    }

    @Override
    protected float getDepthUnchecked(ActivityContext context) {
        // TODO(178661709): revert to always scaled
        return SystemProperties.getBoolean("ro.launcher.depth.overview", true)
                ? DepthController.DEPTH_70_PERCENT
                : DepthController.DEPTH_0_PERCENT;
    }

    @Override
    public void onBackStarted(Launcher launcher) {
        if (enablePredictiveBackInOverview()) {
            RecentsStateUtilsKt.toRecentsState(this).onBackStarted((QuickstepLauncher) launcher);
        } else {
            super.onBackStarted(launcher);
        }
    }

    @Override
    public void onBackProgressed(Launcher launcher, float backProgress) {
        if (enablePredictiveBackInOverview()) {
            RecentsStateUtilsKt.toRecentsState(this).onBackProgressed((QuickstepLauncher) launcher,
                    backProgress);
        } else {
            super.onBackProgressed(launcher, backProgress);
        }
    }

    @Override
    public void onBackInvoked(Launcher launcher) {
        RecentsStateUtilsKt.toRecentsState(this).onBackInvoked((QuickstepLauncher) launcher);
    }

    public static OverviewState newBackgroundState(int id) {
        return new BackgroundAppState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new QuickSwitchState(id);
    }

    /**
     *  New Overview substate that represents the overview in modal mode (one task shown on its own)
     */
    public static OverviewState newModalTaskState(int id) {
        return new OverviewModalTaskState(id);
    }

    /**
     * New Overview substate representing state where 1 app for split screen has been selected and
     * pinned and user is selecting the second one
     */
    public static OverviewState newSplitSelectState(int id) {
        return new SplitScreenSelectState(id);
    }

    public static OverviewState newOverviewState(int id) {
        return new OverviewState(id, STATE_FLAGS | FLAG_IS_TASK_VIEW_INTERACTIVE);
    }
}
