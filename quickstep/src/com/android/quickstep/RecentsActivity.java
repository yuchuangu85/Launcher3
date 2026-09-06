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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.os.Trace.TRACE_TAG_APP;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;

import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.launcher3.LauncherConstants.SavedInstanceKeys.RUNTIME_STATE;
import static com.android.launcher3.LauncherConstants.SavedInstanceKeys.RUNTIME_STATE_RECREATE_TO_UPDATE_THEME;
import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_PRE_DELAY;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.testing.shared.TestProtocol.LAUNCHER_ACTIVITY_LOST_WINDOW_FOCUS_MESSAGE;
import static com.android.launcher3.testing.shared.TestProtocol.LAUNCHER_ACTIVITY_STOPPED_MESSAGE;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL;
import static com.android.launcher3.util.WallpaperThemeManager.setWallpaperDependentTheme;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;
import static com.android.quickstep.TaskUtils.taskIsATargetWithMode;
import static com.android.quickstep.TaskViewUtils.createRecentsWindowAnimator;
import static com.android.quickstep.fallback.RecentsState.BACKGROUND_APP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl.Transaction;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.window.RemoteTransition;
import android.window.SplashScreen;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAnimationRunner;
import com.android.launcher3.LauncherAnimationRunner.AnimationResult;
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory;
import com.android.launcher3.LauncherRootView;
import com.android.launcher3.R;
import com.android.launcher3.SplitScreenUiState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ContextTracker;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.fallback.FallbackActivityRecentsView;
import com.android.quickstep.fallback.FallbackRecentsStateController;
import com.android.quickstep.fallback.RecentsDragLayer;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.split.SplitScreenAppResolver;
import com.android.quickstep.split.SplitSelectStateController;
import com.android.quickstep.sysuiconnection.SysUIConnectionTracker;
import com.android.quickstep.util.RecentsAtomicAnimationFactory;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TraceStateLoggerHelper;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * A recents activity that shows the recently launched tasks as swipable task cards.
 * See {@link com.android.quickstep.views.RecentsView}.
 */
