/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.touch;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.app.animation.Interpolators.DECELERATED_EASE;
import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_SCRIM_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;

import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.states.StateAnimationConfig;

/**
 * TouchController to switch between NORMAL and ALL_APPS state.
 */
public class AllAppsSwipeController extends AbstractStateChangeTouchController {

    private static final float ALL_APPS_SCRIM_VISIBLE_THRESHOLD = 0.1f;
    private static final float ALL_APPS_STAGGERED_FADE_THRESHOLD = 0.5f;

    private static final Interpolator ALL_APPS_SCRIM_RESPONDER =
            Interpolators.clampToProgress(
                    LINEAR, ALL_APPS_SCRIM_VISIBLE_THRESHOLD, ALL_APPS_STAGGERED_FADE_THRESHOLD);

    // Depth to apply behind All Apps when it's presented on a sheet.
    private static final Interpolator ALL_APPS_SHEET_DEPTH = DECELERATED_EASE;

    public AllAppsSwipeController(Launcher l) {
        super(l, SingleAxisSwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        if (!mLauncher.isInState(NORMAL) && !mLauncher.isInState(ALL_APPS)) {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false;
        }
        if (mLauncher.isInState(ALL_APPS) && !mLauncher.getAppsView().shouldContainerScroll(ev)) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == NORMAL && shouldOpenAllApps(isDragTowardPositive)) {
            return ALL_APPS;
        } else if (fromState == ALL_APPS && !isDragTowardPositive) {
            return NORMAL;
        }
        return fromState;
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        StateAnimationConfig config = getConfigForStates(mFromState, mToState);
        config.duration = (long) (2 * range);

        mCurrentAnimation = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, config);
        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;
        float totalShift = endVerticalShift - startVerticalShift;
        return 1 / totalShift;
    }

    @Override
    protected StateAnimationConfig getConfigForStates(LauncherState fromState,
            LauncherState toState) {
        StateAnimationConfig config = super.getConfigForStates(fromState, toState);
        config.animProps |= StateAnimationConfig.USER_CONTROLLED;
        if (fromState == NORMAL && toState == ALL_APPS) {
            applyNormalToAllAppsAnimConfig(config);
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            applyAllAppsToNormalConfig(config);
        }
        return config;
    }

    /**
     * Applies Animation config values for transition from all apps to home.
     */
    public static void applyAllAppsToNormalConfig(StateAnimationConfig config) {
        config.setInterpolator(ANIM_SCRIM_FADE,
                Interpolators.reverse(ALL_APPS_SCRIM_RESPONDER));
        config.setInterpolator(ANIM_ALL_APPS_FADE, FINAL_FRAME);
        if (!config.isUserControlled()) {
            config.duration = 200;
            config.setInterpolator(ANIM_VERTICAL_PROGRESS, ACCELERATE);
        }
        config.setInterpolator(ANIM_WORKSPACE_SCALE,
                Interpolators.reverse(ALL_APPS_SHEET_DEPTH));
        config.setInterpolator(ANIM_HOTSEAT_SCALE, Interpolators.reverse(ALL_APPS_SHEET_DEPTH));
        config.setInterpolator(ANIM_DEPTH, Interpolators.reverse(ALL_APPS_SHEET_DEPTH));
    }

    /**
     * Applies Animation config values for transition from home to all apps.
     */
    public static void applyNormalToAllAppsAnimConfig(StateAnimationConfig config) {
        config.setInterpolator(ANIM_ALL_APPS_FADE, INSTANT);
        config.setInterpolator(ANIM_SCRIM_FADE, ALL_APPS_SCRIM_RESPONDER);
        if (!config.isUserControlled()) {
            config.setInterpolator(ANIM_VERTICAL_PROGRESS, EMPHASIZED);
        }
        config.setInterpolator(ANIM_WORKSPACE_SCALE, ALL_APPS_SHEET_DEPTH);
        config.setInterpolator(ANIM_HOTSEAT_SCALE, ALL_APPS_SHEET_DEPTH);
        config.setInterpolator(ANIM_DEPTH, ALL_APPS_SHEET_DEPTH);
    }
}
