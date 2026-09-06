/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.os.Trace.TRACE_TAG_APP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
import static android.window.SplashScreen.SPLASH_SCREEN_STYLE_UNDEFINED;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.AbstractFloatingView.TYPE_ON_BOARD_POPUP;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY;
import static com.android.launcher3.Utilities.calculateTextHeight;
import static com.android.launcher3.Utilities.isRunningInTestHarness;
import static com.android.launcher3.desktop.DesktopStateProvider.getDesktopState;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_OPEN;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_DRAGGING;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_FULLSCREEN;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_SECONDARY_LAUNCHER_ON_CD;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_APP_AUTO;
import static com.android.launcher3.taskbar.TaskbarStashController.SHOULD_BUBBLES_FOLLOW_DEFAULT_VALUE;
import static com.android.launcher3.testing.shared.ResourceUtils.getBoolByName;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Executors.getTaskbarUiThread;
import static com.android.quickstep.RecentsFilterState.EMPTY_FILTER;
import static com.android.quickstep.util.AnimUtils.completeRunnableListCallback;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DUAL_SHADE_ENABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING;
import static com.android.wm.shell.Flags.enableBubbleBar;
import static com.android.wm.shell.Flags.enableBubbleBarOnPhones;
import static com.android.wm.shell.Flags.enableTinyTaskbar;
import static com.android.wm.shell.Flags.fixSwipeUpNotificationShadeWithBubbleBar;

import static java.lang.invoke.MethodHandles.Lookup.PROTECTED;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo.Config;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.IRemoteCallback;
import android.os.Process;
import android.os.Trace;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags.DesktopModeFlag;
import android.window.RemoteTransition;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.internal.jank.Cuj;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.BubbleTextView.RunningAppState;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.desktop.DesktopAppLaunchTransition;
import com.android.launcher3.desktop.DesktopAppLaunchTransition.AppLaunchType;
import com.android.launcher3.deviceprofile.TaskbarDeviceProfileFactory;
import com.android.launcher3.deviceprofile.TaskbarProfile;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.display.LauncherDisplayInfo;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.IModelWriter;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ResolvedTargetInfo;
import com.android.launcher3.model.data.TaskItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainer;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.AutohideSuspendFlag;
import com.android.launcher3.taskbar.TaskbarTranslationController.TransitionCallback;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsController;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController;
import com.android.launcher3.taskbar.bubbles.BubbleBarView;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.bubbles.BubbleCreator;
import com.android.launcher3.taskbar.bubbles.BubbleDismissController;
import com.android.launcher3.taskbar.bubbles.BubbleDragController;
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController;
import com.android.launcher3.taskbar.bubbles.DragToBubbleController;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.TaskbarHotseatDimensionsProvider;
import com.android.launcher3.taskbar.bubbles.stashing.DeviceProfileDimensionsProviderAdapter;
import com.android.launcher3.taskbar.bubbles.stashing.PersistentBubbleStashController;
import com.android.launcher3.taskbar.bubbles.stashing.TransientBubbleStashController;
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator;
import com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator;
import com.android.launcher3.taskbar.growth.NudgeController;
import com.android.launcher3.taskbar.handoff.HandoffSuggestion;
import com.android.launcher3.taskbar.handoff.TaskbarHandoffController;
import com.android.launcher3.taskbar.navbutton.NearestTouchFrame;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemClickHandler.ItemClickProxy;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ApplicationInfoWrapper;
import com.android.launcher3.util.AsyncView;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.FlagDebugUtils;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.NavHandle;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SlideInRemoteTransition;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.animation.ViewRootSync;
import com.android.systemui.rotation.impl.RotationPolicyWrapperImpl;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.rotation.RotationButtonController;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfig;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfigImpl;
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.desktopmode.DesktopTaskToFrontReason;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The {@link ActivityContext} with which we inflate Taskbar-related Views. This allows UI elements
 * that are used by both Launcher and Taskbar (such as Folder) to reference a generic
 * ActivityContext and BaseDragLayer instead of the Launcher activity and its DragLayer.
 */
public class TaskbarActivityContext extends BaseTaskbarContext {

    private static final String IME_DRAWS_IME_NAV_BAR_RES_NAME = "config_imeDrawsImeNavBar";

    private static final Uri URI_USER_SETUP_COMPLETE = Secure.getUriFor(Secure.USER_SETUP_COMPLETE);
    private static final Uri URI_NAV_BAR_KIDS_MODE = Secure.getUriFor(Secure.NAV_BAR_KIDS_MODE);

    private static final String TAG = "TaskbarActivityContext";

    public static final int TASKBAR_WINDOW_FULLSCREEN_DRAG = 1;
    public static final int TASKBAR_WINDOW_FULLSCREEN_BUBBLE_DRAG = 1 << 1;
    public static final int TASKBAR_WINDOW_FULLSCREEN_FOLDER = 1 << 2;
    public static final int TASKBAR_WINDOW_ICON_POPUP_MENU = 1 << 3;
    public static final int TASKBAR_WINDOW_ICON_TASKBAR_OVERFLOW = 1 << 4;
    public static final int TASKBAR_WINDOW_TASKBAR_PINNING = 1 << 5;
    public static final int TASKBAR_WINDOW_ICONS_TRANSITION = 1 << 6;

    private static final String WINDOW_TITLE = "Taskbar";

    public static final String SIMPLE_VIEW_SETTINGS_KEY = "matcha_enable";

    protected static final DesktopModeFlag ENABLE_TASKBAR_BEHIND_SHADE = new DesktopModeFlag(
            Flags::enableTaskbarBehindShade, false);

    private final @Nullable Context mNavigationBarPanelContext;

    private final TaskbarUiState mTaskbarUiState;

    private final TaskbarDragLayer mDragLayer;
    private final TaskbarControllers mControllers;

    private final WindowManager mWindowManager;
    private DeviceProfile mDeviceProfile;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private WindowManager.LayoutParams mLastUpdatedLayoutParams;

    // Set of use-cases that require taskbar to be fullscreen - non-zero value implies that the
    // taskbar window is currently fullscreen.
    private int mTaskbarFullscreenFlags = 0;
    private boolean mIsNotificationShadeExpanded = false;
    // The size we should return to when we call setTaskbarWindowFullscreen(false)
    private int mLastRequestedNonFullscreenSize;
    /**
     * When this is true, the taskbar window size is not updated. Requests to update the window
     * size are stored in {@link #mLastRequestedNonFullscreenSize} and will take effect after
     * bubbles no longer animate and {@link #setTaskbarWindowForAnimatingBubble()} is called.
     */
    private boolean mIsTaskbarSizeFrozenForAnimatingBubble;

    private NavigationMode mNavMode;
    private boolean mImeDrawsImeNavBar;

    /**
     * Static return value of {@link #isImeDocked}, used for testing only. A {@code null} value will
     * revert back to the actual return value of {@link #isImeDocked}.
     */
    @Nullable
    private Boolean mImeDockedOverrideForTest;

    private final boolean mIsSafeModeEnabled;
    private final boolean mIsUserSetupComplete;
    private final boolean mIsNavBarKidsMode;

    private boolean mIsDestroyed = false;

    // The bounds of the taskbar items relative to TaskbarDragLayer
    private final Rect mTransientTaskbarBounds = new Rect();

    private final TaskbarShortcutMenuAccessibilityDelegate mAccessibilityDelegate;

    private TaskbarProfile mTransientTaskbarProfile;

    private TaskbarProfile mPersistentTaskbarProfile;

    private final LauncherPrefs mLauncherPrefs;
    private final int mPrimaryDisplayId;
    private final SystemUiProxy mSysUiProxy;
    private final ActivityManagerWrapper mActivityManagerWrapper;
    private final DesktopState mDesktopState;
    private final Context mWindowContext;
    private final Set<InputConsumer> mInputConsumerCleanUpSet =
            Collections.newSetFromMap(new WeakHashMap<>());

    private final TaskbarFeatureEvaluator mTaskbarFeatureEvaluator;

    private final TaskbarSpecsEvaluator mTaskbarSpecsEvaluator;

    // Snapshot is used to temporarily draw taskbar behind the shade.
    private @Nullable View mTaskbarSnapshotView;
    private @Nullable TaskbarOverlayContext mTaskbarSnapshotOverlay;

    private final boolean mIsTransient;
    private final boolean mIsPinned;

    // Number of currently visible folders. Increased when a folder is about to be open, decreased
    // when a folder closes (after closes animation completes). Used to determine whether taskbar
    // needs to be fullscreen to accommodate folder bubbles.
    private int mFolderCount = 0;
    private int mVisiblePopupCount = 0;

    private BubbleFeatureConfig mBubbleFeatureConfig;

