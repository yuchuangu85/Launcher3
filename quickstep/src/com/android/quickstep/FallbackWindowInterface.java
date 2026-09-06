/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.quickstep.fallback.RecentsState.BACKGROUND_APP;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;
import static com.android.quickstep.fallback.RecentsState.HIDDEN;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.dagger.PerDisplaySingleton;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.util.JoinedAnimator;
import com.android.launcher3.util.ThreadedAnimator;
import com.android.launcher3.views.ScrimColors;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.ContextInitListener;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.quickstep.window.RecentsWindowTracker;

import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.inject.Inject;


/**
 * {@link BaseWindowInterface} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsWindowManager} as opposed
 * to the in-launcher one.
 */
@PerDisplaySingleton
public final class FallbackWindowInterface extends BaseWindowInterface {

    @NonNull private final RecentsWindowTracker mRecentsWindowTracker;

    @Nullable private RecentsWindowManager mRecentsWindowManager = null;

    @Inject
    public FallbackWindowInterface(
            @NonNull RecentsWindowTracker recentsWindowTracker,
            @NonNull TaskAnimationManager taskAnimationManager) {
        super(DEFAULT, BACKGROUND_APP, taskAnimationManager);
        mRecentsWindowTracker = recentsWindowTracker;
    }

    public void setRecentsWindowManager(@Nullable RecentsWindowManager recentsWindowManager) {
        mRecentsWindowManager = recentsWindowManager;
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
    public AnimationFactory<RecentsState, RecentsWindowManager> prepareRecentsUI(
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback) {
        notifyRecentsOfOrientation();
        DefaultAnimationFactory factory =
                new DefaultAnimationFactory(callback) {
                    @Override
                    protected void createBackgroundToOverviewAnim(RecentsWindowManager container,
                            PendingAnimation pa) {
                        super.createBackgroundToOverviewAnim(container, pa);
                        if (container.getDepthController() == null) {
                            return;
                        }

                        // Animate the blur and wallpaper zoom
                        float fromDepthRatio = BACKGROUND_APP.getDepth(container);
                        float toDepthRatio = DEFAULT.getDepth(container);
                        pa.addFloat(container.getDepthController().stateDepth,
                                new LauncherAnimUtils.ClampedProperty<>(
                                        MULTI_PROPERTY_VALUE, fromDepthRatio, toDepthRatio),
                                fromDepthRatio, toDepthRatio, LINEAR);
                    }
                };
        factory.initBackgroundStateUI();
        return factory;
    }

    @Override
    public ContextInitListener<RecentsWindowManager> createActivityInitListener(
            Predicate<Boolean> onInitListener) {
        return new ContextInitListener<>(
                (activity, alreadyOnHome) -> onInitListener.test(alreadyOnHome),
                mRecentsWindowTracker);
    }

    @Nullable
    @Override
    public RecentsWindowManager getCreatedContainer() {
        return mRecentsWindowManager;
    }

    @Nullable
    public SurfaceControl getOverviewOverlay() {
        if (mRecentsWindowManager == null) {
            return null;
        }
        return mRecentsWindowManager.getOverviewOverlay();
    }


    @Override
    public TaskbarInteractor getTaskbarInteractor() {
        RecentsWindowManager manager = getCreatedContainer();
        return manager == null ? null : manager.getTaskbarInteractor();
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTarget target) {
        // TODO: Remove this once b/77875376 is fixed
        return target.screenSpaceBounds;
    }

    @Nullable
    @Override
    public <T extends RecentsView<?, ?>> T getVisibleRecentsView() {
        RecentsWindowManager manager = getCreatedContainer();
        if (manager == null || !manager.isStarted()) {
            return null;
        }
        return manager.getOverviewPanel();
    }

    @Override
    public boolean switchToRecentsIfVisible(Animator.AnimatorListener animatorListener) {
        return false;
    }

    @Override
    protected ScrimColors getOverviewScrimColorForState(RecentsWindowManager container,
            RecentsState state) {
        return state.getScrimColor(container.asContext());
    }

    @Override
    public void onExitOverview(Runnable exitRunnable) {
        RecentsWindowManager windowManager = getCreatedContainer();
        final StateManager<RecentsState, RecentsWindowManager> stateManager =
                windowManager != null ? windowManager.getStateManager() : null;
        if (stateManager == null || stateManager.getState() == HIDDEN) {
            exitRunnable.run();
            notifyRecentsOfOrientation();
            return;
        }

        stateManager.addStateListener(
                new StateManager.StateListener<RecentsState>() {
                    @Override
                    public void onStateTransitionComplete(RecentsState toState) {
                        // Are we going from Recents to Workspace?
                        if (toState == HIDDEN) {
                            exitRunnable.run();
                            notifyRecentsOfOrientation();
                            stateManager.removeStateListener(this);
                        }
                    }
                });
    }

    @Override
    public void onLaunchTaskFailed() {
        RecentsWindowManager manager = getCreatedContainer();
        if (manager == null) {
            return;
        }
        manager.getStateManager().goToState(DEFAULT);
    }

    @Override
    public RecentsState stateFromGestureEndTarget(@NonNull GestureEndTarget endTarget) {
        return switch (endTarget) {
            case RECENTS -> DEFAULT;
            case NEW_TASK, LAST_TASK -> BACKGROUND_APP;
            default -> HIDDEN;
        };
    }

    private void notifyRecentsOfOrientation() {
        RecentsWindowManager recentsWindowManager = getCreatedContainer();
        if (recentsWindowManager != null) {
            // reset layout on swipe to home
            ((RecentsView) recentsWindowManager.getOverviewPanel()).reapplyActiveRotation();
        }
    }

    @Override
    public @Nullable ThreadedAnimator getParallelAnimationToGestureEndTarget(
            GestureEndTarget endTarget, long duration, RecentsAnimationCallbacks callbacks) {
        TaskbarInteractor taskbarInteractor = getTaskbarInteractor();
        ThreadedAnimator superAnimator = super.getParallelAnimationToGestureEndTarget(
                endTarget, duration, callbacks);
        if (taskbarInteractor == null) {
            return superAnimator;
        }
        ThreadedAnimator taskbarAnimator =
                taskbarInteractor.getParallelAnimationToGestureEndTarget(
                        endTarget, duration, callbacks);
        if (taskbarAnimator == null) {
            return superAnimator;
        }
        if (superAnimator == null) {
            return taskbarAnimator;
        }
        return new JoinedAnimator(superAnimator, taskbarAnimator);
    }
}
