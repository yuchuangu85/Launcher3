/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.role.RoleManager.ROLE_HOME;
import static android.provider.Settings.Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN;

import static com.android.app.animation.Interpolators.AGGRESSIVE_EASE;
import static com.android.app.animation.Interpolators.DECELERATE_1_5;
import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.EXAGGERATED_EASE;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.internal.util.LatencyTracker.ACTION_DESKTOP_MODE_EXIT_MODE_ON_LAST_WINDOW_CLOSE;
import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.BaseActivity.INVISIBLE_ALL;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_APP_TRANSITIONS;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_PENDING_FLAGS;
import static com.android.launcher3.BaseActivity.PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;
import static com.android.launcher3.Flags.appLaunchBlur;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.getScaleProperty;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.mapBoundToRange;
import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;
import static com.android.launcher3.desktop.DesktopAppLaunchTransitionManager.createDesktopAppLaunchRemoteTransition;
import static com.android.launcher3.desktop.DesktopAppLaunchTransitionManager.isDesktopAppLaunch;
import static com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION_WITHOUT_ICON_ALIGNMENT;
import static com.android.launcher3.testing.shared.TestProtocol.WALLPAPER_OPEN_ANIMATION_FINISHED_MESSAGE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.TaskViewUtils.findTaskViewToLaunch;
import static com.android.quickstep.util.AnimUtils.clampToDuration;
import static com.android.quickstep.util.AnimUtils.completeRunnableListCallback;
import static com.android.quickstep.util.FloatingIconViewHelper.getFloatingIconView;
import static com.android.systemui.shared.Flags.enableRecentsInTaskbar;
import static com.android.systemui.shared.system.QuickStepContract.getWindowCornerRadius;
import static com.android.systemui.shared.system.QuickStepContract.supportsRoundedCornersOnWindows;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationDefinition;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.window.DesktopModeFlags;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.RemoteTransitionStub;
import android.window.TransitionFilter;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Animations;
import com.android.app.animation.Interpolators;
import com.android.internal.jank.Cuj;
import com.android.internal.util.LatencyTracker;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.desktop.DesktopAppLaunchTransition.AppLaunchType;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.remoteanimations.AnimOpenProperties;
import com.android.launcher3.remoteanimations.ContainerAnimationRunner;
import com.android.launcher3.remoteanimations.RemoteAnimationCoordinateTransfer;
import com.android.launcher3.remoteanimations.SpringAnimRunner;
import com.android.launcher3.remoteanimations.StartingWindowListener;
import com.android.launcher3.remotetransitions.IRemoteTransitionEx;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.StableViewInfo;
import com.android.launcher3.util.TaskbarAsyncAnimator;
import com.android.launcher3.views.FloatingIconView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.quickstep.AnimatedSurfaces;
import com.android.quickstep.HomeVisibilityState;
import com.android.quickstep.LauncherBackAnimationController;
import com.android.quickstep.SplitRecentsAnimUtils;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.util.AlreadyStartedBackAnimState;
import com.android.quickstep.util.AnimatorBackState;
import com.android.quickstep.util.BackAnimState;
import com.android.quickstep.util.CrossDisplayMoveTransition;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.RectFSpringAnim.DefaultSpringConfig;
import com.android.quickstep.util.RectFSpringAnim.WidgetSpringConfig;
import com.android.quickstep.util.ScalingWorkspaceRevealAnim;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskRestartedDuringLaunchListener;
import com.android.quickstep.util.WorkspaceRevealAnim;
import com.android.quickstep.views.FloatingWidgetView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.animation.RemoteAnimationRunnerCompat;
import com.android.systemui.animation.RemoteTransitionPickerDelegate;
import com.android.systemui.shared.system.BlurUtils;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.wm.shell.shared.compat.AnimatedSurface;
import com.android.wm.shell.shared.compat.AnimatedSurfaceUtils;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Manages the opening and closing app transitions from Launcher
 */
public class QuickstepTransitionManager implements OnDeviceProfileChangeListener {
    private static final String TAG = "QuickstepTransitionManager";

    private static final boolean ENABLE_SHELL_STARTING_SURFACE =
            SystemProperties.getBoolean("persist.debug.shell_starting_surface", true);

    /** Duration of status bar animations. */
    public static final int STATUS_BAR_TRANSITION_DURATION = 120;

    /**
     * Since our animations decelerate heavily when finishing, we want to start status bar
     * animations x ms before the ending.
     */
    public static final int STATUS_BAR_TRANSITION_PRE_DELAY = 96;

    public static final long APP_LAUNCH_DURATION = 500;

    private static final long APP_LAUNCH_ALPHA_DURATION = 50;
    private static final long APP_LAUNCH_ALPHA_START_DELAY = 25;

