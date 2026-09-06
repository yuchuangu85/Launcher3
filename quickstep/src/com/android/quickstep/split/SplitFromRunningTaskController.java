/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.split;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_MOVE_TO_SPLIT_SCREEN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_SPLIT_LEFT_TOP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_SPLIT_RIGHT_BOTTOM;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_LEFT_TOP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_RIGHT_BOTTOM;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.window.DesktopExperienceFlags;
import android.window.TransitionInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.SafeCloseable;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ExternalDisplaysKt;
import com.android.quickstep.util.ScalingWorkspaceRevealAnim;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.views.FloatingDesktopTaskView;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.wm.shell.splitscreen.ISplitSelectListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Handles the transition from a fullscreen app to split select, when triggered from desktop mode
 * or keyboard shortcuts.
 */
public class SplitFromRunningTaskController {
    private static final String TAG = "SplitFromRunningTaskController";
    private static final boolean SPLIT_SELECT_ON_EXTERNAL_DISPLAY_ENABLED =
            DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue();
    private final QuickstepLauncher mLauncher;
    private final RecentsWindowManager mRecentsWindowManager;
    private final OverviewComponentObserver mOverviewComponentObserver;
    private final int mSplitPlaceholderSize;
    private final int mSplitPlaceholderInset;
    private final Context mContext;
    private SplitSelectStateController mSplitSelectStateController;
    private RecentsViewContainer mContainer;
    private ActivityManager.RunningTaskInfo mTaskInfo;
    private SafeCloseable mSplitSelectListenerCleanup;
    private Drawable mAppIcon;
    @Nullable
    private RecentsAnimationController mRecentsAnimationController;

    public SplitFromRunningTaskController(RecentsViewContainer recentsViewContainer) {
        mContainer = recentsViewContainer;
        if (recentsViewContainer instanceof QuickstepLauncher) {
            mLauncher = (QuickstepLauncher) recentsViewContainer;
            mRecentsWindowManager = null;
        } else if (recentsViewContainer instanceof RecentsWindowManager) {
            mRecentsWindowManager = (RecentsWindowManager) recentsViewContainer;
            mLauncher = null;
        } else {
            // Unsupported for 3P launcher, no-op and we don't register the listener (below)
            mRecentsWindowManager = null;
            mLauncher = null;
        }

        mContext = mContainer.asContext();
        mOverviewComponentObserver = OverviewComponentObserver.INSTANCE.get(mContext);
        mSplitPlaceholderSize = mContext.getResources()
                .getDimensionPixelSize(R.dimen.split_placeholder_size);
        mSplitPlaceholderInset = mContext.getResources()
                .getDimensionPixelSize(R.dimen.split_placeholder_inset);
        if (mRecentsWindowManager == null && mLauncher == null) {
            return;
        }
        mSplitSelectListenerCleanup = SystemUiProxy.INSTANCE.get(mContext).getSplitSelectListeners()
                .register(new SplitSelectListenerImpl(this));
    }

    /** TODO(b/458362590): We should really avoid this weird circular init dependency thing */
    public void init(SplitSelectStateController splitSelectStateController) {
        mSplitSelectStateController = splitSelectStateController;
    }

    void onDestroy() {
        if (mSplitSelectListenerCleanup != null) {
            mSplitSelectListenerCleanup.close();
            mSplitSelectListenerCleanup = null;
        }
        mContainer = null;
    }

    public ActivityManager.RunningTaskInfo getTaskInfo() {
        return mTaskInfo;
    }

    /**
     * Return whether this instance of {@link SplitSelectStateController} is capable of running
     * the animation for this {@link android.app.ActivityManager.RunningTaskInfo}. Certain
     * controllers can only run animations for tasks on selected displays.
     */
    public boolean ableToStartSplitSelectAnimation(ActivityManager.RunningTaskInfo taskInfo) {
        int displayId = ExternalDisplaysKt.getSafeDisplayId(taskInfo);
        return (displayId == DEFAULT_DISPLAY && mLauncher != null)
                || (displayId != DEFAULT_DISPLAY && mRecentsWindowManager != null);
    }

