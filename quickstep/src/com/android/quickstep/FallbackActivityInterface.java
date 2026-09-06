/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.quickstep.fallback.RecentsState.BACKGROUND_APP;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;
import static com.android.quickstep.fallback.RecentsState.HOME;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.JoinedAnimator;
import com.android.launcher3.util.ThreadedAnimator;
import com.android.launcher3.views.ScrimColors;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.ContextInitListener;
import com.android.quickstep.views.RecentsView;

import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.inject.Inject;

/**
 * {@link BaseActivityInterface} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsActivity} as opposed
 * to the in-launcher one.
 */
@LauncherAppSingleton
public final class FallbackActivityInterface extends
        BaseActivityInterface<RecentsState, RecentsActivity> {

    public static final DaggerSingletonObject<FallbackActivityInterface> INSTANCE =
            new DaggerSingletonObject<>(QuickstepBaseAppComponent::getFallbackActivityInterface);

    @Inject
    public FallbackActivityInterface(
            @NonNull PerDisplayRepository<TaskAnimationManager> taskAnimationManagerRepo) {
        super(false, DEFAULT, BACKGROUND_APP, taskAnimationManagerRepo.get(DEFAULT_DISPLAY));
    }

    /** 2 */
    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect, orientationHandler);
        if (dp.isVerticalBarLayout() && DisplayController.getNavigationMode(context) != NO_BUTTON) {
            return dp.isSeascape() ? outRect.left : (dp.getDeviceProperties().getWidthPx() - outRect.right);
        } else {
            return dp.getDeviceProperties().getHeightPx() - outRect.bottom;
        }
    }

    /** 5 */
    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        // This class becomes active when the screen is locked.
        // Rather than having it handle assistant visibility changes, the assistant visibility is
        // set to zero prior to this class becoming active.
    }

    /** 6 */
    @Override
    public AnimationFactory prepareRecentsUI(
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback) {
        notifyRecentsOfOrientation();
        DefaultAnimationFactory factory = new DefaultAnimationFactory(callback);
        factory.initBackgroundStateUI();
        return factory;
    }

    @Override
    public ContextInitListener<RecentsActivity> createActivityInitListener(
            Predicate<Boolean> onInitListener) {
        return new ContextInitListener<>((activity, alreadyOnHome) ->
                onInitListener.test(alreadyOnHome), RecentsActivity.ACTIVITY_TRACKER);
    }

    @Nullable
    @Override
    public RecentsActivity getCreatedContainer() {
        return RecentsActivity.ACTIVITY_TRACKER.getCreatedContext();
    }

    @Override
    public TaskbarInteractor getTaskbarInteractor() {
        RecentsActivity activity = getCreatedContainer();
        if (activity == null) {
            return null;
        }
        return activity.getTaskbarInteractor();
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        RecentsActivity activity = getCreatedContainer();
        if (activity != null) {
            if (activity.hasBeenResumed() || isInLiveTileMode()) {
                return activity.getOverviewPanel();
            }
        }
        return null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Animator.AnimatorListener animatorListener) {
        return false;
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTarget target) {
        // TODO: Remove this once b/77875376 is fixed
        return target.screenSpaceBounds;
    }

    @Override
    public boolean deferStartingActivity(
            @NonNull RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        // In non-gesture mode, user might be clicking on the home button which would directly
        // start the home activity instead of going through recents. In that case, defer starting
        // recents until we are sure it is a gesture.
        return !deviceState.isFullyGesturalNavMode()
                || super.deferStartingActivity(deviceState, ev);
    }

    @Override
    public void onExitOverview(Runnable exitRunnable) {
        final StateManager<RecentsState, RecentsActivity> stateManager =
                getCreatedContainer().getStateManager();
        if (stateManager.getState() == HOME) {
            exitRunnable.run();
            notifyRecentsOfOrientation();
            return;
        }

        stateManager.addStateListener(
                new StateManager.StateListener<RecentsState>() {
                    @Override
                    public void onStateTransitionComplete(RecentsState toState) {
                        // Are we going from Recents to Workspace?
                        if (toState == HOME) {
                            exitRunnable.run();
                            notifyRecentsOfOrientation();
                            stateManager.removeStateListener(this);
                        }
                    }
                });
    }

    @Override
    public void onLaunchTaskFailed() {
        RecentsActivity activity = getCreatedContainer();
        if (activity == null) {
            return;
        }
        activity.getStateManager().goToState(DEFAULT);
    }

    @Override
    public RecentsState stateFromGestureEndTarget(@NonNull GestureEndTarget endTarget) {
        return switch (endTarget) {
            case RECENTS -> DEFAULT;
            case NEW_TASK, LAST_TASK -> BACKGROUND_APP;
            default -> HOME;
        };
    }

    private void notifyRecentsOfOrientation() {
        // reset layout on swipe to home
        getCreatedContainer().getOverviewPanel().reapplyActiveRotation();
    }

    @Override
    public @Nullable ThreadedAnimator getParallelAnimationToGestureEndTarget(
            GestureEndTarget endTarget, long duration, RecentsAnimationCallbacks callbacks) {
        TaskbarInteractor interactor = getTaskbarInteractor();
        ThreadedAnimator superAnimator = super.getParallelAnimationToGestureEndTarget(
                endTarget, duration, callbacks);
        if (interactor == null) {
            return superAnimator;
        }
        ThreadedAnimator taskbarAnimator = interactor.getParallelAnimationToGestureEndTarget(
                endTarget, duration, callbacks);
        if (taskbarAnimator == null) {
            return superAnimator;
        }
        if (superAnimator == null) {
            return taskbarAnimator;
        }
        return new JoinedAnimator(superAnimator, taskbarAnimator);
    }

    @Override
    protected ScrimColors getOverviewScrimColorForState(RecentsActivity activity,
            RecentsState state) {
        return state.getScrimColor(activity);
    }
}
