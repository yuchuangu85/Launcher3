/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.os.Trace.TRACE_TAG_APP;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_OPTIMIZE_MEASURE;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.internal.jank.Cuj.CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_WORKSPACE;
import static com.android.launcher3.Flags.blurOnMoreSurfaces;
import static com.android.launcher3.Flags.enableUnfoldStateAnimation;
import static com.android.launcher3.LauncherConstants.SavedInstanceKeys.PENDING_SPLIT_SELECT_INFO;
import static com.android.launcher3.LauncherConstants.SavedInstanceKeys.RUNTIME_STATE;
import static com.android.launcher3.LauncherSettings.Animation.DEFAULT_NO_ICON;
import static com.android.launcher3.LauncherSettings.Animation.VIEW_BACKGROUND;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.FLAG_SKIP_STATE_ANNOUNCEMENT;
import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.NO_OFFSET;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.Utilities.isRtl;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;
import static com.android.launcher3.desktop.DesktopStateProvider.getDesktopState;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_EXIT_HOME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_EXIT_INTERRUPTED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;
import static com.android.launcher3.popup.PinToTaskbarShortcut.getPinShortcutFactoryFromLauncher;
import static com.android.launcher3.popup.QuickstepSystemShortcut.getSplitSelectShortcutByPosition;
import static com.android.launcher3.popup.SystemShortcut.ADD_TO_HOME_SCREEN;
import static com.android.launcher3.popup.SystemShortcut.APP_INFO;
import static com.android.launcher3.popup.SystemShortcut.APP_LOCK;
import static com.android.launcher3.popup.SystemShortcut.BUBBLE_SHORTCUT;
import static com.android.launcher3.popup.SystemShortcut.DONT_SUGGEST_APP;
import static com.android.launcher3.popup.SystemShortcut.INSTALL;
import static com.android.launcher3.popup.SystemShortcut.PRIVATE_PROFILE_INSTALL;
import static com.android.launcher3.popup.SystemShortcut.REMOVE;
import static com.android.launcher3.popup.SystemShortcut.UNINSTALL_APP;
import static com.android.launcher3.popup.SystemShortcut.WIDGETS;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.ALL_APPS_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.MINUS_ONE_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.WIDGETS_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_TWO_BUTTON_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.QUICK_SWITCH_STATE_ORDINAL;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.split.SplitAnimationTimings.TABLET_HOME_TO_SPLIT;
import static com.android.quickstep.util.AnimUtils.completeRunnableListCallback;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.media.permission.SafeCloseable;
import android.os.Build;
import android.os.Bundle;
import android.os.IRemoteCallback;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AnalogClock;
import android.widget.TextClock;
import android.window.BackEvent;
import android.window.DesktopExperienceFlags;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedDispatcher;
import android.window.RemoteTransition;
import android.window.SplashScreen;