public final class RecentsActivity extends StatefulActivity<RecentsState> implements
        RecentsViewContainer,  InvariantDeviceProfile.OnIDPChangeListener {
    private static final String TAG = "RecentsActivity";

    public static final ContextTracker.ActivityTracker<RecentsActivity> ACTIVITY_TRACKER =
            new ContextTracker.ActivityTracker<>();

    private Handler mUiHandler = new Handler(Looper.getMainLooper());

    private static final long HOME_APPEAR_DURATION = 250;
    private static final long RECENTS_ANIMATION_TIMEOUT = 1000;

    private RecentsDragLayer mDragLayer;
    private ScrimView mScrimView;
    private FallbackActivityRecentsView mFallbackRecentsView;
    private OverviewActionsView<?> mActionsView;
    private SysUIConnectionTracker mSysUIConnectionTracker;
    private @Nullable volatile TaskbarInteractor mTaskbarInteractor;

    private StateManager<RecentsState, RecentsActivity> mStateManager;

    // Strong refs to runners which are cleared when the activity is destroyed
    private RemoteAnimationFactory mActivityLaunchAnimationRunner;

    private final Runnable mAnimationStartTimeoutRunnable = this::onAnimationStartTimeout;
    private SplitSelectStateController mSplitSelectStateController;
    @Nullable
    private DesktopRecentsTransitionController mDesktopRecentsTransitionController;

    // Tracks whether the current state should have RecentsView visible.
    private boolean mIsInRecentsViewVisibleState = false;


    /**
     * Init drag layer and overview panel views.
     */
    private void setupViews() {
        getTheme().applyStyle(R.style.OverviewBlurFallbackStyle, true);
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(this);
        // SplitSelectStateController needs to be created before setContentView()
        mSplitSelectStateController =
                new SplitSelectStateController(this, getStateManager(),
                        null /* depthController */, getStatsLogManager(),
                        systemUiProxy, RecentsModel.INSTANCE.get(this),
                        null /*activityBackCallback*/, new SplitScreenUiState(),
                        new SplitScreenAppResolver(this));
        // Setup root and child views
        inflateRootView(R.layout.fallback_recents_activity);
        LauncherRootView rootView = getRootView();
        mDragLayer = rootView.findViewById(R.id.drag_layer);
        mScrimView = rootView.findViewById(R.id.scrim_view);
        ViewStub recentsViewStub = rootView.findViewById(R.id.overview_panel);
        recentsViewStub.setLayoutResource(R.layout.fallback_activity_recents_view);
        mFallbackRecentsView = (FallbackActivityRecentsView) recentsViewStub.inflate();
        mActionsView = rootView.findViewById(R.id.overview_actions_view);
        ViewGroup emptyRecentsMessageView = rootView.findViewById(R.id.empty_recents_message_view);

        if (DesktopModeStatus.canEnterDesktopMode(this)) {
            mDesktopRecentsTransitionController = new DesktopRecentsTransitionController(
                    getStateManager(), systemUiProxy, getIApplicationThread(),
                    null /* depthController */
            );
        }
        mFallbackRecentsView.init(mActionsView, mSplitSelectStateController,
                mDesktopRecentsTransitionController, new SurfaceTransactionApplier(getRootView()),
                emptyRecentsMessageView);

        setContentView(rootView);
        rootView.getSysUiScrim().getSysUIProgress().updateValue(0);
        mDragLayer.recreateControllers();

        mSysUIConnectionTracker = SysUIConnectionTracker.get(this);
        mSysUIConnectionTracker.onConnected(this, c -> c.getTaskbarManager().setActivity(this));
    }

    @AnyThread
    @Override
    public void setTaskbarInteractor(@Nullable TaskbarInteractor taskbarInteractor) {
        mTaskbarInteractor = taskbarInteractor;
    }

    @Nullable
    @Override
    public TaskbarInteractor getTaskbarInteractor() {
        return mTaskbarInteractor;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ACTIVITY_TRACKER.handleNewIntent(this);
    }

    @Override
    public void onHandleConfigurationChanged() {
        Trace.instant(Trace.TRACE_TAG_APP, "recentsActivity_onHandleConfigurationChanged");
        initDeviceProfile();

        AbstractFloatingView.closeOpenViews(this, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
        dispatchDeviceProfileChanged();

        reapplyUi();
        mDragLayer.recreateControllers();
    }

    /**
     * Generate the device profile to use in this activity.
     * @return device profile
     */
    protected DeviceProfile createDeviceProfile() {
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(this).getDeviceProfile(this);

        // In case we are reusing IDP, create a copy so that we don't conflict with Launcher
        // activity.
        return dp.copy();
    }

    @Override
    public BaseDragLayer getDragLayer() {
        return mDragLayer;
    }

    public ScrimView getScrimView() {
        return mScrimView;
    }

    @Override
    public RecentsState getBackgroundAppState() {
        return BACKGROUND_APP;
    }

    @Override
    public FallbackActivityInterface getContainerInterface() {
        return FallbackActivityInterface.INSTANCE.get(this);
    }

    @Override
    public SplitSelectStateController getSplitSelectStateController() {
        return mSplitSelectStateController;
    }

    @Override
    public void goToRecentsState(RecentsState recentsState, boolean animated,
            Animator.AnimatorListener listener) {
        getStateManager().goToState(recentsState, animated, listener);
    }

    @Override
    public FallbackActivityRecentsView getOverviewPanel() {
        return mFallbackRecentsView;
    }

    @Override
    public OverviewActionsView<?> getActionsView() {
        return mActionsView;
    }

    @Override
    public void returnToHomescreenAfterFreeformShortcut() {
        // No-op
    }

    /**
     * Called if the remote animation callback from #getActivityLaunchOptions() hasn't called back
     * in a reasonable time due to a conflict with the recents animation.
     */
    private void onAnimationStartTimeout() {
        if (mActivityLaunchAnimationRunner != null) {
            mActivityLaunchAnimationRunner.onAnimationCancelled();
        }
    }

    @NonNull
    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(final View v, @Nullable ItemInfo item) {
        if (!(v instanceof TaskView)) {
            return super.getActivityLaunchOptions(v, item);
        }

        final TaskView taskView = (TaskView) v;
        final RecentsView recentsView = taskView.getRecentsView();
        if (recentsView == null) {
            return super.getActivityLaunchOptions(v, item);
        }

        RunnableList onEndCallback = new RunnableList();

        mActivityLaunchAnimationRunner = new RemoteAnimationFactory() {
            @Override
            public void onAnimationStart(int transit, RemoteAnimationTarget[] appTargets,
                    RemoteAnimationTarget[] wallpaperTargets,
                    RemoteAnimationTarget[] nonAppTargets, AnimationResult result) {
                mHandler.removeCallbacks(mAnimationStartTimeoutRunnable);
                AnimatorSet anim = composeRecentsLaunchAnimator(recentsView, taskView, appTargets,
                        wallpaperTargets, nonAppTargets);
                anim.addListener(resetStateListener());
                result.setAnimation(anim, RecentsActivity.this, onEndCallback::executeAllAndDestroy,
                        true /* skipFirstFrame */);
            }

            @Override
            public void onAnimationCancelled() {
                mHandler.removeCallbacks(mAnimationStartTimeoutRunnable);
                onEndCallback.executeAllAndDestroy();
            }
        };

        final LauncherAnimationRunner wrapper = new LauncherAnimationRunner(
                mUiHandler, mActivityLaunchAnimationRunner, true /* startAtFrontOfQueue */);
        final ActivityOptions options = ActivityOptions.makeRemoteAnimation(
                new RemoteAnimationAdapter(wrapper, RECENTS_LAUNCH_DURATION,
                        RECENTS_LAUNCH_DURATION - STATUS_BAR_TRANSITION_DURATION
                                - STATUS_BAR_TRANSITION_PRE_DELAY),
                new RemoteTransition(wrapper.toRemoteTransition(), getIApplicationThread(),
                        "LaunchFromRecents"));
        final ActivityOptionsWrapper activityOptions = new ActivityOptionsWrapper(options,
                onEndCallback);
        activityOptions.options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
        activityOptions.options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        mHandler.postDelayed(mAnimationStartTimeoutRunnable, RECENTS_ANIMATION_TIMEOUT);
        return activityOptions;
    }

    /**
     * Composes the animations for a launch from the recents list if possible.
     */
    private AnimatorSet composeRecentsLaunchAnimator(
            @NonNull RecentsView recentsView,
            @NonNull TaskView taskView,
            RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets,
            RemoteAnimationTarget[] nonAppTargets) {
        AnimatorSet target = new AnimatorSet();
        boolean activityClosing = taskIsATargetWithMode(appTargets, getTaskId(), MODE_CLOSING);
        PendingAnimation pa = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        createRecentsWindowAnimator(recentsView, taskView, !activityClosing, appTargets,
                wallpaperTargets, nonAppTargets, /* depthController= */ null,
                /* transitionInfo= */ null, /* appearedTaskId= */ INVALID_TASK_ID, pa);
        target.play(pa.buildAnim());

        // Found a visible recents task that matches the opening app, lets launch the app from there
        if (activityClosing) {
            Animator adjacentAnimation = mFallbackRecentsView
                    .createAdjacentPageAnimForTaskLaunch(taskView);
            adjacentAnimation.setInterpolator(Interpolators.TOUCH_RESPONSE);
            adjacentAnimation.setDuration(RECENTS_LAUNCH_DURATION);
            adjacentAnimation.addListener(resetStateListener());
            target.play(adjacentAnimation);
        }
        return target;
    }

    @Override
    protected void onStart() {
        // Set the alpha to 1 before calling super, as it may get set back to 0 due to
        // onActivityStart callback.
        mFallbackRecentsView.setContentAlpha(1);
        super.onStart();
        mFallbackRecentsView.updateLocusId();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Workaround for b/78520668, explicitly trim memory once UI is hidden
        onTrimMemory(TRIM_MEMORY_UI_HIDDEN);
        mFallbackRecentsView.updateLocusId();
        AccessibilityManagerCompat.sendTestProtocolEventToTest(
                this, LAUNCHER_ACTIVITY_STOPPED_MESSAGE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AccessibilityManagerCompat.sendStateEventToTest(getBaseContext(), OVERVIEW_STATE_ORDINAL);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWallpaperDependentTheme(this);
        mStateManager = new StateManager<>(this, RecentsState.BG_LAUNCHER);

        initDeviceProfile();
        InvariantDeviceProfile.INSTANCE.get(this).addOnChangeListener(this);
        setupViews();

        getSystemUiController().updateUiState(SystemUiController.UI_STATE_BASE_WINDOW,
                Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText));
        ACTIVITY_TRACKER.handleCreate(this);

        // Set screen title for Talkback
        setTitle(R.string.accessibility_recent_apps);

        restoreState(savedInstanceState);
        new TraceStateLoggerHelper(this).startTraceStateLogger();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(RUNTIME_STATE, mStateManager.getState().ordinal);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            AccessibilityManagerCompat.sendTestProtocolEventToTest(
                    this, LAUNCHER_ACTIVITY_LOST_WINDOW_FOCUS_MESSAGE);
        }
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        // RecentsState is only restored after theme changes.
        int stateOrdinal = savedState.getInt(RUNTIME_STATE, RecentsState.DEFAULT.ordinal);
        RecentsState recentsState = RecentsState.stateFromOrdinal(stateOrdinal);
        boolean isUiModeChange = savedState.getBoolean(RUNTIME_STATE_RECREATE_TO_UPDATE_THEME);
        if (!recentsState.shouldDisableRestore(isUiModeChange)) {
            mStateManager.goToState(recentsState, /*animated=*/false);
        }
    }

    @Override
    public void onStateSetEnd(RecentsState state) {
        super.onStateSetEnd(state);

        if (state == RecentsState.DEFAULT) {
            AccessibilityManagerCompat.sendStateEventToTest(getBaseContext(),
                    OVERVIEW_STATE_ORDINAL);
        }

        if (mIsInRecentsViewVisibleState && !state.isRecentsViewVisible() && !isFinishing()
                && !mFallbackRecentsView.isGestureActive()) {
            Log.d(TAG, "onStateSetEnd - moveTaskToBack as Recents should no longer be visible");
            moveTaskToBack(/*nonRoot=*/true);
        }
        mIsInRecentsViewVisibleState = state.isRecentsViewVisible();
    }

    @Override
    public boolean shouldAnimateStateChange() {
        return false;
    }

    /**
     * Initialize/update the device profile.
     */
    private void initDeviceProfile() {
        mDeviceProfile = createDeviceProfile();
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        // After the transition to home, enable the high-res thumbnail loader if it wasn't enabled
        // as a part of quickstep, so that high-res thumbnails can load the next time we enter
        // overview
        RecentsModel.INSTANCE.get(this).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        RecentsModel.INSTANCE.get(this).onTrimMemory(level);
    }

    @Override
    protected void onDestroy() {
        RecentsView recentsView = getOverviewPanel();
        if (recentsView != null) {
            recentsView.destroy();
        }
        super.onDestroy();
        ACTIVITY_TRACKER.onContextDestroyed(this);
        mActivityLaunchAnimationRunner = null;
        mSplitSelectStateController.onDestroy();
        InvariantDeviceProfile.INSTANCE.get(this).removeOnChangeListener(this);
    }

    @Override
    public void onBackPressed() {
        getStateManager().getState().onBackInvoked(this);
    }

    @Override
    public void startHome(boolean animated, @Nullable Runnable onHomeAnimationComplete) {
        RecentsView recentsView = getOverviewPanel();
        recentsView.switchToScreenshot(() -> recentsView.finishRecentsAnimation(true,
                () -> startHomeInternal(onHomeAnimationComplete)));
    }

    private void startHomeInternal(@Nullable Runnable onHomeAnimationComplete) {
        RemoteAnimationFactory animationToHomeFactory =
                (transit, appTargets, wallpaperTargets, nonAppTargets, result) -> {
                    StateAnimationConfig config = new StateAnimationConfig();
                    config.duration = HOME_APPEAR_DURATION;
                    if (mFallbackRecentsView.hasTaskViews()) {
                        config.setInterpolator(ANIM_OVERVIEW_FADE, FINAL_FRAME);
                    }
                    AnimatorPlaybackController controller =
                            getStateManager().createAnimationToNewWorkspace(
                                    RecentsState.BG_LAUNCHER, config);
                    controller.dispatchOnStart();

                    RemoteAnimationTargets targets = new RemoteAnimationTargets(
                            appTargets, wallpaperTargets, nonAppTargets, MODE_OPENING);
                    for (RemoteAnimationTarget app : targets.apps) {
                        new Transaction().setAlpha(app.leash, 1).apply();
                    }
                    AnimatorSet anim = new AnimatorSet();
                    anim.play(controller.getAnimationPlayer());
                    anim.setDuration(HOME_APPEAR_DURATION);
                    result.setAnimation(anim, RecentsActivity.this,
                            () -> {
                                getStateManager().goToState(RecentsState.HOME, false);
                                if (onHomeAnimationComplete != null) {
                                    onHomeAnimationComplete.run();
                                }
                            },
                            true /* skipFirstFrame */);
                };

        LauncherAnimationRunner runner = new LauncherAnimationRunner(
                getMainThreadHandler(), animationToHomeFactory, true);
        ActivityOptions options = ActivityOptions.makeRemoteAnimation(
                new RemoteAnimationAdapter(runner, HOME_APPEAR_DURATION, 0),
                new RemoteTransition(runner.toRemoteTransition(), getIApplicationThread(),
                        "StartHomeFromRecents"));
        startHomeIntentSafely(this, options.toBundle(), TAG, getDisplayId());
    }

    @Override
    public void collectStateHandlers(List<StateHandler<RecentsState>> out) {
        out.add(new FallbackRecentsStateController(this));
    }

    @Override
    public StateManager<RecentsState, RecentsActivity> getStateManager() {
        return mStateManager;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println(prefix + "Misc:");
        dumpMisc(prefix + "\t", writer);
    }

    @Override
    public AtomicAnimationFactory<RecentsState> createAtomicAnimationFactory() {
        return new RecentsAtomicAnimationFactory<>(this);
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        Trace.instantForTrack(TRACE_TAG_APP, "RecentsActivity#DeviceProfileChanged",
                getDeviceProfile().toSmallString());
    }

    private AnimatorListenerAdapter resetStateListener() {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mFallbackRecentsView.resetTaskVisuals();
                mStateManager.reapplyState();
            }
        };
    }

    public boolean canStartHomeSafely() {
        var conn = mSysUIConnectionTracker.getActiveComponent().getValue();
        if (conn != null) {
            var overviewCommandHelper = conn.getOverviewCommandHelper().getIfReady();
            return overviewCommandHelper == null || overviewCommandHelper.canStartHomeSafely();
        }
        return true;
    }

    @Override
    public boolean isRecentsViewVisible() {
        return getStateManager().getState().isRecentsViewVisible();
    }

    @Override
    public void onIdpChanged(boolean modelPropertiesChanged) {
        onHandleConfigurationChanged();
    }
}
