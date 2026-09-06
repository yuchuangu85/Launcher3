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
package com.android.quickstep;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.LauncherPrefs.backedUpItem;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMotionEvent;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.display.LauncherDisplayInfo.CHANGE_NIGHT_MODE;
import static com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_SEEN;
import static com.android.launcher3.util.window.WindowManagerProxy.MIN_TABLET_WIDTH;
import static com.android.quickstep.GestureState.DEFAULT_STATE;
import static com.android.quickstep.GestureState.TrackpadGestureType.getTrackpadGestureType;
import static com.android.quickstep.InputConsumer.TYPE_CURSOR_HOVER;
import static com.android.quickstep.InputConsumer.createNoOpInputConsumer;
import static com.android.quickstep.InputConsumerUtils.newConsumer;
import static com.android.quickstep.InputConsumerUtils.tryCreateAssistantInputConsumer;
import static com.android.quickstep.RecentsAnimationDeviceState.RESET_TO_DEFAULT_GESTURAL_HEIGHT;
import static com.android.quickstep.dagger.SysUIConnectionComponentKt.CONNECTION_CLEANER;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.app.displaylib.DisplayRepository;
import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.ConstantItem;
import com.android.launcher3.EncryptionType;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.concurrent.annotations.Ui;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.desktop.DesktopAppLaunchTransitionManager;
import com.android.launcher3.display.DisplayController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.taskbar.TaskbarUiState;
import com.android.launcher3.taskbar.TaskbarUiStateMonitor;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.PluginManagerWrapper;
import com.android.launcher3.util.PostUnlockObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.launcher3.util.ThreadSafeRunnableList;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.OverviewComponentObserver.OverviewChangeListener;
import com.android.quickstep.dagger.SysUIConnectionSingleton;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.inputconsumers.BubbleBarInputConsumer;
import com.android.quickstep.inputconsumers.OneHandedModeInputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActiveGestureLog.CompoundString;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;
import com.android.quickstep.util.ActiveTrackpadList;
import com.android.quickstep.util.ActivityPreloadUtil;
import com.android.quickstep.util.ContextualSearchStateManager;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.quickstep.window.RecentsWindowSwipeHandler;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.shared.desktopmode.DesktopState;

import kotlinx.coroutines.CoroutineDispatcher;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@SysUIConnectionSingleton
public class TouchInteractionHandler extends ContextWrapper {

    private static final String TAG = "TouchInteractionService";

    private static final ConstantItem<Boolean> HAS_ENABLED_QUICKSTEP_ONCE = backedUpItem(
            "launcher.has_enabled_quickstep_once", false, EncryptionType.ENCRYPTED);

    private final AbsSwipeUpHandler.Factory mLauncherSwipeHandlerFactory =
            this::createLauncherSwipeHandler;
    private final AbsSwipeUpHandler.Factory mFallbackSwipeHandlerFactory =
            this::createFallbackSwipeHandler;
    private final AbsSwipeUpHandler.Factory mRecentsWindowSwipeHandlerFactory =
            this::createRecentsWindowSwipeHandler;

    private final OverviewChangeListener mOverviewChangeListener = this::onOverviewTargetChanged;