    public TaskbarActivityContext(int displayId, Context windowContext,
            @Nullable Context navigationBarPanelContext, DeviceProfile launcherDp,
            TaskbarNavButtonController buttonController,
            ScopedUnfoldTransitionProgressProvider unfoldTransitionProgressProvider,
            boolean isPrimaryDisplay, int primaryDisplayId, SystemUiProxy sysUiProxy,
            ActivityManagerWrapper activityManagerWrapper, DesktopState desktopState) {
        super(windowContext, displayId, isPrimaryDisplay);
        mTaskbarFeatureEvaluator = getActivityComponent().getTaskbarFeatureEvaluator();
        mIsTransient = mTaskbarFeatureEvaluator.isTransient();
        mIsPinned = mTaskbarFeatureEvaluator.isPinned();
        mTaskbarUiState = TaskbarUiStateMonitor.INSTANCE.get(this).getTaskbarUiState(displayId);
        resetResourceValueInTaskbarUiState();
        mTaskbarUiState.setPrimaryDisplay(isPrimaryDisplay);
        mTaskbarUiState.setIsTransient(mIsTransient);
        mNavigationBarPanelContext = navigationBarPanelContext;
        mSysUiProxy = sysUiProxy;
        mActivityManagerWrapper = activityManagerWrapper;
        mDesktopState = desktopState;
        mPrimaryDisplayId = primaryDisplayId;
        mWindowContext = windowContext;
        SettingsCache settingsCache = SettingsCache.INSTANCE.get(this);
        mIsUserSetupComplete = settingsCache.getValue(URI_USER_SETUP_COMPLETE);
        mIsNavBarKidsMode = settingsCache.getValue(URI_NAV_BAR_KIDS_MODE);
        mBubbleFeatureConfig =
                new BubbleFeatureConfigImpl(mWindowContext, getDesktopState(mWindowContext));

        applyDeviceProfile(launcherDp);
        mTaskbarSpecsEvaluator = new TaskbarSpecsEvaluator(
                this,
                mTaskbarFeatureEvaluator,
                mDeviceProfile.inv.numRows,
                mDeviceProfile.inv.numColumns);

        mImeDrawsImeNavBar = getBoolByName(IME_DRAWS_IME_NAV_BAR_RES_NAME, getResources(), false)
                && isPrimaryDisplay();
        mIsSafeModeEnabled = TraceHelper.allowIpcs("isSafeMode",
                () -> getPackageManager().isSafeMode());

        mWindowManager = windowContext.getSystemService(WindowManager.class);

        // Inflate views.
        boolean isTransientTaskbar = isTransientTaskbar();
        int taskbarLayout = isTransientTaskbar ? R.layout.transient_taskbar : R.layout.taskbar;
        mDragLayer = (TaskbarDragLayer) mLayoutInflater.inflate(taskbarLayout, null, false);
        TaskbarView taskbarView = mDragLayer.findViewById(R.id.taskbar_view);
        TaskbarScrimView taskbarScrimView = mDragLayer.findViewById(R.id.taskbar_scrim);
        NearestTouchFrame navButtonsView = mDragLayer.findViewById(R.id.navbuttons_view);
        navButtonsView.initialize(isPrimaryDisplay);
        StashedHandleView stashedHandleView = mDragLayer.findViewById(R.id.stashed_handle);
        NudgeView nudgeView = mDragLayer.findViewById(R.id.nudge_icon);
        BubbleBarView bubbleBarView = mDragLayer.findViewById(R.id.taskbar_bubbles);
        FrameLayout bubbleBarContainer = mDragLayer.findViewById(R.id.taskbar_bubbles_container);
        StashedHandleView bubbleHandleView = mDragLayer.findViewById(R.id.stashed_bubble_handle);

        mAccessibilityDelegate = new TaskbarShortcutMenuAccessibilityDelegate(this);

        // If Bubble bar is present, TaskbarControllers depends on it so build it first.
        Optional<BubbleControllers> bubbleControllersOptional = Optional.empty();
        BubbleBarController.onTaskbarRecreated();
        mTaskbarUiState.setHasBubbles(false);
        final boolean deviceBubbleBarEnabled = enableBubbleBarOnPhones()
                || (!mDeviceProfile.getDeviceProperties().isPhone() && !mDeviceProfile.isVerticalBarLayout());
        if (BubbleBarController.isBubbleBarEnabled() && deviceBubbleBarEnabled
                && bubbleBarView != null && isPrimaryDisplay) {
            Optional<BubbleStashedHandleViewController> bubbleHandleController = Optional.empty();
            Optional<BubbleBarSwipeController> bubbleBarSwipeController = Optional.empty();
            if (isTransientTaskbar) {
                bubbleHandleController = Optional.of(
                        new BubbleStashedHandleViewController(
                                this, bubbleHandleView, mTaskbarUiState));
                bubbleBarSwipeController = Optional.of(new BubbleBarSwipeController(this));
            }
            TaskbarHotseatDimensionsProvider dimensionsProvider =
                    new DeviceProfileDimensionsProviderAdapter(this);
            BubbleStashController bubbleStashController = isTransientTaskbar
                    ? new TransientBubbleStashController(dimensionsProvider, this, mTaskbarUiState)
                    : new PersistentBubbleStashController(dimensionsProvider, mTaskbarUiState);
            bubbleStashController.setBubbleBarVerticalCenterForHome(
                    launcherDp.getBubbleBarVerticalCenterForHome());
            bubbleControllersOptional = Optional.of(new BubbleControllers(
                    new BubbleBarController(this, bubbleBarView),
                    new BubbleBarViewController(
                            this, mTaskbarUiState, bubbleBarView, bubbleBarContainer),
                    bubbleStashController,
                    bubbleHandleController,
                    new BubbleDragController(this, mWindowContext, mDragLayer, mTaskbarUiState),
                    new BubbleDismissController(this, mDragLayer),
                    bubbleBarSwipeController,
                    new DragToBubbleController(mWindowContext, bubbleBarContainer, this),
                    new BubbleCreator(this)
            ));
        }

        // Construct controllers.
        RotationButtonController rotationButtonController = new RotationButtonController(
                new RotationPolicyWrapperImpl(windowContext),
                this,
                windowContext.getColor(R.color.floating_rotation_button_light_color),
                windowContext.getColor(R.color.floating_rotation_button_dark_color),
                R.drawable.ic_sysbar_rotate_button_ccw_start_0,
                R.drawable.ic_sysbar_rotate_button_ccw_start_90,
                R.drawable.ic_sysbar_rotate_button_cw_start_0,
                R.drawable.ic_sysbar_rotate_button_cw_start_90,
                () -> getDisplay().getRotation());
        rotationButtonController.setBgExecutor(Executors.UI_HELPER_EXECUTOR);

        mControllers = new TaskbarControllers(this,
                new TaskbarDragController(this),
                buttonController,
                new NavbarButtonsViewController(this, mNavigationBarPanelContext, navButtonsView,
                        getMainThreadHandler(), mTaskbarUiState),
                rotationButtonController,
                new TaskbarDragLayerController(this, mDragLayer),
                new TaskbarViewController(this, taskbarView, mTaskbarUiState),
                new TaskbarScrimViewController(this, taskbarScrimView),
                new TaskbarUnfoldAnimationController(this, unfoldTransitionProgressProvider,
                        mWindowManager,
                        new RotationChangeProvider(
                                windowContext.getSystemService(DisplayManager.class), this,
                                UI_HELPER_EXECUTOR.getHandler(), getMainThreadHandler())),
                new TaskbarKeyguardController(this),
                new StashedHandleViewController(this, stashedHandleView),
                new TaskbarStashController(this, mTaskbarUiState),
                new TaskbarAutohideSuspendController(this),
                new TaskbarPopupController(this),
                new TaskbarForceVisibleImmersiveController(this),
                new TaskbarOverlayController(this, launcherDp),
                new TaskbarAllAppsController(),
                new TaskbarInsetsController(this),
                new VoiceInteractionWindowController(this),
                new TaskbarTranslationController(this),
                new TaskbarSpringOnStashController(this),
                new TaskbarRecentAppsController(this, RecentsModel.INSTANCE.get(this),
                        ThemeManager.INSTANCE.get(this),
                        LauncherComponentProvider.get(this).getDesktopModeCompatPolicy()),
                TaskbarEduTooltipController.newInstance(this),
                new KeyboardQuickSwitchController(),
                new TaskbarPinningController(this),
                bubbleControllersOptional,
                new TaskbarDesktopModeController(this,
                        DesktopVisibilityController.INSTANCE.get(this)),
                new CueBarController(this),
                new NudgeController(this),
                new NudgeViewController(this, nudgeView),
                new TaskbarHandoffController(this),
                new TaskbarViewDragDropController(this, taskbarView));

        mLauncherPrefs = LauncherPrefs.get(this);
        onViewCreated();
    }

    public final int getPrimaryDisplayId() {
        return mPrimaryDisplayId;
    }

    public TaskbarUiState getTaskbarUiState() {
        return mTaskbarUiState;
    }

    @Override
    public boolean isTransientTaskbar() {
        return isTransienTaskbarForDeviceProfile(mDeviceProfile);
    }

    private boolean isTransienTaskbarForDeviceProfile(DeviceProfile deviceProfile) {
        return mIsTransient && isPrimaryDisplay() && !isDeviceProfileForPhoneMode(deviceProfile);
    }

    @Override
    public boolean isPinnedTaskbar() {
        return mIsPinned;
    }

    @Override
    public NavigationMode getNavigationMode() {
        return isPrimaryDisplay() ? DisplayController.getNavigationMode(this)
                : NavigationMode.THREE_BUTTONS;
    }

    @Override
    public boolean isInDesktopMode() {
        return mControllers != null
                && mControllers.taskbarDesktopModeController.isInDesktopMode(getDisplayId());
    }

    @Override
    public boolean isTaskbarShowingDesktopTasks() {
        return mControllers != null
                && mControllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar(
                        getDisplayId());
    }

    @Override
    public boolean showDesktopTaskbarForFreeformDisplay() {
        return DisplayController.getInfo(this).getShowDesktopTaskbarForFreeformDisplay();
    }

    @Override
    public Point getScreenSize() {
        return DisplayController.getInfo(this).currentSize;
    }

    @Override
    public int getDisplayHeight() {
        return DisplayController.getInfo(this).currentSize.y;
    }

    public boolean isDesktopFormFactor() {
        return mWindowContext.getResources().getBoolean(
                R.bool.desktop_form_factor);
    }

    @MainThread
    public void addInputConsumerToCleanUp(InputConsumer inputConsumer) {
        mInputConsumerCleanUpSet.add(inputConsumer);
    }

    /**
     * Copy the original DeviceProfile, match the number of hotseat icons and qsb width and update
     * the icon size
     */
    private void applyDeviceProfile(DeviceProfile originDeviceProfile) {
        Consumer<DeviceProfile> overrideProvider =
                deviceProfile -> TaskbarDeviceProfileFactory.INSTANCE
                        .createDeviceProfile(
                                deviceProfile, this,
                                isTransienTaskbarForDeviceProfile(deviceProfile)
                        );
        mDeviceProfile = originDeviceProfile.toBuilder()
                .withDimensionsOverride(overrideProvider).build();
        mTaskbarUiState.setDeviceProfile(mDeviceProfile);
        resetResourceValueInTaskbarUiState();

        if (isTransientTaskbar()) {
            mTransientTaskbarProfile = mDeviceProfile.getTaskbarProfile();
            mPersistentTaskbarProfile = TaskbarProfile.Factory.createTaskbarProfile(
                    getResources(),
                    false,
                    mDeviceProfile.getDeviceProperties().getTaskbarConfiguration()
                            .isTaskbarPresent(),
                    mDeviceProfile.getDisplayOptionSpec()
            );
        } else {
            mPersistentTaskbarProfile = mDeviceProfile.getTaskbarProfile();
            mTransientTaskbarProfile = TaskbarProfile.Factory.createTaskbarProfile(
                    getResources(),
                    true,
                    mDeviceProfile.getDeviceProperties().getTaskbarConfiguration()
                            .isTaskbarPresent(),
                    mDeviceProfile.getDisplayOptionSpec()
            );
        }
        mNavMode = getNavigationMode();
        mTaskbarUiState.setNavigationMode(mNavMode);

        if (mControllers != null) {
            mControllers.taskbarEduTooltipController.updateShouldShowEduOnAppLaunch();
        }
    }

    /** Called when the visibility of the bubble bar changed. */
    public void bubbleBarVisibilityChanged(boolean isVisible) {
        mControllers.uiController.adjustHotseatForBubbleBar(isVisible);
        mControllers.taskbarViewController.adjustTaskbarForBubbleBar();
    }

    /** Whether app bubbles are supported on this device. */
    public boolean areAppBubblesSupported() {
        return mBubbleFeatureConfig.areAppBubblesSupported();
    }

    /** Returns {@code true} if the bubble scrim is enabled. */
    public boolean isBubbleScrimEnabled() {
        return mBubbleFeatureConfig.isScrimEnabled(getDisplayId());
    }

    /**
     * Sets an override for {@link #mBubbleFeatureConfig} for testing.
     */
    @VisibleForTesting
    public void overrideBubbleFeatureConfigForTests(BubbleFeatureConfig featureConfig) {
        mBubbleFeatureConfig = featureConfig;
    }

    /**
     * Init of taskbar activity context.
     * @param duration If duration is greater than 0, it will be used to create an animation
 *                     for the taskbar create/recreate process.
     */
    public void init(@NonNull TaskbarSharedState sharedState, boolean userUnlocked, int duration) {
        mImeDrawsImeNavBar = getBoolByName(IME_DRAWS_IME_NAV_BAR_RES_NAME, getResources(), false)
                && isPrimaryDisplay();
        mLastRequestedNonFullscreenSize = getDefaultTaskbarWindowSize();
        mWindowLayoutParams = createAllWindowParams();
        mLastUpdatedLayoutParams = new WindowManager.LayoutParams();


        AnimatorSet recreateAnim = null;
        if (duration > 0) {
            recreateAnim = onRecreateAnimation(duration);
        }

        // Initialize controllers after all are constructed.
        mControllers.init(sharedState, recreateAnim, mTaskbarUiState, userUnlocked);
        // This may not be necessary and can be reverted once we move towards recreating all
        // controllers without re-creating the window
        mControllers.rotationButtonController.onNavigationModeChanged(mNavMode.resValue);
        updateSysuiStateFlags(sharedState.sysuiStateFlags, true /* fromInit */);
        disableNavBarElements(sharedState.disableNavBarDisplayId, sharedState.disableNavBarState1,
                sharedState.disableNavBarState2, false /* animate */);
        onSystemBarAttributesChanged(sharedState.systemBarAttrsDisplayId,
                sharedState.systemBarAttrsBehavior);
        onNavButtonsDarkIntensityChanged(sharedState.navButtonsDarkIntensity);
        onNavigationBarLumaSamplingEnabled(sharedState.mLumaSamplingDisplayId,
                sharedState.mIsLumaSamplingEnabled);
        setWallpaperVisible(sharedState.wallpaperVisible);
        onTransitionModeUpdated(sharedState.barMode, true /* checkBarModes */);

        // This entire class gets re-created, which resets the value of mIsDestroyed. We re-use the
        // class for small-screen, so we explicitly have to mark this class as non-destroyed
        mIsDestroyed = false;
        notifyUpdateLayoutParams();

        if (recreateAnim != null) {
            recreateAnim.start();
        }
    }

    /**
     * Create AnimatorSet for taskbar create/recreate animation. Further used in init
     */
    public AnimatorSet onRecreateAnimation(int duration) {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(duration);
        return animatorSet;
    }

    /**
     * Called when we want destroy current taskbar with animation as part of recreate process.
     */
    public AnimatorSet onDestroyAnimation(int duration) {
        mIsDestroyed = true;
        AnimatorSet animatorSet = new AnimatorSet();
        mControllers.taskbarViewController.onDestroyAnimation(animatorSet);
        mControllers.taskbarDragLayerController.onDestroyAnimation(animatorSet);
        animatorSet.setInterpolator(LINEAR);
        animatorSet.setDuration(duration);
        return animatorSet;
    }

    /**
     * @return {@code true} if the device profile isn't a large screen profile and we are using a
     * single window for taskbar and navbar.
     */
    public boolean isPhoneMode() {
        return isDeviceProfileForPhoneMode(mDeviceProfile);
    }

    private boolean isDeviceProfileForPhoneMode(DeviceProfile deviceProfile) {
        return deviceProfile.getDeviceProperties().isPhone()
                && !deviceProfile.getDeviceProperties().getTaskbarConfiguration()
                .isTaskbarPresent();
    }

    public boolean isTaskbarInMinimalState() {
        return mControllers.taskbarViewController.isTaskbarInMinimalState();
    }

