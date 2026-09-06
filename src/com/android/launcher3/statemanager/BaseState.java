/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.statemanager;


import com.android.launcher3.DeviceProfile;
import com.android.launcher3.views.ActivityContext;

/**
 * Interface representing a state of a StatefulContainer
 */
public interface BaseState<T> {

    // Flag to indicate that the StatefulContainer is non-interactive in this state
    int FLAG_NON_INTERACTIVE = 1 << 0;
    // Flag to disable restoring the StatefulContainer to this state
    int FLAG_DISABLE_RESTORE_ABSOLUTE = 1 << 1;
    // Flag to disable restoring the StatefulContainer to this state, expect when the
    // StatefulContainer is restarting due to a UI mode change
    int FLAG_DISABLE_RESTORE_EXCEPT_UI_MODE_CHANGE = 1 << 2;
    // Flag to enable interacting a TaskView
    int FLAG_IS_TASK_VIEW_INTERACTIVE = 1 << 3;

    static int getFlag(int index) {
        // reserve few spots to base flags
        return 1 << (index + 4);
    }

    /**
     * @return How long the animation to this state should take (or from this state to NORMAL).
     */
    int getTransitionDuration(ActivityContext context, boolean isToState);

    /**
     * Returns the state to go back to from this state
     */
    T getHistoryForState(T previousState);

    /**
     * @return true if the state can be persisted across activity restarts.
     */
    default boolean shouldDisableRestore() {
        return shouldDisableRestore(/* isUiModeChange= */ false);
    }

    /**
     * @param isUiModeChange whether the activity restart was due to a theme change
     * @return true if the state can be persisted across activity restarts.
     */
    default boolean shouldDisableRestore(boolean isUiModeChange) {
        return isUiModeChange
                ? hasFlag(FLAG_DISABLE_RESTORE_ABSOLUTE)
                : hasFlag(FLAG_DISABLE_RESTORE_ABSOLUTE | FLAG_DISABLE_RESTORE_EXCEPT_UI_MODE_CHANGE);
    }

    default boolean isTaskViewInteractive() {
        return hasFlag(FLAG_IS_TASK_VIEW_INTERACTIVE);
    }

    /**
     * Returns if the state has the provided flag
     */
    boolean hasFlag(int flagMask);

    /**
     * For this state, whether tasks should layout as a grid rather than a list.
     */
    default boolean displayOverviewTasksAsGrid(DeviceProfile deviceProfile) {
        return false;
    }

    /**
     * For this state, whether tasks should show the thumbnail splash.
     */
    default boolean showTaskThumbnailSplash() {
        return false;
    }

    /**
     * For this state, whether we should show desktop exploded view in Overview.
     */
    default boolean showExplodedDesktopView() {
        return isInOverview();
    }

    default boolean isInOverview() {
        return false;
    }

    /**
     * For this state, whether fullscreen and desktop quickswitch carousel are detached.
     */
    default boolean detachDesktopCarousel() {
        return false;
    }

    /**
     * For this state, whether member variables and other forms of data state should be preserved
     * or wiped when the state is reapplied. (See {@link StateManager#reapplyState()})
     */
    default boolean shouldPreserveDataStateOnReapply() {
        return false;
    }

    /**
     * The amount of blur and wallpaper zoom to apply to the background of either the app
     * or StatefulContainer surface in this state. Should be a number between 0 and 1, inclusive.
     * <p>
     * 0 means completely zoomed in, without blurs. 1 is zoomed out, with blurs.
     */
    float getDepth(ActivityContext context);

}