    public static final int ANIMATION_NAV_FADE_IN_DURATION = 266;
    public static final int ANIMATION_NAV_FADE_OUT_DURATION = 133;
    public static final long ANIMATION_DELAY_NAV_FADE_IN =
            APP_LAUNCH_DURATION - ANIMATION_NAV_FADE_IN_DURATION;
    public static final Interpolator NAV_FADE_IN_INTERPOLATOR =
            new PathInterpolator(0f, 0f, 0f, 1f);
    public static final Interpolator NAV_FADE_OUT_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 1f, 1f);

    public static final int RECENTS_LAUNCH_DURATION = 336;
    private static final int LAUNCHER_RESUME_START_DELAY = 100;
    private static final int CLOSING_TRANSITION_DURATION_MS = 250;
    public static final int SPLIT_LAUNCH_DURATION = 370;
    public static final int SPLIT_DIVIDER_ANIM_DURATION = 100;

    public static final int CONTENT_ALPHA_DURATION = 217;
    public static final int TRANSIENT_TASKBAR_TRANSITION_DURATION = 417;
    public static final int PINNED_TASKBAR_TRANSITION_DURATION = 600;
    public static final int TASKBAR_TO_APP_DURATION = 600;
    // TODO(b/236145847): Tune TASKBAR_TO_HOME_DURATION to 383 after conflict with unlock animation
    // is solved.
    private static final int TASKBAR_TO_HOME_DURATION_FAST = 300;
    private static final int TASKBAR_TO_HOME_DURATION_SLOW = 1000;
    protected static final int CONTENT_SCALE_DURATION = 350;

    // Cross-fade duration between App Widget and App when launching from widget.
    private static final int WIDGET_CROSSFADE_DURATION_MILLIS = 125;
    // The progress at which a window closing into a widget becomes fully transparent.
    private static final float WIDGET_CLOSE_ALPHA_END_PROGRESS = 0.40f;
    private static final float WIDGET_CLOSE_ALPHA_END_PROGRESS_LEGACY = 0.85f;

    private static final float MAX_SCRIM_ALPHA_DARK = 0.8f;
    private static final float MAX_SCRIM_ALPHA_LIGHT = 0.2f;

    private final RunnableList mCleanupTask = new RunnableList();
    protected final QuickstepLauncher mLauncher;
    protected final DragLayer mDragLayer;

    protected final Handler mHandler;

    private final float mClosingWindowTransY;
    private final float mClosingFreeformWindowTransY;
    private final float mMaxShadowRadius;
    private final int mMaxBlurRadius;
    private final boolean mIsAppLaunchBlurEnabled;

    private final StartingWindowListener mStartingWindowListener = new StartingWindowListener();

    // TODO(b/397690719): Investigate the memory leak from TaskStackChangeListeners#mImpl
    // This is a temporary fix of memory leak b/397690719. We track registered
    // {@link TaskRestartedDuringLaunchListener}, and remove them on activity destroy.
    private final List<TaskRestartedDuringLaunchListener> mRegisteredTaskStackChangeListener =
            new ArrayList<>();

    private DeviceProfile mDeviceProfile;

    // Strong refs to runners which are cleared when the launcher activity is destroyed
    private RemoteAnimationFactory mWallpaperOpenRunner;
    private RemoteAnimationFactory mAppLaunchRunner;
    private IRemoteTransition mAppLaunchTransition;

    private RemoteAnimationFactory mWallpaperOpenTransitionRunner;
    private RemoteTransition mLauncherOpenTransition;
    private RemoteTransition mMoveDisplayTransition;

    private final LatencyTracker mLatencyTracker;

    // Preemptive animation run whenever Launcher reappears and its mode is NORMAL (unless coming
    // from Keyguard). This animation is only triggered if another reveal animation is not already
    // running, and is cancelled if another animation starts as part of transition handling. The
    // reason for this backup is that sometimes the transition is animated by a different process
    // (e.g. System UI), but we still want the contents of Launcher to animate instead of just
    // popping in statically.
    private ScalingWorkspaceRevealAnim mFallbackRevealAnimation;
    private boolean mIsLauncherAnimating = false;

    private LauncherBackAnimationController mBackAnimationController;
    private final AnimatorListenerAdapter mForceInvisibleListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            mLauncher.addForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mLauncher.clearForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS);
        }
    };


    private final Interpolator mOpeningXInterpolator;
    private final Interpolator mOpeningInterpolator;

    private final SystemUiProxy mSystemUiProxy;

    private HomeVisibilityState.VisibilityChangeListener mHomeVisibilityChangeListener;

    public QuickstepTransitionManager(QuickstepLauncher launcher) {
        mLauncher = launcher;
        mDragLayer = mLauncher.getDragLayer();
        mHandler = new Handler(Looper.getMainLooper());
        mDeviceProfile = mLauncher.getDeviceProfile();
        mBackAnimationController = new LauncherBackAnimationController(mLauncher, this);

        Resources res = mLauncher.getResources();
        mClosingWindowTransY = res.getDimensionPixelSize(R.dimen.closing_window_trans_y);
        mClosingFreeformWindowTransY =
                res.getDimensionPixelSize(R.dimen.closing_freeform_window_trans_y);
        mMaxShadowRadius = res.getDimensionPixelSize(R.dimen.max_shadow_radius);

        mLauncher.addOnDeviceProfileChangeListener(this);
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mLauncher);

        if (ENABLE_SHELL_STARTING_SURFACE) {
            mCleanupTask.add(mSystemUiProxy.getStartingWindowListeners()
                    .register(mStartingWindowListener)::close);
        }

        if (Flags.fallbackRevealAnimation()) {
            // Make sure that we know whenever Launcher becomes visible AND is in its NORMAL state,
            // so we can run the reveal animation.
            mHomeVisibilityChangeListener = new HomeVisibilityState.VisibilityChangeListener() {
                @Override
                public boolean handleDesktopVisibilityOnlyChanges() {
                    return false;
                }

                @Override
                public void onHomeVisibilityChanged(boolean isVisible,
                        boolean keyguardGoingAwayOrWaking, boolean behindDesktop) {
                    if (isVisible && mLauncher.isInState(NORMAL) && !mIsLauncherAnimating
                            && !keyguardGoingAwayOrWaking) {
                        mIsLauncherAnimating = true;
                        mFallbackRevealAnimation =
                                new ScalingWorkspaceRevealAnim(
                                        mLauncher, null /* siblingAnimation */,
                                        null /* windowTargetRect */, true /* playAlphaReveal */,
                                        true /* playBlur */);
                        mFallbackRevealAnimation.getAnimators().addListener(
                                new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        mIsLauncherAnimating = false;
                                    }
                                });
                        mFallbackRevealAnimation.start();
                    }
                }
            };
            mSystemUiProxy.getHomeVisibilityState().addListener(mHomeVisibilityChangeListener);
        }

        mOpeningXInterpolator = AnimationUtils.loadInterpolator(
                launcher, R.interpolator.app_open_x);
        mOpeningInterpolator = AnimationUtils.loadInterpolator(
                launcher, R.interpolator.emphasized_interpolator);
        mLatencyTracker = LatencyTracker.getInstance(launcher);

        mMaxBlurRadius = res.getDimensionPixelSize(
                R.dimen.max_depth_blur_radius_enhanced);
        mIsAppLaunchBlurEnabled = appLaunchBlur() && res.getBoolean(
                com.android.internal.R.bool.config_enableAppLaunchBlur);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mDeviceProfile = dp;
    }

    private void startCrossDisplayMoveAnimation(TransitionInfo info, SurfaceControl.Transaction t,
            IRemoteTransitionFinishedCallback finishCallback) {
        mHandler.post(() -> {
            // If launcher was destroyed between animation start and the post, don't give out a
            // ref to that launcher, just finish the transition immediately.
            if (mLauncher.isDestroyed()) {
                try {
                    finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
                } catch (RemoteException e) {
                    // Ignore.
                }
            } else {
                CrossDisplayMoveTransition.startCrossDisplayMoveAnimation(mLauncher,
                        APP_LAUNCH_DURATION, CLOSING_TRANSITION_DURATION_MS, info, t,
                        finishCallback);
            }
        });
    }

    /**
     * A {@link RemoteTransitionStub} that handles cross display move animations.
     */
    private static class MoveDisplayChangeRunner extends RemoteTransitionStub {
        private final java.lang.ref.WeakReference<QuickstepTransitionManager> mManagerRef;

        MoveDisplayChangeRunner(QuickstepTransitionManager manager) {
            // Ensure we don't use a strong to the manager; we don't want to extend its lifetime if
            // the manager happens to be destroyed before the animation binder collects.
            mManagerRef = new java.lang.ref.WeakReference<>(manager);
        }

        @Override
        public void startAnimation(IBinder token, TransitionInfo info, SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
            final QuickstepTransitionManager manager = mManagerRef.get();
            if (manager != null) {
                manager.startCrossDisplayMoveAnimation(info, t, finishCallback);
            } else {
                finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
            }
        }
    }

    /**
     * @return ActivityOptions with remote animations that controls how the window of the opening
     * targets are displayed.
     */
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, ItemInfo itemInfo) {
        boolean fromRecents = isLaunchingFromRecents(v, null /* targets */);
        RunnableList onEndCallback = new RunnableList();

        // Handle the case where an already visible task is launched which results in no transition
        TaskRestartedDuringLaunchListener restartedListener =
                new TaskRestartedDuringLaunchListener();
        restartedListener.register(onEndCallback::executeAllAndDestroy);
        mRegisteredTaskStackChangeListener.add(restartedListener);
        onEndCallback.add(new Runnable() {
            @Override
            public void run() {
                restartedListener.unregister();
                mRegisteredTaskStackChangeListener.remove(restartedListener);
            }
        });

        RemoteAnimationRunnerCompat appLaunchRunner = createAppLaunchRunner(v, onEndCallback);
        IRemoteTransition appLaunchRemoteTransition =
                createAppLaunchRemoteTransition(
                        appLaunchRunner, onEndCallback::executeAllAndDestroy);

        RemoteTransition remoteTransition = new RemoteTransition(appLaunchRemoteTransition,
                mLauncher.getIApplicationThread(), "QuickstepLaunch");

        if (com.android.window.flags.Flags.crossDisplayTransitionV2()) {
            TransitionFilter filter = new TransitionFilter();
            filter.mRequirements = new TransitionFilter.Requirement[]{
                    new TransitionFilter.Requirement()};

            // This animation should not run on cross-display transitions.
            filter.mRequirements[0].mNot = true;
            filter.mRequirements[0].mIsCrossDisplayMove = true;
            remoteTransition.setFilter(filter);
        }

        // Note that this duration is a guess as we do not know if the animation will be a
        // recents launch or not for sure until we know the opening app targets.
        long duration = fromRecents
                ? RECENTS_LAUNCH_DURATION
                : APP_LAUNCH_DURATION;

        long statusBarTransitionDelay = duration - STATUS_BAR_TRANSITION_DURATION
                - STATUS_BAR_TRANSITION_PRE_DELAY;
      ActivityOptions options = ActivityOptions.makeRemoteAnimation(
              new RemoteAnimationAdapter(appLaunchRunner, duration, statusBarTransitionDelay),
              remoteTransition);
        IRemoteCallback endCallback = completeRunnableListCallback(
                onEndCallback, mLauncher, MAIN_EXECUTOR);
        options.setOnAnimationAbortListener(endCallback);
        options.setOnAnimationFinishedListener(endCallback);
        options.setLaunchCookie(StableViewInfo.toLaunchCookie(itemInfo));

        // Prepare taskbar for animation synchronization. This needs to happen here before any
        // app transition is created.
        TaskbarInteractor taskbarInteractor = mLauncher.getTaskbarInteractor();
        if (mLauncher.getStateManager().getState() == NORMAL
                && taskbarInteractor != null
                // Disable synchronization for widgets due to issues with PendingIntent.
                && (itemInfo != null && itemInfo.itemType != ITEM_TYPE_APPWIDGET)) {
            taskbarInteractor.setIgnoreInAppFlagForSync(true);
            mLauncher.addEventCallback(EVENT_DESTROYED, onEndCallback::executeAllAndDestroy);
            onEndCallback.add(() -> {
                taskbarInteractor.setIgnoreInAppFlagForSync(false);
            });
        }

        return new ActivityOptionsWrapper(options, onEndCallback);
    }

    /**
     * Selects the appropriate type of launch runner for the given view, builds it, and returns it.
     * {@link QuickstepTransitionManager#mAppLaunchRunner} is updated as a by-product of this
     * method.
     */
    private RemoteAnimationRunnerCompat createAppLaunchRunner(View v, RunnableList onEndCallback) {
        ItemInfo tag = (ItemInfo) v.getTag();
        ContainerAnimationRunner containerRunner = null;
        if (tag != null && tag.shouldUseBackgroundAnimation()) {
            containerRunner = ContainerAnimationRunner.fromView(
                    v, true /* forLaunch */, mLauncher, mStartingWindowListener, onEndCallback,
                    null /* windowState */);
        }

        mAppLaunchRunner = containerRunner != null
                ? containerRunner : new AppLaunchAnimationRunner(v, onEndCallback);
        return new LauncherAnimationRunner(
                mHandler, mAppLaunchRunner, true /* startAtFrontOfQueue */);
    }

    /**
     * Creates a remote transition for app launches.
     *
     * The app launch runner picks an animation based on the view type (e.g. icon, widget).
     *
     * However, for cross-display moves, we need to pick a different animation based on the
     * (dynamic) TransitionInfo content. This is controlled by the
     * enableCrossDisplaysAppLaunchTransition flag.
     *
     * If the flag is enabled, the returned transition will:
     * 1. Check if the transition is a cross-display move, and if so, use the cross-display
     *    animation.
     * 2. Otherwise, use the default app launch animation from the runner.
     *
     * If the flag is disabled, this will always return the default app launch animation.
     */
    private IRemoteTransition createAppLaunchRemoteTransition(
            RemoteAnimationRunnerCompat defaultAppLaunchRunner,
            Runnable onEndCallback
    ) {
        IRemoteTransition defaultAppLaunchTransition = defaultAppLaunchRunner.toRemoteTransition();
        if (!com.android.window.flags.Flags.enableCrossDisplaysAppLaunchTransition()
                || com.android.window.flags.Flags.crossDisplayTransitionV2()) {
            return defaultAppLaunchTransition;
        }

        IRemoteTransition crossDisplayMoveTransition = new MoveDisplayChangeRunner(this);
        mAppLaunchTransition = new RemoteTransitionPickerDelegate(
                (info) -> {
                    if (CrossDisplayMoveTransition.isCrossDisplayMove(info)) {
                        Log.d(TAG, "Handling launch as a cross display move transition");
                        return crossDisplayMoveTransition;
                    } else if (isDesktopAppLaunch(mLauncher.getApplicationContext(), info)) {
                        Log.d(TAG, "Handling launch as a Desktop app launch transition");
                        return createDesktopAppLaunchRemoteTransition(
                                mLauncher.getApplicationContext(),
                                AppLaunchType.LAUNCH,
                                Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_INTENT,
                                onEndCallback);
                    } else {
                        return defaultAppLaunchTransition;
                    }
                });
        return IRemoteTransitionEx.toWeakRef(mAppLaunchTransition);
    }

    /**
     * Whether the launch is a recents app transition and we should do a launch animation
     * from the recents view. Note that if the remote animation targets are not provided, this
     * may not always be correct as we may resolve the opening app to a task when the animation
     * starts.
     *
     * @param v         the view to launch from
     * @param surfaces  apps that are opening/closing
     * @return true if the app is launching from recents, false if it most likely is not
     */
    protected boolean isLaunchingFromRecents(@NonNull View v,
            @Nullable AnimatedSurface[] surfaces) {
        return mLauncher.getStateManager().getState().isRecentsViewVisible
                && findTaskViewToLaunch(mLauncher.getOverviewPanel(), v, surfaces) != null;
    }

    /**
     * Composes the animations for a launch from the recents list.
     *
     * @param anim            the animator set to add to
     * @param v               the launching view
     * @param appTargets      the apps that are opening/closing
     * @param launcherClosing true if the launcher app is closing
     */
    protected void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets, boolean launcherClosing) {
        TaskViewUtils.composeRecentsLaunchAnimator(anim, v, appTargets, wallpaperTargets,
                nonAppTargets, launcherClosing, mLauncher.getStateManager(),
                mLauncher.getOverviewPanel(), mLauncher.getDepthController(),
                /* transitionInfo= */ null, /* appearedTaskId= */ INVALID_TASK_ID);
    }

    private boolean areAllSurfacesTranslucent(@NonNull AnimatedSurface[] surfaces) {
        boolean isAllOpeningTargetTrs = true;
        for (int i = 0; i < surfaces.length; i++) {
            AnimatedSurface surface = surfaces[i];
            if (AnimatedSurfaceUtils.isOpening(surface)) {
                isAllOpeningTargetTrs &= surface.isTranslucent;
            }
            if (!isAllOpeningTargetTrs) break;
        }
        return isAllOpeningTargetTrs;
    }

    /**
     * Compose the animations for a launch from the app icon.
     *
     * @param anim            the animation to add to
     * @param v               the launching view with the icon
     * @param appSurfaces     the list of opening/closing apps
     * @param launcherClosing true if launcher is closing
     */
    private void composeIconLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull AnimatedSurface[] appSurfaces,
            @NonNull AnimatedSurface[] wallpaperSurfaces,
            @NonNull AnimatedSurface[] nonAppSurfaces,
            boolean launcherClosing) {
        // Set the state animation first so that any state listeners are called
        // before our internal listeners.
        mLauncher.getStateManager().setCurrentAnimation(anim);

        // Note: the targetBounds are relative to the launcher
        int startDelay = getSingleFrameMs(mLauncher);
        Animator windowAnimator = getOpeningWindowAnimators(
                v, appSurfaces, wallpaperSurfaces, nonAppSurfaces, launcherClosing);
        windowAnimator.setStartDelay(startDelay);
        anim.play(windowAnimator);
        if (launcherClosing) {
            // Delay animation by a frame to avoid jank.
            Pair<AnimatorSet, Runnable> launcherContentAnimator =
                    getLauncherContentAnimator(true /* isAppOpening */, startDelay, false);
            anim.play(launcherContentAnimator.first);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    launcherContentAnimator.second.run();
                }
            });
        }
    }

    private void composeWidgetLaunchAnimator(
            @NonNull AnimatorSet anim,
            @NonNull LauncherAppWidgetHostView v,
            @NonNull AnimatedSurface[] appSurfaces,
            @NonNull AnimatedSurface[] wallpaperSurfaces,
            @NonNull AnimatedSurface[] nonAppSurfaces,
            boolean launcherClosing) {
        mLauncher.getStateManager().setCurrentAnimation(anim);
        anim.play(getOpeningWindowAnimatorsForWidget(
                v, appSurfaces, wallpaperSurfaces, nonAppSurfaces, launcherClosing));
    }

    /**
     * Return the window bounds of the opening target.
     * In multiwindow mode, we need to get the final size of the opening app window target to help
     * figure out where the floating view should animate to.
     */
    private Rect getWindowTargetBounds(@NonNull AnimatedSurface[] appSurfaces,
            int rotationChange) {
        AnimatedSurface surface = null;
        for (AnimatedSurface s : appSurfaces) {
            if (!AnimatedSurfaceUtils.isOpening(s)) continue;
            surface = s;
            break;
        }
        final int widthPx = mDeviceProfile.getDeviceProperties().getWidthPx();
        final int heightPx = mDeviceProfile.getDeviceProperties().getHeightPx();
        if (surface == null) return new Rect(0, 0, widthPx, heightPx);
        final Rect bounds = new Rect(surface.screenSpaceBounds);
        if (surface.localBounds != null) {
            bounds.set(surface.localBounds);
        } else {
            bounds.offsetTo(surface.position.x, surface.position.y);
        }
        if (rotationChange != 0) {
            if ((rotationChange % 2) == 1) {
                // undoing rotation, so our "original" parent size is actually flipped
                Utilities.rotateBounds(bounds, heightPx, widthPx,
                        4 - rotationChange);
            } else {
                Utilities.rotateBounds(bounds, widthPx, heightPx,
                        4 - rotationChange);
            }
        }
        return bounds;
    }

    /** Dump debug logs to bug report. */
    public void dump(@NonNull String prefix, @NonNull PrintWriter printWriter) {}

    /**
     * Content is everything on screen except the background and the floating view (if any).
     *
     * @param isAppOpening     True when this is called when an app is opening.
     *                         False when this is called when an app is closing.
     * @param startDelay       Start delay duration.
     * @param skipAllAppsScale True if we want to avoid scaling All Apps
     */
    private Pair<AnimatorSet, Runnable> getLauncherContentAnimator(boolean isAppOpening,
            int startDelay, boolean skipAllAppsScale) {
        AnimatorSet launcherAnimator = new AnimatorSet();
        Runnable endListener;

        float[] alphas = isAppOpening
                ? new float[]{1, 0}
                : new float[]{0, 1};

        float[] scales = isAppOpening
                ? new float[]{1, mDeviceProfile.getWorkspaceProfile().getWorkspaceContentScale()}
                : new float[]{mDeviceProfile.getWorkspaceProfile().getWorkspaceContentScale(), 1};

        // Pause expensive view updates as they can lead to layer thrashing and skipped frames.
        mLauncher.pauseExpensiveViewUpdates();

        if (mLauncher.isInState(ALL_APPS)) {
            // All Apps in portrait mode is full screen, so we only animate AllAppsContainerView.
            final View appsView = mLauncher.getAppsView();
            final float startAlpha = appsView.getAlpha();
            final float startScale = SCALE_PROPERTY.get(appsView);
            if (mDeviceProfile.getDeviceProperties().isLargeScreen()) {

                // AllApps should not fade at all in tablets.
                alphas = new float[]{1, 1};
            }
            appsView.setAlpha(alphas[0]);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(appsView, View.ALPHA, alphas);
            alpha.setDuration(CONTENT_ALPHA_DURATION);
            alpha.setInterpolator(LINEAR);
            appsView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            alpha.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    appsView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            });

            FloatProperty<View> scaleProperty = getScaleProperty();

            if (!skipAllAppsScale) {
                scaleProperty.set(appsView, scales[0]);
                ObjectAnimator scale = ObjectAnimator.ofFloat(appsView, scaleProperty, scales);
                scale.setInterpolator(AGGRESSIVE_EASE);
                scale.setDuration(CONTENT_SCALE_DURATION);
                launcherAnimator.play(scale);
            }

            launcherAnimator.play(alpha);

            endListener = () -> {
                appsView.setAlpha(startAlpha);
                scaleProperty.set(appsView, startScale);
                appsView.setLayerType(View.LAYER_TYPE_NONE, null);
                mLauncher.resumeExpensiveViewUpdates();
            };
        } else if (mLauncher.isInState(OVERVIEW)) {
            endListener = composeViewContentAnimator(launcherAnimator, alphas, scales);
        } else {
            List<View> viewsToAnimate = new ArrayList<>();
            viewsToAnimate.add(mLauncher.getWorkspace());

            Hotseat hotseat = mLauncher.getHotseat();
            // Do not scale hotseat as a whole when taskbar is present, and scale QSB only if it's
            // not inline.
            if (mDeviceProfile.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent()) {
                if (!mDeviceProfile.getHotseatProfile().isQsbInline()) {
                    viewsToAnimate.add(hotseat.getQsb());
                }
            } else {
                viewsToAnimate.add(hotseat);
            }

            viewsToAnimate.forEach(view -> {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                // Start the animation from the current value, instead of assuming the views are
                // in their resting state, so interrupted animations merge seamlessly.
                // TODO(b/367591368): ideally these animations would be refactored to be
                //  controlled centrally so each instances doesn't need to care about this
                //  coordination.
                float[] scale = new float[]{view.getScaleX(), scales[1]};

                // Cancel any ongoing animations. This is necessary to avoid a conflict between
                // e.g. the unfinished animation triggered when closing an app back to Home and
                // this animation caused by a launch.
                Animations.Companion.cancelOngoingAnimation(view);
                // Make sure to cache the current animation, so it can be properly interrupted.
                Animations.Companion.setOngoingAnimation(view, launcherAnimator);

                ObjectAnimator scaleAnim = ObjectAnimator.ofFloat(view, SCALE_PROPERTY, scale)
                        .setDuration(CONTENT_SCALE_DURATION);
                scaleAnim.setInterpolator(DECELERATE_1_5);
                launcherAnimator.play(scaleAnim);
            });

            endListener = () -> {
                viewsToAnimate.forEach(view -> {
                    SCALE_PROPERTY.set(view, 1f);
                    view.setLayerType(View.LAYER_TYPE_NONE, null);

                    // Reset the cached animation.
                    Animations.Companion.setOngoingAnimation(view, null /* animation */);
                });
                mLauncher.resumeExpensiveViewUpdates();
            };
        }

        launcherAnimator.setStartDelay(startDelay);
        return new Pair<>(launcherAnimator, endListener);
    }

    /**
     * Compose recents view alpha and translation Y animation when launcher opens/closes apps.
     *
     * @param anim   the animator set to add to
     * @param alphas the alphas to animate to over time
     * @param scales the scale values to animator to over time
     * @return listener to run when the animation ends
     */
    protected Runnable composeViewContentAnimator(@NonNull AnimatorSet anim,
            float[] alphas, float[] scales) {
        RecentsView overview = mLauncher.getOverviewPanel();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(overview,
                RecentsView.CONTENT_ALPHA, alphas);
        alpha.setDuration(CONTENT_ALPHA_DURATION);
        alpha.setInterpolator(LINEAR);
        anim.play(alpha);
        overview.setFreezeViewVisibility(true);

        FloatProperty<View> scaleProperty = getScaleProperty();
        ObjectAnimator scaleAnim = ObjectAnimator.ofFloat(overview, scaleProperty, scales);
        scaleAnim.setInterpolator(AGGRESSIVE_EASE);
        scaleAnim.setDuration(CONTENT_SCALE_DURATION);
        anim.play(scaleAnim);

        return () -> {
            overview.setFreezeViewVisibility(false);
            scaleProperty.set(overview, 1f);
            mLauncher.getStateManager().reapplyState();
            mLauncher.resumeExpensiveViewUpdates();
        };
    }

    private boolean shouldCropToInset(AnimatedSurface surface) {
        return mDeviceProfile.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent()
                && mDeviceProfile.getTaskbarProfile().isTaskbarPresentInApps()
                && surface != null && !surface.willShowImeOnTarget
                && !isTransientTaskbar();
    }

    /**
     * @return Animator that controls the window of the opening targets from app icons.
     */
    private Animator getOpeningWindowAnimators(View v,
            AnimatedSurface[] appSurfaces,
            AnimatedSurface[] wallpaperSurfaces,
            AnimatedSurface[] nonAppSurfaces,
            boolean launcherClosing) {
        AnimatedSurfaces openingSurfaces = AnimatedSurfaces.from(appSurfaces,
                wallpaperSurfaces, nonAppSurfaces, AnimatedSurface.Mode.OPENING);
        int rotationChange = getRotationChange(appSurfaces);
        Rect windowTargetBounds = getWindowTargetBounds(appSurfaces, rotationChange);
        final int[] bottomInsetPos = new int[]{
                mSystemUiProxy.getHomeVisibilityState().getNavbarInsetPosition()};
        final AnimatedSurface surface = openingSurfaces.getFirstAppSurface();
        final boolean cropToInset = shouldCropToInset(surface);
        if (cropToInset) {
            // Animate to above the taskbar.
            windowTargetBounds.bottom = Math.min(bottomInsetPos[0],
                    windowTargetBounds.bottom);
        }
        boolean appTargetsAreTranslucent = areAllSurfacesTranslucent(appSurfaces);

        RectF launcherIconBounds = new RectF();
        FloatingIconView floatingView = getFloatingIconView(mLauncher, v,
                (mLauncher.getTaskbarInteractor() == null || !isTransientTaskbar())
                        ? null
                        : mLauncher.getTaskbarInteractor().findMatchingAsyncView(v),
                null /* fadeOutView */, !appTargetsAreTranslucent, launcherIconBounds,
                true /* isOpening */);
        Rect crop = new Rect();
        Matrix matrix = new Matrix();

        SurfaceTransactionApplier surfaceApplier =
                new SurfaceTransactionApplier(floatingView);
        openingSurfaces.addReleaseCheck(surfaceApplier);
        AnimatedSurface navBarSurface = openingSurfaces.getNavBarAnimatedSurface();

        int[] dragLayerBounds = new int[2];
        mDragLayer.getLocationOnScreen(dragLayerBounds);

        final boolean hasSplashScreen = ENABLE_SHELL_STARTING_SURFACE
                && mStartingWindowListener.consumeTaskLaunchInfo(
                        openingSurfaces.getFirstSurfaceTaskId()).windowType
                            == STARTING_WINDOW_TYPE_SPLASH_SCREEN;

        AnimOpenProperties prop = new AnimOpenProperties(mLauncher.getResources(),
                windowTargetBounds, launcherIconBounds, v, dragLayerBounds[0], dragLayerBounds[1],
                hasSplashScreen, floatingView.isDifferentFromAppIcon());
        int left = prop.cropCenterXStart - prop.cropWidthStart / 2;
        int top = prop.cropCenterYStart - prop.cropHeightStart / 2;
        int right = left + prop.cropWidthStart;
        int bottom = top + prop.cropHeightStart;
        // Set the crop here so we can calculate the corner radius below.
        crop.set(left, top, right, bottom);

        RectF floatingIconBounds = new RectF();
        RectF tmpRectF = new RectF();
        Point tmpPos = new Point();

        final SurfaceControl scrimLayer = addScrimLayer(surfaceApplier, openingSurfaces);
        final float scrimAlpha = getScrimAlpha();

        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(APP_LAUNCH_DURATION);
        appAnimator.setInterpolator(LINEAR);
        appAnimator.addListener(floatingView);
        appAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (shouldShowEduOnAppLaunch()) {
                    // LAUNCHER_TASKBAR_EDUCATION_SHOWING is set to true here, when the education
                    // flow is about to start, to avoid a race condition with other components
                    // that would show something else to the user as soon as the app is opened.
                    Settings.Secure.putInt(mLauncher.getContentResolver(),
                            LAUNCHER_TASKBAR_EDUCATION_SHOWING, 1);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (v instanceof BubbleTextView) {
                    ((BubbleTextView) v).setStayPressed(false);
                }
                TaskbarInteractor taskbarInteractor = mLauncher.getTaskbarInteractor();
                if (taskbarInteractor != null) {
                    taskbarInteractor.showEduOnAppLaunch();
                }
                if (mIsAppLaunchBlurEnabled) {
                    resetScrim(surfaceApplier, scrimLayer);
                }
                openingSurfaces.release();
            }

            private boolean shouldShowEduOnAppLaunch() {
                return mLauncher.getTaskbarUiState().getShowTaskbarEduOnAppLaunch();
            }
        });

        final float initialWindowRadius = supportsRoundedCornersOnWindows(mLauncher.getResources())
                ? Math.max(crop.width(), crop.height()) / 2f
                : 0f;
        final float finalShadowRadius = appTargetsAreTranslucent ? 0 : mMaxShadowRadius;

        MultiValueUpdateListener listener = new MultiValueUpdateListener(mOpeningInterpolator) {
            FloatProp mDx = new FloatProp(0, prop.dX, mOpeningXInterpolator);
            FloatProp mDy = new FloatProp(0, prop.dY);

            FloatProp mIconScaleToFitScreen = new FloatProp(prop.initialAppIconScale,
                    prop.finalAppIconScale);
            FloatProp mIconAlpha = new FloatProp(prop.iconAlphaStart, 0f,
                    clampToDuration(LINEAR, APP_LAUNCH_ALPHA_START_DELAY, APP_LAUNCH_ALPHA_DURATION,
                            APP_LAUNCH_DURATION));

            FloatProp mWindowRadius =
                    new FloatProp(initialWindowRadius, getWindowCornerRadius(mLauncher));
            FloatProp mShadowRadius = new FloatProp(0, finalShadowRadius);

            FloatProp mCropRectCenterX = new FloatProp(prop.cropCenterXStart, prop.cropCenterXEnd);
            FloatProp mCropRectCenterY = new FloatProp(prop.cropCenterYStart, prop.cropCenterYEnd);
            FloatProp mCropRectWidth = new FloatProp(prop.cropWidthStart, prop.cropWidthEnd);
            FloatProp mCropRectHeight = new FloatProp(prop.cropHeightStart, prop.cropHeightEnd);

            FloatProp mNavFadeOut = new FloatProp(1f, 0f, clampToDuration(
                    NAV_FADE_OUT_INTERPOLATOR, 0, ANIMATION_NAV_FADE_OUT_DURATION,
                    APP_LAUNCH_DURATION));
            FloatProp mNavFadeIn = new FloatProp(0f, 1f, clampToDuration(
                    NAV_FADE_IN_INTERPOLATOR, ANIMATION_DELAY_NAV_FADE_IN,
                    ANIMATION_NAV_FADE_IN_DURATION, APP_LAUNCH_DURATION));

            FloatProp mBlurRadius = new FloatProp(0f, mMaxBlurRadius, DECELERATE_1_5);
            FloatProp mBlurScrimAlpha = new FloatProp(0f, scrimAlpha, DECELERATE_1_5);

            @Override
            public void onUpdate(float percent, boolean initOnly) {
                if (cropToInset && bottomInsetPos[0] != mSystemUiProxy.getHomeVisibilityState()
                        .getNavbarInsetPosition()) {
                    final AnimatedSurface surface = openingSurfaces.getFirstAppSurface();
                    bottomInsetPos[0] = mSystemUiProxy.getHomeVisibilityState()
                            .getNavbarInsetPosition();
                    final Rect bounds = surface != null
                            ? surface.screenSpaceBounds : windowTargetBounds;
                    // Animate to above the taskbar.
                    int bottomLevel = Math.min(bottomInsetPos[0], bounds.bottom);
                    windowTargetBounds.bottom = bottomLevel;

                    AnimOpenProperties prop = new AnimOpenProperties(mLauncher.getResources(),
                            windowTargetBounds, launcherIconBounds, v,
                            dragLayerBounds[0], dragLayerBounds[1], hasSplashScreen,
                            floatingView.isDifferentFromAppIcon());
                    mCropRectCenterY = new FloatProp(prop.cropCenterYStart, prop.cropCenterYEnd);
                    mCropRectHeight = new FloatProp(prop.cropHeightStart, prop.cropHeightEnd);
                    mDy = new FloatProp(0, prop.dY);
                    mIconScaleToFitScreen = new FloatProp(prop.initialAppIconScale,
                            prop.finalAppIconScale);
                    float interpolatedPercent = getDefaultInterpolator().getInterpolation(percent);
                    mCropRectHeight.value = Utilities.mapRange(interpolatedPercent,
                            prop.cropHeightStart, prop.cropHeightEnd);
                    mCropRectCenterY.value = Utilities.mapRange(interpolatedPercent,
                            prop.cropCenterYStart, prop.cropCenterYEnd);
                    mDy.value = Utilities.mapRange(interpolatedPercent, 0, prop.dY);
                    mIconScaleToFitScreen.value = Utilities.mapRange(interpolatedPercent,
                            prop.initialAppIconScale, prop.finalAppIconScale);
                }

                // Calculate the size of the scaled icon.
                float iconWidth = launcherIconBounds.width() * mIconScaleToFitScreen.value;
                float iconHeight = launcherIconBounds.height() * mIconScaleToFitScreen.value;

                int left = (int) (mCropRectCenterX.value - mCropRectWidth.value / 2);
                int top = (int) (mCropRectCenterY.value - mCropRectHeight.value / 2);
                int right = (int) (left + mCropRectWidth.value);
                int bottom = (int) (top + mCropRectHeight.value);
                crop.set(left, top, right, bottom);

                final int windowCropWidth = crop.width();
                final int windowCropHeight = crop.height();
                if (rotationChange != 0) {
                    Utilities.rotateBounds(crop, mDeviceProfile.getDeviceProperties().getWidthPx(),
                            mDeviceProfile.getDeviceProperties().getHeightPx(), rotationChange);
                }

                // Scale the size of the icon to match the size of the window crop.
                float scaleX = iconWidth / windowCropWidth;
                float scaleY = iconHeight / windowCropHeight;
                float scale = Math.min(1f, Math.max(scaleX, scaleY));

                float scaledCropWidth = windowCropWidth * scale;
                float scaledCropHeight = windowCropHeight * scale;
                float offsetX = (scaledCropWidth - iconWidth) / 2;
                float offsetY = (scaledCropHeight - iconHeight) / 2;

                // Calculate the window position to match the icon position.
                tmpRectF.set(launcherIconBounds);
                tmpRectF.offset(dragLayerBounds[0], dragLayerBounds[1]);
                tmpRectF.offset(mDx.value, mDy.value);
                Utilities.scaleRectFAboutCenter(tmpRectF, mIconScaleToFitScreen.value);
                float windowTransX0 = tmpRectF.left - offsetX - crop.left * scale;
                float windowTransY0 = tmpRectF.top - offsetY - crop.top * scale;

                // Calculate the icon position.
                floatingIconBounds.set(launcherIconBounds);
                floatingIconBounds.offset(mDx.value, mDy.value);
                Utilities.scaleRectFAboutCenter(floatingIconBounds, mIconScaleToFitScreen.value);
                floatingIconBounds.left -= offsetX;
                floatingIconBounds.top -= offsetY;
                floatingIconBounds.right += offsetX;
                floatingIconBounds.bottom += offsetY;

                if (initOnly) {
                    // For the init pass, we want full alpha since the window is not yet ready.
                    floatingView.update(1f, floatingIconBounds, percent, 0f,
                            mWindowRadius.value * scale, true /* isOpening */);
                    return;
                }

                SurfaceTransaction transaction = new SurfaceTransaction();

                for (int i = appSurfaces.length - 1; i >= 0; i--) {
                    AnimatedSurface surface = appSurfaces[i];
                    SurfaceProperties builder = transaction.forSurface(surface.leash);

                    if (AnimatedSurfaceUtils.isOpening(surface)) {
                        matrix.setScale(scale, scale);
                        if (rotationChange == 1) {
                            matrix.postTranslate(windowTransY0,
                                    mDeviceProfile.getDeviceProperties().getWidthPx() - (windowTransX0 + scaledCropWidth));
                        } else if (rotationChange == 2) {
                            matrix.postTranslate(
                                    mDeviceProfile.getDeviceProperties().getWidthPx() - (windowTransX0 + scaledCropWidth),
                                    mDeviceProfile.getDeviceProperties().getHeightPx() - (windowTransY0 + scaledCropHeight));
                        } else if (rotationChange == 3) {
                            matrix.postTranslate(
                                    mDeviceProfile.getDeviceProperties().getHeightPx() - (windowTransY0 + scaledCropHeight),
                                    windowTransX0);
                        } else {
                            matrix.postTranslate(windowTransX0, windowTransY0);
                        }

                        floatingView.update(mIconAlpha.value, floatingIconBounds, percent, 0f,
                                mWindowRadius.value * scale, true /* isOpening */);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(1f - mIconAlpha.value)
                                .setCornerRadius(mWindowRadius.value)
                                .setShadowRadius(mShadowRadius.value);
                    } else if (AnimatedSurfaceUtils.isClosing(surface)) {
                        if (surface.localBounds != null) {
                            tmpPos.set(surface.localBounds.left, surface.localBounds.top);
                        } else {
                            tmpPos.set(surface.position.x, surface.position.y);
                        }
                        final Rect crop = new Rect(surface.screenSpaceBounds);
                        crop.offsetTo(0, 0);

                        if ((rotationChange % 2) == 1) {
                            int tmp = crop.right;
                            crop.right = crop.bottom;
                            crop.bottom = tmp;
                            tmp = tmpPos.x;
                            tmpPos.x = tmpPos.y;
                            tmpPos.y = tmp;
                        }
                        matrix.setTranslate(tmpPos.x, tmpPos.y);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(1f);
                    }
                }

                if (navBarSurface != null) {
                    SurfaceProperties navBuilder = transaction.forSurface(navBarSurface.leash);
                    if (mNavFadeIn.value > mNavFadeIn.getStartValue()) {
                        matrix.setScale(scale, scale);
                        matrix.postTranslate(windowTransX0, windowTransY0);
                        navBuilder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(mNavFadeIn.value);
                    } else {
                        navBuilder.setAlpha(mNavFadeOut.value);
                    }
                }

                if (mIsAppLaunchBlurEnabled && scrimLayer != null && scrimLayer.isValid()) {
                    SurfaceProperties builder = transaction.forSurface(scrimLayer);
                    builder.setAlpha(mBlurScrimAlpha.value);
                    builder.setBackgroundBlurRadius((int) mBlurRadius.value);
                }

                surfaceApplier.scheduleApply(transaction);
            }
        };
        appAnimator.addUpdateListener(listener);
        // Since we added a start delay, call update here to init the FloatingIconView properly.
        listener.onUpdate(0, true /* initOnly */);

        // If app targets are translucent, do not animate the background as it causes a visible
        // flicker when it resets itself at the end of its animation.
        if (appTargetsAreTranslucent || !launcherClosing) {
            animatorSet.play(appAnimator);
        } else {
            animatorSet.playTogether(appAnimator, getBackgroundAnimator());
        }
        return animatorSet;
    }

    private boolean isTransientTaskbar() {
        return mLauncher.getActivityComponent().getTaskbarFeatureEvaluator().isTransient();
    }

    private Animator getOpeningWindowAnimatorsForWidget(LauncherAppWidgetHostView v,
            AnimatedSurface[] appSurfaces,
            AnimatedSurface[] wallpaperSurfaces,
            AnimatedSurface[] nonAppSurfaces, boolean launcherClosing) {
        Rect windowTargetBounds = getWindowTargetBounds(appSurfaces,
                getRotationChange(appSurfaces));
        boolean appTargetsAreTranslucent = areAllSurfacesTranslucent(appSurfaces);

        final RectF widgetBackgroundBounds = new RectF();
        final Rect appWindowCrop = new Rect();
        final Matrix matrix = new Matrix();
        AnimatedSurfaces openingSurfaces = AnimatedSurfaces.from(appSurfaces,
                wallpaperSurfaces, nonAppSurfaces, AnimatedSurface.Mode.OPENING);

        AnimatedSurface openingSurface = openingSurfaces.getFirstAppSurface();
        int fallbackBackgroundColor = 0;
        if (openingSurface != null && ENABLE_SHELL_STARTING_SURFACE) {
            fallbackBackgroundColor = mStartingWindowListener
                    .consumeTaskLaunchInfo(openingSurface.taskId).backgroundColor;
        }
        if (fallbackBackgroundColor == 0) {
            fallbackBackgroundColor =
                    FloatingWidgetView.getDefaultBackgroundColor(mLauncher, openingSurface);
        }

        final float finalWindowRadius = getWindowCornerRadius(mLauncher);
        final FloatingWidgetView floatingView = FloatingWidgetView.getFloatingWidgetView(mLauncher,
                v, widgetBackgroundBounds,
                new Size(windowTargetBounds.width(), windowTargetBounds.height()),
                finalWindowRadius, appTargetsAreTranslucent, fallbackBackgroundColor);
        final float initialWindowRadius = supportsRoundedCornersOnWindows(mLauncher.getResources())
                ? floatingView.getInitialCornerRadius() : 0;

        SurfaceTransactionApplier surfaceApplier = new SurfaceTransactionApplier(floatingView);
        openingSurfaces.addReleaseCheck(surfaceApplier);

        AnimatedSurface navBarSurface = openingSurfaces.getNavBarAnimatedSurface();
        final SurfaceControl scrimLayer = addScrimLayer(surfaceApplier, openingSurfaces);
        final float scrimAlpha = getScrimAlpha();

        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(APP_LAUNCH_DURATION);
        appAnimator.setInterpolator(LINEAR);
        appAnimator.addListener(floatingView);
        appAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mIsAppLaunchBlurEnabled) {
                    resetScrim(surfaceApplier, scrimLayer);
                }
                openingSurfaces.release();
            }
        });
        floatingView.setFastFinishRunnable(animatorSet::end);

        appAnimator.addUpdateListener(new MultiValueUpdateListener(mOpeningInterpolator) {
            float mAppWindowScale = 1;
            final FloatProp mWidgetForegroundAlpha = new FloatProp(1, 0, clampToDuration(
                    LINEAR, 0, WIDGET_CROSSFADE_DURATION_MILLIS / 2, APP_LAUNCH_DURATION));

            final FloatProp mWidgetFallbackBackgroundAlpha = new FloatProp(0, 1,
                    clampToDuration(LINEAR, 0, 75, APP_LAUNCH_DURATION));
            final FloatProp mPreviewAlpha = new FloatProp(0, 1, clampToDuration(
                    LINEAR,
                    WIDGET_CROSSFADE_DURATION_MILLIS / 2 /* delay */,
                    WIDGET_CROSSFADE_DURATION_MILLIS / 2 /* duration */,
                    APP_LAUNCH_DURATION));
            final FloatProp mWindowRadius = new FloatProp(initialWindowRadius, finalWindowRadius);
            final FloatProp mCornerRadiusProgress = new FloatProp(0, 1);

            // Window & widget background positioning bounds
            final FloatProp mDx = new FloatProp(widgetBackgroundBounds.centerX(),
                    windowTargetBounds.centerX(), mOpeningXInterpolator);
            final FloatProp mDy = new FloatProp(widgetBackgroundBounds.centerY(),
                    windowTargetBounds.centerY());
            final FloatProp mWidth = new FloatProp(widgetBackgroundBounds.width(),
                    windowTargetBounds.width());
            final FloatProp mHeight = new FloatProp(widgetBackgroundBounds.height(),
                    windowTargetBounds.height());

            final FloatProp mNavFadeOut = new FloatProp(1f, 0f, clampToDuration(
                    NAV_FADE_OUT_INTERPOLATOR, 0, ANIMATION_NAV_FADE_OUT_DURATION,
                    APP_LAUNCH_DURATION));
            final FloatProp mNavFadeIn = new FloatProp(0f, 1f, clampToDuration(
                    NAV_FADE_IN_INTERPOLATOR, ANIMATION_DELAY_NAV_FADE_IN,
                    ANIMATION_NAV_FADE_IN_DURATION, APP_LAUNCH_DURATION));

            @Override
            public void onUpdate(float percent, boolean initOnly) {
                widgetBackgroundBounds.set(mDx.value - mWidth.value / 2f,
                        mDy.value - mHeight.value / 2f, mDx.value + mWidth.value / 2f,
                        mDy.value + mHeight.value / 2f);
                // Set app window scaling factor to match widget background width
                mAppWindowScale = widgetBackgroundBounds.width() / windowTargetBounds.width();
                // Crop scaled app window to match widget
                appWindowCrop.set(0 /* left */, 0 /* top */,
                        windowTargetBounds.width() /* right */,
                        Math.round(widgetBackgroundBounds.height() / mAppWindowScale) /* bottom */);
                matrix.setTranslate(widgetBackgroundBounds.left, widgetBackgroundBounds.top);
                matrix.postScale(mAppWindowScale, mAppWindowScale, widgetBackgroundBounds.left,
                        widgetBackgroundBounds.top);

                SurfaceTransaction transaction = new SurfaceTransaction();
                float floatingViewAlpha = appTargetsAreTranslucent ? 1 - mPreviewAlpha.value : 1;
                for (int i = appSurfaces.length - 1; i >= 0; i--) {
                    AnimatedSurface surface = appSurfaces[i];
                    SurfaceProperties builder = transaction.forSurface(surface.leash);
                    if (AnimatedSurfaceUtils.isOpening(surface)) {
                        floatingView.update(widgetBackgroundBounds, floatingViewAlpha,
                                mWidgetForegroundAlpha.value, mWidgetFallbackBackgroundAlpha.value,
                                mCornerRadiusProgress.value);
                        builder.setMatrix(matrix)
                                .setWindowCrop(appWindowCrop)
                                .setAlpha(mPreviewAlpha.value)
                                .setCornerRadius(mWindowRadius.value / mAppWindowScale);
                    }
                }

                if (navBarSurface != null) {
                    SurfaceProperties navBuilder = transaction.forSurface(navBarSurface.leash);
                    if (mNavFadeIn.value > mNavFadeIn.getStartValue()) {
                        navBuilder.setMatrix(matrix)
                                .setWindowCrop(appWindowCrop)
                                .setAlpha(mNavFadeIn.value);
                    } else {
                        navBuilder.setAlpha(mNavFadeOut.value);
                    }
                }

                if (mIsAppLaunchBlurEnabled && scrimLayer != null && scrimLayer.isValid()) {
                    SurfaceProperties builder = transaction.forSurface(scrimLayer);
                    builder.setAlpha(percent * scrimAlpha);
                    builder.setBackgroundBlurRadius((int) (percent * mMaxBlurRadius));
                }

                surfaceApplier.scheduleApply(transaction);
            }
        });

        // If app targets are translucent, do not animate the background as it causes a visible
        // flicker when it resets itself at the end of its animation.
        if (appTargetsAreTranslucent || !launcherClosing) {
            animatorSet.play(appAnimator);
        } else {
            animatorSet.playTogether(appAnimator, getBackgroundAnimator());
        }
        return animatorSet;
    }

    private SurfaceControl addScrimLayer(SurfaceTransactionApplier applier,
            AnimatedSurfaces surfaces) {
        if (!mIsAppLaunchBlurEnabled) {
            return null;
        }

        AnimatedSurface launcherSurface = null;
        if (surfaces.unfilteredApps != null) {
            for (final AnimatedSurface surface : surfaces.unfilteredApps) {
                if (AnimatedSurfaceUtils.isClosing(surface)) {
                    launcherSurface = surface;
                    break;
                }
            }
        }


        SurfaceControl parent = launcherSurface != null ? launcherSurface.leash : null;
        if (parent == null || !parent.isValid()) {
            // Parent surface is not ready at the moment. Retry later.
            return null;
        }
        SurfaceControl scrimLayer = new SurfaceControl.Builder()
                .setName("App launch background scrim")
                .setCallsite("AppLaunchAnimationRunner")
                .setEffectLayer()
                .setOpaque(false)
                .setHidden(true)
                .build();
        final float[] colorComponents = new float[] { 0f, 0f, 0f };

        SurfaceTransaction transaction = new SurfaceTransaction();
        SurfaceProperties builder = transaction.forSurface(scrimLayer);
        builder
                .setColor(colorComponents)
                .setAlpha(0)
                .reparent(launcherSurface.leash)
                .setShow()
                // Ensure the scrim layer occludes wallpaper
                .setLayer(1000);
        applier.scheduleApply(transaction);
        return scrimLayer;
    }

    private float getScrimAlpha() {
        return Utilities.isDarkTheme(mLauncher) ? MAX_SCRIM_ALPHA_DARK : MAX_SCRIM_ALPHA_LIGHT;
    }

    private void resetScrim(SurfaceTransactionApplier applier, SurfaceControl scrimLayer) {
        if (scrimLayer != null && scrimLayer.isValid()) {
            SurfaceTransaction surfaceTransaction = new SurfaceTransaction();
            surfaceTransaction.getTransaction().remove(scrimLayer);
            applier.scheduleApply(surfaceTransaction);
        }
    }

    /** Returns animator that controls depth/blur of the background during app/widget opening. */
    private Animator getBackgroundAnimator() {
        if (!Flags.allAppsSurface()) {
            // Don't animate/blur the background for this launch, regardless of the launcher state.
            // We have too many performance issues with the blur.
            return new AnimatorSet();
        }

        // When launching an app from overview that doesn't map to a task, we still want to just
        // blur the wallpaper instead of the launcher surface as well
        LauncherState launcherState = mLauncher.getStateManager().getState();
        boolean allowBlurringLauncher =
                launcherState != OVERVIEW && BlurUtils.supportsBlursOnWindows();

        ObjectAnimator backgroundRadiusAnim = ObjectAnimator.ofFloat(
                        mLauncher.getDepthController().stateDepth, MULTI_PROPERTY_VALUE,
                        BACKGROUND_APP.getDepth(mLauncher))
                .setDuration(APP_LAUNCH_DURATION);

        if (allowBlurringLauncher) {
            // Create a temporary effect layer, that lives on top of launcher, so we can apply
            // the blur to it. The EffectLayer will be fullscreen, which will help with caching
            // optimizations on the SurfaceFlinger side:
            // - Results would be able to be cached as a texture
            // - There won't be texture allocation overhead, because EffectLayers don't have
            //   buffers
            ViewRootImpl viewRootImpl = mLauncher.getDragLayer().getViewRootImpl();
            SurfaceControl parent = viewRootImpl != null
                    ? viewRootImpl.getSurfaceControl()
                    : null;
            SurfaceControl dimLayer = new SurfaceControl.Builder()
                    .setName("Blur layer")
                    .setParent(parent)
                    .setOpaque(false)
                    .setHidden(false)
                    .setEffectLayer()
                    .build();

            backgroundRadiusAnim.addListener(AnimatorListeners.forEndCallback(() -> {
                // Use try-with-resources to ensure the transaction gets closed.
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    transaction.remove(dimLayer).apply();
                }
            }));
        }

        return backgroundRadiusAnim;
    }

    /**
     * Registers remote animations used when closing apps to home screen.
     */
    public void registerRemoteAnimations() {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        RemoteAnimationDefinition definition = new RemoteAnimationDefinition();
        addRemoteAnimations(definition);
        mLauncher.registerRemoteAnimations(definition);
    }

    /**
     * Adds remote animations to a {@link RemoteAnimationDefinition}. May be overridden to add
     * additional animations.
     */
    private void addRemoteAnimations(RemoteAnimationDefinition definition) {
        mWallpaperOpenRunner = new WallpaperOpenLauncherAnimationRunner();
        definition.addRemoteAnimation(WindowManager.TRANSIT_OLD_WALLPAPER_OPEN,
                WindowConfiguration.ACTIVITY_TYPE_STANDARD,
                new RemoteAnimationAdapter(
                        new LauncherAnimationRunner(mHandler, mWallpaperOpenRunner,
                                false /* startAtFrontOfQueue */),
                        CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));
    }

    /**
     * Registers remote animations used when closing apps to home screen.
     */
    public void registerRemoteTransitions() {
        SystemUiProxy.INSTANCE.get(mLauncher).shareTransactionQueue();
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }

        mWallpaperOpenTransitionRunner = new WallpaperOpenLauncherAnimationRunner();
        mLauncherOpenTransition = new RemoteTransition(
                new LauncherAnimationRunner(mHandler, mWallpaperOpenTransitionRunner,
                        false /* startAtFrontOfQueue */).toRemoteTransition(),
                mLauncher.getIApplicationThread(), "QuickstepLaunchHome");

        TransitionFilter homeCheck = new TransitionFilter();
        // No need to handle the transition that also dismisses keyguard.
        homeCheck.mNotFlags = TRANSIT_FLAG_KEYGUARD_GOING_AWAY;

        homeCheck.mRequirements =
                new TransitionFilter.Requirement[]{new TransitionFilter.Requirement(),
                        new TransitionFilter.Requirement(),
                        new TransitionFilter.Requirement()};

        homeCheck.mRequirements[0].mActivityType = ACTIVITY_TYPE_HOME;
        homeCheck.mRequirements[0].mTopActivity = mLauncher.getComponentName();
        homeCheck.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};

        homeCheck.mRequirements[1].mActivityType = ACTIVITY_TYPE_STANDARD;
        homeCheck.mRequirements[1].mModes = new int[]{TRANSIT_CLOSE, TRANSIT_TO_BACK};

        homeCheck.mRequirements[2].mNot = true;
        homeCheck.mRequirements[2].mCustomAnimation = true;
        homeCheck.mRequirements[2].mMustBeTask = true;
        homeCheck.mRequirements[2].mMustBeIndependent = true;

        mLauncherOpenTransition.setFilter(homeCheck);

        SystemUiProxy.INSTANCE.get(mLauncher)
                .registerRemoteTransition(mLauncherOpenTransition);
        if (mBackAnimationController != null) {
            mBackAnimationController.registerComponentCallbacks();
            if (isHomeRoleHeld()) {
                mBackAnimationController.registerBackCallbacks(mHandler);
            }
        }

        /*
         * For cross-display moves, moving to the default display is handled by the LaunchOptions
         * transition setup, which forwards to AppLaunchRemoteAnimationRunner. However, if the
         * launch happens via a different means (e.g. desktop mode), we also need to handle the
         * cross-display move via a remote transition.
         */
        if (com.android.window.flags.Flags.enableCrossDisplaysAppLaunchTransition()
                && !com.android.window.flags.Flags.crossDisplayTransitionV2()) {
            mMoveDisplayTransition = new RemoteTransition(new MoveDisplayChangeRunner(this),
                    mLauncher.getIApplicationThread(), "QuickstepDisplayMove");
            TransitionFilter changeCheck = new TransitionFilter();
            changeCheck.mRequirements = new TransitionFilter.Requirement[]{
                    new TransitionFilter.Requirement()};
            changeCheck.mRequirements[0].mModes = new int[]{TRANSIT_CHANGE};
            changeCheck.mRequirements[0].mMustBeTask = true;
            // (TRANSIT_CHANGE is never independent.)
            changeCheck.mRequirements[0].mMustBeIndependent = false;
            changeCheck.mRequirements[0].mActivityType = ACTIVITY_TYPE_STANDARD;
            changeCheck.mRequirements[0].mIsCrossDisplayMove = true;

            mMoveDisplayTransition.setFilter(changeCheck);

            SystemUiProxy.INSTANCE.get(mLauncher)
                    .registerRemoteTransition(mMoveDisplayTransition);
        }
    }

    public void onActivityDestroyed() {
        mCleanupTask.executeAllAndDestroy();
        unregisterRemoteAnimations();
        unregisterRemoteTransitions();
        mLauncher.removeOnDeviceProfileChangeListener(this);
        if (Flags.fallbackRevealAnimation()) {
            mSystemUiProxy.getHomeVisibilityState().removeListener(mHomeVisibilityChangeListener);
            mHomeVisibilityChangeListener = null;
        }
        if (BuildConfig.IS_STUDIO_BUILD && !mRegisteredTaskStackChangeListener.isEmpty()) {
            Log.e(TAG, "IllegalState: Failed to run onEndCallback created from"
                    + " getActivityLaunchOptions()");
        }
        mRegisteredTaskStackChangeListener.forEach(TaskRestartedDuringLaunchListener::unregister);
        mRegisteredTaskStackChangeListener.clear();
    }

    /**
     * Called when the overview-target changes. Updates the back callback registration state.
     */
    public void onOverviewTargetChange() {
        if (isHomeRoleHeld()) {
            mBackAnimationController.registerBackCallbacks(mHandler);
        } else {
            mBackAnimationController.unregisterBackCallbacks();
        }
    }

    private boolean isHomeRoleHeld() {
        RoleManager roleManager = mLauncher.getSystemService(RoleManager.class);
        return roleManager == null || roleManager.isRoleHeld(ROLE_HOME);
    }

    private void unregisterRemoteAnimations() {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        mLauncher.unregisterRemoteAnimations();

        // Also clear strong references to the runners registered with the remote animation
        // definition so we don't have to wait for the system gc
        mWallpaperOpenRunner = null;
        mAppLaunchRunner = null;
        mAppLaunchTransition = null;
    }

    protected void unregisterRemoteTransitions() {
        SystemUiProxy.INSTANCE.get(mLauncher).unshareTransactionQueue();
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        if (mLauncherOpenTransition == null) return;
        SystemUiProxy.INSTANCE.get(mLauncher).unregisterRemoteTransition(
                mLauncherOpenTransition);
        mLauncherOpenTransition = null;
        mWallpaperOpenTransitionRunner = null;
        if (mMoveDisplayTransition != null) {
            SystemUiProxy.INSTANCE.get(mLauncher)
                    .unregisterRemoteTransition(mMoveDisplayTransition);
            mMoveDisplayTransition = null;
        }
        if (mBackAnimationController != null) {
            mBackAnimationController.unregisterBackCallbacks();
            mBackAnimationController.unregisterComponentCallbacks();
            mBackAnimationController = null;
        }
    }

    private boolean launcherIsASurfaceWithMode(AnimatedSurface[] surfaces,
            @AnimatedSurfaceUtils.AnimatedSurfaceMode int mode) {
        for (final AnimatedSurface surface : surfaces) {
            if (surface.mode == mode && surface.taskInfo != null
                    // Compare component name instead of task-id because transitions will promote
                    // the target up to the root task while getTaskId returns the leaf.
                    && surface.taskInfo.topActivity != null
                    && surface.taskInfo.topActivity.equals(mLauncher.getComponentName())) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldPlayFallbackClosingAnimation(RemoteAnimationTarget[] targets) {
        int numTargets = 0;
        for (RemoteAnimationTarget target : targets) {
            if (target.mode == MODE_CLOSING) {
                numTargets++;
                if (numTargets > 1 || target.windowConfiguration.getWindowingMode()
                        == WINDOWING_MODE_MULTI_WINDOW) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getRotationChange(AnimatedSurface[] appSurfaces) {
        int rotationChange = 0;
        for (AnimatedSurface surface : appSurfaces) {
            if (Math.abs(surface.rotationChange) > Math.abs(rotationChange)) {
                rotationChange = surface.rotationChange;
            }
        }
        return rotationChange;
    }

    /**
     * Returns view on launcher that corresponds to the closing app in the list of app targets
     */
    public @Nullable View findLauncherView(RemoteAnimationTarget[] appTargets) {
        for (RemoteAnimationTarget appTarget : appTargets) {
            if (appTarget.mode == MODE_CLOSING) {
                View launcherView = findLauncherView(appTarget);
                if (launcherView != null) {
                    return launcherView;
                }
            }
        }
        return null;
    }

    /**
     * Returns view on launcher that corresponds to the {@param runningTaskTarget}.
     */
    private @Nullable View findLauncherView(RemoteAnimationTarget runningTaskTarget) {
        if (runningTaskTarget == null || runningTaskTarget.taskInfo == null) {
            return null;
        }

        final ComponentName[] taskInfoActivities = new ComponentName[]{
                runningTaskTarget.taskInfo.baseActivity,
                runningTaskTarget.taskInfo.origActivity,
                runningTaskTarget.taskInfo.realActivity,
                runningTaskTarget.taskInfo.topActivity};

        String packageName = null;
        for (ComponentName component : taskInfoActivities) {
            if (component != null && component.getPackageName() != null) {
                packageName = component.getPackageName();
                break;
            }
        }

        if (packageName == null) {
            return null;
        }

        // Find the associated item info for the launch cookie (if available), note that predicted
        // apps actually have an id of -1, so use another default id here
        final List<IBinder> launchCookies = runningTaskTarget.taskInfo.launchCookies == null
                ? Collections.EMPTY_LIST
                : runningTaskTarget.taskInfo.launchCookies;

        return mLauncher.getFirstVisibleElementForAppClose(
                StableViewInfo.fromLaunchCookies(launchCookies), packageName,
                UserHandle.of(runningTaskTarget.taskInfo.userId));
    }

    private @NonNull RectF getDefaultWindowTargetRect() {
        RecentsView recentsView = mLauncher.getOverviewPanel();
        PagedOrientationHandler orientationHandler = recentsView.getPagedOrientationHandler();
        DeviceProfile dp = mLauncher.getDeviceProfile();
        final int halfIconSize = dp.getWorkspaceProfile().getIconSizePx() / 2;
        float primaryDimension = orientationHandler
                .getPrimaryValue(dp.getDeviceProperties().getAvailableWidthPx(), dp.getDeviceProperties().getAvailableHeightPx());
        float secondaryDimension = orientationHandler
                .getSecondaryValue(dp.getDeviceProperties().getAvailableWidthPx(), dp.getDeviceProperties().getAvailableHeightPx());
        final float targetX = primaryDimension / 2f;
        final float targetY = secondaryDimension - dp.getHotseatProfile().getBarSizePx();
        return new RectF(targetX - halfIconSize, targetY - halfIconSize,
                targetX + halfIconSize, targetY + halfIconSize);
    }


    /**
     * Closing animator that animates the window into its final location on the workspace.
     */
    protected RectFSpringAnim getClosingWindowAnimators(AnimatorSet animation,
            RemoteAnimationTarget[] targets, View launcherView, PointF velocityPxPerS,
            RectF closingWindowStartRectF, float startWindowCornerRadius) {
        FloatingIconView floatingIconView = null;
        FloatingWidgetView floatingWidget = null;
        RectF targetRect = new RectF();

        RemoteAnimationTarget runningTaskTarget = null;
        boolean isTransluscent = false;
        for (RemoteAnimationTarget target : targets) {
            if (target.mode == MODE_CLOSING) {
                runningTaskTarget = target;
                isTransluscent = runningTaskTarget.isTranslucent;
                break;
            }
        }

        // Get floating view and target rect.
        if (launcherView instanceof LauncherAppWidgetHostView) {
            Size windowSize = new Size(mDeviceProfile.getDeviceProperties().getWidthPx(),
                    mDeviceProfile.getDeviceProperties().getHeightPx());
            int fallbackBackgroundColor =
                    FloatingWidgetView.getDefaultBackgroundColor(mLauncher,
                            AnimatedSurfaceUtils.from(runningTaskTarget));
            floatingWidget = FloatingWidgetView.getFloatingWidgetView(mLauncher,
                    (LauncherAppWidgetHostView) launcherView, targetRect, windowSize,
                    getWindowCornerRadius(mLauncher), isTransluscent, fallbackBackgroundColor);
        } else if (launcherView != null && !RemoveAnimationSettingsTracker.INSTANCE.get(
                mLauncher).isRemoveAnimationEnabled()) {
            floatingIconView = getFloatingIconView(mLauncher, launcherView, null,
                    mLauncher.getTaskbarInteractor() == null
                            ? null
                            : mLauncher.getTaskbarInteractor().findMatchingAsyncView(launcherView),
                    true /* hideOriginal */, targetRect, false /* isOpening */);
        } else {
            targetRect.set(getDefaultWindowTargetRect());
        }

        RectFSpringAnim anim = new RectFSpringAnim(floatingWidget != null
                && Flags.widgetReturnAnimationMinorFixes()
                ? new WidgetSpringConfig(
                        mLauncher, mDeviceProfile, closingWindowStartRectF, targetRect)
                : new DefaultSpringConfig(
                        mLauncher, mDeviceProfile, closingWindowStartRectF, targetRect));

        // Hook up floating views to the closing window animators.
        // note the coordinate of closingWindowStartRect is based on launcher
        if (floatingIconView != null) {
            anim.addAnimatorListener(floatingIconView);
            floatingIconView.setOnTargetChangeListener(anim::onTargetPositionChanged);
            floatingIconView.setFastFinishRunnable(anim::end);
            FloatingIconView finalFloatingIconView = floatingIconView;

            // We want the window alpha to be 0 once this threshold is met, so that the
            // FloatingIconView can be seen morphing into the icon shape.
            final float windowAlphaThreshold = 1f - SHAPE_PROGRESS_DURATION;

            RectFSpringAnim.OnUpdateListener runner = new SpringAnimRunner(targets, targetRect,
                    closingWindowStartRectF, mLauncher, startWindowCornerRadius) {
                @Override
                public void onUpdate(RectF currentRectF, float progress) {
                    // We want the icon alpha to be 1 once this threshold is met, so that it can be
                    // seen morphing into the icon shape. But before the threshold, we want to limit
                    // the alpha to reduce the blur effect behind the window.
                    float iconAlpha =
                            Interpolators.clampToProgress(progress, 0f, windowAlphaThreshold);
                    finalFloatingIconView.update(iconAlpha, currentRectF, progress,
                            windowAlphaThreshold, getCornerRadius(progress), false);

                    super.onUpdate(currentRectF, progress);
                }
            };
            anim.addOnUpdateListener(runner);
        } else if (floatingWidget != null) {
            anim.addAnimatorListener(floatingWidget);
            floatingWidget.setOnTargetChangeListener(anim::onTargetPositionChanged);
            floatingWidget.setFastFinishRunnable(anim::end);

            final float floatingWidgetAlpha = isTransluscent ? 0 : 1;
            final float alphaEndProgress = Flags.widgetReturnAnimationMinorFixes()
                    ? WIDGET_CLOSE_ALPHA_END_PROGRESS : WIDGET_CLOSE_ALPHA_END_PROGRESS_LEGACY;
            FloatingWidgetView finalFloatingWidget = floatingWidget;

            final Function<RectF, Float> posProvider = LauncherAnimUtils
                    .getPosProviderForRect(closingWindowStartRectF, targetRect);
            final float totalDiff = Math.abs(posProvider.apply(closingWindowStartRectF)
                    - posProvider.apply(targetRect));
            final float startPos = posProvider.apply(closingWindowStartRectF);

            anim.addOnUpdateListener(
                    new SpringAnimRunner(targets, targetRect, closingWindowStartRectF, mLauncher,
                            startWindowCornerRadius, alphaEndProgress) {
                        private float mWidgetAlphaLowerBound = 1f;
                        private boolean mThresholdCaptured = false;

                        @Override
                        public void onUpdate(RectF currentRectF, float progress) {
                            if (Flags.widgetReturnAnimationMinorFixes()) {
                                // The progress parameter represents the scaling progress (closing
                                // window down to the size of FloatingWidget). currentProgress is
                                // used to capture the progress for the primary axis(the axis with
                                // longer distance between initial to final position).
                                float currentProgress = totalDiff > 0
                                        ? Math.abs(posProvider.apply(currentRectF) - startPos)
                                        / totalDiff : 1f;

                                // Capture the lower threshold for revealing the widget only once
                                // when the scaling is nearly finished.
                                if (!mThresholdCaptured && progress >= 0.99f) {
                                    mWidgetAlphaLowerBound = currentProgress;
                                    mThresholdCaptured = true;
                                }

                                finalFloatingWidget.update(currentProgress, mWidgetAlphaLowerBound,
                                        currentRectF, floatingWidgetAlpha, 1 - progress);
                                float radius = finalFloatingWidget.getOutlineRadius();
                                setWindowCornerRadius(radius);
                            } else {
                                float fallbackBackgroundAlpha = 1 - mapBoundToRange(progress,
                                        0.8f, 1, 0, 1, EXAGGERATED_EASE);
                                float foregroundAlpha = mapBoundToRange(progress,
                                        0.5f, 1, 0, 1, EXAGGERATED_EASE);
                                finalFloatingWidget.update(currentRectF, floatingWidgetAlpha,
                                        foregroundAlpha, fallbackBackgroundAlpha, 1 - progress);
                            }
                            super.onUpdate(currentRectF, progress);
                        }
                    });
        } else {
            // If no floating icon or widget is present, animate the to the default window
            // target rect.
            anim.addOnUpdateListener(new SpringAnimRunner(
                    targets, targetRect, closingWindowStartRectF,
                    mLauncher,
                    startWindowCornerRadius));
        }

        // Use a fixed velocity to start the animation.
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                anim.start(mLauncher, mDeviceProfile, velocityPxPerS);
            }
        });
        return anim;
    }

    /**
     * Closing window animator that moves the window down and offscreen.
     */
    private Animator getFallbackClosingWindowAnimators(RemoteAnimationTarget[] appTargets) {
        AnimatedSurface[] appSurfaces = AnimatedSurfaceUtils.mapFromTargets(appTargets);
        final int rotationChange = getRotationChange(appSurfaces);
        SurfaceTransactionApplier surfaceApplier = new SurfaceTransactionApplier(mDragLayer);
        Matrix matrix = new Matrix();
        Point tmpPos = new Point();
        Rect tmpRect = new Rect();
        ValueAnimator closingAnimator = ValueAnimator.ofFloat(0, 1);
        int duration = CLOSING_TRANSITION_DURATION_MS;
        float windowCornerRadius = getWindowCornerRadius(mLauncher);
        float startShadowRadius = areAllSurfacesTranslucent(appSurfaces) ? 0 : mMaxShadowRadius;
        closingAnimator.setDuration(duration);
        boolean isFreeform = isFreeformAnimation(appTargets);
        float translateY = isFreeform ? mClosingFreeformWindowTransY : mClosingWindowTransY;
        float endScale = isFreeform ? 0.95f : 1f;
        Interpolator alphaInterpolator = isFreeform
                ? clampToDuration(LINEAR, 0, 100, duration)
                : clampToDuration(LINEAR, 25, 125, duration);
        closingAnimator.addUpdateListener(new MultiValueUpdateListener() {
            FloatProp mDy = new FloatProp(0, translateY, DECELERATE_1_7);
            FloatProp mScale = new FloatProp(1f, endScale, DECELERATE_1_7);
            FloatProp mAlpha = new FloatProp(1f, 0f, alphaInterpolator);
            FloatProp mShadowRadius = new FloatProp(startShadowRadius, 0, DECELERATE_1_7);

            @Override
            public void onUpdate(float percent, boolean initOnly) {
                SurfaceTransaction transaction = new SurfaceTransaction();
                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTarget target = appTargets[i];
                    SurfaceProperties builder = transaction.forSurface(target.leash);

                    if (target.screenSpaceBounds != null) {
                        tmpPos.set(target.screenSpaceBounds.left, target.screenSpaceBounds.top);
                    } else {
                        tmpPos.set(target.position.x, target.position.y);
                    }

                    final Rect crop = new Rect(target.localBounds);
                    crop.offsetTo(0, 0);
                    if (target.mode == MODE_CLOSING) {
                        tmpRect.set(target.screenSpaceBounds);
                        if ((rotationChange % 2) != 0) {
                            final int right = crop.right;
                            crop.right = crop.bottom;
                            crop.bottom = right;
                        }
                        matrix.setScale(mScale.value, mScale.value,
                                tmpRect.centerX(),
                                tmpRect.centerY());
                        matrix.postTranslate(0, mDy.value);
                        matrix.postTranslate(tmpPos.x, tmpPos.y);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(mAlpha.value)
                                .setCornerRadius(windowCornerRadius)
                                .setShadowRadius(mShadowRadius.value);
                    } else if (target.mode == MODE_OPENING) {
                        matrix.setTranslate(tmpPos.x, tmpPos.y);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(1f);
                    }
                }
                surfaceApplier.scheduleApply(transaction);
            }
        });

        return closingAnimator;
    }

    private boolean isFreeformAnimation(RemoteAnimationTarget[] appTargets) {
        return DesktopModeStatus.canEnterDesktopMode(mLauncher.getApplicationContext())
                && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX.isTrue()
                && Arrays.stream(appTargets)
                        .anyMatch(app -> app.taskInfo != null && app.taskInfo.isFreeform());
    }

    private void addCujInstrumentation(Animator anim, int cuj) {
        anim.addListener(getCujAnimationSuccessListener(cuj, /* cujPreStartCallback= */null));
    }

    private void addCujInstrumentation(Animator anim, int cuj, Runnable cujPreStartCallback) {
        anim.addListener(getCujAnimationSuccessListener(cuj, cujPreStartCallback));
    }

    private void addCujInstrumentation(RectFSpringAnim anim, int cuj) {
        anim.addAnimatorListener(
                getCujAnimationSuccessListener(cuj, /* cujPreStartCallback= */null));
    }

    private AnimationSuccessListener getCujAnimationSuccessListener(
            int cuj, Runnable cujPreStartCallback) {
        return new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mDragLayer.getViewTreeObserver().addOnDrawListener(
                        new ViewTreeObserver.OnDrawListener() {
                            boolean mHandled = false;

                            @Override
                            public void onDraw() {
                                if (mHandled) {
                                    return;
                                }
                                mHandled = true;
                                if (cujPreStartCallback != null) {
                                    cujPreStartCallback.run();
                                }
                                InteractionJankMonitorWrapper.begin(mDragLayer, cuj);

                                mDragLayer.post(() ->
                                        mDragLayer.getViewTreeObserver().removeOnDrawListener(
                                                this));
                            }
                        });
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                InteractionJankMonitorWrapper.cancel(cuj);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                InteractionJankMonitorWrapper.end(cuj);
            }
        };
    }

    /**
     * Creates the {@link RectFSpringAnim} and {@link AnimatorSet} required to animate
     * the transition.
     */
    @NonNull
    public BackAnimState createWallpaperOpenAnimations(
            RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpapers,
            RemoteAnimationTarget[] nonAppTargets,
            RectF startRect,
            float startWindowCornerRadius,
            boolean fromPredictiveBack) {
        View launcherView = findLauncherView(appTargets);
        if (launcherView != null
                && launcherView.getTag() instanceof ItemInfo info
                && info.shouldUseBackgroundAnimation()) {
            // Try to create a return animation
            RunnableList onEndCallback = new RunnableList();
            WindowAnimationState windowState = new WindowAnimationState();
            windowState.bounds = startRect;
            windowState.bottomLeftRadius = windowState.bottomRightRadius =
                    windowState.topLeftRadius = windowState.topRightRadius =
                            startWindowCornerRadius;
            ContainerAnimationRunner runner = ContainerAnimationRunner.fromView(
                    launcherView, false /* forLaunch */, mLauncher, mStartingWindowListener,
                    onEndCallback, windowState);
            if (runner != null) {
                runner.startAnimation(TRANSIT_CLOSE,
                        appTargets, wallpapers, nonAppTargets,
                        new IRemoteAnimationFinishedCallback.Stub() {
                            @Override
                            public void onAnimationFinished() {
                                onEndCallback.executeAllAndDestroy();
                            }
                        });
                return new AlreadyStartedBackAnimState(onEndCallback);
            }
        }

        AnimatorSet anim = new AnimatorSet();
        RectFSpringAnim rectFSpringAnim = null;

        final boolean launcherIsForceInvisibleOrOpening = mLauncher.isForceInvisible()
                || launcherIsASurfaceWithMode(AnimatedSurfaceUtils.mapFromTargets(appTargets),
                AnimatedSurface.Mode.OPENING);

        boolean playFallBackAnimation = (launcherView == null
                && launcherIsForceInvisibleOrOpening)
                || mLauncher.getWorkspace().isOverlayShown()
                || shouldPlayFallbackClosingAnimation(appTargets);

        boolean playWorkspaceReveal = true;
        boolean skipAllAppsScale = false;

        if (Flags.fallbackRevealAnimation()) {
            if (mFallbackRevealAnimation != null) {
                mFallbackRevealAnimation.cancelAnimations();
                mFallbackRevealAnimation = null;
            }
            mIsLauncherAnimating = true;
        }

        if (mLauncher.isInState(OVERVIEW)) {
            playWorkspaceReveal = false;
        } else if (!playFallBackAnimation) {
            rectFSpringAnim = getClosingWindowAnimators(
                    anim, appTargets, launcherView, new PointF(), startRect,
                    startWindowCornerRadius);
            if (mLauncher.isInState(LauncherState.ALL_APPS)) {
                // Skip scaling all apps, otherwise FloatingIconView will get wrong
                // layout bounds.
                skipAllAppsScale = true;
            } else {
                anim.play(
                        new ScalingWorkspaceRevealAnim(mLauncher, rectFSpringAnim,
                                rectFSpringAnim.getTargetRect(),
                                !fromPredictiveBack /* playAlphaReveal */,
                                true /* playBlur */).getAnimators());

                // We play StaggeredWorkspaceAnim as a part of the closing window animation.
                playWorkspaceReveal = false;
            }
        } else {
            anim.play(getFallbackClosingWindowAnimators(appTargets));
        }

        AnimatorListenerAdapter endListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mIsLauncherAnimating = false;
                AccessibilityManagerCompat.sendTestProtocolEventToTest(
                        mLauncher, WALLPAPER_OPEN_ANIMATION_FINISHED_MESSAGE);
            }
        };
        if (rectFSpringAnim != null) {
            rectFSpringAnim.addAnimatorListener(endListener);
        } else {
            anim.addListener(endListener);
        }

        // Normally, we run the launcher content animation when we are transitioning
        // home, but if home is already visible, then we don't want to animate the
        // contents of launcher unless we know that we are animating home as a result
        // of the home button press with quickstep, which will result in launcher being
        // started on touch down, prior to the animation home (and won't be in the
        // targets list because it is already visible). In that case, we force
        // invisibility on touch down, and only reset it after the animation to home
        // is initialized.
        if (launcherIsForceInvisibleOrOpening) {
            if (rectFSpringAnim != null && anim.getChildAnimations().isEmpty()) {
                addCujInstrumentation(rectFSpringAnim, Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_HOME);
            } else {
                if (isFreeformAnimation(appTargets)) {
                    addCujInstrumentation(
                            anim,
                            Cuj.CUJ_DESKTOP_MODE_EXIT_MODE_ON_LAST_WINDOW_CLOSE,
                            /* cujPreStartCallback= */ () -> {
                                mLatencyTracker.onActionEnd(
                                        ACTION_DESKTOP_MODE_EXIT_MODE_ON_LAST_WINDOW_CLOSE);
                            });
                }
                addCujInstrumentation(anim, playFallBackAnimation
                        ? Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_HOME_FALLBACK
                        : Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_HOME);
            }

            // Only register the content animation for cancellation when state changes
            mLauncher.getStateManager().setCurrentAnimation(anim);

            if (mLauncher.isInState(LauncherState.ALL_APPS)) {
                Pair<AnimatorSet, Runnable> contentAnimator =
                        getLauncherContentAnimator(false, LAUNCHER_RESUME_START_DELAY,
                                skipAllAppsScale);
                anim.play(contentAnimator.first);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        contentAnimator.second.run();
                    }
                });
            } else if (playWorkspaceReveal) {
                anim.play(new WorkspaceRevealAnim(mLauncher, false).getAnimators());
            }
        }

        return new AnimatorBackState(rectFSpringAnim, anim);
    }

    /** Get animation duration for taskbar for going to home. */
    public static int getTaskbarToHomeDuration(
            boolean isPersistentTaskbarAndNotInDesktopMode) {
        return getTaskbarToHomeDuration(false, isPersistentTaskbarAndNotInDesktopMode);
    }

    /**
     * Get animation duration for taskbar for going to home.
     *
     * @param shouldOverrideToFastAnimation should overwrite scaling reveal home animation duration
     */
    public static int getTaskbarToHomeDuration(boolean shouldOverrideToFastAnimation,
            boolean isPersistentTaskbarAndNotInDesktopMode) {
        if (isPersistentTaskbarAndNotInDesktopMode) {
            return PINNED_TASKBAR_TRANSITION_DURATION;
        } else if (enableRecentsInTaskbar()) {
            return TASKBAR_STASH_DURATION_WITHOUT_ICON_ALIGNMENT;
        } else if (!shouldOverrideToFastAnimation) {
            return TASKBAR_TO_HOME_DURATION_SLOW;
        } else {
            return TASKBAR_TO_HOME_DURATION_FAST;
        }
    }

    /**
     * Remote animation runner for animation from the app to Launcher, including recents.
     */
    protected class WallpaperOpenLauncherAnimationRunner implements RemoteAnimationFactory {

        @Override
        public void onAnimationStart(int transit,
                RemoteAnimationTarget[] appTargets,
                RemoteAnimationTarget[] wallpaperTargets,
                RemoteAnimationTarget[] nonAppTargets,
                LauncherAnimationRunner.AnimationResult result) {
            if (mLauncher.isDestroyed()) {
                AnimatorSet anim = new AnimatorSet();
                anim.play(getFallbackClosingWindowAnimators(appTargets));
                result.setAnimation(anim, mLauncher.getApplicationContext());
                return;
            }

            if (mLauncher.hasSomeInvisibleFlag(PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION)) {
                mLauncher.addForceInvisibleFlag(INVISIBLE_BY_PENDING_FLAGS);
                mLauncher.getStateManager().moveToRestState();
            }

            AnimatedSurface[] appSurfaces = AnimatedSurfaceUtils.mapFromTargets(appTargets);

            RectF windowTargetBounds =
                    new RectF(getWindowTargetBounds(appSurfaces, getRotationChange(appSurfaces)));

            final RectF resolveRectF = new RectF(windowTargetBounds);
            for (RemoteAnimationTarget t : appTargets) {
                if (t.mode == MODE_CLOSING) {
                    new RemoteAnimationCoordinateTransfer(mLauncher)
                            .transferRectToOwnerSurface(t, windowTargetBounds, resolveRectF);
                    break;
                }
            }

            BackAnimState bankAnimState = createWallpaperOpenAnimations(
                    appTargets, wallpaperTargets, nonAppTargets, resolveRectF,
                    QuickStepContract.getWindowCornerRadius(mLauncher),
                    false /* fromPredictiveBack */);

            SplitRecentsAnimUtils splitRecentsAnimUtils = new SplitRecentsAnimUtils(nonAppTargets);
            splitRecentsAnimUtils.fadeOutDimLayer(/* immediate= */ true);
            splitRecentsAnimUtils.fadeOutDivider(/* immediate= */ true);
            mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
            bankAnimState.applyToAnimationResult(result, mLauncher);
        }
    }

    /**
     * Remote animation runner for animation to launch an app.
     */
    private class AppLaunchAnimationRunner implements RemoteAnimationFactory {

        private final View mV;
        private final RunnableList mOnEndCallback;

        AppLaunchAnimationRunner(View v, RunnableList onEndCallback) {
            mV = v;
            mOnEndCallback = onEndCallback;
        }

        @Override
        public void onAnimationStart(int transit,
                RemoteAnimationTarget[] appTargets,
                RemoteAnimationTarget[] wallpaperTargets,
                RemoteAnimationTarget[] nonAppTargets,
                LauncherAnimationRunner.AnimationResult result) {
            AnimatedSurface[] appSurfaces = AnimatedSurfaceUtils.mapFromTargets(appTargets);
            AnimatedSurface[] wallpaperSurfaces =
                    AnimatedSurfaceUtils.mapFromTargets(wallpaperTargets);
            AnimatedSurface[] nonAppSurfaces = AnimatedSurfaceUtils.mapFromTargets(nonAppTargets);

            AnimatorSet anim = new AnimatorSet();
            boolean launcherClosing =
                    launcherIsASurfaceWithMode(appSurfaces, AnimatedSurface.Mode.CLOSING);
            final boolean launchingFromWidget = mV instanceof LauncherAppWidgetHostView;
            final boolean launchingFromRecents = isLaunchingFromRecents(mV, appSurfaces);
            final boolean skipFirstFrame;
            if (launchingFromWidget) {
                composeWidgetLaunchAnimator(anim, (LauncherAppWidgetHostView) mV, appSurfaces,
                        wallpaperSurfaces, nonAppSurfaces, launcherClosing);
                addCujInstrumentation(anim, Cuj.CUJ_LAUNCHER_APP_LAUNCH_FROM_WIDGET);
                skipFirstFrame = true;
            } else if (launchingFromRecents) {
                composeRecentsLaunchAnimator(anim, mV, appTargets, wallpaperTargets, nonAppTargets,
                        launcherClosing);
                skipFirstFrame = true;
            } else {
                composeIconLaunchAnimator(anim, mV, appSurfaces, wallpaperSurfaces, nonAppSurfaces,
                        launcherClosing);
                addCujInstrumentation(anim, Cuj.CUJ_LAUNCHER_APP_LAUNCH_FROM_ICON);
                skipFirstFrame = false;
            }

            if (launcherClosing) {
                anim.addListener(mForceInvisibleListener);
            }

            // Syncs the app launch animation and taskbar stash animation (if exists).
            TaskbarInteractor taskbarInteractor = mLauncher.getTaskbarInteractor();
            TaskbarAsyncAnimator taskbarStashAnimation = null;
            if (taskbarInteractor != null) {
                taskbarInteractor.setIgnoreInAppFlagForSync(false);

                if (launcherClosing) {
                    // If taskbar stash animation is played on main thread (same as app launch
                    // animation), the stash animation will be added as child of launcher's app
                    // launch animation. Otherwise a TaskbarAsyncAnimator will be returned and
                    // launcher (on main thread) need to explicitly start taskbar stash animation
                    // on taskbar ui thread.
                    taskbarStashAnimation = taskbarInteractor.createAnimToApp(anim);
                }
            }

            result.setAnimation(anim, mLauncher, mOnEndCallback::executeAllAndDestroy,
                    skipFirstFrame);
            // If app launch animation is started and TaskbarAsyncAnimator is returned (meaning
            // taskbar stash animation will be played on taskbar's ui thread), launcher needs to
            // explicitly trigger taskbar stash animation from main thread.
            if (taskbarStashAnimation != null && anim.isStarted()) {
                taskbarStashAnimation.start();
            }
        }

        @Override
        public void onAnimationCancelled() {
            mOnEndCallback.executeAllAndDestroy();
        }
    }

}