    /**
     * @return {@code true} if {@link #isPhoneMode()} is true and we're using 3 button-nav
     */
    public boolean isPhoneButtonNavMode() {
        return isPhoneMode() && isThreeButtonNav();
    }

    /**
     * @return {@code true} if {@link #isPhoneMode()} is true and we're using gesture nav
     */
    public boolean isPhoneGestureNavMode() {
        return isPhoneMode() && !isThreeButtonNav();
    }

    /** Returns whether Taskbar draws its own background, vs being translucent for apps to draw. */
    public boolean drawsTaskbarBackground() {
        return !isPhoneMode();
    }

    /** Returns {@code true} iff a tiny version of taskbar is shown on phone. */
    public boolean isTinyTaskbar() {
        return enableTinyTaskbar()
                && mDeviceProfile.getDeviceProperties().isPhone()
                && mDeviceProfile.getDeviceProperties().getTaskbarConfiguration()
                .isTaskbarPresent();
    }

    public boolean isBubbleBarOnPhone() {
        return enableBubbleBarOnPhones() && enableBubbleBar() && mDeviceProfile.getDeviceProperties().isPhone();
    }

    /**
     * Returns {@code true} iff bubble bar is enabled (but not necessarily visible /
     * containing bubbles).
     */
    @Override
    public boolean isBubbleBarEnabled() {
        return getBubbleControllers() != null && BubbleBarController.isBubbleBarEnabled();
    }

    private boolean isBubbleBarAnimating() {
        return mControllers
                .bubbleControllers
                .map(controllers -> controllers.bubbleBarViewController.isAnimatingNewBubble())
                .orElse(false);
    }