import androidx.annotation.AnyThread;
import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.app.viewcapture.ViewCaptureFactory;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.GestureNavContract;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepAccessibilityDelegate;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.display.LauncherDisplayInfo;
import com.android.launcher3.hybridhotseat.HotseatPredictionController;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.proxy.ProxyActivityStarter;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statehandlers.LauncherDepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.taskbar.TaskbarUiState;
import com.android.launcher3.taskbar.TaskbarUiStateMonitor;
import com.android.launcher3.taskbar.bubbles.BubbleActivityStarter;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.uioverrides.states.QuickstepAtomicAnimationFactory;
import com.android.launcher3.uioverrides.touchcontrollers.NavBarToHomeTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.NoButtonNavbarToOverviewTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.NoButtonQuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.QuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewDismissTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewLaunchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TransposedQuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TwoButtonNavbarTouchController;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ListenableRef;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.util.OverviewCommandHelperProtoLogProxy;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.util.PendingSplitSelectInfo;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.StableViewInfo;
import com.android.launcher3.util.StartActivityParams;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.WindowBlurState;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.LauncherActivityInterface;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.OverviewComponentObserver.OverviewChangeListener;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.RecentsStateUtilsKt;
import com.android.quickstep.split.SplitScreenAppResolver;
import com.android.quickstep.split.SplitSelectStateController;
import com.android.quickstep.split.SplitToWorkspaceController;
import com.android.quickstep.split.SplitWithKeyboardShortcutController;
import com.android.quickstep.sysuiconnection.SysUIConnectionTracker;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;
import com.android.quickstep.util.AnimUtils;
import com.android.quickstep.util.AsyncClockEventDelegate;
import com.android.quickstep.util.LauncherUnfoldAnimationController;
import com.android.quickstep.util.QuickstepOnboardingPrefs;
import com.android.quickstep.util.ScalingWorkspaceRevealAnim;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TraceStateLoggerHelper;
import com.android.quickstep.util.unfold.LauncherUnfoldTransitionController;
import com.android.quickstep.util.unfold.ProxyUnfoldTransitionProvider;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.systemui.animation.back.FlingOnBackAnimationCallback;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.unfold.RemoteUnfoldSharedComponent;
import com.android.systemui.unfold.UnfoldTransitionFactory;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.config.ResourceUnfoldTransitionConfig;
import com.android.systemui.unfold.config.UnfoldTransitionConfig;
import com.android.systemui.unfold.dagger.UnfoldMain;
import com.android.systemui.unfold.progress.RemoteUnfoldTransitionReceiver;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfig;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfigImpl;
import com.android.wm.shell.shared.bubbles.logging.EntryPoint;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import kotlin.Unit;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class QuickstepLauncher extends Launcher implements RecentsViewContainer,
        SystemShortcut.BubbleActivityStarter {
    private static final boolean TRACE_LAYOUTS =
            SystemProperties.getBoolean("persist.debug.trace_layouts", false);
    private static final String TRACE_RELAYOUT_CLASS =
            SystemProperties.get("persist.debug.trace_request_layout_class", null);

    protected static final String RING_APPEAR_ANIMATION_PREFIX = "RingAppearAnimation\t";

    private PredictedContainerInfo mAllAppsPredictions;
    private HotseatPredictionController mHotseatPredictionController;
    private LauncherDepthController mDepthController;
    private QuickstepTransitionManager mAppTransitionManager;

    private OverviewActionsView<?> mActionsView;
    private SysUIConnectionTracker mSysUIConnectionTracker;
    // Can be set from taskbar ui thread, read access has to follow below pattern
    //   TaskbarInteractor it = mTaskbarInteractor;
    //   if (ti != null) { ti.xxx(); }
    private @Nullable volatile TaskbarInteractor mTaskbarInteractor;
    // Will be updated when dragging from taskbar.
    private @Nullable volatile UnfoldTransitionProgressProvider mUnfoldTransitionProgressProvider;
    private @Nullable LauncherUnfoldAnimationController mLauncherUnfoldAnimationController;

    private SplitSelectStateController mSplitSelectStateController;
    private SplitWithKeyboardShortcutController mSplitWithKeyboardShortcutController;
    private SplitToWorkspaceController mSplitToWorkspaceController;
    private BubbleBarLocation mBubbleBarLocation;

    private TaskbarUiState mTaskbarUiState;
    private RecentsAnimationDeviceState mRecentsAnimationDeviceState;

    /**
     * If Launcher restarted while in the middle of an Overview split select, it needs this data to
     * recover. In all other cases this will remain null.
     */
    private PendingSplitSelectInfo mPendingSplitSelectInfo = null;

    @Nullable
    private DesktopRecentsTransitionController mDesktopRecentsTransitionController;

    private SafeCloseable mViewCapture;

    private boolean mEnableWidgetDepth;

    private boolean mCanShowAllAppsEducationView;

    private boolean mIsOverlayVisible;

    private final OverviewChangeListener mOverviewChangeListener = this::onOverviewTargetChanged;

    private BubbleFeatureConfig mBubbleFeatureConfig;

    private void setupBlurState() {
        ListenableRef<Boolean> blurState = WindowBlurState.getInstance(this);
        boolean blurEnabled = blurState.getValue();

        // Recreate launcher if the blur enabled state changes
        closeOnDestroy(blurState.forEach(getUiExecutor(), v -> {
            if (v != blurEnabled) mWallpaperThemeManager.recreateToUpdateTheme();
            return null;
        }));
        mDepthController = new LauncherDepthController(this, blurState);
        getTheme().applyStyle(blurEnabled ? R.style.OverviewBlurStyle
                : R.style.OverviewBlurFallbackStyle, true);
        if (blurOnMoreSurfaces()) {
            getTheme().applyStyle(blurEnabled ? R.style.FolderBlurStyle
                    : R.style.FolderBlurFallbackStyle, true);
        }
    }

    @Override
    protected void setupViews() {
        getAppWidgetHolder().setOnViewCreationCallback(new QuickstepInteractionHandler(this));
        setupBlurState();
        super.setupViews();
        SurfaceTransactionApplier surfaceTransactionApplier = new SurfaceTransactionApplier(
                getRootView());
        mDepthController.setSurfaceTransactionApplier(surfaceTransactionApplier);

        mActionsView = findViewById(R.id.overview_actions_view);
        RecentsView<?, LauncherState> overviewPanel = getOverviewPanel();
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(this);
        mSplitSelectStateController =
                new SplitSelectStateController(this, getStateManager(),
                        getDepthController(), getStatsLogManager(),
                        systemUiProxy, RecentsModel.INSTANCE.get(this),
                        () -> onStateBack(), mLauncherUiState.getSplitScreenUiState(),
                        new SplitScreenAppResolver(this));
        if (DesktopModeStatus.canEnterDesktopMode(this)) {
            mDesktopRecentsTransitionController = new DesktopRecentsTransitionController(
                    getStateManager(), systemUiProxy, getIApplicationThread(),
                    getDepthController());
        }
        ViewGroup emptyRecentsMessageView = findViewById(R.id.empty_recents_message_view);
        overviewPanel.init(mActionsView, mSplitSelectStateController,
                mDesktopRecentsTransitionController, surfaceTransactionApplier,
                emptyRecentsMessageView);
        mSplitWithKeyboardShortcutController = new SplitWithKeyboardShortcutController(
                this, mSplitSelectStateController);
        mSplitToWorkspaceController = new SplitToWorkspaceController(this,
                mSplitSelectStateController);
        mActionsView.updateDimension(getDeviceProfile(), overviewPanel.getLastComputedTaskSize());
        mActionsView.updateVerticalMargin(DisplayController.getNavigationMode(this));

        mAppTransitionManager = buildAppTransitionManager();
        mAppTransitionManager.registerRemoteAnimations();
        mAppTransitionManager.registerRemoteTransitions();

        mRecentsAnimationDeviceState = LauncherComponentProvider.get(this)
                .getRecentsAnimationDeviceStateRepository().get(getDisplayId());
        mSysUIConnectionTracker = SysUIConnectionTracker.get(this);
        mSysUIConnectionTracker.onConnected(this, c -> c.getTaskbarManager().setActivity(this));

        if (DesktopModeStatus.canEnterDesktopModeOrShowAppHandle(this)) {
            mSplitSelectStateController.initSplitFromDesktopController(this);
        }
        mHotseatPredictionController = new HotseatPredictionController(this);

        mEnableWidgetDepth = SystemProperties.getBoolean("ro.launcher.depth.widget", true);
        getWorkspace().addOverlayCallback(progress ->
                onTaskbarInAppDisplayProgressUpdate(progress, MINUS_ONE_PAGE_PROGRESS_INDEX));
        addBackAnimationCallback(mSplitSelectStateController.getSplitBackHandler());
        mTaskbarUiState = TaskbarUiStateMonitor.INSTANCE.get(this)
                .getTaskbarUiState(getDisplayId());

        mBubbleFeatureConfig = new BubbleFeatureConfigImpl(this, getDesktopState(this));
    }

    @Override
    public void logAppLaunch(StatsLogManager statsLogManager, ItemInfo info,
            InstanceId instanceId) {
        // If the app launch is from any of the surfaces in AllApps then add the InstanceId from
        // LiveSearchManager to recreate the AllApps session on the server side.
        if (mAllAppsSessionLogId != null && ALL_APPS.equals(
                getStateManager().getCurrentStableState())) {
            instanceId = mAllAppsSessionLogId;
        }

        StatsLogger logger = statsLogManager.logger().withItemInfo(info).withInstanceId(instanceId);

        if (mAllAppsPredictions != null
                && (info.itemType == ITEM_TYPE_APPLICATION
                || info.itemType == ITEM_TYPE_DEEP_SHORTCUT)) {
            List<ItemInfo> items = mAllAppsPredictions.getContents();
            int count = items.size();
            for (int i = 0; i < count; i++) {
                ItemInfo targetInfo = items.get(i);
                if (targetInfo.itemType == info.itemType
                        && targetInfo.user.equals(info.user)
                        && Objects.equals(targetInfo.getIntent(), info.getIntent())) {
                    logger.withRank(i);
                    break;
                }
            }
        }
        logger.log(LAUNCHER_APP_LAUNCH_TAP);

        mHotseatPredictionController.logLaunchedAppRankingInfo(info, instanceId);
    }

    @Override
    protected void completeAddShortcut(Intent data, int container, int screenId, int cellX,
            int cellY, PendingRequestArgs args) {
        if (container == CONTAINER_HOTSEAT) {
            mHotseatPredictionController.onDeferredDrop(cellX, cellY);
        }
        super.completeAddShortcut(data, container, screenId, cellX, cellY, args);
    }

    @Override
    protected LauncherAccessibilityDelegate createAccessibilityDelegate() {
        return new QuickstepAccessibilityDelegate(this);
    }

    /**
     * Returns Prediction controller for hybrid hotseat
     */
    public HotseatPredictionController getHotseatPredictionController() {
        return mHotseatPredictionController;
    }

    @Override
    public void enableHotseatEdu(boolean enable) {
        super.enableHotseatEdu(enable);
        mHotseatPredictionController.enableHotseatEdu(enable);
    }

    /**
     * Builds the {@link QuickstepTransitionManager} instance to use for managing transitions.
     */
    protected QuickstepTransitionManager buildAppTransitionManager() {
        return new QuickstepTransitionManager(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onStateOrResumeChanging(false /* inTransition */);
    }

    @Override
    protected void onScreenOnChanged(boolean isOn) {
        if (!isOn && isRecentsViewVisible()) {
            ((RecentsView) getOverviewPanel()).startHome();
        } else {
            super.onScreenOnChanged(isOn);
        }
    }

    @Override
    public RunnableList startActivitySafely(View v, Intent intent, ItemInfo item) {
        PredictionRowView<?> predictionRowView =
                getAppsView().getFloatingHeaderView().findFixedRowByType(PredictionRowView.class);
        // Pause the prediction row updates until the transition (if it exists) ends.
        predictionRowView.setPredictionUiUpdatePaused(true);
        RunnableList result = super.startActivitySafely(v, intent, item);
        if (result == null) {
            predictionRowView.setPredictionUiUpdatePaused(false);
        } else {
            result.add(() -> predictionRowView.setPredictionUiUpdatePaused(false));
        }
        return result;
    }

    @Override
    protected void onActivityFlagsChanged(int changeBits) {
        if ((changeBits & ACTIVITY_STATE_STARTED) != 0) {
            mDepthController.setActivityStarted(isStarted());
        }

        super.onActivityFlagsChanged(changeBits);
        if ((changeBits & (ACTIVITY_STATE_DEFERRED_RESUMED | ACTIVITY_STATE_STARTED
                | ACTIVITY_STATE_USER_ACTIVE | ACTIVITY_STATE_TRANSITION_ACTIVE)) != 0) {
            onStateOrResumeChanging((getActivityFlags() & ACTIVITY_STATE_TRANSITION_ACTIVE) == 0);
        }
        mLauncherUiState.setActivityFlag(getActivityFlags());
    }

    @Override
    protected void showAllAppsFromIntent(boolean alreadyOnHome) {
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY);
        super.showAllAppsFromIntent(alreadyOnHome);
    }

    public TaskbarUiState getTaskbarUiState() {
        return mTaskbarUiState;
    }

    protected void onItemClicked(View view) {
        if (!mSplitToWorkspaceController.handleSecondAppSelectionForSplit(view)) {
            super.getItemOnClickListener().onClick(view);
        }
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return this::onItemClicked;
    }

    @Override
    public Stream<SystemShortcut.Factory> getSupportedShortcuts(ItemInfo itemInfo) {
        // Order matters as it affects order of appearance in popup container
        List<SystemShortcut.Factory> shortcuts = new ArrayList(Arrays.asList(
                APP_INFO, WellbeingModel.SHORTCUT_FACTORY, mHotseatPredictionController));
        int container = itemInfo.container;
        if (canPinAppWithContextMenu()
                && DisplayController.getInfo(this).getShowDesktopTaskbarForFreeformDisplay()
                && canContainerHavePinContextMenu(container)) {
            TaskbarInteractor ti = mTaskbarInteractor;
            int maxPinnableCount = ti != null ? ti.getMaxPinnableCount() : -1;
            boolean supportPinAppsOverflow = ti != null && ti.getSupportsPinnedAppsOverflow();
            shortcuts.add(
                    0, getPinShortcutFactoryFromLauncher(maxPinnableCount, supportPinAppsOverflow));
        }

        shortcuts.addAll(getSplitShortcuts());
        shortcuts.add(WIDGETS);
        shortcuts.add(INSTALL);
        // TODO(b/444744861): Update private space apps to have its own container.
        boolean isPinnable = itemInfo instanceof ItemInfoWithIcon info
                && (info.runtimeStatusFlags & FLAG_NOT_PINNABLE) == 0;
        if (container == CONTAINER_HOTSEAT || container == CONTAINER_DESKTOP
                || /* Folder */ container > 0) {
            shortcuts.add(REMOVE);
        } else if (isPinnable && (container == CONTAINER_ALL_APPS
                || container == CONTAINER_ALL_APPS_PREDICTION)) {
            shortcuts.add(ADD_TO_HOME_SCREEN);
        }
        shortcuts.add(DONT_SUGGEST_APP);
        shortcuts.add(PRIVATE_PROFILE_INSTALL);
        if (Flags.enablePrivateSpace()) {
            shortcuts.add(UNINSTALL_APP);
        }
        if (mBubbleFeatureConfig.areAppBubblesSupported()) {
            shortcuts.add(BUBBLE_SHORTCUT);
        }
        if (android.security.Flags.appLockApis() && Flags.enableAppLockShortcut()) {
            shortcuts.add(APP_LOCK);
        }
        return shortcuts.stream();
    }

    private boolean canContainerHavePinContextMenu(int container) {
        return container == CONTAINER_ALL_APPS
                || container == CONTAINER_ALL_APPS_PREDICTION
                || container == CONTAINER_DESKTOP
                || container > 0; // container > 0 means the app is inside a folder.
    }

    private boolean canPinAppWithContextMenu() {
        return DesktopVisibilityController.INSTANCE.get(this).isInDesktopMode(getDisplayId())
                || mTaskbarUiState.getShowDesktopTaskbarForFreeformDisplay();
    }

    private List<SystemShortcut.Factory<QuickstepLauncher>> getSplitShortcuts() {
        if (!mDeviceProfile.getDeviceProperties().isLargeScreen()
                || mSplitSelectStateController.isSplitSelectActive()) {
            return Collections.emptyList();
        }
        if (getDisplayId() != DEFAULT_DISPLAY
                && !DesktopExperienceFlags.ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX.isTrue()) {
            return Collections.emptyList();
        }
        RecentsView recentsView = getOverviewPanel();
        // TODO(b/266482558): Pull it out of PagedOrentationHandler for split from workspace.
        List<SplitPositionOption> positions =
                recentsView.getPagedOrientationHandler().getSplitPositionOptions(
                        mDeviceProfile);
        List<SystemShortcut.Factory<QuickstepLauncher>> splitShortcuts = new ArrayList<>();
        for (SplitPositionOption position : positions) {
            splitShortcuts.add(getSplitSelectShortcutByPosition(position));
        }
        return splitShortcuts;
    }

    /**
     * Recents logic that triggers when launcher state changes or launcher activity stops/resumes.
     */
    private void onStateOrResumeChanging(boolean inTransition) {
        LauncherState state = getStateManager().getState();
        boolean started = ((getActivityFlags() & ACTIVITY_STATE_STARTED)) != 0;
        DeviceProfile profile = getDeviceProfile();
        boolean visible = state.areElementsVisible(getLauncherUiState(), HOTSEAT_ICONS)
                && started
                && isUserActive()
                && !profile.isVerticalBarLayout()
                && !mIsOverlayVisible;
        SystemUiProxy.INSTANCE.get(this)
                .setLauncherKeepClearAreaHeight(visible,
                        profile.getHotseatProfile().getBarSizePx());
    }

    @Override
    public void onOverlayVisibilityChanged(boolean visible) {
        super.onOverlayVisibilityChanged(visible);
        mIsOverlayVisible = visible;
    }

    @Override
    public void bindPredictedContainerInfo(PredictedContainerInfo info) {
        super.bindPredictedContainerInfo(info);
        switch (info.id) {
            case Favorites.CONTAINER_ALL_APPS_PREDICTION:
                mAllAppsPredictions = info;
                getAppsView().getFloatingHeaderView().findFixedRowByType(
                        PredictionRowView.class).setPredictedApps(info.getContents());
                break;
            case Favorites.CONTAINER_HOTSEAT_PREDICTION:
                mHotseatPredictionController.setPredictedItems(info);
                break;
        }
    }

    @Override
    public void onItemPinnedFromContextMenu() {
        super.onItemPinnedFromContextMenu();
        mHotseatPredictionController.onItemPinnedFromContextMenu();
    }

    @Override
    public void onDestroy() {
        if (mAppTransitionManager != null) {
            mAppTransitionManager.onActivityDestroyed();
        }
        mAppTransitionManager = null;

        if (mUnfoldTransitionProgressProvider != null) {
            mUnfoldTransitionProgressProvider.destroy();
        }

        OverviewComponentObserver.INSTANCE.get(this)
                .removeOverviewChangeListener(mOverviewChangeListener);

        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onDestroy();
        }

        if (mSplitSelectStateController != null) {
            mSplitSelectStateController.onDestroy();
        }

        RecentsView recentsView = getOverviewPanel();
        if (recentsView != null) {
            recentsView.destroy();
        }

        super.onDestroy();
        if (mViewCapture != null) mViewCapture.close();
        removeBackAnimationCallback(mSplitSelectStateController.getSplitBackHandler());
    }

    @Override
    public void onStateSetEnd(LauncherState state) {
        super.onStateSetEnd(state);
        handlePendingActivityRequest();

        switch (state.ordinal) {
            case HINT_STATE_ORDINAL: {
                Workspace<?> workspace = getWorkspace();
                getStateManager().goToState(NORMAL);
                if (workspace.getNextPage() != Workspace.DEFAULT_PAGE) {
                    workspace.post(workspace::moveToDefaultScreen);
                }
                break;
            }
            case HINT_STATE_TWO_BUTTON_ORDINAL: {
                getStateManager().goToState(OVERVIEW);
                getDragLayer().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                break;
            }
            case OVERVIEW_STATE_ORDINAL: {
                RecentsView rv = getOverviewPanel();
                sendCustomAccessibilityEvent(
                        rv.getPageAt(rv.getCurrentPage()), TYPE_VIEW_FOCUSED, null);
                break;
            }
            case QUICK_SWITCH_STATE_ORDINAL: {
                RecentsView rv = getOverviewPanel();
                TaskView currentPageTask = rv.getCurrentPageTaskView();
                TaskView fallbackTask = rv.getFirstTaskView();
                if (currentPageTask != null || fallbackTask != null) {
                    TaskView taskToLaunch = currentPageTask;
                    if (currentPageTask == null) {
                        taskToLaunch = fallbackTask;
                        ActiveGestureProtoLogProxy.logQuickSwitchFromHomeFallback(
                                rv.getCurrentPage());
                    }
                    taskToLaunch.launchWithoutAnimation(success -> {
                        if (!success) {
                            getStateManager().goToState(OVERVIEW);
                        } else {
                            getStateManager().moveToRestState();
                        }
                        return Unit.INSTANCE;
                    });
                } else {
                    ActiveGestureProtoLogProxy.logQuickSwitchFromHomeFailed(rv.getCurrentPage());
                    getStateManager().goToState(NORMAL);
                }
                break;
            }

        }
    }

    @Override
    protected void setTitle(@NonNull LauncherState state) {
        // Prevent accessibility title update announcement
        getWindow().getAttributes().accessibilityTitle = state.hasFlag(FLAG_SKIP_STATE_ANNOUNCEMENT)
                ? getString(state.getTitle()) : null;
        super.setTitle(state);
    }

    @Override
    public TouchController[] createTouchControllers() {
        NavigationMode mode = DisplayController.getNavigationMode(this);

        ArrayList<TouchController> list = new ArrayList<>();
        list.add(getDragController());
        BiConsumer<AnimatorSet, Long> splitAnimator = (animatorSet, duration) ->
                animatorSet.play(mSplitSelectStateController.getSplitAnimationController()
                        .createPlaceholderDismissAnim(this, LAUNCHER_SPLIT_SELECTION_EXIT_HOME,
                                duration));
        switch (mode) {
            case NO_BUTTON:
                list.add(new NoButtonQuickSwitchTouchController(this));
                list.add(new NavBarToHomeTouchController(this, splitAnimator));
                list.add(new NoButtonNavbarToOverviewTouchController(this, splitAnimator));
                break;
            case TWO_BUTTONS:
                list.add(new TwoButtonNavbarTouchController(this));
                list.add(getDeviceProfile().isVerticalBarLayout()
                        ? new TransposedQuickSwitchTouchController(this)
                        : new QuickSwitchTouchController(this));
                list.add(new PortraitStatesTouchController(this));
                break;
            case THREE_BUTTONS:
                list.add(new NoButtonQuickSwitchTouchController(this));
                list.add(new NavBarToHomeTouchController(this, splitAnimator));
                list.add(new NoButtonNavbarToOverviewTouchController(this, splitAnimator));
                list.add(new PortraitStatesTouchController(this));
                break;
            default:
                list.add(new PortraitStatesTouchController(this));
                break;
        }
        list.add(new StatusBarTouchController(this, () -> this.isInState(LauncherState.NORMAL)));
        list.add(new TaskViewLaunchTouchController<>(this,
                animController -> getStateManager().setCurrentUserControlledAnimation(
                        animController)));
        list.add(new TaskViewDismissTouchController<>(this));
        return list.toArray(new TouchController[list.size()]);
    }

    @Override
    public AtomicAnimationFactory<LauncherState> createAtomicAnimationFactory() {
        return new QuickstepAtomicAnimationFactory(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mPendingSplitSelectInfo = ObjectWrapper.unwrap(
                    savedInstanceState.getIBinder(PENDING_SPLIT_SELECT_INFO));
        }
        initUnfoldTransitionProgressProvider();
        mViewCapture = ViewCaptureFactory.getInstance(this).startCapture(getWindow());
        getWindow().addPrivateFlags(PRIVATE_FLAG_OPTIMIZE_MEASURE);
        QuickstepOnboardingPrefs.setup(this);
        View.setTraceLayoutSteps(TRACE_LAYOUTS);
        View.setTracedRequestLayoutClassClass(TRACE_RELAYOUT_CLASS);
        OverviewComponentObserver.INSTANCE.get(this)
                .addOverviewChangeListener(mOverviewChangeListener);
        new TraceStateLoggerHelper(this).startTraceStateLogger();
    }

    @Override
    protected boolean initDeviceProfile(InvariantDeviceProfile idp) {
        final boolean ret = super.initDeviceProfile(idp);
        if (ret) {
            SystemUiProxy.INSTANCE.get(this).setLauncherAppIconSize(
                    mDeviceProfile.getWorkspaceProfile().getIconSizePx());
        }
        return ret;
    }

    @Override
    public void startSplitSelection(SplitSelectSource splitSelectSource) {
        RecentsView recentsView = getOverviewPanel();
        // Check if there is already an instance of this app running, if so, initiate the split
        // using that.
        mSplitSelectStateController.findLastActiveTasksAndRunCallback(
                Collections.singletonList(splitSelectSource.getItemInfo().getResolvedTargetInfo()),
                false /* findExactPairMatch */,
                foundTasks -> {
                    @Nullable Task foundTask = foundTasks[0];
                    boolean taskWasFound = foundTask != null;
                    splitSelectSource.alreadyRunningTaskId = taskWasFound
                            ? foundTask.key.id
                            : INVALID_TASK_ID;
                    startSplitToHome(splitSelectSource);
                }
        );
    }

    /** TODO(b/266482558) Migrate into SplitSelectStateController or someplace split specific. */
    private void startSplitToHome(SplitSelectSource source) {
        AbstractFloatingView.closeAllOpenViews(this);
        int splitPlaceholderSize = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_size);
        int splitPlaceholderInset = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_inset);
        Rect tempRect = new Rect();

        mSplitSelectStateController.setInitialTaskSelect(source.intent,
                source.position.stagePosition, source.getItemInfo(), source.splitEvent,
                source.alreadyRunningTaskId);

        RecentsView recentsView = getOverviewPanel();
        recentsView.getPagedOrientationHandler().getInitialSplitPlaceholderBounds(
                splitPlaceholderSize, splitPlaceholderInset, getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), tempRect);

        PendingAnimation anim = new PendingAnimation(TABLET_HOME_TO_SPLIT.getDuration());
        RectF startingTaskRect = new RectF();
        final FloatingTaskView floatingTaskView = FloatingTaskView.getFloatingTaskView(this,
                source.getView(), null /* thumbnail */, source.getDrawable(), startingTaskRect);
        floatingTaskView.setAlpha(1);
        floatingTaskView.addStagingAnimation(anim, startingTaskRect, tempRect,
                false /* fadeWithThumbnail */, true /* isStagedTask */);
        floatingTaskView.setOnClickListener(view ->
                mSplitSelectStateController.getSplitAnimationController().
                        playAnimPlaceholderToFullscreen(this, view, Optional.empty()));
        floatingTaskView.setContentDescription(source.getItemInfo().contentDescription);

        mSplitSelectStateController.setFirstFloatingTaskView(floatingTaskView);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                getDragLayer().removeView(floatingTaskView);
                mSplitSelectStateController.getSplitAnimationController()
                        .removeSplitInstructionsView(QuickstepLauncher.this);
                mSplitSelectStateController.resetState();
            }
        });
        anim.add(mSplitSelectStateController.getSplitAnimationController()
                .getShowSplitInstructionsAnim(this).buildAnim());
        anim.buildAnim().start();
    }

    @Override
    public boolean isSplitSelectionActive() {
        if (mSplitSelectStateController == null) {
            return false;
        }
        return mSplitSelectStateController.isSplitSelectActive();
    }

    @Override
    public void onStateTransitionCompletedAfterSwipeToHome(LauncherState finalState) {
        TaskbarInteractor ti = mTaskbarInteractor;
        if (ti != null) {
            ti.onStateTransitionCompletedAfterSwipeToHome(finalState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onResume();
        }

        TaskbarInteractor ti = mTaskbarInteractor;
        if (ti != null) {
            ti.onLauncherResume();
        }
    }

    @Override
    protected void onPause() {
        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onPause();
        }

        super.onPause();

        // If Launcher pauses before both split apps are selected, exit split screen.
        if (!mSplitSelectStateController.isBothSplitAppsConfirmed() &&
                !mSplitSelectStateController.isLaunchingFirstAppFullscreen()) {
            mSplitSelectStateController
                    .logExitReason(LAUNCHER_SPLIT_SELECTION_EXIT_INTERRUPTED);
            mSplitSelectStateController.getSplitAnimationController()
                    .playPlaceholderDismissAnim(this, LAUNCHER_SPLIT_SELECTION_EXIT_INTERRUPTED,
                            () -> getStateManager().moveToRestState());
        }

        TaskbarInteractor ti = mTaskbarInteractor;
        if (ti != null) {
            ti.onLauncherPause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        TaskbarInteractor ti = mTaskbarInteractor;
        if (ti != null) {
            ti.onLauncherStop();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        boolean intentHasGestureNavContract = GestureNavContract.canBuildFromIntent(intent);
        super.onNewIntent(intent);
        var conn = mSysUIConnectionTracker.getActiveComponent().getValue();
        if (conn != null) {
            var overviewCommandHelper = conn.getOverviewCommandHelper().getIfReady();
            if (overviewCommandHelper != null) {
                overviewCommandHelper.clearPendingCommands();
            }
        }
        if (!intentHasGestureNavContract) {
            BaseContainerInterface<?, ?> defaultDisplayContainerInterface =
                    OverviewComponentObserver.INSTANCE.get(this).getContainerInterface(
                            DEFAULT_DISPLAY);
            if (defaultDisplayContainerInterface != null
                    && defaultDisplayContainerInterface.getCreatedContainer()
                    instanceof RecentsWindowManager recentsWindowManager) {
                // Forcibly reset state so the recents surface doesn't get stuck in background app
                // state
                recentsWindowManager.getStateManager().moveToRestState(
                        /* isAnimated= */ recentsWindowManager.isStarted());
            }
        }
    }

    @Override
    protected void logOnNewIntent(boolean alreadyOnHome, boolean shouldMoveToDefaultScreen,
            String action, boolean internalStateHandled) {
        OverviewCommandHelperProtoLogProxy.logOnNewIntent(alreadyOnHome, shouldMoveToDefaultScreen,
                action, internalStateHandled);
    }

    public QuickstepTransitionManager getAppTransitionManager() {
        return mAppTransitionManager;
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
    protected void handleGestureContract(Intent intent) {
        if (GestureNavContract.isContractEnabled(intent)) {
            super.handleGestureContract(intent);
            new ScalingWorkspaceRevealAnim(
                    /* launcher= */ this,
                    /* siblingAnimation= */ null,
                    /* windowTargetRect= */ null,
                    /* playAlphaReveal= */ true,
                    /* playBlur= */ true).start();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        RecentsModel.INSTANCE.get(this).onTrimMemory(level);
    }

    @Override
    public void onUiChangedWhileSleeping() {
        // Remove the snapshot because the content view may have obvious changes.
        UI_HELPER_EXECUTOR.execute(
                () -> ActivityManagerWrapper.getInstance().invalidateHomeTaskSnapshot(this));
    }

    @Override
    public void onAllAppsTransition(float progress) {
        super.onAllAppsTransition(progress);
        onTaskbarInAppDisplayProgressUpdate(progress, ALL_APPS_PAGE_PROGRESS_INDEX);
    }

    @Override
    public void onWidgetsTransition(float progress) {
        super.onWidgetsTransition(progress);
        onTaskbarInAppDisplayProgressUpdate(progress, WIDGETS_PAGE_PROGRESS_INDEX);
        if (mEnableWidgetDepth) {
            getDepthController().widgetDepth.setValue(Utilities.mapToRange(
                    progress, 0f, 1f, 0f,
                    getDeviceProfile().getBottomSheetProfile().getBottomSheetDepth(), EMPHASIZED));
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return tryHandleBackKey(event) || super.dispatchKeyEvent(event);
    }

    // TODO (b/267248420) Once the recents input consumer has been removed, there is no need to
    //  handle the back key specially.
    private boolean tryHandleBackKey(KeyEvent event) {
        // Unlike normal activity, recents can receive input event from InputConsumer, so the input
        // event won't go through ViewRootImpl#InputStage#onProcess.
        // So when receive back key, try to do the same check thing in
        // ViewRootImpl#NativePreImeInputStage#onProcess
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK
                || event.getAction() != KeyEvent.ACTION_UP || event.isCanceled()) {
            return false;
        }

        getOnBackAnimationCallback().onBackInvoked();
        return true;
    }

    @Override
    protected void registerBackDispatcher() {
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new FlingOnBackAnimationCallback() {

                    @Nullable OnBackAnimationCallback mActiveOnBackAnimationCallback;

                    @Override
                    public void onBackStartedCompat(@NonNull BackEvent backEvent) {
                        if (mActiveOnBackAnimationCallback != null) {
                            mActiveOnBackAnimationCallback.onBackCancelled();
                        }
                        mActiveOnBackAnimationCallback = getOnBackAnimationCallback();
                        mActiveOnBackAnimationCallback.onBackStarted(backEvent);
                    }

                    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    @Override
                    public void onBackInvokedCompat() {
                        // Recreate mActiveOnBackAnimationCallback if necessary to avoid NPE
                        // because:
                        // 1. b/260636433: In 3-button-navigation mode, onBackStarted() is not
                        // called on ACTION_DOWN before onBackInvoked() is called in ACTION_UP.
                        // 2. Launcher#onBackPressed() will call onBackInvoked() without calling
                        // onBackInvoked() beforehand.
                        if (mActiveOnBackAnimationCallback == null) {
                            mActiveOnBackAnimationCallback = getOnBackAnimationCallback();
                        }
                        mActiveOnBackAnimationCallback.onBackInvoked();
                        mActiveOnBackAnimationCallback = null;
                        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onBackInvoked");
                    }

                    @Override
                    public void onBackProgressedCompat(@NonNull BackEvent backEvent) {
                        if (!FeatureFlags.IS_STUDIO_BUILD
                                && mActiveOnBackAnimationCallback == null) {
                            return;
                        }
                        mActiveOnBackAnimationCallback.onBackProgressed(backEvent);
                    }

                    @Override
                    public void onBackCancelledCompat() {
                        if (!FeatureFlags.IS_STUDIO_BUILD
                                && mActiveOnBackAnimationCallback == null) {
                            return;
                        }
                        mActiveOnBackAnimationCallback.onBackCancelled();
                        mActiveOnBackAnimationCallback = null;
                    }
                });
    }

    private void onTaskbarInAppDisplayProgressUpdate(float progress, int flag) {
        TaskbarInteractor ti = mTaskbarInteractor;
        if (ti != null) {
            ti.onTaskbarInAppDisplayProgressUpdate(progress, flag);
        }
    }

    @AnyThread
    public boolean isShowingPostBootLoaderDialog() {
        return false;
    }

    protected void updateTaskbarsVisibility() {
        var conn = mSysUIConnectionTracker.getActiveComponent().getValue();
        if (conn != null) {
            conn.getTaskbarManager().updateTaskbarsVisibility();
        }
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intentSender = intent;
            params.fillInIntent = fillInIntent;
            params.flagsMask = flagsMask;
            params.flagsValues = flagsValues;
            params.extraFlags = extraFlags;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask,
                    flagsValues, extraFlags, options);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intent = intent;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startActivityForResult(intent, requestCode, options);
        }
    }

    @Override
    public void setResumed() {
        super.setResumed();
    }

    @Override
    protected void onDeferredResumed() {
        super.onDeferredResumed();
        handlePendingActivityRequest();
    }

    private void handlePendingActivityRequest() {
        // Check to see whether we're currently animating between states. If we are, ensure the
        // ProxyActivity isn't cancelled while the Taskbar/Recents animation is still in progress.
        boolean isTransitionRunning = getStateManager().isInTransition();

        if (mPendingActivityRequestCode != -1 && isInState(NORMAL)
                && ((getActivityFlags() & ACTIVITY_STATE_DEFERRED_RESUMED) != 0)
                && !isTransitionRunning) {
            // Remove any active ProxyActivityStarter task and send RESULT_CANCELED to Launcher.
            onActivityResult(mPendingActivityRequestCode, RESULT_CANCELED, null);
            // ProxyActivityStarter is started with clear task to reset the task after which it
            // removes the task itself.
            startActivity(ProxyActivityStarter.getLaunchIntent(this, null));
        }
    }

    private void onOverviewTargetChanged(boolean isHomeAndOverviewSame) {
        QuickstepTransitionManager transitionManager = getAppTransitionManager();
        if (transitionManager != null) {
            transitionManager.onOverviewTargetChange();
        }
        if (isHomeAndOverviewSame) {
            var conn = mSysUIConnectionTracker.getActiveComponent().getValue();
            if (conn != null) {
                conn.getTaskbarManager().setActivity(this);
            }
        }
    }

    private void initUnfoldTransitionProgressProvider() {
        if (!enableUnfoldStateAnimation()) {
            final UnfoldTransitionConfig config = new ResourceUnfoldTransitionConfig();
            if (config.isEnabled()) {
                initRemotelyCalculatedUnfoldAnimation(config);
            }
        } else {
            ProxyUnfoldTransitionProvider provider =
                    SystemUiProxy.INSTANCE.get(this).getUnfoldTransitionProvider();
            if (provider != null) {
                new LauncherUnfoldTransitionController(this, provider);
            }
        }
    }

    /** Receives animation progress from sysui process. */
    private void initRemotelyCalculatedUnfoldAnimation(UnfoldTransitionConfig config) {
        RemoteUnfoldSharedComponent unfoldComponent =
                UnfoldTransitionFactory.createRemoteUnfoldSharedComponent(
                        /* context= */ this,
                        config,
                        getMainExecutor(),
                        getMainThreadHandler(),
                        /* backgroundExecutor= */ UI_HELPER_EXECUTOR,
                        /* bgHandler= */ UI_HELPER_EXECUTOR.getHandler(),
                        /* tracingTagPrefix= */ "launcher",
                        getSystemService(DisplayManager.class)
                );

        final RemoteUnfoldTransitionReceiver remoteUnfoldTransitionProgressProvider =
                unfoldComponent.getRemoteTransitionProgress().orElseThrow(
                        () -> new IllegalStateException(
                                "Trying to create getRemoteTransitionProgress when the transition "
                                        + "is disabled"));
        mUnfoldTransitionProgressProvider = remoteUnfoldTransitionProgressProvider;

        closeOnDestroy(SystemUiProxy.INSTANCE.get(this).getUnfoldAnimationListeners()
                .register(remoteUnfoldTransitionProgressProvider));

        initUnfoldAnimationController(mUnfoldTransitionProgressProvider,
                unfoldComponent.getRotationChangeProvider());
    }

    private void initUnfoldAnimationController(UnfoldTransitionProgressProvider progressProvider,
            @UnfoldMain RotationChangeProvider rotationChangeProvider) {
        mLauncherUnfoldAnimationController = new LauncherUnfoldAnimationController(
                /* launcher= */ this,
                getWindowManager(),
                progressProvider,
                rotationChangeProvider
        );
    }

    @AnyThread
    @Override
    public void setTaskbarInteractor(@Nullable TaskbarInteractor taskbarInteractor) {
        mTaskbarInteractor = taskbarInteractor;
    }

    public @Nullable TaskbarInteractor getTaskbarInteractor() {
        return mTaskbarInteractor;
    }

    /** Offset [targetRect] to account for the hotseat translation if applicable. */
    public void offsetBoundsXToHotseatIfApplicable(RectF targetRect, View targetView) {
        if (!(targetView.getTag() instanceof ItemInfo itemInfo) || !itemInfo.isInHotseat()) {
            return;
        }
        int dx = 0;
        if (isBubbleBarEnabled() && mBubbleBarLocation != null) {
            boolean isBubblesOnLeft = mBubbleBarLocation.isOnLeft(isRtl(getResources()));
            dx += mDeviceProfile.getHotseatTranslationXForNavBar(this, isBubblesOnLeft);
        }
        if (isBubbleBarEnabled()
                && mDeviceProfile.shouldAdjustHotseatForBubbleBar(asContext(), hasBubbles())) {
            dx += (int) mDeviceProfile.getHotseatAdjustedTranslation(asContext(), itemInfo.cellX);
        }
        targetRect.offset(dx, 0);
    }

    public SplitToWorkspaceController getSplitToWorkspaceController() {
        return mSplitToWorkspaceController;
    }

    @Override
    protected void handleSplitAnimationGoingToHome(StatsLogManager.EventEnum splitDismissReason) {
        super.handleSplitAnimationGoingToHome(splitDismissReason);
        mSplitSelectStateController.getSplitAnimationController()
                .playPlaceholderDismissAnim(this, splitDismissReason);
    }

    @Override
    public void dismissSplitSelection(StatsLogManager.LauncherEvent splitDismissEvent) {
        super.dismissSplitSelection(splitDismissEvent);
        mSplitSelectStateController.getSplitAnimationController()
                .playPlaceholderDismissAnim(this, splitDismissEvent);
    }

    @Override
    public OverviewActionsView<?> getActionsView() {
        return mActionsView;
    }

    @Override
    protected void closeOpenViews(boolean animate) {
        super.closeOpenViews(animate);
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY);
    }

    @Override
    public void collectStateHandlers(List<StateHandler<LauncherState>> out) {
        super.collectStateHandlers(out);
        out.add(getDepthController());
        out.add(new RecentsViewStateController(this));
    }

    @Override
    public LauncherDepthController getDepthController() {
        return mDepthController;
    }

    @AnyThread
    @Nullable
    public UnfoldTransitionProgressProvider getUnfoldTransitionProgressProvider() {
        return mUnfoldTransitionProgressProvider;
    }

    @Override
    public boolean supportsAdaptiveIconAnimation(View clickedView) {
        return true;
    }

    @Override
    public float[] getNormalOverviewScaleAndOffset() {
        return DisplayController.getNavigationMode(this).hasGestures
                ? new float[] {1, 1} : new float[] {1.1f, NO_OFFSET};
    }

    @Override
    public void finishBindingItems(IntSet pagesBoundFirst) {
        super.finishBindingItems(pagesBoundFirst);
        // Instantiate and initialize WellbeingModel now that its loading won't interfere with
        // populating workspace.
        // TODO: Find a better place for this
        WellbeingModel.INSTANCE.get(this);

        if (mLauncherUnfoldAnimationController != null) {
            // This is needed in case items are rebound while the unfold animation is in progress.
            mLauncherUnfoldAnimationController.updateRegisteredViewsIfNeeded();
        }
    }

    @NonNull
    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        ActivityOptionsWrapper activityOptions = mAppTransitionManager.getActivityLaunchOptions(
                v, item != null ? item : (ItemInfo) v.getTag());
        if (mLastTouchUpTime > 0) {
            activityOptions.options.setSourceInfo(ActivityOptions.SourceInfo.TYPE_LAUNCHER,
                    mLastTouchUpTime);
        }
        if (item != null && (item.animationType == DEFAULT_NO_ICON
                || item.animationType == VIEW_BACKGROUND)) {
            activityOptions.options.setSplashScreenStyle(
                    SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        } else {
            activityOptions.options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        }
        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : DEFAULT_DISPLAY);
        activityOptions.options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return activityOptions;
    }

    @Override
    public ActivityOptionsWrapper makeDefaultActivityOptions(int splashScreenStyle) {
        RunnableList callbacks = new RunnableList();
        ActivityOptions options = ActivityOptions.makeCustomAnimation(this, 0, 0);
        options.setSplashScreenStyle(splashScreenStyle);
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);

        IRemoteCallback endCallback = completeRunnableListCallback(
                callbacks, this, getUiExecutor());
        options.setOnAnimationAbortListener(endCallback);
        options.setOnAnimationFinishedListener(endCallback);
        return new ActivityOptionsWrapper(options, callbacks);
    }

    @Override
    @BinderThread
    public void enterStageSplitFromRunningApp(boolean leftOrTop, int displayId) {
        mSplitWithKeyboardShortcutController.enterStageSplit(leftOrTop, displayId);
    }

    @Override
    public void onDisplayInfoChanged(LauncherDisplayInfo info, int flags) {
        super.onDisplayInfoChanged(info, flags);
        // When changing screens, force moving to rest state similar to StatefulActivity.onStop, as
        // StatefulActivity isn't called consistently.
        if ((flags & CHANGE_ACTIVE_SCREEN) != 0) {
            // Do not animate moving to rest state, as it can clash with Launcher#onIdpChanged
            // where reapplyUi calls StateManager's reapplyState during the state change animation,
            // and cancel the state change unexpectedly. The screen will be off during screen
            // transition, hiding the unanimated transition.
            getStateManager().moveToRestState(/* isAnimated = */false);
        }

        if ((flags & CHANGE_NAVIGATION_MODE) != 0) {
            getDragLayer().recreateControllers();
            if (mActionsView != null) {
                mActionsView.updateVerticalMargin(info.getNavigationMode());
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // If Launcher shuts downs during split select, we save some extra data in the recovery
        // bundle to allow graceful recovery. The normal LauncherState restore mechanism doesn't
        // work in this case because restoring straight to OverviewSplitSelect without staging data,
        // or before the tasks themselves have loaded into Overview, causes a crash. So we tell
        // Launcher to first restore into Overview state, wait for the relevant tasks and icons to
        // load in, and then proceed to OverviewSplitSelect.
        if (isInState(OVERVIEW_SPLIT_SELECT)) {
            // Launcher will restart in Overview and then transition to OverviewSplitSelect.
            outState.putIBinder(PENDING_SPLIT_SELECT_INFO, ObjectWrapper.wrap(
                    new PendingSplitSelectInfo(
                            mSplitSelectStateController.getInitialTaskId(),
                            mSplitSelectStateController.getActiveSplitStagePosition(),
                            mSplitSelectStateController.getSplitEvent())
            ));
            outState.putInt(RUNTIME_STATE, OVERVIEW.ordinal);
        }
    }

    /**
     * When Launcher restarts, it sometimes needs to recover to a split selection state.
     * This function checks if such a recovery is needed.
     * @return a boolean representing whether the launcher is waiting to recover to
     * OverviewSplitSelect state.
     */
    public boolean hasPendingSplitSelectInfo() {
        return mPendingSplitSelectInfo != null;
    }

    /**
     * See {@link #hasPendingSplitSelectInfo()}
     */
    public @Nullable PendingSplitSelectInfo getPendingSplitSelectInfo() {
        return mPendingSplitSelectInfo;
    }

    /**
     * When the launcher has successfully recovered to OverviewSplitSelect state, this function
     * deletes the recovery data, returning it to a null state.
     */
    public void finishSplitSelectRecovery() {
        mPendingSplitSelectInfo = null;
    }

    /**
     * Sets flag whether a predictive back-to-home animation is in progress
     */
    public void setPredictiveBackToHomeInProgress(boolean isInProgress) {
        if (mRecentsAnimationDeviceState != null) {
            mRecentsAnimationDeviceState.setPredictiveBackToHomeInProgress(isInProgress);
        }
    }

    @Override
    public boolean areDesktopTasksVisible() {
        return DesktopVisibilityController.INSTANCE.get(this)
                .isInDesktopModeAndNotInOverview(getDisplayId());
    }

    @Override
    public boolean shouldShowHomeBehindDesktop() {
        return getDesktopState(this).getShouldShowHomeBehindDesktop();
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        Trace.instantForTrack(TRACE_TAG_APP, "QuickstepLauncher#DeviceProfileChanged",
                getDeviceProfile().toSmallString());
        SystemUiProxy.INSTANCE.get(this).setLauncherAppIconSize(
                mDeviceProfile.getWorkspaceProfile().getIconSizePx());
    }

    @Override
    public SparseArray<ItemInfo> getPinnedItems() {
        TaskbarInteractor ti = mTaskbarInteractor;
        if (ti == null) return super.getPinnedItems();
        return ti.getPinnedApps();
    }

    /**
     * Launches the given {@link SplitTask} in splitscreen.
     */
    public void launchSplitTasks(
            @NonNull SplitTask splitTask, @Nullable RemoteTransition remoteTransition) {
        mSplitSelectStateController.launchExistingSplitPair(null /* launchingTaskView */,
                splitTask.getTopLeftTask().key.id,
                splitTask.getBottomRightTask().key.id,
                SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                /* callback= */ success -> mSplitSelectStateController.resetState(),
                /* freezeTaskList= */ false,
                splitTask.getSplitBounds() == null
                        ? SNAP_TO_2_50_50
                        : splitTask.getSplitBounds().snapPosition,
                remoteTransition);
    }

    /**
     * Launches two apps as an app pair.
     */
    public void launchAppPair(AppPairIcon appPairIcon) {
        // Potentially show the Taskbar education once the app pair launch finishes
        mSplitSelectStateController.getAppPairsController().launchAppPair(appPairIcon,
                CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_WORKSPACE,
                (success) -> {
                    TaskbarInteractor ti = mTaskbarInteractor;
                    if (success && ti != null) {
                        ti.showEduOnAppLaunch();
                    }
                });
    }

    @Override
    public void startHome(boolean animated, @Nullable Runnable onHomeAnimationComplete) {
        StateManager stateManager = getStateManager();
        animated &= stateManager.shouldAnimateStateChange();
        if (mSplitSelectStateController.isSplitSelectActive()) {
            AnimUtils.goToNormalStateWithSplitDismissal(stateManager, this,
                    LAUNCHER_SPLIT_SELECTION_EXIT_HOME,
                    mSplitSelectStateController.getSplitAnimationController(),
                    onHomeAnimationComplete);
        } else {
            stateManager.goToState(NORMAL, animated,
                    onHomeAnimationComplete != null ? forEndCallback(onHomeAnimationComplete)
                            : null);
        }
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
    public boolean isBubbleBarEnabled() {
        return (mTaskbarInteractor != null && BubbleBarController.isBubbleBarEnabled());
    }

    @Override
    public boolean hasBubbles() {
        return mTaskbarUiState.getHasBubbles();
    }

    @Override
    public boolean handleIncorrectSplitTargetSelection() {
        if (!mSplitSelectStateController.isSplitSelectActive()) {
            return false;
        }
        mSplitSelectStateController.getSplitInstructionsView().goBoing();
        return true;
    }

    @Override
    public void showShortcutBubble(ShortcutInfo info, EntryPoint entryPoint) {
        if (info == null) return;
        BubbleActivityStarter.INSTANCE.get(this).showShortcutBubble(info, entryPoint);
    }

    @Override
    public void showAppBubble(Intent intent, UserHandle user, EntryPoint entryPoint) {
        if (intent == null || intent.getPackage() == null) return;
        BubbleActivityStarter.INSTANCE.get(this).showAppBubble(intent, user, entryPoint);
    }

    /** Sets the location of the bubble bar */
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        mBubbleBarLocation = bubbleBarLocation;
    }

    /**
     * Similar to {@link #getFirstHomeElementForAppClose} but also matches all apps if its visible
     */
    @Nullable
    public View getFirstVisibleElementForAppClose(
            @Nullable StableViewInfo svi, String packageName, UserHandle user) {
        if (isInState(LauncherState.ALL_APPS)) {
            AllAppsRecyclerView activeRecyclerView = getAppsView().getActiveRecyclerView();
            View v = null;
            if (svi != null) {
                // Preferred item match
                v = activeRecyclerView.findViewByPredicate(view ->
                        view.isAggregatedVisible()
                                && view.getTag() instanceof ItemInfo info && svi.matches(info));
            }
            if (v == null) {
                // Package user match
                v = activeRecyclerView.findViewByPredicate(view ->
                        view.isAggregatedVisible() && view.getTag() instanceof ItemInfo info
                                && info.itemType == ITEM_TYPE_APPLICATION
                                && info.user.equals(user)
                                && TextUtils.equals(info.getTargetPackage(), packageName));
            }

            if (v != null && activeRecyclerView.computeVerticalScrollOffset() > 0) {
                RectF locationBounds = new RectF();
                FloatingIconView.getLocationBoundsForView(this, v, false, locationBounds,
                        new Rect());
                if (locationBounds.top < getAppsView().getHeaderBottom()) {
                    // Icon is covered by scrim, return null to play fallback animation.
                    return null;
                }
            }
            return v;
        }

        return getFirstHomeElementForAppClose(svi, packageName, user);
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        if (mDepthController != null) {
            mDepthController.dump(prefix, writer);
        }
        RecentsView recentsView = getOverviewPanel();
        writer.println("\nQuickstepLauncher:");
        writer.println(prefix + "\tmOrientationState: " + (recentsView == null ? "recentsNull" :
                recentsView.getPagedViewOrientedState()));
        if (recentsView != null) {
            recentsView.getSplitSelectController().dump(prefix, writer);
        }
        if (mAppTransitionManager != null) {
            mAppTransitionManager.dump(prefix + "\t" + RING_APPEAR_ANIMATION_PREFIX, writer);
        }
        if (mHotseatPredictionController != null) {
            mHotseatPredictionController.dump(prefix, writer);
        }
        PredictionRowView<?> predictionRowView =
                getAppsView().getFloatingHeaderView().findFixedRowByType(
                        PredictionRowView.class);
        predictionRowView.dump(prefix, writer);
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        switch (name) {
            case "TextClock", "android.widget.TextClock" -> {
                TextClock tc = new TextClock(context, attrs);
                tc.setClockEventDelegate(AsyncClockEventDelegate.INSTANCE.get(this));
                return tc;
            }
            case "AnalogClock", "android.widget.AnalogClock" -> {
                AnalogClock ac = new AnalogClock(context, attrs);
                ac.setClockEventDelegate(AsyncClockEventDelegate.INSTANCE.get(this));
                return ac;
            }
        }
        return super.onCreateView(parent, name, context, attrs);
    }

    @Override
    public boolean isRecentsViewVisible() {
        return getStateManager().getState().isRecentsViewVisible;
    }

    public boolean isCanShowAllAppsEducationView() {
        return mCanShowAllAppsEducationView;
    }

    public void setCanShowAllAppsEducationView(boolean canShowAllAppsEducationView) {
        mCanShowAllAppsEducationView = canShowAllAppsEducationView;
    }

    @Override
    public void returnToHomescreenAfterFreeformShortcut() {
        getStateManager().goToState(LauncherState.NORMAL);
    }

    @Override
    public LauncherActivityInterface getContainerInterface() {
        return LauncherActivityInterface.INSTANCE.get(this);
    }

    @Override
    public SplitSelectStateController getSplitSelectStateController() {
        return mSplitSelectStateController;
    }

    @Override
    public void goToRecentsState(RecentsState recentsState, boolean animated,
            Animator.AnimatorListener listener) {
        getStateManager().goToState(RecentsStateUtilsKt.toLauncherState(recentsState), animated,
                listener);
    }

    @Override
    protected boolean shouldDisableBackGesture() {
        BaseContainerInterface<?, ?> defaultDisplayContainerInterface =
            OverviewComponentObserver.INSTANCE.get(this)
                    .getContainerInterface(DEFAULT_DISPLAY);

        return super.shouldDisableBackGesture()
                && (defaultDisplayContainerInterface == null
                || !defaultDisplayContainerInterface.shouldHandleBackGesture());
    }

    @Override
    public boolean isOnBackInvokedCallbackEnabled() {
        return getApplicationInfo().isOnBackInvokedCallbackEnabled();
    }
}