    // We should clean up the recents window on the primary display on home intent start, however we
    // have no other way of listening to this event in the 3P launcher case.
    private final TaskStackChangeListener mHomeIntentStartedListener =
            new TaskStackChangeListener() {
                @Override
                public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                        boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
                    TaskStackChangeListener.super.onActivityRestartAttempt(task, homeTaskVisible,
                            clearedTask, wasVisible);
                    if (task.configuration.windowConfiguration.getActivityType()
                            != ACTIVITY_TYPE_HOME
                            || task.displayId != DEFAULT_DISPLAY) {
                        // We only want to handle home intent starts on the primary
                        // display.
                        return;
                    }
                    BaseContainerInterface<?, ?> defaultContainerInterface =
                            OverviewComponentObserver.INSTANCE.get(
                                    TouchInteractionHandler.this).getContainerInterface(
                                    DEFAULT_DISPLAY);
                    if (defaultContainerInterface == null
                            || !(defaultContainerInterface.getCreatedContainer()
                            instanceof RecentsWindowManager recentsWindowManager)) {
                        return;
                    }
                    StateManager<RecentsState, RecentsWindowManager> stateManager =
                            recentsWindowManager.getStateManager();
                    if (!stateManager.getState().isInOverview()) {
                        // Only hide the recents surface if we receive the home intent while in
                        // overview, otherwise gestures will appear to stop responding when the home
                        // intent is received while in BACKGROUND_APP state.
                        return;
                    }
                    stateManager.moveToRestState(/* animated=*/ true);
                }
            };

    private final PerDisplayRepository<RotationTouchHelper> mRotationTouchHelperRepository;
    private final PerDisplayRepository<RecentsAnimationDeviceState> mDeviceStateRepository;
    private final PerDisplayRepository<TaskAnimationManager> mTaskAnimationManagerRepository;
    private final PerDisplayRepository<RecentsWindowManager> mRecentsWindowManagerRepository;

    private final InputConsumerController mInputConsumer;
    private final Choreographer mMainChoreographer;

    private final DisplayModel.Factory<InputResource> mDisplayModelFactory;
    private final CoroutineDispatcher mUiCoroutineDispatcher;
    private final DisplayRepository mDisplayRepository;
    private final DesktopState mDesktopState;

    // This needs to be a member to be queued and potentially removed later if the service is
    // destroyed before the user is unlocked
    private final Runnable mUserUnlockedRunnable = this::onUserUnlocked;

    private final LockedUserState mLockedUserState;
    private final SystemUiProxy mSystemUiProxy;

    private final PostUnlockObject<OverviewCommandHelper> mOverviewCommandHelper;
    private final PostUnlockObject<OverviewComponentObserver> mOverviewComponentObserver;

    private final AllAppsActionManager mAllAppsActionManager;
    private final TaskbarManager mTaskbarManager;
    private final ActiveTrackpadList mTrackpadsConnected;

    private final DesktopVisibilityController mDesktopVisibilityController;

    private boolean mUserUnlocked = false;

    @Nullable private DisplayModel<InputResource> mInputResourceDisplayModel;

    private DesktopAppLaunchTransitionManager mDesktopAppLaunchTransitionManager;

    @Inject
    public TouchInteractionHandler(
            @ApplicationContext Context context,
            DisplayRepository displayRepository,
            PerDisplayRepository<RecentsAnimationDeviceState> deviceStateRepository,
            PerDisplayRepository<TaskAnimationManager> taskAnimationManagerRepository,
            PerDisplayRepository<RotationTouchHelper> rotationTouchHelperRepository,
            PerDisplayRepository<RecentsWindowManager> recentsWindowManagerRepository,
            @Ui CoroutineDispatcher uiCoroutineDispatcher,
            LockedUserState lockedUserState,
            ScreenOnTracker screenOnTracker,
            SystemUiProxy systemUiProxy,
            DisplayController displayController,
            PostUnlockObject<OverviewCommandHelper> overviewCommandHelper,
            PostUnlockObject<OverviewComponentObserver> overviewComponentObserver,
            AllAppsActionManager allAppsActionManager,
            TaskbarManager taskbarManager,
            ActiveTrackpadList activeTrackpadList,
            DisplayModel.Factory<InputResource> displayModelFactory,
            DesktopVisibilityController desktopVisibilityController,
            DesktopState desktopState,
            @Ui Executor uiExecutor,
            @Named(CONNECTION_CLEANER) ThreadSafeRunnableList cleanupTasks
    ) {
        super(context);
        Log.d(TAG, "onCreate: user=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in onUserUnlocked() below.
        mDisplayRepository = displayRepository;
        mDeviceStateRepository = deviceStateRepository;
        mTaskAnimationManagerRepository = taskAnimationManagerRepository;
        mRotationTouchHelperRepository = rotationTouchHelperRepository;
        mRecentsWindowManagerRepository = recentsWindowManagerRepository;
        mDisplayModelFactory = displayModelFactory;
        mUiCoroutineDispatcher = uiCoroutineDispatcher;
        mLockedUserState = lockedUserState;
        mSystemUiProxy = systemUiProxy;
        mOverviewCommandHelper = overviewCommandHelper;
        mAllAppsActionManager = allAppsActionManager;
        mOverviewComponentObserver = overviewComponentObserver;

        mDesktopState = desktopState;
        mMainChoreographer = Choreographer.getInstance();
        mTaskbarManager = taskbarManager;

        mDesktopAppLaunchTransitionManager =
                new DesktopAppLaunchTransitionManager(this, systemUiProxy, displayController);
        mDesktopAppLaunchTransitionManager.registerTransitions();
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();

        mTrackpadsConnected = activeTrackpadList;
        cleanupTasks.addCloseable(uiExecutor, activeTrackpadList.getConnected().forEach(
                uiExecutor, isConnected -> {
                    if (isConnected && mInputResourceDisplayModel != null) {
                        // Don't destroy and reinitialize input monitor due to trackpad
                        // connecting when it's already set up.
                    } else {
                        initInputMonitor("onTrackpadConnected()");
                    }
                    return null;
                }));

        mDesktopVisibilityController = desktopVisibilityController;

        // Call runOnUserUnlocked() before any other callbacks to ensure everything is initialized.
        lockedUserState.runOnUserUnlocked(mUserUnlockedRunnable);

        // Assume that the navigation mode changes for all displays at once.
        RecentsAnimationDeviceState mainDisplayState =
                Objects.requireNonNull(mDeviceStateRepository.get(DEFAULT_DISPLAY));
        cleanupTasks.addCloseable(uiExecutor, mainDisplayState.addDisplayInfoChangeCallback(
                CHANGE_NAVIGATION_MODE, this::onNavigationModeChanged));
        // Assume that the night mode changes for all displays at once.
        cleanupTasks.addCloseable(uiExecutor, mainDisplayState.addDisplayInfoChangeCallback(
                        CHANGE_NIGHT_MODE, this::onNightModeChanged));
        cleanupTasks.addTask(uiExecutor, this::onDestroy);

        ScreenOnTracker.ScreenOnListener screenOnListener = this::onScreenOnChanged;
        screenOnTracker.addListener(screenOnListener);
        cleanupTasks.addTask(uiExecutor, () -> screenOnTracker.removeListener(screenOnListener));
    }

    @Nullable
    private InputResource getInputResource(int displayId) {
        if (mInputResourceDisplayModel == null) {
            return null;
        }
        return mInputResourceDisplayModel.getDisplayResource(displayId);
    }

    private void disposeEventHandlers(String reason) {
        Log.d(TAG, "disposeEventHandlers: Reason: " + reason
                + " instance=" + System.identityHashCode(this));
        if (mInputResourceDisplayModel != null) {
            mInputResourceDisplayModel.close();
            mInputResourceDisplayModel = null;
        }
    }

    /** Called from TIS Binder */
    public void initInputMonitor(String reason) {
        disposeEventHandlers("Initializing input monitor due to: " + reason);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(DEFAULT_DISPLAY);
        if (deviceState.isButtonNavMode()
                && !deviceState.supportsAssistantGestureInButtonNav()
                && (!mTrackpadsConnected.getConnected().getValue())) {
            return;
        }
        mInputResourceDisplayModel =
                mDisplayModelFactory.newModel(mUiCoroutineDispatcher, InputResource::new);
        mInputResourceDisplayModel.initializeDisplays();

        mRotationTouchHelperRepository.get(DEFAULT_DISPLAY).updateGestureTouchRegions();
    }

    /**
     * Called when the navigation mode changes, guaranteed to be after the device state has updated.
     */
    private void onNavigationModeChanged() {
        initInputMonitor("onNavigationModeChanged()");
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();
    }
    private void onNightModeChanged() {
        ActivityPreloadUtil.preloadOverviewForTIS(this, false /* fromInit */);
    }

    @UiThread
    public void onUserUnlocked() {
        Log.d(TAG, "onUserUnlocked: userId=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        mUserUnlocked = true;
        mInputConsumer.registerInputConsumer();
        mDeviceStateRepository.forEach(/* createIfAbsent= */ true, deviceState ->
                onSystemUiFlagsChanged(deviceState.getSysuiStateFlags(),
                        deviceState.getDisplayId()));
        onAssistantVisibilityChanged();

        // Initialize the task tracker
        TopTaskTracker.INSTANCE.get(this);

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();

        mOverviewComponentObserver.get().addOverviewChangeListener(mOverviewChangeListener);
        onOverviewTargetChanged(mOverviewComponentObserver.get().isHomeAndOverviewSame());

        mAllAppsActionManager.onUserUnlocked();
    }

    public OverviewCommandHelper getOverviewCommandHelper() {
        return mOverviewCommandHelper.getIfReady();
    }

    private void resetHomeBounceSeenOnQuickstepEnabledFirstTime() {
        if (!LockedUserState.get(this).isUserUnlocked() || mDeviceStateRepository.get(
                DEFAULT_DISPLAY).isButtonNavMode()) {
            // Skip if not yet unlocked (can't read user shared prefs) or if the current navigation
            // mode doesn't have gestures
            return;
        }

        // Reset home bounce seen on quick step enabled for first time
        LauncherPrefs prefs = LauncherPrefs.get(this);
        if (!prefs.get(HAS_ENABLED_QUICKSTEP_ONCE)) {
            prefs.put(
                    HAS_ENABLED_QUICKSTEP_ONCE.to(true),
                    HOME_BOUNCE_SEEN.to(false));
        }
    }

    private void onOverviewTargetChanged(boolean isHomeAndOverviewSame) {
        mAllAppsActionManager.setHomeAndOverviewSame(isHomeAndOverviewSame);
        BaseContainerInterface<?, ?> containerInterface =
                mOverviewComponentObserver.get().getContainerInterface(DEFAULT_DISPLAY);
        RecentsViewContainer newOverviewContainer =
                containerInterface == null ? null : containerInterface.getCreatedContainer();
        if (newOverviewContainer != null) {
            if (newOverviewContainer instanceof StatefulActivity<?> activity) {
                // This will also call setRecentsViewContainer() internally.
                mTaskbarManager.setActivity(activity);
            } else if (!isHomeAndOverviewSame) {
                // When isHomeAndOverviewSame is true, QuickstepLauncher.onOverviewTargetChanged
                // will call TaskbarManager.setActivity
                mTaskbarManager.setRecentsViewContainer(newOverviewContainer);
            }
        }
        mRecentsWindowManagerRepository.forEach(
                /* createIfAbsent= */ false, RecentsWindowManager::cleanUpSurfaceControlViewHost);
        if (isHomeAndOverviewSame) {
            TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                    mHomeIntentStartedListener);
        } else {
            TaskStackChangeListeners.getInstance().registerTaskStackListener(
                    mHomeIntentStartedListener);
        }
    }

    @UiThread
    private void onSystemUiFlagsChanged(@SystemUiStateFlags long lastSysUIFlags, int displayId) {
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        if (LockedUserState.get(this).isUserUnlocked()) {
            TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(
                    displayId);
            if (deviceState != null && taskAnimationManager != null) {
                long systemUiStateFlags = deviceState.getSysuiStateFlags();
                mTaskbarManager.onSystemUiFlagsChanged(systemUiStateFlags, displayId);
                if (displayId == DEFAULT_DISPLAY) {
                    // The following don't care about non-default displays, at least for now. If
                    // they ever will, they should be taken care of.
                    mSystemUiProxy.setLastSystemUiStateFlags(systemUiStateFlags);
                    mOverviewComponentObserver.get().setHomeDisabled(deviceState.isHomeDisabled());
                }
                taskAnimationManager.onSystemUiFlagsChanged(lastSysUIFlags, systemUiStateFlags);
            }
        } else if (deviceState != null) {
            mTaskbarManager.onSystemUiFlagsChanged(deviceState.getSysuiStateFlags(), displayId);
        }
    }

    @UiThread
    public void onAssistantVisibilityChanged() {
        if (LockedUserState.get(this).isUserUnlocked()) {
            mOverviewComponentObserver.get().getContainerInterface(
                    DEFAULT_DISPLAY).onAssistantVisibilityChanged(
                    mDeviceStateRepository.get(DEFAULT_DISPLAY).getAssistantVisibility());
        }
    }

    private void onDestroy() {
        Log.d(TAG, "onDestroy: user=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        if (LockedUserState.get(this).isUserUnlocked()) {
            mInputConsumer.unregisterInputConsumer();
            mOverviewComponentObserver.get().setHomeDisabled(false);
            mOverviewComponentObserver.get().removeOverviewChangeListener(mOverviewChangeListener);
        }
        disposeEventHandlers("TouchInteractionService onDestroy()");
        mSystemUiProxy.clearProxy();

        mAllAppsActionManager.onDestroy();

        if (mDesktopAppLaunchTransitionManager != null) {
            mDesktopAppLaunchTransitionManager.unregisterTransitions();
        }
        mDesktopAppLaunchTransitionManager = null;
        mLockedUserState.removeOnUserUnlockedRunnable(mUserUnlockedRunnable);
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                mHomeIntentStartedListener);
    }

    protected void onScreenOnChanged(boolean isOn) {
        if (isOn) {
            return;
        }
        long currentTime = SystemClock.uptimeMillis();
        MotionEvent cancelEvent = MotionEvent.obtain(
                currentTime, currentTime, ACTION_CANCEL, 0f, 0f, 0);
        onInputEvent(cancelEvent);
        cancelEvent.recycle();
    }

    private void onInputEvent(InputEvent ev) {
        int displayId = ev.getDisplayId();
        if (!(ev instanceof MotionEvent)) {
            ActiveGestureProtoLogProxy.logUnknownInputEvent(displayId, ev.toString());
            return;
        }
        MotionEvent event = (MotionEvent) ev;

        TestLogging.recordMotionEvent(
                TestProtocol.SEQUENCE_TIS, "TouchInteractionService.onInputEvent", event);

        if (!LockedUserState.get(this).isUserUnlocked()) {
            ActiveGestureProtoLogProxy.logOnInputEventUserLocked(displayId);
            return;
        }

        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        if (deviceState == null) {
            Log.d(TAG, "RecentsAnimationDeviceState not available for displayId " + displayId);
            return;
        }

        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        if (rotationTouchHelper == null) {
            Log.d(TAG, "RotationTouchHelper not available for displayId " + displayId);
            return;
        }

        InputResource inputResource = getInputResource(displayId);
        if (inputResource == null) {
            Log.d(TAG, "InputResource not available for displayId " + displayId);
            return;
        }

        NavigationMode currentNavMode = deviceState.getMode();
        NavigationMode gestureStartNavMode = inputResource.gestureStartNavMode;

        // On CD, only consume input event if flag is on and taskbar is stashed.
        TaskbarActivityContext tac = mTaskbarManager.getTaskbarForDisplay(displayId);
        boolean shouldConnectedDisplayConsumeEvent =
                displayId != DEFAULT_DISPLAY
                && tac != null && tac.isTaskbarStashed();
        if (gestureStartNavMode != null && gestureStartNavMode != currentNavMode) {
            ActiveGestureProtoLogProxy.logOnInputEventNavModeSwitched(
                    displayId, gestureStartNavMode.name(), currentNavMode.name());
            event.setAction(ACTION_CANCEL);
        } else if (deviceState.isButtonNavMode()
                && !deviceState.supportsAssistantGestureInButtonNav()
                && !isTrackpadMotionEvent(event)
                && !shouldConnectedDisplayConsumeEvent) {
            ActiveGestureProtoLogProxy.logOnInputEventThreeButtonNav(displayId);
            return;
        }

        final int action = event.getActionMasked();
        // Note this will create a new consumer every mouse click, as after ACTION_UP from the click
        // an ACTION_HOVER_ENTER will fire as well.
        boolean isHoverActionWithoutConsumer = isHoverActionWithoutConsumer(event);

        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        if (taskAnimationManager == null) {
            Log.e(TAG, "TaskAnimationManager not available for displayId " + displayId);
            ActiveGestureProtoLogProxy.logOnTaskAnimationManagerNotAvailable(displayId);
            return;
        }
        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            taskAnimationManager.notifyNewGestureStart();
        }
        if (taskAnimationManager.shouldIgnoreMotionEvents()) {
            if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
                ActiveGestureProtoLogProxy.logOnInputIgnoringFollowingEvents(displayId);
            }
            return;
        }

        InputMonitorCompat inputMonitorCompat = inputResource.inputMonitorCompat;
        InputEventReceiver inputEventReceiver = inputResource.inputEventReceiver;

        if (inputMonitorCompat == null) {
            Log.d(TAG, "InputMonitorCompat not available for displayId " + displayId);
            return;
        }
        if (inputEventReceiver == null) {
            Log.d(TAG, "InputEventReceiver not available for displayId " + displayId);
            return;
        }

        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            inputResource.gestureStartNavMode = currentNavMode;
        } else if (action == ACTION_UP || action == ACTION_CANCEL) {
            inputResource.gestureStartNavMode = null;
        }

        SafeCloseable traceToken = TraceHelper.INSTANCE.allowIpcs("TIS.onInputEvent");

        CompoundString reasonString = action == ACTION_DOWN
                ? CompoundString.newEmptyString() : CompoundString.NO_OP;
        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            rotationTouchHelper.setOrientationTransformIfNeeded(event);

            boolean isOneHandedModeActive = deviceState.isOneHandedModeActive();
            boolean isInSwipeUpTouchRegion = rotationTouchHelper.isInSwipeUpTouchRegion(event);
            BubbleControllers bubbleControllers = tac != null ? tac.getBubbleControllers() : null;
            boolean isOnBubbles = bubbleControllers != null
                    && BubbleBarInputConsumer.isEventOnBubbles(tac, event);
            if (deviceState.isButtonNavMode()
                    && deviceState.supportsAssistantGestureInButtonNav()) {
                reasonString.append("in three button mode which supports Assistant gesture");
                // Consume gesture event for Assistant (all other gestures should do nothing).
                if (deviceState.canTriggerAssistantAction(event)) {
                    reasonString.append(" and event can trigger assistant action, "
                            + "consuming gesture for assistant action");
                    inputResource.gestureState = createGestureState(
                            displayId, inputResource.gestureState, getTrackpadGestureType(event));
                    inputResource.uncheckedConsumer = tryCreateAssistantInputConsumer(
                            this,
                            deviceState,
                            inputMonitorCompat,
                            inputResource.gestureState,
                            event);
                } else {
                    reasonString.append(" but event cannot trigger Assistant, "
                            + "consuming gesture as no-op");
                    inputResource.uncheckedConsumer = createNoOpInputConsumer(displayId);
                }
            } else if ((!isOneHandedModeActive && isInSwipeUpTouchRegion)
                    || isHoverActionWithoutConsumer || isOnBubbles) {
                reasonString.append(!isOneHandedModeActive && isInSwipeUpTouchRegion
                        ? "one handed mode is not active and event is in swipe up region, "
                                + "creating new input consumer"
                        : "isHoverActionWithoutConsumer == true, creating new input consumer");
                // Clone the previous gesture state since onConsumerAboutToBeSwitched might trigger
                // onConsumerInactive and wipe the previous gesture state
                GestureState prevGestureState = new GestureState(inputResource.gestureState);
                GestureState newGestureState = createGestureState(
                        displayId, inputResource.gestureState, getTrackpadGestureType(event));
                inputResource.consumer.onConsumerAboutToBeSwitched();
                inputResource.gestureState = newGestureState;
                inputResource.consumer = newConsumer(
                        this,
                        mUserUnlocked,
                        mOverviewComponentObserver.get(),
                        deviceState,
                        prevGestureState,
                        inputResource.gestureState,
                        taskAnimationManager,
                        inputMonitorCompat,
                        getSwipeUpHandlerFactory(displayId),
                        this::onConsumerInactive,
                        inputEventReceiver,
                        mTaskbarManager,
                        mOverviewCommandHelper.get(),
                        event,
                        rotationTouchHelper,
                        mDesktopState,
                        mDesktopVisibilityController);
                inputResource.uncheckedConsumer = inputResource.consumer;
            } else if ((deviceState.isFullyGesturalNavMode() || isTrackpadMultiFingerSwipe(event))
                    && deviceState.canTriggerAssistantAction(event)) {
                reasonString.append(deviceState.isFullyGesturalNavMode()
                        ? "using fully gestural nav and event can trigger assistant action, "
                                + "consuming gesture for assistant action"
                        : "event is a trackpad multi-finger swipe and event can trigger assistant "
                                + "action, consuming gesture for assistant action");
                inputResource.gestureState = createGestureState(
                        displayId, inputResource.gestureState, getTrackpadGestureType(event));
                // Do not change mConsumer as if there is an ongoing QuickSwitch gesture, we
                // should not interrupt it. QuickSwitch assumes that interruption can only
                // happen if the next gesture is also quick switch.
                inputResource.uncheckedConsumer = tryCreateAssistantInputConsumer(
                        this, deviceState, inputMonitorCompat, inputResource.gestureState, event);
            } else if (deviceState.canTriggerOneHandedAction(event)) {
                reasonString.append("event can trigger one-handed action, "
                        + "consuming gesture for one-handed action");
                // Consume gesture event for triggering one handed feature.
                inputResource.uncheckedConsumer = new OneHandedModeInputConsumer(
                        this,
                        displayId,
                        deviceState,
                        InputConsumer.createNoOpInputConsumer(displayId), inputMonitorCompat);
            } else {
                inputResource.uncheckedConsumer = InputConsumer.createNoOpInputConsumer(displayId);
            }
        } else {
            // Other events
            if (inputResource.uncheckedConsumer.getType() != InputConsumer.TYPE_NO_OP) {
                // Only transform the event if we are handling it in a proper consumer
                rotationTouchHelper.setOrientationTransformIfNeeded(event);
            }
        }

        if (inputResource.uncheckedConsumer.getType() != InputConsumer.TYPE_NO_OP) {
            switch (action) {
                case ACTION_DOWN:
                    ActiveGestureProtoLogProxy.logOnInputEventActionDown(displayId, reasonString);
                    // fall through
                case ACTION_UP:
                    ActiveGestureProtoLogProxy.logOnInputEventActionUp(
                            (int) event.getRawX(),
                            (int) event.getRawY(),
                            action,
                            MotionEvent.classificationToString(event.getClassification()),
                            displayId);
                    break;
                case ACTION_MOVE:
                    ActiveGestureProtoLogProxy.logOnInputEventActionMove(
                            MotionEvent.actionToString(action),
                            MotionEvent.classificationToString(event.getClassification()),
                            event.getPointerCount(),
                            displayId);
                    break;
                default: {
                    ActiveGestureProtoLogProxy.logOnInputEventGenericAction(
                            MotionEvent.actionToString(action),
                            MotionEvent.classificationToString(event.getClassification()),
                            displayId);
                }
            }
        }

        boolean cancelGesture = inputResource.gestureState.getContainerInterface() != null
                && inputResource.gestureState.getContainerInterface()
                .shouldCancelCurrentGesture(displayId);
        boolean cleanUpConsumer = (action == ACTION_UP || action == ACTION_CANCEL || cancelGesture)
                && inputResource.consumer != null
                && !inputResource.consumer.getActiveConsumerInHierarchy()
                .isConsumerDetachedFromGesture();
        if (cancelGesture) {
            event.setAction(ACTION_CANCEL);
        }

        if (inputResource.gestureState.isTrackpadGesture() && (action == ACTION_POINTER_DOWN
                || action == ACTION_POINTER_UP)) {
            // Skip ACTION_POINTER_DOWN and ACTION_POINTER_UP events from trackpad.
        } else if (isCursorHoverEvent(event)) {
            inputResource.uncheckedConsumer.onHoverEvent(event);
        } else {
            inputResource.uncheckedConsumer.onMotionEvent(event);
        }

        if (cleanUpConsumer) {
            reset(displayId);
        }
        traceToken.close();
    }

    private boolean isHoverActionWithoutConsumer(MotionEvent event) {
        // Only process these events when taskbar is present.
        int displayId = event.getDisplayId();
        TaskbarActivityContext tac = mTaskbarManager.getTaskbarForDisplay(displayId);
        InputResource inputResource = getInputResource(displayId);
        boolean isTaskbarPresent = tac != null
                && tac.getDeviceProfile()
                .getDeviceProperties()
                .getTaskbarConfiguration()
                .isTaskbarPresent()
                && !tac.isPhoneMode();
        return event.isHoverEvent()
                && inputResource != null
                && (inputResource.uncheckedConsumer.getType() & TYPE_CURSOR_HOVER) == 0
                && isTaskbarPresent;
    }

    // Talkback generates hover events on touch, which we do not want to consume.
    private boolean isCursorHoverEvent(MotionEvent event) {
        return event.isHoverEvent() && event.getSource() == InputDevice.SOURCE_MOUSE;
    }

    public GestureState createGestureState(
            int displayId,
            GestureState previousGestureState,
            GestureState.TrackpadGestureType trackpadGestureType) {
        final GestureState gestureState;
        TopTaskTracker.CachedTaskInfo taskInfo;
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        if (taskAnimationManager != null && taskAnimationManager.isRecentsAnimationRunning()) {
            gestureState = new GestureState(
                    mOverviewComponentObserver.get(), displayId,
                    ActiveGestureLog.INSTANCE.getLogId());
            TopTaskTracker.CachedTaskInfo previousTaskInfo = previousGestureState.getRunningTask();
            // previousTaskInfo can be null iff previousGestureState == GestureState.DEFAULT_STATE
            taskInfo = previousTaskInfo != null
                    ? previousTaskInfo
                    : TopTaskTracker.INSTANCE.get(this).getCachedTopTask(false, displayId);
            gestureState.updateRunningTask(taskInfo);
            gestureState.updateLastStartedTaskIds(previousGestureState.getLastStartedTaskIds());
            gestureState.updatePreviouslyAppearedTaskIds(
                    previousGestureState.getPreviouslyAppearedTaskIds());
            gestureState.setTaskbarAlreadyOpen(previousGestureState.isTaskbarAlreadyOpen());
            gestureState.setTaskbarAllAppsOpen(previousGestureState.isTaskbarAllAppsOpen());
        } else {
            gestureState = new GestureState(
                    mOverviewComponentObserver.get(),
                    displayId,
                    ActiveGestureLog.INSTANCE.incrementLogId());
            taskInfo = TopTaskTracker.INSTANCE.get(this).getCachedTopTask(false, displayId);
            gestureState.updateRunningTask(taskInfo);
            TaskbarUiState uiState = TaskbarUiStateMonitor.INSTANCE.get(this)
                    .getTaskbarUiState(displayId);
            gestureState.setTaskbarAlreadyOpen(!uiState.isTaskbarStashed());
            gestureState.setTaskbarAllAppsOpen(uiState.isTaskbarAllAppsOpen());
        }
        gestureState.setTrackpadGestureType(trackpadGestureType);

        // Log initial state for the gesture.
        ActiveGestureProtoLogProxy.logRunningTaskPackage(taskInfo.getPackageName());
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        if (deviceState != null) {
            ActiveGestureProtoLogProxy.logSysuiStateFlags(deviceState.getSystemUiStateString());
        }
        return gestureState;
    }

    /**
     * Returns a AbsSwipeUpHandler.Factory, used to instantiate AbsSwipeUpHandler later.
     * @param displayId The displayId of the display this handler will be used on.
     */
    public AbsSwipeUpHandler.Factory getSwipeUpHandlerFactory(int displayId) {
        BaseContainerInterface<?, ?> containerInterface =
                mOverviewComponentObserver.get().getContainerInterface(displayId);
        if (containerInterface instanceof FallbackWindowInterface) {
            return mRecentsWindowSwipeHandlerFactory;
        } else if (containerInterface instanceof LauncherActivityInterface) {
            return mLauncherSwipeHandlerFactory;
        } else {
            return mFallbackSwipeHandlerFactory;
        }
    }

    /**
     * To be called by the consumer when it's no longer active. This can be called by any consumer
     * in the hierarchy at any point during the gesture (ie. if a delegate consumer starts
     * intercepting touches, the base consumer can try to call this).
     */
    private void onConsumerInactive(InputConsumer caller) {
        InputResource inputResource = getInputResource(caller.getDisplayId());
        if (inputResource != null
                && inputResource.consumer.getActiveConsumerInHierarchy() == caller) {
            reset(caller.getDisplayId());
        }
    }

    /** Resets any active input related to this display */
    @VisibleForTesting
    public void reset(int displayId) {
        InputResource inputResource = getInputResource(displayId);
        if (inputResource == null) {
            return;
        }
        inputResource.consumer = inputResource.uncheckedConsumer =
                InputConsumerUtils.getDefaultInputConsumer(
                        displayId,
                        mUserUnlocked,
                        mTaskAnimationManagerRepository.get(displayId),
                        mTaskbarManager,
                        CompoundString.NO_OP);
        inputResource.gestureState = DEFAULT_STATE;
        // By default, use batching of the input events, but check receiver before using in the rare
        // case that the monitor was disposed before the swipe settled
        inputResource.inputEventReceiver.setBatchingEnabled(true);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (!LockedUserState.get(this).isUserUnlocked()) {
            return;
        }
        // TODO (b/399094853): handle config updates for all connected displays (relevant only for
        // gestures on external displays)
        final BaseContainerInterface containerInterface =
                mOverviewComponentObserver.get().getContainerInterface(DEFAULT_DISPLAY);
        final RecentsViewContainer container = containerInterface.getCreatedContainer();
        if (container == null || container.isStarted()) {
            // We only care about the existing background activity.
            return;
        }
        Configuration oldConfig = container.asContext().getResources().getConfiguration();
        boolean isFoldUnfold = isTablet(oldConfig) != isTablet(newConfig);
        if (!isFoldUnfold && mOverviewComponentObserver.get().canHandleConfigChanges(
                container.getComponentName(),
                container.asContext().getResources().getConfiguration().diff(newConfig))) {
            // Since navBar gestural height are different between portrait and landscape,
            // can handle orientation changes and refresh navigation gestural region through
            // onOneHandedModeChanged()
            mDeviceStateRepository.forEach(/* createIfAbsent= */ true, deviceState ->
                    deviceState.setGesturalHeight(RESET_TO_DEFAULT_GESTURAL_HEIGHT));
            return;
        }

        ActivityPreloadUtil.preloadOverviewForTIS(this, false /* fromInit */);
    }

    private static boolean isTablet(Configuration config) {
        return config.smallestScreenWidthDp >= MIN_TABLET_WIDTH;
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] rawArgs) {
        // Dump everything
        if (LockedUserState.get(this).isUserUnlocked()) {
            PluginManagerWrapper.INSTANCE.get(getBaseContext()).dump(pw);
        }
        if (mOverviewComponentObserver.getIfReady() != null) {
            mOverviewComponentObserver.getIfReady().dump(pw);
        }
        if (mOverviewCommandHelper.getIfReady() != null) {
            mOverviewCommandHelper.getIfReady().dump(pw);
        }
        pw.println("Input state:");
        if (mInputResourceDisplayModel == null) {
            pw.println("\tmInputResourceDisplayModel=null");
        } else {
            mInputResourceDisplayModel.dump("\t", pw);
        }
        DisplayController.INSTANCE.get(this).dump(pw);
        mDisplayRepository.getDisplayIds().getValue().forEach(displayId -> {
            pw.println(String.format(Locale.ENGLISH, "TouchState (displayId %d):", displayId));
            RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
            if (deviceState != null) {
                deviceState.dump(pw);
            }
            BaseContainerInterface<?, ?> containerInterface =
                    mOverviewComponentObserver.getIfReady() == null ? null
                            : mOverviewComponentObserver.getIfReady().getContainerInterface(
                                    displayId);
            RecentsViewContainer createdOverviewContainer = containerInterface == null ? null :
                    containerInterface.getCreatedContainer();
            boolean resumed = containerInterface != null && containerInterface.isResumed();
            pw.println("\tcreatedOverviewActivity=" + createdOverviewContainer);
            pw.println("\tresumed=" + resumed);
            if (createdOverviewContainer != null) {
                createdOverviewContainer.getDeviceProfile().dump(this, "", pw);
            }
            TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(
                    displayId);
            if (taskAnimationManager != null) {
                taskAnimationManager.dump("\t", pw);
            }
        });
        ActiveGestureLog.INSTANCE.dump("", pw);
        RecentsModel.INSTANCE.get(this).dump("", pw);
        mTaskbarManager.dumpLogs("", pw);
        mDesktopVisibilityController.dumpLogs("", pw);
        pw.println("ContextualSearchStateManager:");
        ContextualSearchStateManager.INSTANCE.get(this).dump("\t", pw);
        mSystemUiProxy.dump(pw);
        DeviceConfigWrapper.get().dump("   ", pw);
        TopTaskTracker.INSTANCE.get(this).dump(pw);
        mAllAppsActionManager.dump(pw);
    }

    private @Nullable AbsSwipeUpHandler<?, ?, ?> createLauncherSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        int displayId = gestureState.getDisplayId();
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        if (taskAnimationManager == null || deviceState == null || rotationTouchHelper == null) {
            Log.d(TAG, "displayId " + displayId + " not valid");
            return null;
        }
        return new LauncherSwipeHandlerV2(
                /*context= */ this,
                taskAnimationManager,
                deviceState,
                rotationTouchHelper,
                gestureState,
                taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer,
                MSDLPlayerWrapper.INSTANCE.get(this),
                displayId);
    }

    private @Nullable AbsSwipeUpHandler<?, ?, ?> createFallbackSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        int displayId = gestureState.getDisplayId();
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        if (taskAnimationManager == null || deviceState == null || rotationTouchHelper == null) {
            Log.d(TAG, "displayId " + displayId + " not valid");
            return null;
        }
        return new FallbackSwipeHandler(
                /* context= */ this,
                taskAnimationManager,
                deviceState,
                rotationTouchHelper,
                gestureState,
                taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer,
                MSDLPlayerWrapper.INSTANCE.get(this),
                displayId);
    }

    private @Nullable AbsSwipeUpHandler<?, ?, ?> createRecentsWindowSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        int displayId = gestureState.getDisplayId();
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        RecentsWindowManager recentsWindowManager = mRecentsWindowManagerRepository.get(displayId);
        if (taskAnimationManager == null || deviceState == null || rotationTouchHelper == null
                || recentsWindowManager == null) {
            Log.d(TAG, "displayId " + displayId + " not valid");
            return null;
        }
        return new RecentsWindowSwipeHandler(recentsWindowManager,
                taskAnimationManager,
                deviceState,
                rotationTouchHelper,
                recentsWindowManager,
                gestureState,
                taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer,
                MSDLPlayerWrapper.INSTANCE.get(this),
                displayId);
    }

    public class InputResource implements DisplayModel.DisplayResource {

        private final int displayId;
        private @NonNull final InputMonitorCompat inputMonitorCompat;
        private @NonNull final InputEventReceiver inputEventReceiver;

        private @Nullable NavigationMode gestureStartNavMode = null;
        private @NonNull InputConsumer uncheckedConsumer = InputConsumer.DEFAULT_NO_OP;
        private @NonNull InputConsumer consumer = InputConsumer.DEFAULT_NO_OP;
        private @NonNull GestureState gestureState = DEFAULT_STATE;

        private InputResource(int displayId) {
            this.displayId = displayId;
            inputMonitorCompat = new InputMonitorCompat("swipe-up", displayId);
            inputEventReceiver = inputMonitorCompat.getInputReceiver(
                    Looper.getMainLooper(),
                    TouchInteractionHandler.this.mMainChoreographer,
                    TouchInteractionHandler.this::onInputEvent);
        }

        @Override
        public void cleanup() {
            inputEventReceiver.dispose();
            inputMonitorCompat.dispose();
        }

        @Override
        public void dump(@NonNull String prefix , PrintWriter writer) {
            writer.println(prefix + "InputMonitorResource:");

            writer.println(prefix + "\tdisplayId=" + displayId);
            writer.println(prefix + "\tinputMonitorCompat=" + inputMonitorCompat);
            writer.println(prefix + "\tinputEventReceiver=" + inputEventReceiver);
            writer.println(prefix + "\tgestureStartNavMode=" + gestureStartNavMode);
            writer.println(prefix + "\tconsumer=" + consumer.getName());
            writer.println(prefix + "\tuncheckedConsumer=" + uncheckedConsumer.getName());

            gestureState.dump(prefix + "\t", writer);
        }
    }
}