    /**
     * Enter split select from a running task, this invokes the recents animation to get the
     * current running target to begin the animation, which happens in
     * {@link SplitFromRunningTaskAnimator}
     *
     * @param taskInfo the running task to move to split stage
     * @param splitPosition the stage position used for this transition
     * @param taskBounds the bounds of the task, used for {@link FloatingTaskView} animation
     * @param startRecents if {@code true}, this method must be called on the main thread since
     *                     this adds RecentsAnimationCallbacks which can only be added on main
     *                     thread
     */
    public void enterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
            int splitPosition, Rect taskBounds, StatsLogManager.EventEnum logEvent,
            boolean startRecents, @Nullable WindowContainerTransaction withRecentsWct) {
        Log.v(TAG, "enterSplitSelect, startRecents=" + startRecents);

        mTaskInfo = taskInfo;
        PackageManager pm = mContext.getPackageManager();
        IconProvider provider = new IconProvider(mContext);
        int displayId = ExternalDisplaysKt.getSafeDisplayId(taskInfo);

        try {
            mAppIcon = provider.getIcon(pm.getActivityInfo(mTaskInfo.baseActivity,
                    PackageManager.ComponentInfoFlags.of(0)));
        } catch (PackageManager.NameNotFoundException e) {
            final String packageName =
                    mTaskInfo.realActivity == null
                            ? "(null)"
                            : mTaskInfo.realActivity.getPackageName();
            Log.w(TAG, "Package not found: " + packageName, e);
        }

        final SplitFromRunningTaskAnimator animation = new SplitFromRunningTaskAnimator(
                splitPosition, taskBounds, logEvent);
        final TaskbarInteractor taskbarInteractor = mContainer.getTaskbarInteractor();

        final Runnable updateTaskbarRunnable = () -> {
            if (taskbarInteractor != null) {
                taskbarInteractor.updateTaskbarLauncherStateGoingHome();
            }
        };
        if (startRecents) {
            RecentsAnimationCallbacks callbacks = new RecentsAnimationCallbacks(mContainer);
            callbacks.addListener(new RecentsAnimationCallbacks.RecentsAnimationListener() {
                @Override
                public void onRecentsAnimationStart(RecentsAnimationController controller,
                        RecentsAnimationTargets targets,
                        @Nullable TransitionInfo transitionInfo) {
                    mRecentsAnimationController = controller;
                    animation.start(targets, () ->
                            controller.finish(
                                    /* toHome= */ true,
                                    updateTaskbarRunnable,
                                    /* sendUserLeaveHint= */ false,
                                    /* reason= */ new ActiveGestureLog.CompoundString(
                                            "SplitSelectStateController.enterSplitController")));
                }
            });

            Intent intent = (SPLIT_SELECT_ON_EXTERNAL_DISPLAY_ENABLED
                    && displayId != DEFAULT_DISPLAY)
                    ? mOverviewComponentObserver.getHomeIntent(displayId)
                    : mOverviewComponentObserver.getOverviewIntent();

            UI_HELPER_EXECUTOR.execute(() -> {
                // Transition from app to enter stage split in launcher with recents animation
                final ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
                options.setTransientLaunch();
                options.setLaunchDisplayId(displayId);
                SystemUiProxy.INSTANCE.get(mContext)
                        .startRecentsTransition(
                                intent, options,
                                callbacks, false /* useSyntheticRecentsTransition */,
                                withRecentsWct,
                                displayId);
            });
        } else {
            animation.start(/* targets= */null, updateTaskbarRunnable);
        }
    }

    /** Reset controller state after a split-select transition is finished. */
    public void resetState() {
        mTaskInfo = null;
        mAppIcon = null;
    }

    /**
     * Callback for the recents animation, when invoked it launches into the split staging
     * animation.
     */
    private class SplitFromRunningTaskAnimator {
        private final Rect mTempRect = new Rect();
        private final RectF mTaskBounds = new RectF();
        private final int mSplitPosition;
        private final StatsLogManager.EventEnum mLogEvent;

        SplitFromRunningTaskAnimator(int splitPosition, Rect taskBounds,
                StatsLogManager.EventEnum logEvent) {
            mSplitPosition = splitPosition;
            mTaskBounds.set(taskBounds);
            mLogEvent = logEvent;
        }

        void start(
                @Nullable RecentsAnimationTargets targets,
                @NonNull Runnable finishController) {
            mSplitSelectStateController.setInitialTaskSelect(mTaskInfo, mSplitPosition,
                    null, mLogEvent);
            if (mRecentsWindowManager != null) {
                mRecentsWindowManager.showRecentsWindow(null);
            }

            final SurfaceTransactionApplier surfaceApplier =
                    new SurfaceTransactionApplier(mContainer.getDragLayer());

            final RecentsView recentsView = mContainer.getOverviewPanel();
            recentsView.getPagedOrientationHandler().getInitialSplitPlaceholderBounds(
                    mSplitPlaceholderSize, mSplitPlaceholderInset,
                    mContainer.getDeviceProfile(),
                    mSplitSelectStateController.getActiveSplitStagePosition(), mTempRect);

            final PendingAnimation anim = new PendingAnimation(
                    SplitAnimationTimings.TABLET_HOME_TO_SPLIT.getDuration());
            List<FloatingDesktopTaskView> closingTaskViews =
                    setUpClosingWindowViews(anim, targets);
            final FloatingTaskView floatingTaskView = setUpStagingTaskView(anim);

            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    InteractionJankMonitorWrapper.begin(
                            floatingTaskView, CUJ_DESKTOP_MODE_MOVE_TO_SPLIT_SCREEN);
                    if (targets == null) {
                        return;
                    }

                    SurfaceTransaction transaction = new SurfaceTransaction();
                    // Hide all app targets, to allow Launcher to use views to animate the apps
                    for (RemoteAnimationTarget target : targets.apps) {
                        transaction.getTransaction().hide(target.leash);
                    }
                    showHomeTarget(transaction, targets);
                    surfaceApplier.scheduleApply(transaction);
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    InteractionJankMonitorWrapper.end(CUJ_DESKTOP_MODE_MOVE_TO_SPLIT_SCREEN);
                    for (FloatingDesktopTaskView taskView : closingTaskViews) {
                        mContainer.getDragLayer().removeView(taskView);
                    }
                    finishController.run();
                }
                @Override
                public void onAnimationCancel(Animator animation) {
                    InteractionJankMonitorWrapper.cancel(CUJ_DESKTOP_MODE_MOVE_TO_SPLIT_SCREEN);
                    mContainer.getDragLayer().removeView(floatingTaskView);
                    mSplitSelectStateController.getSplitAnimationController()
                            .removeSplitInstructionsView(mContainer);
                    for (FloatingDesktopTaskView taskView : closingTaskViews) {
                        mContainer.getDragLayer().removeView(taskView);
                    }
                    finishController.run();
                    mSplitSelectStateController.resetState();
                }
            });
            anim.add(mSplitSelectStateController.getSplitAnimationController()
                    .getShowSplitInstructionsAnim(mContainer).buildAnim());
            if (mContainer instanceof QuickstepLauncher) {
                // TODO: b/438065072 - Support createHomeRevealAnimation in non-default display
                //  when there is no launcher.
                anim.add(createHomeRevealAnimation());
            }
            anim.buildAnim().start();
        }

        private List<FloatingDesktopTaskView> setUpClosingWindowViews(
                PendingAnimation anim, RecentsAnimationTargets targets) {
            if (targets == null) {
                return Collections.emptyList();
            }
            RemoteAnimationTarget[] appTargets = Arrays.stream(targets.apps)
                    // Fetch all closing freeform targets, except the staging task
                    .filter(target ->
                            target.taskInfo != null
                                    && target.taskInfo.isFreeform()
                                    && target.taskInfo.taskId != mTaskInfo.taskId
                                    && target.mode == RemoteAnimationTarget.MODE_CLOSING)
                    .toArray(RemoteAnimationTarget[]::new);
            List<FloatingDesktopTaskView> floatingTaskViews = new ArrayList<>();
            // Targets are ordered top-to-bottom, so iterate backwards here to add bottom task
            // views first as newly added views are added in front.
            for (int i = appTargets.length - 1; i >= 0; i--) {
                RemoteAnimationTarget appTarget = appTargets[i];
                RectF startBounds = new RectF(appTarget.localBounds);
                final FloatingDesktopTaskView taskView =
                        FloatingDesktopTaskView.Companion.create(
                                mContainer, startBounds, getTaskThumbnail(appTarget.taskInfo));
                taskView.setAlpha(1);
                taskView.addClosingAnimation(mContainer, anim);
                floatingTaskViews.add(taskView);
            }
            return floatingTaskViews;
        }

        private FloatingTaskView setUpStagingTaskView(PendingAnimation anim) {
            FloatingTaskView floatingTaskView = FloatingTaskView.getFloatingTaskView(
                    mContainer, mContainer.getDragLayer(),
                    getTaskThumbnail(mTaskInfo),
                    mAppIcon, /* positionOut= */ new RectF());
            floatingTaskView.setOnClickListener(view -> {
                InteractionJankMonitorWrapper.cancel(CUJ_DESKTOP_MODE_MOVE_TO_SPLIT_SCREEN);
                SystemUiProxy.INSTANCE.get(mContext)
                        .onDesktopSplitSelectChoice(mTaskInfo);
                mSplitSelectStateController.getSplitAnimationController()
                        .playAnimPlaceholderToFullscreen(mContainer, view,
                                Optional.of(() -> mSplitSelectStateController.resetState()));
            });
            floatingTaskView.setUseFitXYThumbnailScale();
            floatingTaskView.setAlpha(1);
            floatingTaskView.addStagingAnimation(anim, mTaskBounds, mTempRect,
                    true /* fadeWithThumbnail */, true /* isStagedTask */);
            mSplitSelectStateController.setFirstFloatingTaskView(floatingTaskView);
            return floatingTaskView;
        }

        private @Nullable Bitmap getTaskThumbnail(ActivityManager.RunningTaskInfo taskInfo) {
            if (taskInfo == null) return null;
            if (mRecentsAnimationController == null) return null;
            return mRecentsAnimationController.screenshotTask(taskInfo.taskId).getThumbnail();
        }

        private AnimatorSet createHomeRevealAnimation() {
            return new ScalingWorkspaceRevealAnim(mLauncher, /* siblingAnimation= */ null,
                    /* windowTargetRect= */ null, true /* playAlphaReveal */,
                    true /* playBlur */)
                    .getAnimators();
        }

        private static void showHomeTarget(
                SurfaceTransaction transaction, RemoteAnimationTargets targets) {
            RemoteAnimationTarget homeTarget = Arrays.stream(targets.unfilteredApps)
                    .filter(target -> target.taskInfo != null
                            && target.taskInfo.topActivityType == ACTIVITY_TYPE_HOME)
                    .findAny()
                    .orElse(null);
            if (homeTarget == null) return;
            transaction.getTransaction().setAlpha(homeTarget.leash, 1f);
        }
    }

    /**
     * Wrapper for the ISplitSelectListener stub to prevent lingering references to the launcher
     * activity via the controller.
     */
    private static class SplitSelectListenerImpl extends ISplitSelectListener.Stub {

        private final SplitFromRunningTaskController mController;

        SplitSelectListenerImpl(@NonNull SplitFromRunningTaskController controller) {
            mController = controller;
        }

        @Override
        public void onRequestSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
                int splitPosition, Rect taskBounds, boolean startRecents,
                @Nullable WindowContainerTransaction withRecentsWct) {
            if (!mController.ableToStartSplitSelectAnimation(taskInfo)) {
                Log.v(TAG, "onRequestSplitSelect, controller not able to start "
                        + "animation for taskId: " + taskInfo.taskId);
                return;
            }
            MAIN_EXECUTOR.execute(() -> {
                final StatsLogManager.LauncherEvent logEvent;
                boolean leftOrTop = splitPosition == STAGE_POSITION_BOTTOM_OR_RIGHT;
                if (taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
                    logEvent = leftOrTop
                            ? LAUNCHER_DESKTOP_MODE_SPLIT_RIGHT_BOTTOM
                            : LAUNCHER_DESKTOP_MODE_SPLIT_LEFT_TOP;
                } else {
                    logEvent = leftOrTop
                            ? LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_LEFT_TOP
                            : LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_RIGHT_BOTTOM;
                }
                mController.enterSplitSelect(taskInfo, splitPosition, taskBounds,
                        logEvent, startRecents, withRecentsWct);
            });
        }
    }
}
