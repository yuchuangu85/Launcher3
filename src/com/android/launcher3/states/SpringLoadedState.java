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
package com.android.launcher3.states;

import static com.android.launcher3.Flags.centerSpringLoadedStateVertically;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;
import com.android.launcher3.deviceprofile.HotseatProfile;
import com.android.launcher3.views.ActivityContext;

/**
 * Definition for spring loaded state used during drag and drop.
 */
public class SpringLoadedState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_MULTI_PAGE
            | FLAG_WORKSPACE_INACCESSIBLE
            | FLAG_DISABLE_RESTORE_EXCEPT_UI_MODE_CHANGE
            | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_WORKSPACE_HAS_BACKGROUNDS
            | FLAG_WORKSPACE_ICONS_BEING_DRAGGED;

    public static final float DEPTH_15_PERCENT = 0.15f;

    public SpringLoadedState(int id) {
        super(id, LAUNCHER_STATE_HOME, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        return 150;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        DeviceProfile grid = launcher.getDeviceProfile();
        Workspace<?> ws = launcher.getWorkspace();
        if (ws.getChildCount() == 0) {
            return super.getWorkspaceScaleAndTranslation(launcher);
        }

        float scale = grid.getWorkspaceSpringLoadScale(launcher);

        if (centerSpringLoadedStateVertically()) {
            float centeredContentY = getCenteredSpringLoadedContentY(launcher);
            float dropTargetBarHeight = grid.getDropTargetBarHeight();

            // Calculate the workspace's top position relative to the screen.
            // This accounts for the workspace's current position and scale.
            float wsHeight = ws.getHeight();
            float wsPivotY = ws.getTop() + wsHeight / 2f;
            float workspacePageTop = ws.getChildAt(0).getTop();
            float scaledShift = (wsPivotY - workspacePageTop) * scale;
            float actualCellTop = wsPivotY - scaledShift;

            // The final translation is the destination Y (centered Y + bar height) minus the
            // workspace's current top position.
            float translateY = centeredContentY + dropTargetBarHeight - actualCellTop;
            return new ScaleAndTranslation(scale, 0, translateY);
        }

        float shrunkTop = grid.getCellLayoutSpringLoadShrunkTop();
        float halfHeight = ws.getHeight() / 2;
        float myCenter = ws.getTop() + halfHeight;
        float cellTopFromCenter = halfHeight - ws.getChildAt(0).getTop();
        float actualCellTop = myCenter - cellTopFromCenter * scale;
        return new ScaleAndTranslation(scale, 0, shrunkTop - actualCellTop);
    }

    @Override
    public float getDropTargetBarTranslationY(Launcher launcher) {
        // The drop target bar is the top-most element, so its translation is the centered Y.
        return getCenteredSpringLoadedContentY(launcher);
    }

    /**
     * Calculates the Y-coordinate for the top of the spring-loaded content block (which includes
     * the drop target bar, padding, and the scaled workspace) so that it is vertically centered
     * on the screen.
     */
    private float getCenteredSpringLoadedContentY(Launcher launcher) {
        DeviceProfile grid = launcher.getDeviceProfile();
        HotseatProfile hotseat = grid.getHotseatProfile();

        // Calculate the total height of all elements that'll be visible in the spring-loaded state.
        float dropTargetBarHeight = grid.getDropTargetBarHeight();
        float scaledWorkspaceHeight = grid.getCellLayoutHeight()
                * grid.getWorkspaceSpringLoadScale(launcher);
        float totalContentHeight = dropTargetBarHeight + scaledWorkspaceHeight;

        // Calculate the available vertical space for these elements.
        float hotseatTop = grid.isVerticalBarLayout()
                ? 0 : (hotseat.getSpringLoadedBarTopMarginPx() + launcher.getHotseat().getHeight());
        float availableHeight = launcher.getDeviceProfile().getDeviceProperties().getHeightPx()
                - hotseatTop;

        // Position the content block in the center of the available space.
        return (availableHeight - totalContentHeight) / 2f;
    }

    @Override
    protected float getDepthUnchecked(ActivityContext context) {
        return DEPTH_15_PERCENT;
    }

    @Override
    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(1, 0, 0);
    }

    @Override
    public float getWorkspaceBackgroundAlpha(Launcher launcher) {
        return 0.2f;
    }
}