    /**
     * Checks whether the IME is visible and docked (i.e. there are large enough IME insets).
     *
     * <p>This is {@code false} if the IME is not visible, floating, or small, for example (but not
     * limited to) hiding the software keyboard keys when a hardware keyboard is connected.
     *
     * <p>Note, IME insets visibility is updated slightly faster than
     * {@link com.android.systemui.shared.system.QuickStepContract#SYSUI_STATE_IME_VISIBLE}.
     */
    public boolean isImeDocked() {
        if (mImeDockedOverrideForTest != null) {
            return mImeDockedOverrideForTest;
        }
        final var windowInsets = mWindowManager.getCurrentWindowMetrics().getWindowInsets();
        // IME insets implicitly include navigation bar and display cutout bottom insets.
        final var systemBarDisplayCutoutInsets = windowInsets
                .getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        // An approximation for the space below the IME InputView.
        final int imeNavBarHeight = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.input_method_navigation_bar_height);
        // The space below the InputView plus the smallest InputView considered docked.
        final int threshold = Math.max(systemBarDisplayCutoutInsets.bottom, imeNavBarHeight)
                + getResources().getDimensionPixelSize(R.dimen.ime_docked_threshold);
        final var imeInsets = windowInsets.getInsets(WindowInsets.Type.ime());
        return imeInsets.bottom >= threshold;
    }

    /**
     * Sets an override return value for {@link #isImeDocked}, to be used in testing.
     *
     * @param docked whether the IME should be considered docked or not. {@code null} reverts to the
     *               actual return value of {@link #isImeDocked}.
     */
    @VisibleForTesting
    public void setImeDockedOverrideForTest(@Nullable Boolean docked) {
        mImeDockedOverrideForTest = docked;
    }

    /**
     * Show Taskbar upon receiving broadcast
     */
    public void showTaskbarFromBroadcast() {
        // If user is in middle of taskbar education handle go to next step of education
        if (mControllers.taskbarEduTooltipController.isBeforeTooltipFeaturesStep()) {
            mControllers.taskbarEduTooltipController.hide();
            mControllers.taskbarEduTooltipController.maybeShowFeaturesEdu();
        }
        if (!isInDesktopMode()) {
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(false);
        } else {
            mControllers.taskbarStashController.updateAndAnimatePinnedTaskbar(false);
        }
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        Trace.instantForTrack(TRACE_TAG_APP, "TaskbarActivityContext#DeviceProfileChanged",
                getDeviceProfile().toSmallString());
    }

    @NonNull
    public LauncherPrefs getLauncherPrefs() {
        return mLauncherPrefs;
    }

    /**
     * Returns the View bounds of transient taskbar.
     */
    public Rect getTransientTaskbarBounds() {
        return mTransientTaskbarBounds;
    }

    protected float getCurrentTaskbarWidth() {
        return mControllers.taskbarViewController.getCurrentVisualTaskbarWidth();
    }

    @Override
    public StatsLogManager getStatsLogManager() {
        // Used to mock, can't mock a default interface method directly
        return super.getStatsLogManager();
    }

    /**
     * Creates LayoutParams for adding a view directly to WindowManager as a new window.
     *
     * @param type  The window type to pass to the created WindowManager.LayoutParams.
     * @param title The window title to pass to the created WindowManager.LayoutParams.
     */
    public WindowManager.LayoutParams createDefaultWindowLayoutParams(int type, String title) {
        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (!isTransientTaskbar() && !isTaskbarShowingDesktopTasks()) {
            // Allow apps to receive swipe events from non-transient taskbar (e.g. 3 button nav).
            // Desktop taskbar should not allow other apps to receive touch events so that
            // drag-and-drop gestures on taskbar icons are not interrupted.
            windowFlags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
        }
        boolean watchOutside = isTransientTaskbar() || isThreeButtonNav();
        if (watchOutside && !isRunningInTestHarness()) {
            windowFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        }
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams(
                MATCH_PARENT,
                mLastRequestedNonFullscreenSize,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        windowLayoutParams.setTitle(title);
        windowLayoutParams.packageName = getPackageName();
        windowLayoutParams.gravity = Gravity.BOTTOM;
        windowLayoutParams.setFitInsetsTypes(0);
        windowLayoutParams.receiveInsetsIgnoringZOrder = true;
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        windowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        windowLayoutParams.privateFlags =
                WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
        windowLayoutParams.accessibilityTitle = getString(
                isPhoneMode() ? R.string.taskbar_phone_a11y_title : R.string.taskbar_a11y_title);

        return windowLayoutParams;
    }

    /**
     * Creates {@link WindowManager.LayoutParams} for Taskbar, and also sets LP.paramsForRotation
     * for taskbar
     */
    private WindowManager.LayoutParams createAllWindowParams() {
        final int windowType = isPrimaryDisplay() ? TYPE_NAVIGATION_BAR : TYPE_NAVIGATION_BAR_PANEL;
        WindowManager.LayoutParams windowLayoutParams =
                createDefaultWindowLayoutParams(windowType, TaskbarActivityContext.WINDOW_TITLE);

        windowLayoutParams.paramsForRotation = new WindowManager.LayoutParams[4];
        for (int rot = Surface.ROTATION_0; rot <= Surface.ROTATION_270; rot++) {
            WindowManager.LayoutParams lp =
                    createDefaultWindowLayoutParams(windowType,
                            TaskbarActivityContext.WINDOW_TITLE);
            if (isPhoneButtonNavMode()) {
                populatePhoneButtonNavModeWindowLayoutParams(rot, lp);
            }
            windowLayoutParams.paramsForRotation[rot] = lp;
        }

        // Override with current layout params
        WindowManager.LayoutParams currentParams =
                windowLayoutParams.paramsForRotation[getDisplay().getRotation()];
        windowLayoutParams.width = currentParams.width;
        windowLayoutParams.height = currentParams.height;
        windowLayoutParams.gravity = currentParams.gravity;

        return windowLayoutParams;
    }

    /**
     * Update {@link WindowManager.LayoutParams} with values specific to phone and 3 button
     * navigation users
     */
    private void populatePhoneButtonNavModeWindowLayoutParams(int rot,
            WindowManager.LayoutParams lp) {
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.BOTTOM;

        // Override with per-rotation specific values
        switch (rot) {
            case Surface.ROTATION_0, Surface.ROTATION_180 -> {
                lp.height = mLastRequestedNonFullscreenSize;
            }
            case Surface.ROTATION_90 -> {
                lp.width = mLastRequestedNonFullscreenSize;
                lp.gravity = Gravity.END;
            }
            case Surface.ROTATION_270 -> {
                lp.width = mLastRequestedNonFullscreenSize;
                lp.gravity = Gravity.START;
            }
        }
    }

    public void onConfigurationChanged(@Config int configChanges) {
        mControllers.onConfigurationChanged(configChanges);
        if (!mIsUserSetupComplete) {
            setTaskbarWindowSize(getSetupWindowSize());
        }
        resetResourceValueInTaskbarUiState();
    }


    /** Should be called after init, config changed or DeviceProfile change. */
    private void resetResourceValueInTaskbarUiState() {
        final Resources res = getResources();
        mTaskbarUiState.setTaskbarUnstashAreaSizePx(
                res.getDimensionPixelSize(R.dimen.taskbar_unstash_input_area));
        mTaskbarUiState.setTaskbarActionCornerPaddingPx(
                res.getDimensionPixelSize(R.dimen.transient_taskbar_action_corner_padding));
        if (mDeviceProfile != null) {
            mTaskbarUiState.setTaskbarNavThreshold(
                    TaskbarThresholdUtils.getFromNavThreshold(res, mDeviceProfile));
        }
        mTaskbarUiState.setTaskbarSlowVelocityYThreshold(
                res.getDimensionPixelSize(R.dimen.taskbar_slow_velocity_y_threshold));
        mTaskbarUiState.setTaskbarStashedScreenEdgeHoverDeadzoneHeightPx(
                res.getDimensionPixelSize(
                        R.dimen.taskbar_stashed_screen_edge_hover_deadzone_height));
        mTaskbarUiState.setTaskbarStashedBelowHoverDeadzoneHeightPx(
                res.getDimensionPixelSize(R.dimen.taskbar_stashed_below_hover_deadzone_height));
    }

    public boolean isThreeButtonNav() {
        return mNavMode == NavigationMode.THREE_BUTTONS;
    }

    /** Returns whether taskbar should start align. */
    public boolean shouldStartAlignTaskbar() {
        return isThreeButtonNav() && mDeviceProfile.getTaskbarProfile().isStartAlignTaskbar();
    }

    public boolean isGestureNav() {
        return mNavMode == NavigationMode.NO_BUTTON;
    }

    public boolean imeDrawsImeNavBar() {
        return mImeDrawsImeNavBar;
    }

    public int getCornerRadius() {
        return isPhoneMode() ? 0 : getResources().getDimensionPixelSize(
                R.dimen.persistent_taskbar_corner_radius);
    }

    public WindowManager.LayoutParams getWindowLayoutParams() {
        return mWindowLayoutParams;
    }

    @Override
    public TaskbarDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public Rect getFolderBoundingBox() {
        return mControllers.taskbarDragLayerController.getFolderBoundingBox();
    }

    @Override
    public TaskbarDragController getDragController() {
        return mControllers.taskbarDragController;
    }

    @Override
    public IModelWriter getModelWriter() {
        return mControllers.taskbarViewController.getModelWriter();
    }

    public NavbarButtonsViewController getNavBarButtonsViewController() {
        return mControllers.navbarButtonsViewController;
    }

    @Nullable
    public BubbleControllers getBubbleControllers() {
        return mControllers.bubbleControllers.orElse(null);
    }

    @NonNull
    public NavHandle getNavHandle() {
        return mControllers.stashedHandleViewController;
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return this::onTaskbarIconClicked;
    }

    /**
     * Change from hotseat/predicted hotseat to taskbar container.
     */
    @Override
    public void applyOverwritesToLogItem(LauncherAtom.ItemInfo.Builder itemInfoBuilder) {
        if (!itemInfoBuilder.hasContainerInfo()) {
            return;
        }
        LauncherAtom.ContainerInfo oldContainer = itemInfoBuilder.getContainerInfo();

        LauncherAtom.TaskBarContainer.Builder taskbarBuilder =
                LauncherAtom.TaskBarContainer.newBuilder();
        if (mControllers.uiController.isInOverviewUi()) {
            taskbarBuilder.setTaskSwitcherContainer(
                    LauncherAtom.TaskSwitcherContainer.newBuilder());
        }

        if (oldContainer.hasPredictedHotseatContainer()) {
            LauncherAtom.PredictedHotseatContainer predictedHotseat =
                    oldContainer.getPredictedHotseatContainer();

            if (predictedHotseat.hasIndex()) {
                taskbarBuilder.setIndex(predictedHotseat.getIndex());
            }
            if (predictedHotseat.hasCardinality()) {
                taskbarBuilder.setCardinality(predictedHotseat.getCardinality());
            }

            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setTaskBarContainer(taskbarBuilder));
        } else if (oldContainer.hasHotseat()) {
            LauncherAtom.HotseatContainer hotseat = oldContainer.getHotseat();

            if (hotseat.hasIndex()) {
                taskbarBuilder.setIndex(hotseat.getIndex());
            }

            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setTaskBarContainer(taskbarBuilder));
        } else if (oldContainer.hasFolder() && oldContainer.getFolder().hasHotseat()) {
            LauncherAtom.FolderContainer.Builder folderBuilder = oldContainer.getFolder()
                    .toBuilder();
            LauncherAtom.HotseatContainer hotseat = folderBuilder.getHotseat();

            if (hotseat.hasIndex()) {
                taskbarBuilder.setIndex(hotseat.getIndex());
            }

            folderBuilder.setTaskbar(taskbarBuilder);
            folderBuilder.clearHotseat();
            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setFolder(folderBuilder));
        } else if (oldContainer.hasAllAppsContainer()) {
            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setAllAppsContainer(oldContainer.getAllAppsContainer().toBuilder()
                            .setTaskbarContainer(taskbarBuilder)));
        } else if (oldContainer.hasPredictionContainer()) {
            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setPredictionContainer(oldContainer.getPredictionContainer().toBuilder()
                            .setTaskbarContainer(taskbarBuilder)));
        }
    }

    @NonNull
    @Override
    public LauncherBindableItemsContainer getContent() {
        return mControllers.taskbarViewController.getContent();
    }

    @Override
    public ActivityAllAppsContainerView<?> getAppsView() {
        return mControllers.taskbarAllAppsController.getAppsView();
    }

    @Override
    public View.AccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    @Override
    public void onDragStart() {
        setTaskbarWindowFullscreen(true, TASKBAR_WINDOW_FULLSCREEN_DRAG);
    }

    @Override
    public void onDragEnd() {
        // Reverts Taskbar window to its original size
        Runnable resetTaskbarFullscreen = () -> {
            setTaskbarWindowFullscreen(false, TASKBAR_WINDOW_FULLSCREEN_DRAG);
        };
        mControllers.bubbleControllers.ifPresentOrElse(
                bc -> bc.dragToBubbleController.runAfterDropTargetsHidden(
                        resetTaskbarFullscreen), resetTaskbarFullscreen);

        setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_DRAGGING,
                mControllers.taskbarDragController.isSystemDragInProgress());
    }

    @Override
    public void onPopupVisibilityChanged(boolean isVisible) {
        boolean needsUpdate = false;
        if (isVisible) {
            collapseSysUiPanels();
            mVisiblePopupCount++;
            needsUpdate = mVisiblePopupCount == 1;
        } else {
            mVisiblePopupCount--;
            needsUpdate = mVisiblePopupCount == 0;
        }
        if (needsUpdate) {
            setTaskbarWindowFocusable(isVisible /* focusable */, false /* imeFocusable */);
            setTaskbarWindowFullscreen(isVisible, TASKBAR_WINDOW_ICON_POPUP_MENU);
        }
    }

    @Override
    public void onSplitScreenMenuButtonClicked() {
        PopupContainer<?> popup = PopupContainer.getOpen(this);
        if (popup != null) {
            popup.addOnCloseCallback(() -> {
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            });
        }
    }

    @Override
    public ActivityOptionsWrapper makeDefaultActivityOptions(int splashScreenStyle) {
        RunnableList callbacks = new RunnableList();
        ActivityOptions options = ActivityOptions.makeCustomAnimation(this, 0, 0);
        options.setSplashScreenStyle(splashScreenStyle);
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        IRemoteCallback endCallback = completeRunnableListCallback(
                callbacks, this, getTaskbarUiThread());
        options.setOnAnimationAbortListener(endCallback);
        options.setOnAnimationFinishedListener(endCallback);

        return new ActivityOptionsWrapper(options, callbacks);
    }

    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        return makeDefaultActivityOptions(SPLASH_SCREEN_STYLE_UNDEFINED);
    }

    private ActivityOptionsWrapper getSingleActivityLaunchOptions(@Nullable ItemInfo item) {
        return getSingleActivityLaunchOptions(item,
                shouldLaunchInDesktop(item) ? WINDOWING_MODE_FREEFORM
                        : WINDOWING_MODE_FULLSCREEN);
    }

    private ActivityOptionsWrapper getFullscreenActivityLaunchOptions(@Nullable ItemInfo item) {
        return getSingleActivityLaunchOptions(item, WINDOWING_MODE_FULLSCREEN);
    }

    /**
     * Returns activity options for launching a single activity from the taskbar on the display
     * associated with the taskbar.
     */
    private ActivityOptionsWrapper getSingleActivityLaunchOptions(@Nullable ItemInfo item,
            int windowingMode) {
        final ActivityOptionsWrapper opts = getActivityLaunchOptions(null, item);
        opts.options.setLaunchDisplayId(getDisplayId());
        opts.options.setLaunchWindowingMode(windowingMode);
        return opts;
    }

    private ActivityOptionsWrapper getActivityLaunchDesktopOptions() {
        ActivityOptions options = ActivityOptions.makeRemoteTransition(
                createDesktopAppLaunchRemoteTransition(
                        AppLaunchType.LAUNCH, Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON));
        return new ActivityOptionsWrapper(options, new RunnableList());
    }

    /**
     * Sets a new data-source for this taskbar instance
     */
    public void setUIController(@NonNull TaskbarUIController uiController) {
        mControllers.setUiController(uiController);
        if (BubbleBarController.isBubbleBarEnabled() && mControllers.bubbleControllers.isEmpty()) {
            // if the bubble bar was visible in a previous configuration of taskbar and is being
            // recreated now without bubbles, clean up any bubble bar adjustments from hotseat
            bubbleBarVisibilityChanged(/* isVisible= */ false);
        }
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mControllers.taskbarStashController.setSetupUIVisible(isVisible);
    }

    public void setWallpaperVisible(boolean isVisible) {
        mControllers.navbarButtonsViewController.setWallpaperVisible(isVisible);
    }

    public void checkNavBarModes() {
        mControllers.navbarButtonsViewController.checkNavBarModes();
    }

    public void finishBarAnimations() {
        mControllers.navbarButtonsViewController.finishBarAnimations();
    }

    public void touchAutoDim(boolean reset) {
        mControllers.navbarButtonsViewController.touchAutoDim(reset);
    }

    public void transitionTo(@BarTransitions.TransitionMode int barMode,
            boolean animate) {
        mControllers.navbarButtonsViewController.transitionTo(barMode, animate);
    }

    public void appTransitionPending(boolean pending) {
        mControllers.stashedHandleViewController.setIsAppTransitionPending(pending);
    }

    /**
     * Called when this instance of taskbar is no longer needed
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        onViewDestroyed();
        removeTaskbarSnapshot();
        mIsDestroyed = true;
        setUIController(TaskbarUIController.DEFAULT);
        mControllers.onDestroy();
        MAIN_EXECUTOR.execute(() -> {
            mInputConsumerCleanUpSet.forEach(InputConsumer::onConsumerAboutToBeSwitched);
        });
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    public void updateSysuiStateFlags(@SystemUiStateFlags long systemUiStateFlags,
            boolean fromInit) {
        mControllers.navbarButtonsViewController.updateStateForSysuiFlags(systemUiStateFlags,
                fromInit);
        onNotificationShadeExpandChanged(systemUiStateFlags, fromInit || isPhoneMode());
        mControllers.taskbarViewController.setRecentsButtonDisabled(
                mControllers.navbarButtonsViewController.isRecentsDisabled()
                        || isNavBarKidsModeActive());
        mControllers.stashedHandleViewController.setIsHomeButtonDisabled(
                mControllers.navbarButtonsViewController.isHomeDisabled());
        mControllers.cueBarController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.stashedHandleViewController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.taskbarKeyguardController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.taskbarStashController.updateStateForSysuiFlags(
                systemUiStateFlags, fromInit || !isUserSetupComplete());
        mControllers.taskbarScrimViewController.updateStateForSysuiFlags(systemUiStateFlags,
                fromInit);
        mControllers.taskbarEduTooltipController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.navButtonController.updateSysuiFlags(systemUiStateFlags);
        mControllers.taskbarForceVisibleImmersiveController.updateSysuiFlags(systemUiStateFlags);
        mControllers.voiceInteractionWindowController.setIsVoiceInteractionWindowVisible(
                (systemUiStateFlags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0, fromInit);
        mControllers.uiController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.bubbleControllers.ifPresent(controllers -> {
            controllers.bubbleBarController.updateStateForSysuiFlags(systemUiStateFlags);
            controllers.bubbleStashedHandleViewController.ifPresent(controller ->
                    controller.setIsHomeButtonDisabled(
                            mControllers.navbarButtonsViewController.isHomeDisabled()));
        });
    }

    /** Whether the notification shade is currently expanded */
    public boolean isNotificationShadeExpanded() {
        return mIsNotificationShadeExpanded;
    }

    /**
     * Returns whether the taskbar should remain touchable when the notification shade is expanded.
     */
    public boolean isTaskbarTouchableBehindNotificationShade() {
        return !fixSwipeUpNotificationShadeWithBubbleBar() || isDesktopFormFactor();
    }

    /**
     * Collapses the Quick Settings and Notification panels.
     */
    public void collapseSysUiPanels() {
        if (Flags.enableCollapseSysuiPanelsOnTaskbarClick() && isNotificationShadeExpanded()) {
            StatusBarManager statusBarManager = getSystemService(StatusBarManager.class);
            if (statusBarManager != null) {
                statusBarManager.collapsePanels();
            }
        }
    }

    /**
     * Hides the taskbar icons and background when the notification shade is expanded.
     */
    private void onNotificationShadeExpandChanged(long systemUiStateFlags,
            boolean skipAnim) {
        boolean isExpanded = (systemUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE) != 0;
        boolean isDualShadeEnabled = (systemUiStateFlags & SYSUI_STATE_DUAL_SHADE_ENABLED) != 0;
        boolean isExpandedUpdated = isExpanded != mIsNotificationShadeExpanded;
        mIsNotificationShadeExpanded = isExpanded;
        // Close all floating views within the Taskbar window to make sure nothing is shown over
        // the notification shade.
        if (isExpanded && isExpandedUpdated) {
            AbstractFloatingView.closeAllOpenViewsExcept(this, TYPE_TASKBAR_OVERLAY_PROXY);
        }

        // Avoid hiding the taskbar when shade is shown with dual shade enabled on desktop form
        // factor.
        if (isDualShadeEnabled && isDesktopFormFactor()) {
            return;
        }

        float alpha = isExpanded ? 0 : 1;
        AnimatorSet anim = new AnimatorSet();
        anim.play(mControllers.taskbarViewController.getTaskbarIconAlpha().get(
                TaskbarViewController.ALPHA_INDEX_NOTIFICATION_EXPANDED).animateToValue(alpha));
        anim.play(mControllers.taskbarDragLayerController.getNotificationShadeBgTaskbar()
                .animateToValue(alpha));

        if (isExpandedUpdated) {
            mControllers.bubbleControllers.ifPresent(controllers -> {
                BubbleBarViewController bubbleBarViewController =
                        controllers.bubbleBarViewController;
                if (!isTaskbarTouchableBehindNotificationShade()
                        && bubbleBarViewController.isExpanded()) {
                    // If bubbles are expanded when the shade expansion changes, then the touchable
                    // insets need to be updated.
                    mControllers.taskbarInsetsController
                            .onTaskbarOrBubblebarWindowHeightOrInsetsChanged();
                }
                anim.play(bubbleBarViewController.getBubbleBarAlpha().get(0).animateToValue(alpha));
                MultiPropertyFactory<View>.MultiProperty handleAlpha =
                        controllers.bubbleStashController.getHandleViewAlpha();
                if (handleAlpha != null) {
                    anim.play(handleAlpha.animateToValue(alpha));
                }
            });
        }
        anim.start();
        if (skipAnim) {
            anim.end();
        }

        updateTaskbarSnapshot(anim, isExpanded);
    }

    private void updateTaskbarSnapshot(AnimatorSet anim, boolean isExpanded) {
        if (!ENABLE_TASKBAR_BEHIND_SHADE.isTrue()
                || isPhoneMode()) {
            return;
        }
        if (mTaskbarSnapshotView == null) {
            mTaskbarSnapshotView = new View(this);
        }
        if (isExpanded) {
            if (!mTaskbarSnapshotView.isAttachedToWindow()
                    && mDragLayer.isAttachedToWindow()
                    && mDragLayer.isLaidOut()
                    && mDragLayer.isVisibleToUser()
                    && mTaskbarSnapshotView.getParent() == null) {
                NearestTouchFrame navButtonsView = mDragLayer.findViewById(R.id.navbuttons_view);
                int oldNavButtonsVisibility = navButtonsView.getVisibility();
                navButtonsView.setVisibility(View.INVISIBLE);

                Drawable drawable = new FastBitmapDrawable(BitmapRenderer.createHardwareBitmap(
                        mDragLayer.getWidth(),
                        mDragLayer.getHeight(),
                        mDragLayer::draw));

                navButtonsView.setVisibility(oldNavButtonsVisibility);
                mTaskbarSnapshotView.setBackground(drawable);
                mTaskbarSnapshotView.setAlpha(0f);

                mTaskbarSnapshotView.addOnAttachStateChangeListener(
                        new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(@NonNull View v) {
                                mTaskbarSnapshotView.removeOnAttachStateChangeListener(this);
                                anim.end();
                                mTaskbarSnapshotView.setAlpha(1f);
                                if (!Utilities.isRunningInTestHarness()) {
                                    ViewRootSync.synchronizeNextDraw(mDragLayer,
                                            mTaskbarSnapshotView,
                                            () -> {});
                                }
                            }

                            @Override
                            public void onViewDetachedFromWindow(@NonNull View v) {}
                        });
                BaseDragLayer.LayoutParams layoutParams = new BaseDragLayer.LayoutParams(
                        mDragLayer.getWidth(), mDragLayer.getHeight());
                layoutParams.gravity = mWindowLayoutParams.gravity;
                layoutParams.ignoreInsets = true;
                mTaskbarSnapshotOverlay = mControllers.taskbarOverlayController.requestWindow();
                mTaskbarSnapshotOverlay.getDragLayer().addView(mTaskbarSnapshotView, layoutParams);
            }
        } else {
            if (mTaskbarSnapshotView.isAttachedToWindow()) {
                mTaskbarSnapshotView.setAlpha(0f);
                anim.end();
                if (Utilities.isRunningInTestHarness()) {
                    removeTaskbarSnapshot();
                } else {
                    ViewRootSync.synchronizeNextDraw(mDragLayer, mTaskbarSnapshotView,
                            this::removeTaskbarSnapshot);
                }
            } else {
                removeTaskbarSnapshot();
            }
        }
    }

    private void removeTaskbarSnapshot() {
        if (mTaskbarSnapshotOverlay != null) {
            mTaskbarSnapshotOverlay.getDragLayer().removeView(mTaskbarSnapshotView);
        }
        mTaskbarSnapshotView = null;
        mTaskbarSnapshotOverlay = null;
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        mControllers.rotationButtonController.onRotationProposal(rotation, isValid);
    }

    public void disableNavBarElements(int displayId, int state1, int state2, boolean animate) {
        if (displayId != getDisplayId()) {
            return;
        }
        mControllers.rotationButtonController.onDisable2FlagChanged(state2);
    }

    public void onSystemBarAttributesChanged(int displayId, int behavior) {
        mControllers.rotationButtonController.onBehaviorChanged(displayId, behavior);
    }

    public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
        mControllers.navbarButtonsViewController.onTransitionModeUpdated(barMode, checkBarModes);
    }

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        mControllers.navbarButtonsViewController.getTaskbarNavButtonDarkIntensity().updateValue(
                darkIntensity);
    }

    /**
     * Called when assistant long press enabled state changes.
     */
    public void onLongPressHomeEnabledChanged() {
        mControllers.navbarButtonsViewController.onLongPressHomeEnabledChanged();
    }

    public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
        mControllers.stashedHandleViewController.onNavigationBarLumaSamplingEnabled(displayId,
                enable);
    }

    /**
     * Called to update a {@link AutohideSuspendFlag} with a new value.
     */
    public void setAutohideSuspendFlag(@AutohideSuspendFlag int flag, boolean newValue) {
        mControllers.taskbarAutohideSuspendController.updateFlag(flag, newValue);
    }

    /**
     * Updates and applies {@link TaskbarStashController#FLAG_IN_SECONDARY_LAUNCHER_ON_CD} to
     * {@link TaskbarStashController} state flags.
     */
    void updateStashControllerLauncherStateFlag(boolean enabled) {
        if (isPrimaryDisplay()) {
            return;
        }

        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_SECONDARY_LAUNCHER_ON_CD, enabled);
        if (!enabled) {
            // When moving away from launcher, don't stash the taskbar right away, let it auto stash
            // through timeout.
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, /* enabled= */ false);
        }

        // Un-stash taskbar if required.
        if (enabled && isTaskbarStashed()) {
            stashController.updateAndAnimatePinnedTaskbar(/* stash= */ false);
        } else {
            stashController.applyState();
            stashController.updateTaskbarTimeout(/* isAutohideSuspended= */ false);
        }
    }

    /**
     * Updates the TaskbarContainer to MATCH_PARENT vs original Taskbar size.
     */
    public void setTaskbarWindowFullscreen(boolean fullscreen, int flags) {
        boolean wasFullscreen = isTaskbarWindowFullscreen();
        if (fullscreen) {
            mTaskbarFullscreenFlags |= flags;
        } else {
            mTaskbarFullscreenFlags &= ~flags;
        }
        boolean newIsFullscreen = mTaskbarFullscreenFlags != 0;
        if (wasFullscreen == newIsFullscreen) {
            return;
        }
        setTaskbarWindowFullscreenInternal(newIsFullscreen);
    }

    private void setTaskbarWindowFullscreenInternal(boolean fullscreen) {
        setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_FULLSCREEN, fullscreen);
        setTaskbarWindowSize(fullscreen ? MATCH_PARENT : mLastRequestedNonFullscreenSize);
    }

    /**
     * Updates the taskbar window size according to whether bubbles are animating.
     *
     * <p>This method should be called when bubbles start animating and again after the animation is
     * complete.
     */
    public void setTaskbarWindowForAnimatingBubble() {
        if (isBubbleBarAnimating()) {
            // the default window size accounts for the bubble flyout
            setTaskbarWindowSize(getDefaultTaskbarWindowSize());
            mIsTaskbarSizeFrozenForAnimatingBubble = true;
        } else {
            mIsTaskbarSizeFrozenForAnimatingBubble = false;
            setTaskbarWindowSize(
                    mLastRequestedNonFullscreenSize != 0
                            ? mLastRequestedNonFullscreenSize : getDefaultTaskbarWindowSize());
        }
    }

    public boolean isTaskbarWindowFullscreen() {
        return mTaskbarFullscreenFlags != 0;
    }

    /**
     * Updates the TaskbarContainer size (pass {@link #getDefaultTaskbarWindowSize()} to reset).
     */
    public void setTaskbarWindowSize(int size) {
        // In landscape phone button nav mode, we should set the task bar width instead of height
        // because this is the only case in which the nav bar is not on the display bottom.
        boolean landscapePhoneButtonNav = isPhoneButtonNavMode() && mDeviceProfile.getDeviceProperties().isLandscape();
        if ((landscapePhoneButtonNav ? mWindowLayoutParams.width : mWindowLayoutParams.height)
                == size || mIsDestroyed) {
            return;
        }
        if (size == MATCH_PARENT) {
            size = mDeviceProfile.getDeviceProperties().getHeightPx();
        } else {
            mLastRequestedNonFullscreenSize = size;
            if (isTaskbarWindowFullscreen() || mIsTaskbarSizeFrozenForAnimatingBubble) {
                // We either still need to be fullscreen or a bubble is still animating, so defer
                // any change to our height until setTaskbarWindowFullscreen(false) is called or
                // setTaskbarWindowForAnimatingBubble() is called after the bubble animation
                // completed. For example, this could happen when dragging from the gesture region,
                // as the drag will cancel the gesture and reset launcher's state, which in turn
                // normally would reset the taskbar window height as well.
                return;
            }
        }
        if (landscapePhoneButtonNav) {
            mWindowLayoutParams.width = size;
            for (int rot = Surface.ROTATION_0; rot <= Surface.ROTATION_270; rot++) {
                mWindowLayoutParams.paramsForRotation[rot].width = size;
            }
        } else {
            mWindowLayoutParams.height = size;
            for (int rot = Surface.ROTATION_0; rot <= Surface.ROTATION_270; rot++) {
                mWindowLayoutParams.paramsForRotation[rot].height = size;
            }
        }
        mControllers.runAfterInit(
                mControllers.taskbarInsetsController
                        ::onTaskbarOrBubblebarWindowHeightOrInsetsChanged);
        notifyUpdateLayoutParams();
    }

    /**
     * Returns the default size (in most cases height, but in 3-button phone mode, width) of the
     * window, including the static corner radii above taskbar.
     */
    public int getDefaultTaskbarWindowSize() {
        Resources resources = getResources();

        if (isPhoneMode()) {
            return isThreeButtonNav() ?
                    resources.getDimensionPixelSize(R.dimen.taskbar_phone_size) :
                    resources.getDimensionPixelSize(R.dimen.taskbar_stashed_size);
        }

        if (!isUserSetupComplete()) {
            return getSetupWindowSize();
        }

        int bubbleBarTop = mControllers.bubbleControllers.map(bubbleControllers ->
                bubbleControllers.bubbleBarViewController.getBubbleBarWithFlyoutMaximumHeight()
        ).orElse(0);
        int taskbarWindowSize;
        boolean shouldTreatAsTransient =
                isTransientTaskbar() || (
                        mTaskbarFeatureEvaluator.getSupportsTransitionToTransientTaskbar()
                                && isPrimaryDisplay());

        int extraHeightForTaskbarTooltips = resources.getDimensionPixelSize(
                R.dimen.arrow_toast_arrow_height)
                + (resources.getDimensionPixelSize(R.dimen.taskbar_tooltip_vertical_padding) * 2)
                + calculateTextHeight(
                resources.getDimensionPixelSize(R.dimen.arrow_toast_text_size));

        // Return transient taskbar window height when pinning feature is enabled, so taskbar view
        // does not get cut off during pinning animation. We should only do this on primary display.
        if (shouldTreatAsTransient) {
            TaskbarProfile transientTaskbarProfile = TaskbarProfile.Factory.createTaskbarProfile(
                    getResources(),
                    true,
                    mDeviceProfile.getDeviceProperties().getTaskbarConfiguration()
                            .isTaskbarPresent(),
                    mDeviceProfile.getDisplayOptionSpec()
            );

            taskbarWindowSize = transientTaskbarProfile.getHeight()
                    + (2 * transientTaskbarProfile.getBottomMargin())
                    + Math.max(extraHeightForTaskbarTooltips, resources.getDimensionPixelSize(
                    R.dimen.transient_taskbar_shadow_blur));
            return Math.max(taskbarWindowSize, bubbleBarTop);
        }


        taskbarWindowSize = mDeviceProfile.getTaskbarProfile().getHeight()
                + getCornerRadius()
                + extraHeightForTaskbarTooltips;
        return Math.max(taskbarWindowSize, bubbleBarTop);
    }

    public int getSetupWindowSize() {
        return getResources().getDimensionPixelSize(R.dimen.taskbar_suw_frame);
    }

    public TaskbarProfile getTransientTaskbarProfile() {
        return mTransientTaskbarProfile;
    }

    public TaskbarProfile getPersistentTaskbarProfile() {
        return mPersistentTaskbarProfile;
    }

    /**
     * Sets whether the taskbar window should be focusable and IME focusable. This won't be IME
     * focusable unless it is also focusable.
     *
     * @param focusable    whether it should be focusable.
     * @param imeFocusable whether it should be IME focusable.
     *
     * @see WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE
     * @see WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM
     */
    public void setTaskbarWindowFocusable(boolean focusable, boolean imeFocusable) {
        if (isPhoneMode()) {
            return;
        }
        if (focusable) {
            mWindowLayoutParams.flags &= ~FLAG_NOT_FOCUSABLE;
            if (imeFocusable) {
                mWindowLayoutParams.flags &= ~FLAG_ALT_FOCUSABLE_IM;
            } else {
                mWindowLayoutParams.flags |= FLAG_ALT_FOCUSABLE_IM;
            }
        } else {
            mWindowLayoutParams.flags |= FLAG_NOT_FOCUSABLE;
            mWindowLayoutParams.flags &= ~FLAG_ALT_FOCUSABLE_IM;
        }
        notifyUpdateLayoutParams();
    }

    /**
     * Applies forcibly show flag to taskbar window iff transient taskbar is unstashed.
     */
    public void applyForciblyShownFlagWhileTransientTaskbarUnstashed(boolean shouldForceShow) {
        if (!isTransientTaskbar() || isPhoneMode()) {
            return;
        }
        if (shouldForceShow) {
            mWindowLayoutParams.forciblyShownTypes |= WindowInsets.Type.navigationBars();
        } else {
            mWindowLayoutParams.forciblyShownTypes &= ~WindowInsets.Type.navigationBars();
        }
        notifyUpdateLayoutParams();
    }

    /**
     * Applies forcibly show flag to taskbar window in persistent taskbar for bubbles.
     *
     * <p>This is called by Bubbles to ensure that the taskbar window is visible when a new or an
     * updated bubble is animating, and when the bubble bar is expanded. When the bubble bar is
     * collapsed and when bubbles no longer animate, this method is called again to remove the
     * forcibly shown flag so that the taskbar window can hide if it needs to.
     *
     * <p>This method is a no-op for transient taskbar, where this flag is updated through
     * {@link #applyForciblyShownFlagWhileTransientTaskbarUnstashed(boolean)}
     */
    public void applyForciblyShownFlagForBubblesInPersistentTaskbar(boolean shouldForceShow) {
        if (isTransientTaskbar()) {
            return;
        }
        if (shouldForceShow) {
            mWindowLayoutParams.forciblyShownTypes |= WindowInsets.Type.navigationBars();
        } else {
            mWindowLayoutParams.forciblyShownTypes &= ~WindowInsets.Type.navigationBars();
        }
        notifyUpdateLayoutParams();
    }

    /**
     * Sets whether the taskbar window should be focusable, as well as IME focusable. If we're now
     * focusable, also move nav buttons to a separate window above IME.
     *
     * @param focusable whether it should be focusable.
     *
     * @see WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE
     */
    public void setTaskbarWindowFocusableForIme(boolean focusable) {
        if (focusable) {
            mControllers.navbarButtonsViewController.moveNavButtonsToNewWindow();
        } else {
            mControllers.navbarButtonsViewController.moveNavButtonsBackToTaskbarWindow();
        }
        setTaskbarWindowFocusable(focusable, true /* imeFocusable */);
    }

    /** Adds the given view to WindowManager with the provided LayoutParams (creates new window). */
    public void addWindowView(View view, WindowManager.LayoutParams windowLayoutParams) {
        if (!view.isAttachedToWindow()) {
            mWindowManager.addView(view, windowLayoutParams);
        }
    }

    /** Removes the given view from WindowManager. See {@link #addWindowView}. */
    public void removeWindowView(View view) {
        if (view.isAttachedToWindow()) {
            mWindowManager.removeViewImmediate(view);
        }
    }

    @Override
    public void startSplitSelection(SplitSelectSource splitSelectSource) {
        mControllers.uiController.startSplitSelection(splitSelectSource);
    }

    // If in overview, return to desktop if any desks are available, then schedules the provided
    // runnable.
    private void runAfterReturningToDesktopIfInOverview(
            RecentsViewInteractor recents,
            Runnable runnableToRun,
            Executor executor) {
        if (recents == null || !isTaskbarShowingDesktopTasks()
                || !mControllers.uiController.isInOverviewUi()
                || recents.isSplitSelectionActive()) {
            executor.execute(runnableToRun);
            return;
        }

        recents.returnToDesktop(runnableToRun, executor);
    }

    protected void onTaskbarIconClicked(View view) {
        TaskbarUIController taskbarUIController = mControllers.uiController;
        RecentsViewInteractor recents = taskbarUIController.getRecentsViewInteractor();
        boolean shouldCloseAllOpenViews = true;
        Object tag = view.getTag();

        mControllers.keyboardQuickSwitchController.closeQuickSwitchView(false);
        collapseSysUiPanels();

        if (tag instanceof SingleTask singleTask) {
            RemoteTransition remoteTransition =
                    (isTaskbarShowingDesktopTasks() && canUnminimizeDesktopTask(
                            singleTask.getTask().key.id))
                            ? createDesktopAppLaunchRemoteTransition(AppLaunchType.UNMINIMIZE,
                            Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON)
                            : null;
            Pair<Executor, Runnable> inferredTask = inferGroupTaskLaunch(
                    singleTask, remoteTransition,
                    isTaskbarShowingDesktopTasks(), DesktopTaskToFrontReason.TASKBAR_TAP, view,
                    DesktopModeTransitionSource.TASKBAR);

            runAfterReturningToDesktopIfInOverview(
                    recents, inferredTask.second, inferredTask.first);

            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
        } else if (tag instanceof SplitTask st) {
            // Tapping an icon for a split task on Taskbar
            final RemoteTransition slideInTransition = new RemoteTransition(
                    new SlideInRemoteTransition(
                            !Utilities.isRtl(getResources()),
                            getDeviceProfile().getOverviewProfile().getPageSpacing(),
                            QuickStepContract.getWindowCornerRadius(this),
                            AnimationUtils.loadInterpolator(
                                    this, android.R.interpolator.fast_out_extra_slow_in)),
                    "SlideInTransition");
            // SplitTask is only relevant outside of desktop.
            handleGroupTaskLaunch(st, slideInTransition, isTaskbarShowingDesktopTasks(),
                    DesktopTaskToFrontReason.UNKNOWN, view, DesktopModeTransitionSource.TASKBAR);
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
        } else if (tag instanceof FolderInfo) {
            // Tapping an expandable folder icon on Taskbar
            shouldCloseAllOpenViews = false;
            expandFolder((FolderIcon) view);
        } else if (tag instanceof AppPairInfo api) {
            // Tapping an app pair icon on Taskbar
            if (recents != null && recents.isSplitSelectionActive()) {
                Toast.makeText(this, "Unable to split with an app pair. Select another app.",
                        Toast.LENGTH_SHORT).show();
            } else {
                // Else launch the selected app pair
                launchFromTaskbar(recents, view, api.getContents());
                mControllers.uiController.onTaskbarIconLaunched(api);
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            }
        } else if (tag instanceof TaskItemInfo info) {
            if (recents != null && recents.isSplitSelectionActive()
                    && (getControllers().taskbarRecentAppsController.getRunningTaskWithId(
                                info.getTaskId())) != null) {
                taskbarUIController.triggerSecondAppForSplit(info, info.intent, view, EMPTY_FILTER);
            } else {
                RemoteTransition remoteTransition = canUnminimizeDesktopTask(info.getTaskId())
                        ? createDesktopAppLaunchRemoteTransition(
                                AppLaunchType.UNMINIMIZE, Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON)
                        : null;

                Runnable launchTask = () ->
                        SystemUiProxy.INSTANCE.get(this).showDesktopApp(
                                info.getTaskId(), remoteTransition,
                                DesktopTaskToFrontReason.TASKBAR_TAP);
                runAfterReturningToDesktopIfInOverview(recents, launchTask, UI_HELPER_EXECUTOR);
            }
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(
                    /* stash= */ true);
        } else if (tag instanceof WorkspaceItemInfo) {
            // Tapping a launchable icon on Taskbar
            WorkspaceItemInfo info = (WorkspaceItemInfo) tag;
            if (!info.isDisabled() || !ItemClickHandler.handleDisabledItemClicked(info, this)) {
                if (recents != null && recents.isSplitSelectionActive()) {
                    // If we are selecting a second app for split, launch the split tasks
                    taskbarUIController.triggerSecondAppForSplit(info, info.intent, view,
                            EMPTY_FILTER);
                } else {
                    // Else launch the selected task
                    Intent intent = new Intent(info.getIntent())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        if (mIsSafeModeEnabled
                                && !new ApplicationInfoWrapper(this, intent).isSystem()) {
                            Toast.makeText(this, R.string.safemode_shortcut_error,
                                    Toast.LENGTH_SHORT).show();
                        } else if (info.isPromise()) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarPromiseIcon");
                            intent = ApiWrapper.INSTANCE.get(this).getAppMarketActivityIntent(
                                    info.getTargetPackage(), Process.myUserHandle());
                            startActivity(intent, getSingleActivityLaunchOptions(info).toBundle());
                        } else if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarDeepShortcut");
                            String id = info.getDeepShortcutId();
                            String packageName = intent.getPackage();
                            getSystemService(LauncherApps.class)
                                    .startShortcut(packageName, id, null,
                                            getSingleActivityLaunchOptions(info).toBundle(),
                                            info.user);
                        } else {
                            launchFromTaskbar(recents, view, Collections.singletonList(info));
                        }

                    } catch (NullPointerException
                             | ActivityNotFoundException
                             | SecurityException e) {
                        Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT)
                                .show();
                        Log.e(TAG, "Unable to launch. tag=" + info + " intent=" + intent, e);
                        return;
                    }
                }

                // If the app was launched from a folder, stash the taskbar after it closes
                Folder f = Folder.getOpen(this);
                if (f != null && f.getInfo().id == info.container) {
                    f.addOnFolderStateChangedListener(new Folder.OnFolderStateChangedListener() {
                        @Override
                        public void onFolderStateChanged(int newState) {
                            if (newState == Folder.STATE_CLOSED) {
                                f.removeOnFolderStateChangedListener(this);
                                mControllers.taskbarStashController
                                        .updateAndAnimateTransientTaskbar(true);
                            }
                        }
                    });
                }
                mControllers.uiController.onTaskbarIconLaunched(info);
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            }
        } else if (tag instanceof AppInfo) {
            // Tapping an item in AllApps
            AppInfo info = (AppInfo) tag;
            if (recents != null && recents.isSplitSelectionActive()) {
                // If we are selecting a second app for split, launch the split tasks
                taskbarUIController.triggerSecondAppForSplit(info, info.intent, view,
                        EMPTY_FILTER);
            } else {
                launchFromTaskbar(recents, view, Collections.singletonList(info));
            }
            mControllers.uiController.onTaskbarIconLaunched(info);
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
        } else if (tag instanceof ItemClickProxy) {
            ((ItemClickProxy) tag).onItemClicked(view);
        } else if (tag instanceof HandoffSuggestion handoffSuggestion) {
            if (android.companion.Flags.taskContinuity()) {
                mControllers.taskbarHandoffController.launch(handoffSuggestion);
            } else {
                Log.w(
                    TAG,
                    "Click on HandoffSuggestion ignored because Handoff feature flag is disabled.");
            }
        } else {
            Log.e(TAG, "Unknown type clicked: " + tag);
        }

        mControllers.taskbarPopupController.maybeCloseMultiInstanceMenu();
        if (shouldCloseAllOpenViews) {
            AbstractFloatingView.closeAllOpenViews(this);
            taskbarUIController.closeOpenLauncherViews();
        }
    }

    /**
     * Launches the given GroupTask with the following behavior:
     * - If the GroupTask is a DesktopTask, launch the tasks in that Desktop.
     * - If {@code onDesktop}, bring the given GroupTask to the front.
     * - If the GroupTask is a single task, launch it via startActivityFromRecents.
     * - Otherwise, we assume the GroupTask is a Split pair and launch them together.
     * <p>
     * Given start and/or finish callbacks, they will be run before an after the app launch
     * respectively in cases where we can't use the remote transition, otherwise we will assume that
     * these callbacks are included in the remote transition.
     */
    public void handleGroupTaskLaunch(
            GroupTask task,
            @Nullable RemoteTransition remoteTransition,
            boolean onDesktop,
            DesktopTaskToFrontReason toFrontReason,
            View startingView,
            DesktopModeTransitionSource transitionSource) {
        Pair<Executor, Runnable> inferredTask = inferGroupTaskLaunch(
                task, remoteTransition, onDesktop, toFrontReason, startingView, transitionSource);
        inferredTask.first.execute(inferredTask.second);
    }

    private Pair<Executor, Runnable> inferGroupTaskLaunch(
            GroupTask task,
            @Nullable RemoteTransition remoteTransition,
            boolean onDesktop,
            DesktopTaskToFrontReason toFrontReason,
            View startingView,
            DesktopModeTransitionSource transitionSource) {

        if (task instanceof DesktopTask) {
            return Pair.create(UI_HELPER_EXECUTOR,
                    () -> SystemUiProxy.INSTANCE.get(this).showDesktopApps(getDisplayId(),
                            remoteTransition, /* taskIdReorderToFront */ null, transitionSource));
        }

        if (task instanceof SingleTask singleTask) {
            TaskbarUIController taskbarUIController = mControllers.uiController;
            RecentsViewInteractor recents = taskbarUIController.getRecentsViewInteractor();

            if (recents != null && recents.isSplitSelectionActive()) {
                return Pair.create(getTaskbarUiThread(),
                        () -> taskbarUIController.moveRunningTaskToSplitSelection(
                                singleTask.getTask(), null, startingView));
            }

            if (onDesktop) {
                boolean useRemoteTransition = canUnminimizeDesktopTask(singleTask.getTask().key.id);
                return Pair.create(UI_HELPER_EXECUTOR,
                        () -> SystemUiProxy.INSTANCE.get(this).showDesktopApp(
                                singleTask.getTask().key.id,
                                useRemoteTransition ? remoteTransition : null,
                                toFrontReason));
            }

            return Pair.create(UI_HELPER_EXECUTOR,
                    () -> {
                        ActivityOptions activityOptions =
                                makeDefaultActivityOptions(SPLASH_SCREEN_STYLE_UNDEFINED).options;
                        activityOptions.setRemoteTransition(remoteTransition);

                        mActivityManagerWrapper.startActivityFromRecents(
                                singleTask.getTask().key, activityOptions);
                    });
        }

        assert task instanceof SplitTask;
        return Pair.create(getTaskbarUiThread(),
                () -> mControllers.uiController.launchSplitTasks(
                        (SplitTask) task, remoteTransition));
    }

    /** Returns whether the given task is minimized and can be unminimized. */
    public boolean canUnminimizeDesktopTask(int taskId) {
        BubbleTextView.RunningAppState runningAppState =
                mControllers.taskbarRecentAppsController.getRunningAppState(taskId);
        Log.d(TAG, "Task id=" + taskId + ", Running app state=" + runningAppState);
        return runningAppState == RunningAppState.MINIMIZED;
    }

    private RemoteTransition createDesktopAppLaunchRemoteTransition(
            AppLaunchType appLaunchType, @Cuj.CujType int cujType) {
        return new RemoteTransition(
                new DesktopAppLaunchTransition(
                        this,
                        DisplayController.INSTANCE.get(this),
                        appLaunchType,
                        cujType,
                        getTaskbarUiThread()
                ),
                "TaskbarDesktopAppLaunch");
    }

    /**
     * Runs when the user taps a Taskbar icon in TaskbarActivityContext (Overview or inside an app),
     * and calls the appropriate method to animate and launch.
     */
    private void launchFromTaskbar(
            @Nullable RecentsViewInteractor recents, @Nullable View launchingIconView,
            List<? extends ItemInfo> itemInfos) {
        if (mControllers.uiController.isInOverviewUi()) {
            launchFromOverviewTaskbar(recents, launchingIconView, itemInfos);
        } else {
            launchFromInAppTaskbar(recents, launchingIconView, itemInfos);
        }
    }

    /**
     * Runs when the user taps a Taskbar icon while inside an app.
     */
    private void launchFromInAppTaskbar(@Nullable RecentsViewInteractor recents,
            @Nullable View launchingIconView, List<? extends ItemInfo> itemInfos) {
        boolean launchedFromExternalDisplay =
                DesktopExperienceFlags.ENABLE_TASKBAR_CONNECTED_DISPLAYS.isTrue()
                        && !isPrimaryDisplay();
        if (recents == null && !launchedFromExternalDisplay) {
            return;
        }

        boolean tappedAppPair = itemInfos.size() == 2;

        if (tappedAppPair) {
            // If the icon is an app pair, the logic gets a bit complicated because we play
            // different animations depending on which app (or app pair) is currently running on
            // screen, so delegate logic to appPairsController.
            if (recents != null && launchingIconView != null) {
                // TODO: b/441341469 - Split screen should be handled correctly on CD.
                recents.handleAppPairLaunchInApp((AppPairIcon) launchingIconView, itemInfos);
            }
        } else if (showDesktopTaskbarForFreeformDisplay()) {
            launchSingleAppFromFreeFormDisplayTaskbar(itemInfos.get(0));
        } else {
            // Tapped a single app, nothing complicated here.
            startItemInfoActivity(itemInfos.get(0), null /*foundTask*/);
        }
    }

    /**
     * Handles launching {@link SingleTask} on freeform displays - projected / extended / desktop
     * first.
     */
    private void launchSingleAppFromFreeFormDisplayTaskbar(ItemInfo info) {
        if (!info.user.equals(Process.myUserHandle())) {
            startItemInfoActivity(info, /* taskInRecents= */ null);
            return;
        }

        Predicate<GroupTask> predicate = task ->
                task instanceof SingleTask && task.containsPackage(info.getTargetPackage());

        // In case of projected mode, apps should move between connected <--> primary display. In
        // case of 2 connected displays, apps should not move between them.
        if (mDesktopState.isProjectedMode()) {
            predicate = predicate.and(task -> task.getDisplayId() != getPrimaryDisplayId());
        }

        // Look for recent apps so that they can be brought to top.
        RecentsModel.INSTANCE.get(this).getTasks(predicate, groupTasks -> {
            if (!groupTasks.isEmpty() && !groupTasks.getFirst().isEmpty()) {
                ActivityOptionsWrapper opts = getActivityLaunchOptions(null, info);

                // Use slide-in transition, no slide-in happens if app already on top.
                opts.options.setRemoteTransition(new RemoteTransition(new SlideInRemoteTransition(
                        Utilities.isRtl(getResources()),
                        getDeviceProfile().getOverviewProfile().getPageSpacing(),
                        QuickStepContract.getWindowCornerRadius(this),
                        AnimationUtils.loadInterpolator(
                                this, android.R.interpolator.fast_out_extra_slow_in)),
                        "SlideInTransition"));

                Task task = ((SingleTask) groupTasks.getFirst()).getTask();
                if (mActivityManagerWrapper
                        .startActivityFromRecents(task.key, opts.options)) {
                    return;
                }
            }

            // Fallback to existing implementation if app doesn't launch through recents API.
            startItemInfoActivity(info, /* taskInRecents= */ null);
        });
    }

    /**
     * Run when the user taps a Taskbar icon while in Overview. If the tapped app is currently
     * visible to the user in Overview, or is part of a visible split pair, we expand the TaskView
     * as if the user tapped on it (preserving the split pair). Otherwise, launch it normally
     * (potentially breaking a split pair).
     */
    private void launchFromOverviewTaskbar(@Nullable RecentsViewInteractor recents,
            @Nullable View launchingIconView, List<? extends ItemInfo> itemInfos) {
        if (recents == null) {
            return;
        }

        boolean isLaunchingAppPair = itemInfos.size() == 2;
        // Convert the list of ItemInfo instances to a list of ComponentKeys
        List<ResolvedTargetInfo> resolvedTargetInfo =
                itemInfos.stream().map(ItemInfo::getResolvedTargetInfo).toList();
        recents.findLastActiveTasksAndRunCallback(
                EMPTY_FILTER,
                resolvedTargetInfo,
                isLaunchingAppPair,
                foundTasks -> {
                    @Nullable Task foundTask = foundTasks[0];
                    if (foundTask != null) {
                        AsyncView<TaskView> asyncTaskView =
                                recents.getTaskViewByTaskId(foundTask.key.id);
                        asyncTaskView.postCallback((foundTaskView) -> {
                            if (foundTaskView.isVisibleToUser()
                                    && !(foundTaskView instanceof DesktopTaskView)) {
                                TestLogging.recordEvent(
                                        TestProtocol.SEQUENCE_MAIN, "start: taskbarAppIcon");
                                foundTaskView.launchWithAnimation();
                            }
                        });

                    }

                    if (isLaunchingAppPair) {
                        // Finish recents animation if it's running before launching to ensure
                        // we get both leashes for the animation
                        mControllers.uiController.setSkipNextRecentsAnimEnd();
                        recents.switchToScreenshot(() ->
                                recents.finishRecentsAnimation(true /*toHome*/,
                                        false /*shouldPip*/,
                                        () -> recents.launchAppPair((AppPairIcon) launchingIconView,
                                                        -1 /*cuj*/)));
                    } else {
                        Runnable launchTask =
                                () -> startItemInfoActivity(itemInfos.get(0), foundTask);
                        runAfterReturningToDesktopIfInOverview(
                                recents, launchTask, getTaskbarUiThread());
                    }
                }
        );
    }

    /**
     * Starts an activity with the information provided by the "info" param. However, if
     * taskInRecents is present, it will prioritize re-launching an existing instance via
     * {@link ActivityManagerWrapper#startActivityFromRecents(int, ActivityOptions)}
     */
    @UiThread
    private void startItemInfoActivity(ItemInfo info, @Nullable Task taskInRecents) {
        Intent intent = new Intent(info.getIntent())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: taskbarAppIcon");
            final ActivityOptionsWrapper opts = getActivityLaunchOptions(null, info);
            opts.options.setLaunchDisplayId(getDisplayId());
            if (!info.user.equals(Process.myUserHandle())) {
                // TODO b/376819104: support Desktop launch animations for apps in managed profiles
                getSystemService(LauncherApps.class).startMainActivity(
                        intent.getComponent(), info.user, intent.getSourceBounds(),
                        opts.toBundle());
                return;
            }
            // TODO(b/216683257): Use startActivityForResult for search results that require it.
            if (taskInRecents != null) {
                // Re launch instance from recents
                if (mActivityManagerWrapper
                        .startActivityFromRecents(taskInRecents.key, opts.options)) {
                    mControllers.uiController.getRecentsViewInteractor()
                            .addSideTaskLaunchCallback(opts.onEndCallback);
                    return;
                }
            }
            if (shouldLaunchInDesktop(info)) {
                launchDesktopApp(intent, info);
            } else {
                startActivity(intent, getFullscreenActivityLaunchOptions(info).toBundle());
            }
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT)
                    .show();
            Log.e(TAG, "Unable to launch. tag=" + info + " intent=" + intent, e);
        }
    }

    private boolean shouldLaunchInDesktop(ItemInfo info) {
        final Task nonDesktopTask =
                mControllers.taskbarRecentAppsController.getNonDesktopTask(info);
        if (DisplayController.getInfo(this).isInDesktopFirstMode && nonDesktopTask != null) {
            if (!DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_POLICY_IN_LPM.isTrue()) {
                // Keep the fullscreen mode in desktop-first mode.
                return false;
            }
            final LauncherDisplayInfo currentDisplayInfo = DisplayController.INSTANCE.get(this)
                    .getInfoForDisplay(nonDesktopTask.getKey().displayId);
            if (currentDisplayInfo != null && currentDisplayInfo.isInDesktopFirstMode) {
                // Keep the fullscreen mode if both current and target displays are in desktop-first
                // mode.
                return false;
            }
        }
        // Always launch in freeform if in external display.
        return  !isPrimaryDisplay() || isTaskbarShowingDesktopTasks();
    }

    private void launchDesktopApp(Intent intent, ItemInfo info) {
        TaskbarRecentAppsController.TaskState taskState =
                mControllers.taskbarRecentAppsController.getTaskbarItemState(info);
        RunningAppState appState = taskState.getRunningAppState();
        if (appState == RunningAppState.RUNNING || appState == RunningAppState.MINIMIZED) {
            // We only need a custom animation (a RemoteTransition) if the task is minimized - if
            // it's already visible it will just be brought forward.
            RemoteTransition remoteTransition = (appState == RunningAppState.MINIMIZED)
                    ? createDesktopAppLaunchRemoteTransition(
                            AppLaunchType.UNMINIMIZE, Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON)
                    : null;
            UI_HELPER_EXECUTOR.execute(() ->
                    SystemUiProxy.INSTANCE.get(this).showDesktopApp(taskState.getTaskId(),
                            remoteTransition, DesktopTaskToFrontReason.TASKBAR_TAP));
            return;
        }
        // There is no task associated with this launch - launch a new task through an intent
        ActivityOptionsWrapper opts = getActivityLaunchDesktopOptions();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                /* requestCode= */ 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT,
                /* options= */ null);
        mSysUiProxy.startLaunchIntentTransition(pendingIntent, opts.options.toBundle(),
                getDisplayId());
    }

    /** Expands a folder icon when it is clicked */
    private void expandFolder(FolderIcon folderIcon) {
        Folder folder = folderIcon.getFolder();
        if (!folder.isClosed()) {
            return;
        }

        folder.setPriorityOnFolderStateChangedListener(
                new Folder.OnFolderStateChangedListener() {
                    @Override
                    public void onFolderStateChanged(int newState) {
                        if (newState == Folder.STATE_OPEN) {
                            setTaskbarWindowFocusableForIme(true);
                        } else if (newState == Folder.STATE_CLOSED) {
                            if (--mFolderCount == 0) {
                                setTaskbarWindowFullscreen(false, TASKBAR_WINDOW_FULLSCREEN_FOLDER);

                                // Defer by a frame to ensure we're no longer fullscreen and thus
                                // won't jump.
                                getDragLayer().post(() -> setTaskbarWindowFocusableForIme(false));
                            }
                            folder.setPriorityOnFolderStateChangedListener(null);
                        }
                    }
                });

        mFolderCount++;
        setTaskbarWindowFullscreen(true, TASKBAR_WINDOW_FULLSCREEN_FOLDER);
        getDragLayer().post(() -> {
            folder.animateOpen();
            getStatsLogManager().logger().withItemInfo(folder.mInfo).log(LAUNCHER_FOLDER_OPEN);

            folder.mapOverItems((itemInfo, itemView) -> {
                mControllers.taskbarViewController
                        .setClickAndLongClickListenersForIcon(itemView);
                // To play haptic when dragging, like other Taskbar items do.
                itemView.setHapticFeedbackEnabled(true);
                return false;
            });

            // Close any open taskbar tooltips.
            if (AbstractFloatingView.hasOpenView(this, TYPE_ON_BOARD_POPUP)) {
                AbstractFloatingView.getOpenView(this, TYPE_ON_BOARD_POPUP)
                        .close(/* animate= */ false);
            }
        });
    }

    /**
     * Returns whether the taskbar is currently visually stashed.
     */
    public boolean isTaskbarStashed() {
        return mControllers.taskbarStashController.isStashed();
    }

    /**
     * Called when we want to unstash taskbar when user performs swipes up gesture.
     *
     * @param delayTaskbarBackground whether we will delay the taskbar background animation
     */
    public void onSwipeToUnstashTaskbar(boolean delayTaskbarBackground) {
        mControllers.uiController.onSwipeToUnstashTaskbar();

        boolean wasStashed = mControllers.taskbarStashController.isStashed();
        if (isTransientTaskbar()) {
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(/* stash= */ false,
                    SHOULD_BUBBLES_FOLLOW_DEFAULT_VALUE, delayTaskbarBackground);
        } else if (shouldAllowTaskbarToAutoStash()) {
            mControllers.taskbarStashController.updateAndAnimatePinnedTaskbar(false);
        }
        boolean isStashed = mControllers.taskbarStashController.isStashed();
        if (isStashed != wasStashed) {
            VibratorWrapper.INSTANCE.get(this).vibrateForTaskbarUnstash();
        }
        mControllers.taskbarEduTooltipController.hide();
    }

    public void openTaskbarAllApps() {
        mControllers.uiController.toggleAllApps(false);
    }

    /** Returns {@code true} if Taskbar All Apps is open. */
    public boolean isTaskbarAllAppsOpen() {
        return mControllers.taskbarAllAppsController.isOpen();
    }

    /** Toggles the Taskbar's stash state. */
    public void toggleTaskbarStash() {
        mControllers.taskbarStashController.toggleTaskbarStash();
    }

    /**
     * Plays the taskbar background alpha animation if one is not currently playing.
     */
    public void playTaskbarBackgroundAlphaAnimation() {
        mControllers.taskbarStashController.playTaskbarBackgroundAlphaAnimation();
    }

    /**
     * Called to start the taskbar translation spring to its settled translation (0).
     */
    public void startTranslationSpring() {
        mControllers.taskbarTranslationController.startSpring();
    }

    /**
     * Returns a callback to help monitor the swipe gesture.
     */
    public TransitionCallback getTranslationCallbacks() {
        return mControllers.taskbarTranslationController.getTransitionCallback();
    }

    /**
     * Called when a transient Autohide flag suspend status changes.
     */
    public void onTransientAutohideSuspendFlagChanged(boolean isSuspended) {
        mControllers.taskbarStashController.updateTaskbarTimeout(isSuspended);
    }

    /**
     * Called when we detect a motion down or up/cancel in the nav region while stashed.
     *
     * @param animateForward Whether to animate towards the unstashed hint state or back to stashed.
     */
    public void startTaskbarUnstashHint(boolean animateForward) {
        mControllers.taskbarStashController.startUnstashHint(animateForward);
    }

    /**
     * @return if we should allow taskbar to auto stash
     */
    @AnyThread
    public boolean shouldAllowTaskbarToAutoStash() {
        return mControllers.taskbarStashController.shouldAllowTaskbarToAutoStash();
    }

    /**
     * Enables the auto timeout for taskbar stashing. This method should only be used for taskbar
     * testing.
     */
    @AnyThread
    @VisibleForTesting
    public void enableBlockingTimeoutDuringTests(boolean enableBlockingTimeout) {
        mControllers.taskbarStashController.enableBlockingTimeoutDuringTests(enableBlockingTimeout);
    }

    /**
     * Unstashes the Taskbar if it is stashed.
     *
     * @return true if transient taskbar and caller can expect taskbar to be unstashed.
     */
    @VisibleForTesting
    public boolean unstashTaskbarIfStashed() {
        if (isTransientTaskbar()) {
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(false);
            return true;
        } else {
            return false;
        }
    }

    /** Removes bubble bar if present on the screen */
    @VisibleForTesting
    public void removeAllBubbles() {
        mControllers.bubbleControllers.ifPresent(
                controllers -> controllers.bubbleBarViewController.onDismissAllBubbles());
    }

    /** Unstashes the Bubble Bar if it is stashed. */
    @VisibleForTesting
    public void unstashBubbleBarIfStashed() {
        mControllers.bubbleControllers.ifPresent(bubbleControllers -> {
            if (bubbleControllers.bubbleStashController.isStashed()) {
                bubbleControllers.bubbleStashController.showBubbleBar(false);
            }
        });
    }

    public boolean isUserSetupComplete() {
        return mIsUserSetupComplete;
    }

    public boolean isInKidsMode() {
        return mIsNavBarKidsMode;
    }

    /**
     * Checks if the simple view mode is enabled.
     *
     * Since Simple View puts the device in 3 button nav mode, we use that as a precursor to
     * checking the actual value in Settings to avoid extra calls to Settings.
     */
    public boolean isSimpleViewEnabled() {
        return isThreeButtonNav()
                && Settings.Secure.getInt(getContentResolver(), SIMPLE_VIEW_SETTINGS_KEY, 0)
                > 0;
    }

    public boolean isNavBarKidsModeActive() {
        return mIsNavBarKidsMode && isThreeButtonNav();
    }

    @VisibleForTesting(otherwise = PROTECTED)
    public boolean isNavBarForceVisible() {
        return mIsNavBarKidsMode;
    }

    /**
     * Displays a single frame of the Launcher start from SUW animation.
     *
     * This animation is a combination of the Launcher resume animation, which animates the hotseat
     * icons into position, the Taskbar unstash to hotseat animation, which animates the Taskbar
     * stash bar into the hotseat icons, and an override to prevent showing the Taskbar all apps
     * button.
     *
     * This should be used to run a Taskbar unstash to hotseat animation whose progress matches a
     * swipe progress.
     *
     * @param duration a placeholder duration to be used to ensure all full-length
     *                 sub-animations are properly coordinated. This duration should not actually
     *                 be used since this animation tracks a swipe progress.
     */
    protected AnimatorPlaybackController createLauncherStartFromSuwAnim(int duration) {
        AnimatorSet fullAnimation = new AnimatorSet();
        fullAnimation.setDuration(duration);

        TaskbarUIController uiController = mControllers.uiController;
        if (uiController instanceof LauncherTaskbarUIController taskbarUiController) {
            taskbarUiController.addLauncherVisibilityChangedAnimation(fullAnimation, duration);
            taskbarUiController.addWorkspaceRevealAnim(fullAnimation, duration);
        }
        mControllers.taskbarStashController.addUnstashToHotseatAnimationFromSuw(fullAnimation,
                duration);

        View allAppsButton = mControllers.taskbarViewController.getAllAppsButtonView();
        if (!FeatureFlags.enableAllAppsButtonInHotseat()) {
            ValueAnimator alphaOverride = ValueAnimator.ofFloat(0, 1);
            alphaOverride.setDuration(duration);
            alphaOverride.addUpdateListener(a -> {
                // Override the alpha updates in the icon alignment animation.
                allAppsButton.setAlpha(0);
            });
            alphaOverride.addListener(AnimatorListeners.forSuccessCallback(
                    () -> allAppsButton.setAlpha(1f)));
            fullAnimation.play(alphaOverride);
        }

        return AnimatorPlaybackController.wrap(fullAnimation, duration);
    }

    /**
     * @return true if we should force the fallback animation for All Set page
     */
    @AnyThread
    public boolean shouldForceAllSetFallbackAnimation() {
        return !(mControllers.uiController instanceof LauncherTaskbarUIController);
    }

    void notifyUpdateLayoutParams() {
        if (mDragLayer.isAttachedToWindow()) {
            // Copy the current windowLayoutParams to mLastUpdatedLayoutParams and compare the diff.
            // If there is no change, we will skip the call to updateViewLayout.
            int changes = mLastUpdatedLayoutParams.copyFrom(mWindowLayoutParams);
            if (changes == 0) {
                return;
            }
            mWindowManager.updateViewLayout(mDragLayer.getRootView(), mWindowLayoutParams);
        }
    }

    @Override
    public void showPopupMenuForIcon(View icon) {
        setTaskbarWindowFullscreen(true, TASKBAR_WINDOW_ICON_POPUP_MENU);
        icon.post(() -> mControllers.taskbarPopupController.show(icon));
    }

    public void launchKeyboardFocusedTask() {
        mControllers.uiController.launchKeyboardFocusedTask();
    }

    public boolean isInApp() {
        return mControllers.taskbarStashController.isInApp();
    }

    public boolean isInOverview() {
        return mControllers.taskbarStashController.isInOverview();
    }

    public boolean isInStashedLauncherState() {
        return mControllers.taskbarStashController.isInStashedLauncherState();
    }

    @AnyThread
    public TaskbarFeatureEvaluator getTaskbarFeatureEvaluator() {
        return mTaskbarFeatureEvaluator;
    }

    public TaskbarSpecsEvaluator getTaskbarSpecsEvaluator() {
        return mTaskbarSpecsEvaluator;
    }

    private StringJoiner getFullscreenFlags() {
        StringJoiner fullscreenFlags = new StringJoiner("|");
        FlagDebugUtils.appendFlag(fullscreenFlags, mTaskbarFullscreenFlags,
                TASKBAR_WINDOW_FULLSCREEN_DRAG, "TASKBAR_WINDOW_FULLSCREEN_DRAG");
        FlagDebugUtils.appendFlag(fullscreenFlags, mTaskbarFullscreenFlags,
                TASKBAR_WINDOW_FULLSCREEN_BUBBLE_DRAG, "TASKBAR_WINDOW_FULLSCREEN_BUBBLE_DRAG");
        FlagDebugUtils.appendFlag(fullscreenFlags, mTaskbarFullscreenFlags,
                TASKBAR_WINDOW_FULLSCREEN_FOLDER, "TASKBAR_WINDOW_FULLSCREEN_FOLDER");
        FlagDebugUtils.appendFlag(fullscreenFlags, mTaskbarFullscreenFlags,
                TASKBAR_WINDOW_ICON_POPUP_MENU, "TASKBAR_WINDOW_ICON_POPUP_MENU");
        FlagDebugUtils.appendFlag(fullscreenFlags, mTaskbarFullscreenFlags,
                TASKBAR_WINDOW_ICON_TASKBAR_OVERFLOW, "TASKBAR_WINDOW_ICON_TASKBAR_OVERFLOW");
        FlagDebugUtils.appendFlag(fullscreenFlags, mTaskbarFullscreenFlags,
                TASKBAR_WINDOW_TASKBAR_PINNING, "TASKBAR_WINDOW_TASKBAR_PINNING");
        FlagDebugUtils.appendFlag(fullscreenFlags, mTaskbarFullscreenFlags,
                TASKBAR_WINDOW_ICONS_TRANSITION, "TASKBAR_WINDOW_ICONS_TRANSITION");
        return fullscreenFlags;
    }

    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarActivityContext:");

        pw.println(String.format(
                "%s\tmNavMode=%s", prefix, mNavMode));
        pw.println(String.format(
                "%s\tmImeDrawsImeNavBar=%b", prefix, mImeDrawsImeNavBar));
        pw.println(String.format(
                "%s\tmIsUserSetupComplete=%b", prefix, mIsUserSetupComplete));
        pw.println(String.format(
                "%s\tmWindowLayoutParams.height=%dpx", prefix, mWindowLayoutParams.height));
        pw.println(String.format("%s\tmTaskbarFullscreenFlags=%s", prefix, getFullscreenFlags()));

        mControllers.dumpLogs(prefix + "\t", pw);
        mDeviceProfile.dump(this, prefix, pw);
    }

    @VisibleForTesting
    public int getTaskbarAllAppsTopPadding() {
        return mControllers.taskbarAllAppsController.getTaskbarAllAppsTopPadding();
    }

    @VisibleForTesting
    public int getTaskbarAllAppsScroll() {
        return mControllers.taskbarAllAppsController.getTaskbarAllAppsScroll();
    }

    @VisibleForTesting
    @AnyThread
    public float getStashedTaskbarScale() {
        return mControllers.stashedHandleViewController.getStashedHandleHintScale().value;
    }

    /**
     * Sets the upper limit for max number of icons in the taskbar.
     */
    @VisibleForTesting
    public void limitMaxTaskbarIconsNum(int maxIconNumLimit) {
        mControllers.taskbarViewController.limitMaxTaskbarIconsNum(maxIconNumLimit);
    }

    /** Closes the KeyboardQuickSwitchView without an animation if open. */
    public void closeKeyboardQuickSwitchView() {
        mControllers.keyboardQuickSwitchController.closeQuickSwitchView(false);
    }

    boolean isIconAlignedWithHotseat() {
        return mControllers.uiController.isIconAlignedWithHotseat();
    }

    public int getNumbersOfTaskbarIconsOverflowing() {
        return mControllers.taskbarViewController.getNumbersOfTaskbarIconsOverflowing();
    }

    // TODO(b/395061396): Remove `otherwise` when overview in widow is enabled.
    @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
    @AnyThread
    public TaskbarControllers getControllers() {
        return mControllers;
    }

}
