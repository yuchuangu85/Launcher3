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

import static android.content.Context.RECEIVER_EXPORTED;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.Flags.enableGrowthNudge;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING_IN_DESKTOP_MODE;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING_KEY;
import static com.android.launcher3.display.LauncherDisplayInfo.getChangeFlagsString;
import static com.android.launcher3.statehandlers.DesktopVisibilityController.INACTIVE_DESK_ID;
import static com.android.launcher3.taskbar.growth.GrowthConstants.BROADCAST_SHOW_NUDGE;
import static com.android.launcher3.taskbar.growth.GrowthConstants.GROWTH_NUDGE_PERMISSION;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Executors.getTaskbarUiThread;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;
import static com.android.launcher3.util.SimpleBroadcastReceiver.actionsFilter;
import static com.android.quickstep.dagger.SysUIConnectionComponentKt.CONNECTION_CLEANER;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAVIGATION_BAR_DISABLED;

import static java.util.Objects.requireNonNull;

import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.DesktopExperienceFlags;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.util.ToBooleanFunction;
import com.android.launcher3.ActivityInteractor;
import com.android.launcher3.AsyncAnimatorPlaybackController;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener;
import com.android.launcher3.LauncherInteractor;
import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.concurrent.annotations.TaskbarUi;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statehandlers.DesktopVisibilityController.DesktopVisibilityListener;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks;
import com.android.launcher3.taskbar.unfold.NonDestroyableScopedUnfoldTransitionProgressProvider;
import com.android.launcher3.util.ListenableStream;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.MutableListenableStream;
import com.android.launcher3.util.PostUnlockObject;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.util.ThreadSafeRunnableList;
import com.android.quickstep.AllAppsActionManager;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.DisplayModel;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.cuebar.data.repository.AmbientCueRepository;
import com.android.quickstep.dagger.SysUIConnectionSingleton;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.quickstep.util.SystemUiFlagUtils;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.RecentsViewContainerInteractor;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;
import com.android.wm.shell.shared.desktopmode.DesktopState;

import kotlin.Unit;

import kotlinx.coroutines.CoroutineDispatcher;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

/**
 * Class to manage taskbar lifecycle
 */
@SysUIConnectionSingleton
public class TaskbarManagerImpl {
    private static final String TAG = "TaskbarManager";
    private static final boolean DEBUG = false;
    private static final int TASKBAR_DESTROY_DURATION = 100;

    // TODO: b/397738606  - Remove all logs with this tag after the growth framework is integrated.
    public static final String GROWTH_FRAMEWORK_TAG = "Growth Framework";

    private static final Uri USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(
            Settings.Secure.USER_SETUP_COMPLETE);

    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor(
            Settings.Secure.NAV_BAR_KIDS_MODE);

    private final Context mBaseContext;
    private final int mPrimaryDisplayId;
    private final TaskbarNavButtonCallbacks mNavCallbacks;
    private final PostUnlockObject<InvariantDeviceProfile> mUnlockedIDP;
    private final ActivityManagerWrapper mActivityManagerWrapper;
    private final DesktopState mDesktopState;

    // TODO: Remove this during the connected displays lifecycle refactor.
    private final PerDisplayTaskbarResource mPrimaryResource;

    private final DisplayManager mDisplayManager;
    private final SystemUiProxy mSystemUiProxy;

    private final MutableListenableStream<TaskbarUIController> mPrimaryDisplayUiControllerStream =
            new MutableListenableStream<>();

    // The source for this provider is set when Launcher is available
    // We use 'non-destroyable' version here so the original provider won't be destroyed
    // as it is tied to the activity lifecycle, not the taskbar lifecycle.
    // It's destruction/creation will be managed by the activity.
    private final ScopedUnfoldTransitionProgressProvider mUnfoldProgressProvider =
            new NonDestroyableScopedUnfoldTransitionProgressProvider();

    private final DisplayModel<PerDisplayTaskbarResource> mResources;

    private @Nullable ActivityInteractor mActivityInteractor;
    private @Nullable RecentsViewContainerInteractor mRecentsViewContainerInteractor;

    private final DesktopVisibilityListener mDesktopVisibilityListener =
            new DesktopVisibilityListener() {

                @Override
                public void onListenerInitializedFromShell() {
                    getTaskbarUiThread().execute(() -> {
                        DesktopVisibilityController visibilityController =
                                DesktopVisibilityController.INSTANCE.get(mBaseContext);
                        if (getCurrentActivityContext() != null
                                && visibilityController.isInDesktopMode(mPrimaryDisplayId)) {
                            // Taskbar started in desktop mode. Until this callback is invoked,
                            // Taskbar assumes that it isn't in desktop mode, so it now needs to be
                            // recreated.
                            onActiveDeskChanged(
                                    mPrimaryDisplayId,
                                    INACTIVE_DESK_ID,
                                    visibilityController.getActiveDeskId(mPrimaryDisplayId));
                        }

                        mResources.forEach(resource -> {
                            var tac = resource.getTaskbar();
                            if (tac != null) {
                                tac.getControllers().taskbarStashController
                                        .updateFlagForDesktopModeOnCD(/* fromInit= */ false);
                            }
                        });
                    });
                }

                @Override
                public void onActiveDeskChanged(int displayId, int newActiveDesk,
                        int oldActiveDesk) {
                    getTaskbarUiThread().execute(() ->
                            onActiveDeskChangedInternal(displayId, newActiveDesk, oldActiveDesk));
                }

                private void onActiveDeskChangedInternal(int displayId, int newActiveDesk,
                        int oldActiveDesk) {
                    PerDisplayTaskbarResource resource = mResources.getDisplayResource(displayId);
                    if (resource == null) return;
                    TaskbarActivityContext taskbarActivityContext = resource.getTaskbar();
                    if (taskbarActivityContext == null) return;

                    if (newActiveDesk == INACTIVE_DESK_ID || oldActiveDesk == INACTIVE_DESK_ID) {
                        TaskbarControllers controllers = taskbarActivityContext.getControllers();
                        controllers.taskbarStashController.updateFlagForDesktopModeOnCD(
                                /* fromInit= */ false);

                        // Only Handles Special Exit Cases for Desktop Mode Taskbar Recreation.
                        if (!taskbarActivityContext.showDesktopTaskbarForFreeformDisplay()) {
                            int recreateDuration = taskbarActivityContext.getResources().getInteger(
                                    R.integer.to_desktop_animation_duration_ms);
                            AnimatorSet animatorSet = taskbarActivityContext.onDestroyAnimation(
                                    TASKBAR_DESTROY_DURATION);
                            animatorSet.addListener(AnimatorListeners.forEndCallback(
                                    () -> recreateTaskbarForDisplay(resource, recreateDuration,
                                            "onActiveDeskChanged")));
                            animatorSet.start();
                        }
                    }
                }
            };

    /** Not {@code null} if direct boot support is enabled and not {@link #mUserUnlocked} yet. */
    private @Nullable TaskbarBootAppContext mBootAppContext;

    private boolean mUserUnlocked;
    private boolean mDeviceUnlocked;

    private final AllAppsActionManager mAllAppsActionManager;
    private AmbientCueRepository mAmbientCueRepository;

    private @Nullable SafeCloseable mActivityOnDestroySafeCloseable;

    private final Runnable mActivityOnDestroyCallback = new Runnable() {
        @Override
        public void run() {
            int displayId = mPrimaryDisplayId;
            debugTaskbarManager("onActivityDestroyed:", displayId);
            if (mActivityInteractor != null) {
                displayId = mActivityInteractor.getDisplayId();
                if (mDebugActivityDeviceProfileChangedSafeCloseable != null) {
                    mDebugActivityDeviceProfileChangedSafeCloseable.close();
                    mDebugActivityDeviceProfileChangedSafeCloseable = null;
                }
                debugTaskbarManager("onActivityDestroyed: unregistering callbacks", displayId);
                removeActivityCallbacksAndListeners();
                if (mActivityInteractor.isActivitySameObj(mRecentsViewContainerInteractor)) {
                    mRecentsViewContainerInteractor = null;
                }
            }
            mActivityInteractor = null;
            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (taskbar != null) {
                debugTaskbarManager("onActivityDestroyed: setting taskbarUIController", displayId);
                setUiController(taskbar, TaskbarUIController.DEFAULT);
            } else {
                debugTaskbarManager("onActivityDestroyed: taskbar is null!", displayId);
            }
            mUnfoldProgressProvider.setSourceProvider(null);
        }
    };

    /**
     * This constructor will be called on TaskbarUI thread via TaskbarManagerImplWrapper.
     * Callers should not inject it directly, and instead inject TaskbarManager.
     */
    @SuppressLint("WrongConstant")
    @Inject
    public TaskbarManagerImpl(
            @ApplicationContext Context context,
            AllAppsActionManager allAppsActionManager,
            TaskbarNavButtonCallbacks navCallbacks,
            DisplayModel.Factory<PerDisplayTaskbarResource> displayModelFactory,
            @TaskbarUi CoroutineDispatcher dispatcher,
            DesktopVisibilityController desktopVisibilityController,
            SettingsCache settingsCache,
            LockedUserState lockedUserState,
            LauncherPrefs launcherPrefs,
            SystemUiProxy systemUiProxy,
            PostUnlockObject<InvariantDeviceProfile> unlockedIdp,
            @Named(CONNECTION_CLEANER) ThreadSafeRunnableList cleanupTasks,
            ActivityManagerWrapper activityManagerWrapper,
            DesktopState desktopState) {
        Preconditions.assertTaskbarUiThread();
        mBaseContext = context;
        mPrimaryDisplayId = mBaseContext.getDisplayId();
        mAllAppsActionManager = allAppsActionManager;
        mNavCallbacks = navCallbacks;
        mDisplayManager = mBaseContext.getSystemService(DisplayManager.class);
        mSystemUiProxy = systemUiProxy;
        mUnlockedIDP = unlockedIdp;
        mActivityManagerWrapper = activityManagerWrapper;
        mDesktopState = desktopState;

        // Only initialize this context when the user is truly locked. Thus, check unlock state
        // separately from mUserUnlocked, which starts at false until TIS calls onUserUnlocked().
        // TIS can recreate after the user is unlocked, where it notifies unlock immediately. Also,
        // avoid initializing mUserUnlocked here and instead rely on TIS, because it initializes
        // several Taskbar dependencies before notifying us.
        mUserUnlocked = lockedUserState.isUserUnlocked();
        if (!mUserUnlocked) {
            mBootAppContext = new TaskbarBootAppContext(mBaseContext);
        }

        mResources = displayModelFactory.newModel(dispatcher, this::initPerDisplayResource);
        mResources.storeDisplayResource(mPrimaryDisplayId);

        mPrimaryResource = requireNonNull(mResources.getDisplayResource(mPrimaryDisplayId));
        cleanupTasks.addCloseable(getTaskbarUiThread(), mResources);

        LauncherPrefChangeListener prefChangeListener = key -> {
            if (TASKBAR_PINNING_KEY.equals(key)) {
                getTaskbarUiThread().execute(this::recreateTaskbars);
            }
        };
        launcherPrefs.addListener(
                prefChangeListener,
                TASKBAR_PINNING,
                TASKBAR_PINNING_IN_DESKTOP_MODE);

        cleanupTasks.addCloseable(getTaskbarUiThread(), () -> launcherPrefs.removeListener(
                prefChangeListener,
                TASKBAR_PINNING, TASKBAR_PINNING_IN_DESKTOP_MODE));

        desktopVisibilityController.registerDesktopVisibilityListener(mDesktopVisibilityListener);
        cleanupTasks.addTask(getTaskbarUiThread(), () -> desktopVisibilityController
                .unregisterDesktopVisibilityListener(mDesktopVisibilityListener));

        var userSetupCompleteSafeCloseable = settingsCache.getListenableRef(USER_SETUP_COMPLETE_URI)
                .forEach(getTaskbarUiThread(),
                        v -> onSettingChanged(v, TaskbarActivityContext::isUserSetupComplete));
        cleanupTasks.addCloseable(getTaskbarUiThread(), userSetupCompleteSafeCloseable);

        var navBarKidsModeSafeCloseable = settingsCache.getListenableRef(NAV_BAR_KIDS_MODE).forEach(
                getTaskbarUiThread(),
                v -> onSettingChanged(v, TaskbarActivityContext::isInKidsMode));
        cleanupTasks.addCloseable(getTaskbarUiThread(), navBarKidsModeSafeCloseable);

        SimpleBroadcastReceiver shutdownReceiver = new SimpleBroadcastReceiver(
                mBaseContext,
                UI_HELPER_EXECUTOR,
                getTaskbarUiThread(),
                i -> destroyAllTaskbars());
        shutdownReceiver.register(actionsFilter(Intent.ACTION_SHUTDOWN));
        cleanupTasks.addCloseable(getTaskbarUiThread(), shutdownReceiver);

        if (enableGrowthNudge()) {
            // TODO: b/397739323 - Add permission to limit access to Growth Framework.
            SimpleBroadcastReceiver growthBroadcastReceiver = new SimpleBroadcastReceiver(
                    mBaseContext,
                    UI_HELPER_EXECUTOR,
                    getTaskbarUiThread(),
                    this::showGrowthNudge);
            growthBroadcastReceiver.register(
                    actionsFilter(BROADCAST_SHOW_NUDGE),
                    RECEIVER_EXPORTED,
                    GROWTH_NUDGE_PERMISSION);
            cleanupTasks.addCloseable(getTaskbarUiThread(), growthBroadcastReceiver);
        }

        mResources.initializeDisplays();

        if (!mUserUnlocked) {
            Runnable unlockTask = this::onUserUnlocked;
            lockedUserState.runOnUserUnlocked(getTaskbarUiThread(), unlockTask);
            cleanupTasks.addTask(getTaskbarUiThread(),
                    () -> lockedUserState.removeOnUserUnlockedRunnable(unlockTask));
        }

        mUnlockedIDP.whenAvailable(getTaskbarUiThread(), idp -> {
            OnIDPChangeListener changeListener = modelPropertiesChanged -> {
                // The change listener is called on main thread
                getTaskbarUiThread().execute(() -> {
                    var activityContext = getTaskbarForDisplay(mPrimaryDisplayId);
                    if (activityContext != null && activityContext.getDeviceProfile()
                            != idp.getDeviceProfile(mPrimaryResource.getWindowContext())) {
                        recreateTaskbars();
                    }
                });
            };

            idp.addOnChangeListener(changeListener);

            return () -> idp.removeOnChangeListener(changeListener);
        });
        cleanupTasks.addCloseable(getTaskbarUiThread(), mUnlockedIDP);
        mPrimaryResource.debugMsg("TaskbarManager created");

        cleanupTasks.addTask(getTaskbarUiThread(), () -> {
            mPrimaryResource.debugMsg("TaskbarManager#destroy()");
            mRecentsViewContainerInteractor = null;
            if (mBootAppContext != null) {
                mBootAppContext.onDestroy();
            }
            mBootAppContext = null;
            removeActivityCallbacksAndListeners();
        });
    }

    @VisibleForTesting
    public PerDisplayTaskbarResource getPrimaryResource() {
        return mPrimaryResource;
    }

    @Nullable
    private PerDisplayTaskbarResource initPerDisplayResource(int displayId) {
        debugTaskbarManager("createWindowContext: ", displayId);
        Display display = getDisplay(displayId);
        if (display == null) {
            debugTaskbarManager("createWindowContext: display null!", displayId);
            return null;
        }

        var isExternalDisplay = isExternalDisplay(displayId);

        if (isExternalDisplay) {
            var wm = mBaseContext.getSystemService(WindowManager.class);
            if (wm == null) {
                debugTaskbarManager("initPerDisplayResource: WindowManager is null!", displayId);
                return null;
            }

            if (!DesktopExperienceFlags.ENABLE_SYS_DECORS_CALLBACKS_VIA_WM.isTrue()
                    && !wm.shouldShowSystemDecors(displayId)) {
                debugTaskbarManager(
                        "initPerDisplayResource: shouldShowSystemDecors="
                                + wm.shouldShowSystemDecors(displayId), displayId);
                return null;
            }
        }

        int windowType = isExternalDisplay ? TYPE_NAVIGATION_BAR_PANEL : TYPE_NAVIGATION_BAR;
        debugTaskbarManager(
                "createWindowContext: windowType=" + ((windowType == TYPE_NAVIGATION_BAR)
                        ? "TYPE_NAVIGATION_BAR" : "TYPE_NAVIGATION_BAR_PANEL"), displayId);
        Context context = mBaseContext.createWindowContext(display, windowType, null);

        TaskbarNavButtonController navButtonController = new TaskbarNavButtonController(
                displayId,
                mNavCallbacks,
                mSystemUiProxy,
                new Handler(),
                new ContextualSearchInvoker(mBaseContext));

        PerDisplayTaskbarResource resource = new PerDisplayTaskbarResource(
                context,
                displayId,
                navButtonController,
                isExternalDisplay,
                this::onDisplayConfigurationChanged);

        debugTaskbarManager("initPerDisplayResource: addRecreationListener!", displayId);
        addRecreationListener(resource);

        debugTaskbarManager("initPerDisplayResource: recreateTaskbarForDisplay!", displayId);
        recreateTaskbarForDisplay(resource, 0, "onDisplayAddSystemDecorations");
        return resource;
    }

    public ListenableStream<TaskbarUIController> getPrimaryDisplayUiControllerStream() {
        return mPrimaryDisplayUiControllerStream;
    }

    private Unit onSettingChanged(boolean newValue,
            ToBooleanFunction<TaskbarActivityContext> oldValue) {
        mPrimaryResource.debugMsg("Settings changed! Recreating Taskbar!");
        mResources.forEach(resource -> {
            var activity = resource.getTaskbar();
            if (activity != null && oldValue.apply(activity) != newValue) {
                resource.debugMsg("onSettingChanged");
                recreateTaskbarForDisplay(resource, 0, "onSettingChanged");
            }
        });
        return Unit.INSTANCE;
    }

    /**
     * We should update taskbar visibility when 1) changing {@link ActivityInteractor} as it is
     * source of truth of taskbar visibility 2) when post boot animation dialog is dismissed
     * (in such case launcher will invoke this API directly).
     */
    public void updateTaskbarsVisibility() {
        mPrimaryResource.debugMsg("updateTaskbarsVisibility");
        mResources.forEach(resource -> {
            var taskbar = resource.getTaskbar();
            if (taskbar != null) {
                resource.getRootLayout().setVisibility(
                        getTaskbarVisibility(taskbar.isUserSetupComplete()));
            }
        });
    }

    private void destroyAllTaskbars() {
        mPrimaryResource.debugMsg("destroyAllTaskbars");
        mResources.forEach(resource -> {
            resource.debugMsg("destroyAllTaskbars: call destroyTaskbarForDisplay");
            resource.destroyTaskbarForDisplay();
            resource.debugMsg("destroyAllTaskbars: call removeTaskbarRootViewFromWindow");
            resource.removeTaskbarRootViewFromWindow();
        });
    }

    private void showGrowthNudge(Intent intent) {
        if (!enableGrowthNudge()) {
            return;
        }
        if (BROADCAST_SHOW_NUDGE.equals(intent.getAction())) {
            // TODO: b/397738606 - extract the details and create a nudge payload.
            Log.d(GROWTH_FRAMEWORK_TAG, "Intent received");
        }
    }

    /**
     * Shows or hides the All Apps view in the Taskbar or Launcher, based on its current
     * visibility on the System UI tracked focused display.
     */
    @VisibleForTesting
    void toggleAllAppsSearch() {
        if (!mDeviceUnlocked) return;

        TaskbarActivityContext taskbar = getTaskbarForDisplay(getFocusedDisplayId());
        if (taskbar == null) {
            // Home All Apps should be toggled from this class, because the controllers are not
            // initialized when Taskbar is disabled (i.e. TaskbarActivityContext is null).
            if (mActivityInteractor instanceof LauncherInteractor l) l.toggleAllApps(true);
        } else {
            taskbar.getControllers().uiController.toggleAllApps(true);
        }
    }

    /**
     * Retrieve the corresponding resource based on displayId.
     */
    @VisibleForTesting
    @Nullable
    public PerDisplayTaskbarResource getPerDisplayResourceForTest(int displayId) {
        return mResources.getDisplayResource(displayId);
    }

    /**
     * Displays a frame of the first Launcher reveal animation.
     *
     * This should be used to run a first Launcher reveal animation whose progress matches a swipe
     * progress.
     */
    @AnyThread
    @Nullable
    public AsyncAnimatorPlaybackController createLauncherStartFromSuwAnim(int duration) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(mPrimaryDisplayId);
        return taskbar == null
                ? null
                : new AsyncAnimatorPlaybackController(
                        getTaskbarUiThread(),
                        () -> taskbar.createLauncherStartFromSuwAnim(duration));
    }

    /**
     * @return true if we should force the fallback animation for All Set page
     */
    @AnyThread
    public boolean shouldForceAllSetFallbackAnimation() {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(mPrimaryDisplayId);
        return taskbar == null || taskbar.shouldForceAllSetFallbackAnimation();
    }

    /** Called when the user is unlocked */
    private void onUserUnlocked() {
        mPrimaryResource.debugMsg("onUserUnlocked");
        mUserUnlocked = true;
        mPrimaryResource.debugMsg("onUserUnlocked: recreating all taskbars!");

        if (mBootAppContext != null) {
            mBootAppContext.onDestroy();
        }
        mBootAppContext = null;

        // Create DPs for all connected displays if required.
        mResources.forEach(resource -> {
            addRecreationListener(resource);
            resource.debugMsg("recreateTaskbars");
            recreateTaskbarForDisplay(resource, 0, "recreateTaskbars");
        });
    }

    /**
     * Sets a {@link StatefulActivity} to act as taskbar callback
     */
    public void setActivityInteractor(@NonNull ActivityInteractor activityInteractor) {
        mPrimaryResource.debugMsg(
                "setActivityInteractor: mActivityInteractor=" + mActivityInteractor);
        if (mActivityInteractor == activityInteractor
                || activityInteractor.isActivitySameObj(mActivityInteractor)) {
            mPrimaryResource.debugMsg("setActivityInteractor: No need to set activityInteractor!");
            return;
        }
        removeActivityCallbacksAndListeners();
        mActivityInteractor = activityInteractor;
        updateTaskbarsVisibility();
        mPrimaryResource.debugMsg(
                "setActivityInteractor: registering activity lifecycle callbacks.");
        mActivityOnDestroySafeCloseable = mActivityInteractor.addEventCallback(
                EVENT_DESTROYED, mActivityOnDestroyCallback, getTaskbarUiThread());
        mUnfoldProgressProvider.setSourceProvider(
                mActivityInteractor.getUnfoldTransitionProvider());

        RecentsViewContainerInteractor recentsViewContainer =
                activityInteractor.getRecentsViewContainerInteractor();
        if (recentsViewContainer != null) {
            setRecentsViewContainerInteractor(recentsViewContainer);
        }
    }

    /**
     * Sets the current RecentsViewContainer, from which we create a TaskbarUIController.
     */
    public void setRecentsViewContainerInteractor(
            @NonNull RecentsViewContainerInteractor recentsViewContainerInteractor) {
        mPrimaryResource.debugMsg("setRecentsViewContainer");
        if (mRecentsViewContainerInteractor == recentsViewContainerInteractor) {
            return;
        }
        if (mActivityInteractor != null
                && mActivityInteractor.isActivitySameObj(mRecentsViewContainerInteractor)) {
            // When switching to RecentsWindowManager (not an Activity), the old mActivity is not
            // destroyed, nor is there a new Activity to replace it. Thus if we don't clear it here,
            // it will not get re-set properly if we return to the Activity (e.g. NexusLauncher).
            mActivityOnDestroyCallback.run();
        }
        mRecentsViewContainerInteractor = recentsViewContainerInteractor;
        TaskbarActivityContext taskbar = getCurrentActivityContext();
        if (taskbar != null) {
            setUiController(taskbar, createTaskbarUIControllerForRecentsViewContainer(
                    mRecentsViewContainerInteractor, mPrimaryDisplayId));
        }
    }

    /**
     * Sets {@link TaskbarUIController} on {@link TaskbarActivityContext} and only notify changes
     * when {@link TaskbarActivityContext} is tied to primary display.
     */
    private void setUiController(
            @NonNull TaskbarActivityContext taskbarActivityContext,
            @NonNull TaskbarUIController taskbarUIController) {
        taskbarActivityContext.setUIController(taskbarUIController);
        if (taskbarActivityContext.getDisplayId() == mPrimaryDisplayId) {
            mPrimaryDisplayUiControllerStream.dispatchValue(taskbarUIController);
        }
    }

    /** Creates a {@link TaskbarUIController} to use with non default displays. */
    private TaskbarUIController createTaskbarUIControllerForNonDefaultDisplay(int displayId) {
        debugTaskbarManager("createTaskbarUIControllerForNonDefaultDisplay", displayId);
        BaseContainerInterface<?, ?> containerInterface = OverviewComponentObserver.INSTANCE.get(
                mBaseContext).getContainerInterface(displayId);
        if (containerInterface != null) {
            RecentsViewContainer container = containerInterface.getCreatedContainer();
            if (container instanceof RecentsWindowManager) {
                return createTaskbarUIControllerForRecentsViewContainer(container, displayId);
            }
        }
        return new TaskbarUIController();
    }

    /**
     * Creates a {@link TaskbarUIController} to use while the given StatefulActivity is active.
     */
    private TaskbarUIController createTaskbarUIControllerForRecentsViewContainer(
            RecentsViewContainerInteractor interactor, int displayId) {
        debugTaskbarManager("createTaskbarUIControllerForRecentsViewContainer", displayId);
        if (!isExternalDisplay(displayId)
                && mActivityInteractor instanceof LauncherInteractor launcherInteractor) {
            // If 1P Launcher is default, always use LauncherTaskbarUIController, regardless of
            // whether the recents container is NexusLauncherActivity or RecentsWindowManager. This
            // is only applicable for primary displays. In case of foldables both displays have
            // primary display ID and only one of them is primary at a given time, the other one is
            // inactive or has limited functionality (has different display ID in that case).
            return new LauncherTaskbarUIController(launcherInteractor);
        }
        // If a 3P Launcher is default, always use FallbackTaskbarUIController regardless of
        // whether the recents container is RecentsActivity or RecentsWindowManager.
        if (interactor instanceof RecentsActivity recentsActivity) {
            return new FallbackTaskbarUIController<>(recentsActivity);
        }
        if (interactor instanceof RecentsWindowManager recentsWindowManager) {
            return new FallbackTaskbarUIController<>(recentsWindowManager);
        }
        return new TaskbarUIController();
    }

    /**
     * This method is called multiple times (ex. initial init, then when user unlocks) in which case
     * we fully want to destroy existing taskbars and create all desired new ones.
     * In other case (folding/unfolding) we don't need to remove and add window.
     */
    public synchronized void recreateTaskbars() {
        mResources.forEach(resource -> {
            resource.debugMsg("recreateTaskbars");
            recreateTaskbarForDisplay(resource, 0, "recreateTaskbars");
        });
    }

    /**
     * This method is called multiple times (ex. initial init, then when user unlocks) in which case
     * we fully want to destroy an existing taskbar for a specified display and create a new one.
     * In other case (folding/unfolding) we don't need to remove and add window.
     */
    @VisibleForTesting
    protected void recreateTaskbarForDisplay(
            PerDisplayTaskbarResource resource, int duration, String caller) {
        Preconditions.assertTaskbarUiThread();
        resource.debugMsg("recreateTaskbarForDisplay");
        String traceName = "recreateTaskbarForDisplay: caller=" + caller;
        String traceNameTruncated = traceName.substring(0, Math.min(traceName.length(), 80));
        Trace.beginSection(traceNameTruncated);
        int displayId = resource.getDisplayId();

        TaskbarActivityContext taskbar = null;
        try {
            resource.getCreateTaskbarLatencyLogger().logStart();
            resource.debugMsg("recreateTaskbarForDisplay: getting device profile");

            DeviceProfile dp;
            var mainIdp = mUnlockedIDP.getIfReady();
            if (resource.isExternalDisplay()) {
                dp = mainIdp == null ? null : mainIdp
                        .createDeviceProfileForSecondaryDisplay(resource.getWindowContext());
            } else if (mainIdp != null) {
                dp = mainIdp.getDeviceProfile(resource.getWindowContext());
            } else if (mBootAppContext != null) {
                dp = InvariantDeviceProfile.INSTANCE.get(mBootAppContext)
                        .getDeviceProfile(resource.getWindowContext());
            } else {
                dp = null;
            }

            // All Apps action is unrelated to navbar unification, so we only need to check DP.
            final boolean isLargeScreenTaskbar = dp != null
                    && dp.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent();
            mAllAppsActionManager.setTaskbarPresent(isLargeScreenTaskbar);
            resource.debugMsg("recreateTaskbarForDisplay: destroying taskbar");
            resource.destroyTaskbarForDisplay();

            boolean displayExists = getDisplay(displayId) != null;
            boolean isTaskbarEnabled = dp != null && resource.isTaskbarEnabled();
            resource.debugMsg("recreateTaskbarForDisplay: isTaskbarEnabled=" + isTaskbarEnabled
                    + " [dp != null]=" + (dp != null)
                    + " mUserUnlocked=" + mUserUnlocked
                    + " dp.isTaskbarPresent=" + (dp == null ? "null"
                    : dp.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent())
                    + " isTaskbarEnabled=" + isTaskbarEnabled
                    + " displayExists=" + displayExists);

            if (!isTaskbarEnabled || !isLargeScreenTaskbar || !displayExists) {
                mSystemUiProxy.notifyTaskbarStatus(/* visible */ false, /* stashed */ false);
                // Do not update bubble bar unless it is the primary display
                // As bubbles are only available on primary display
                if (displayId == mPrimaryDisplayId) {
                    mSystemUiProxy.setHasBubbleBar(false);
                }
                if (!isTaskbarEnabled || !displayExists) {
                    resource.debugMsg(
                            "recreateTaskbarForDisplay: exiting bc (!isTaskbarEnabled || "
                                    + "!displayExists)");
                    return;
                }
            }

            resource.debugMsg("recreateTaskbarForDisplay: creating taskbar");
            taskbar = createTaskbarActivityContext(dp, resource);
            if (taskbar == null) {
                resource.debugMsg("recreateTaskbarForDisplay: new taskbar instance is null!");
                return;
            }

            TaskbarSharedState sharedState = resource.getSharedState();
            sharedState.startTaskbarVariantIsTransient = taskbar.isTransientTaskbar();
            sharedState.allAppsVisible = sharedState.allAppsVisible && isLargeScreenTaskbar;
            Trace.beginSection("taskbar.init");
            try {
                taskbar.init(sharedState, mUserUnlocked, duration);
            } finally {
                Trace.endSection();
            }

            // Non default displays should not use LauncherTaskbarUIController as they shouldn't
            // have access to the Launcher activity.
            if (resource.isExternalDisplay()) {
                setUiController(taskbar, createTaskbarUIControllerForNonDefaultDisplay(displayId));
            } else if (mRecentsViewContainerInteractor != null) {
                setUiController(taskbar, createTaskbarUIControllerForRecentsViewContainer(
                        mRecentsViewContainerInteractor,
                        mPrimaryDisplayId));
            }

            resource.debugMsg("recreateTaskbarForDisplay: adding rootView");
            FrameLayout taskbarRootLayout = resource.getRootLayout();
            resource.debugMsg("recreateTaskbarForDisplay: adding root layout");
            taskbarRootLayout.removeAllViews();

            resource.setCurrentTaskbar(taskbar);
            taskbarRootLayout.addView(taskbar.getDragLayer());
            taskbarRootLayout.setVisibility(getTaskbarVisibility(taskbar.isUserSetupComplete()));
            taskbar.notifyUpdateLayoutParams();
        } finally {
            Trace.endSection();
            if (taskbar != null) {
                resource.getCreateTaskbarLatencyLogger().logEnd(taskbar.getStatsLogManager());
            }
        }
    }

    @VisibleForTesting
    protected void injectTestInsights() {
        mAmbientCueRepository.injectTestInsightForCueBar();
    }

    /** Called when the SysUI flags for a given display change. */
    public void onSystemUiFlagsChanged(@SystemUiStateFlags long systemUiStateFlags, int displayId) {
        if (displayId == mPrimaryDisplayId) {
            mDeviceUnlocked = !SystemUiFlagUtils.isLocked(systemUiStateFlags);
        }
        PerDisplayTaskbarResource resource = mResources.getDisplayResource(displayId);
        if (resource == null) {
            Log.d(TAG, "No taskbar resource dor display " + displayId);
            return;
        }
        TaskbarSharedState sharedState = resource.getSharedState();
        if (DEBUG) {
            Log.d(TAG, "SysUI flags changed: " + formatFlagChange(systemUiStateFlags,
                    sharedState.sysuiStateFlags, QuickStepContract::getSystemUiStateString));
        }
        long changedFlags = systemUiStateFlags ^ sharedState.sysuiStateFlags;
        sharedState.sysuiStateFlags = systemUiStateFlags;
        if ((changedFlags & SYSUI_STATE_NAVIGATION_BAR_DISABLED) != 0) {
            recreateTaskbarForDisplay(resource, 0, "onSystemUiFlagsChanged");
            return;
        }
        TaskbarActivityContext taskbar = resource.getTaskbar();
        if (taskbar != null) {
            taskbar.updateSysuiStateFlags(systemUiStateFlags, false /* fromInit */);
        }
    }

    public void onLongPressHomeEnabled(boolean assistantLongPressEnabled) {
        mResources.forEach(res -> {
            res.getSharedState().assistantLongPressEnabled = assistantLongPressEnabled;
            TaskbarActivityContext taskbar = res.getTaskbar();
            if (taskbar != null) {
                taskbar.onLongPressHomeEnabledChanged();
            }
        });
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mAllAppsActionManager.setSetupUiVisible(isVisible);
        mResources.forEach(res -> {
            res.getSharedState().setupUIVisible = isVisible;
            TaskbarActivityContext taskbar = res.getTaskbar();
            if (taskbar != null) {
                taskbar.setSetupUIVisible(isVisible);
            }
        });
    }

    /**
     * Sets wallpaper visibility for specific display.
     */
    public void setWallpaperVisible(int displayId, boolean isVisible) {
        PerDisplayTaskbarResource resource = mResources.getDisplayResource(displayId);
        if (resource == null) return;
        resource.getSharedState().wallpaperVisible = isVisible;
        TaskbarActivityContext taskbar = resource.getTaskbar();
        if (taskbar != null) {
            taskbar.setWallpaperVisible(isVisible);
        }
    }

    public void checkNavBarModes(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.checkNavBarModes();
        }
    }

    public void finishBarAnimations(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.finishBarAnimations();
        }
    }

    public void touchAutoDim(int displayId, boolean reset) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.touchAutoDim(reset);
        }
    }

    public void transitionTo(int displayId, @BarTransitions.TransitionMode int barMode,
            boolean animate) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.transitionTo(barMode, animate);
        }
    }

    public void appTransitionPending(boolean pending) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(mPrimaryDisplayId);
        if (taskbar != null) {
            taskbar.appTransitionPending(pending);
        }
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(mPrimaryDisplayId);
        if (taskbar != null) {
            taskbar.onRotationProposal(rotation, isValid);
        }
    }

    public void disableNavBarElements(int displayId, int state1, int state2, boolean animate) {
        PerDisplayTaskbarResource resource = mResources.getDisplayResource(displayId);
        if (resource == null) return;

        TaskbarSharedState sharedState = resource.getSharedState();
        sharedState.disableNavBarDisplayId = displayId;
        sharedState.disableNavBarState1 = state1;
        sharedState.disableNavBarState2 = state2;
        TaskbarActivityContext taskbar = resource.getTaskbar();
        if (taskbar != null) {
            taskbar.disableNavBarElements(displayId, state1, state2, animate);
        }
    }

    public void onSystemBarAttributesChanged(int displayId, int behavior) {
        PerDisplayTaskbarResource resource = mResources.getDisplayResource(displayId);
        if (resource == null) return;

        TaskbarSharedState sharedState = resource.getSharedState();
        sharedState.systemBarAttrsDisplayId = displayId;
        sharedState.systemBarAttrsBehavior = behavior;
        TaskbarActivityContext taskbar = resource.getTaskbar();
        if (taskbar != null) {
            taskbar.onSystemBarAttributesChanged(displayId, behavior);
        }
    }

    public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
        mResources.forEach(res -> {
            res.getSharedState().barMode = barMode;
            TaskbarActivityContext taskbar = res.getTaskbar();
            if (taskbar != null) {
                taskbar.onTransitionModeUpdated(barMode, checkBarModes);
            }
        });
    }

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        mResources.forEach(res -> {
            res.getSharedState().navButtonsDarkIntensity = darkIntensity;
            TaskbarActivityContext taskbar = res.getTaskbar();
            if (taskbar != null) {
                taskbar.onNavButtonsDarkIntensityChanged(darkIntensity);
            }
        });
    }

    public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
        mResources.forEach(res -> {
            res.getSharedState().mLumaSamplingDisplayId = displayId;
            res.getSharedState().mIsLumaSamplingEnabled = enable;
            TaskbarActivityContext taskbar = res.getTaskbar();
            if (taskbar != null) {
                taskbar.onNavigationBarLumaSamplingEnabled(displayId, enable);
            }
        });
    }

    private void removeActivityCallbacksAndListeners() {
        if (mActivityOnDestroySafeCloseable != null) {
            mActivityOnDestroySafeCloseable.close();
            mActivityOnDestroySafeCloseable = null;
        }
    }

    @AnyThread
    public @Nullable TaskbarActivityContext getCurrentActivityContext() {
        return getTaskbarForDisplay(mPrimaryDisplayId);
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarManager:");
        pw.println(prefix + "\tmUserUnlocked=" + mUserUnlocked);
        pw.println(prefix + "\tmDeviceUnlocked=" + mDeviceUnlocked);
        pw.println(prefix + "\thasBootAppContext=" + (mBootAppContext != null));

        mResources.dump(prefix, pw);
    }

    private int getTaskbarVisibility(boolean isUserSetupComplete) {
        if (!isUserSetupComplete || mActivityInteractor == null) {
            // Taskbar needs to be visible 1) during SUW flow, otherwise SUW UI will not properly
            // update after keyboard is dismissed 2) if 3p taskbar is used where mActivityInteractor
            // is not set
            return View.VISIBLE;
        } else if (mActivityInteractor instanceof LauncherInteractor launcherInteractor) {
            // If post boot dialog is visible, hide taskbar
            return launcherInteractor.isPostbootDialogVisible() ? View.INVISIBLE : View.VISIBLE;
        } else {
            // Show taskbar
            return View.VISIBLE;
        }
    }

    /**
     * Returns the {@link TaskbarUIController} associated with the given display ID.
     * TODO(b/395061396): Remove this method when overview in widow is enabled.
     *
     * @param displayId The ID of the display to retrieve the taskbar for.
     * @return The {@link TaskbarUIController} for the specified display, or
     * {@code null} if no taskbar is associated with that display.
     */
    @Nullable
    public TaskbarUIController getUIControllerForDisplay(int displayId) {
        TaskbarActivityContext taskbarActivityContext = getTaskbarForDisplay(displayId);
        if (taskbarActivityContext == null) {
            return null;
        }

        return taskbarActivityContext.getControllers().uiController;
    }

    /**
     * Returns the {@link TaskbarActivityContext} associated with the given display ID.
     *
     * @param displayId The ID of the display to retrieve the taskbar for.
     * @return The {@link TaskbarActivityContext} for the specified display, or
     * {@code null} if no taskbar is associated with that display.
     */
    @AnyThread
    @Nullable
    public TaskbarActivityContext getTaskbarForDisplay(int displayId) {
        PerDisplayTaskbarResource resource = mResources.getDisplayResource(displayId);
        return resource == null ? null : resource.getTaskbar();
    }

    /**
     * Creates a {@link TaskbarActivityContext} for the given display and adds it to the map.
     *
     * @param dp        The {@link DeviceProfile} for the display.
     * @param resource The ID of the display.
     */
    private @Nullable TaskbarActivityContext createTaskbarActivityContext(
            DeviceProfile dp, PerDisplayTaskbarResource resource) {
        Trace.beginSection("createTaskbarActivityContext");
        try {
            int displayId = resource.getDisplayId();
            Display display = getDisplay(displayId);
            if (display == null) {
                resource.debugMsg("createTaskbarActivityContext: display null");
                return null;
            }

            Context navigationBarPanelContext = mBaseContext.createWindowContext(display,
                    TYPE_NAVIGATION_BAR_PANEL, null);

            Context windowContext = resource.getWindowContext();
            if (mBootAppContext != null) {
                windowContext = mBootAppContext.wrapWindowContext(windowContext);
            }

            TaskbarActivityContext taskbarActivityContext =
                    new TaskbarActivityContext(displayId, windowContext, navigationBarPanelContext,
                            dp, resource.getNavButtonController(), mUnfoldProgressProvider,
                            !resource.isExternalDisplay(), getPrimaryDisplayId(),
                            mSystemUiProxy, mActivityManagerWrapper, mDesktopState);
            mAmbientCueRepository = taskbarActivityContext.getControllers().cueBarController
                    .getAmbientCueRepository();
            return taskbarActivityContext;
        } finally {
            Trace.endSection();
        }
    }

    private void addRecreationListener(PerDisplayTaskbarResource resource) {
        if (!mUserUnlocked) {
            return;
        }
        resource.setDisplayChangeListener(change -> recreateTaskbarForDisplay(
                resource,
                /* duration= */ 0,
                /* caller */ "onDisplayInfoChanged: " + getChangeFlagsString(change)));
    }

    private Unit onDisplayConfigurationChanged(PerDisplayTaskbarResource resource, int configDiff) {
        if (configDiff != 0 || resource.getTaskbar() == null) {
            resource.debugMsg("onConfigurationChanged: call recreateTaskbars");
            recreateTaskbarForDisplay(resource, /* duration= */ 0,
                    "onConfigChanged; configDiff / null taskbar");
        } else if (!resource.isTaskbarEnabled()) {
            // Config change might be handled without re-creating the taskbar
            resource.debugMsg("onConfigurationChanged: isTaskbarEnabled()=False | "
                    + "destroyTaskbarForDisplay");
            resource.destroyTaskbarForDisplay();
        } else {
            resource.debugMsg("onConfigurationChanged: isTaskbarEnabled()=True");
            // Re-initialize for screen size change? Should this be done
            // by looking at screen-size change flag in configDiff in the
            // block above?
            resource.debugMsg("onConfigurationChanged: call recreateTaskbars");
            recreateTaskbarForDisplay(resource, /* duration= */ 0,
                    "onConfigChanged, taskbarEnabled");
        }

        // reset taskbar was pinned value, so we don't automatically unstash taskbar upon
        // user unfolding the device.
        resource.getSharedState().setTaskbarWasPinned(false);
        return Unit.INSTANCE;
    }

    private @Nullable Display getDisplay(int displayId) {
        if (mDisplayManager == null) {
            debugTaskbarManager("cannot get DisplayManager", displayId);
            return null;
        }

        Display display = mDisplayManager.getDisplay(displayId);
        if (display == null) {
            debugTaskbarManager("Cannot get display!", displayId);
            return null;
        }

        return mDisplayManager.getDisplay(displayId);
    }

    @VisibleForTesting
    public Context getPrimaryWindowContext() {
        return mPrimaryResource.getWindowContext();
    }

    private boolean isExternalDisplay(int displayId) {
        return DesktopExperienceFlags.ENABLE_TASKBAR_CONNECTED_DISPLAYS.isTrue()
                && (mPrimaryDisplayId != displayId);
    }

    private int getFocusedDisplayId() {
        return mSystemUiProxy.getFocusState().getFocusedDisplayId();
    }

    /**
     * Returns the primary display id associated with this manager.
     */
    public int getPrimaryDisplayId() {
        return mPrimaryDisplayId;
    }

    /**
     * Logs debug information about the TaskbarManager for primary display.
     *
     * @param debugReason A string describing the reason for the debug log.
     * @param displayId   The ID of the display for which to log debug information.
     */
    public void debugTaskbarManager(String debugReason, int displayId) {
        Log.d(TAG, debugReason + " displayId=" + displayId
                + " mPrimaryDisplayId=" + mPrimaryDisplayId);
    }

    private @Nullable SafeCloseable mDebugActivityDeviceProfileChangedSafeCloseable;

    /** Use weak reference to avoid leaking TIS via {@link TaskbarManagerImpl} */
    @SysUIConnectionSingleton
    public static class AllAppsIntentSender extends IIntentSender.Stub {
        private @Nullable Provider<TaskbarManagerImpl> mTaskbarManagerProvider;
        private @Nullable WeakReference<TaskbarManagerImpl> mWeakTaskbarManager;

        @Inject
        AllAppsIntentSender(Provider<TaskbarManagerImpl> taskbarManagerProvider) {
            mTaskbarManagerProvider = taskbarManagerProvider;
        }

        @Override
        public void send(int i, Intent intent, String s, IBinder iBinder,
                IIntentReceiver iIntentReceiver, String s1, Bundle bundle) {
            getTaskbarUiThread().execute(() -> {
                TaskbarManagerImpl taskbarManager = null;
                if (mWeakTaskbarManager != null) {
                    taskbarManager = mWeakTaskbarManager.get();
                } else if (mTaskbarManagerProvider != null) {
                    taskbarManager = mTaskbarManagerProvider.get();
                    mWeakTaskbarManager = new WeakReference<>(taskbarManager);
                    mTaskbarManagerProvider = null;
                }
                if (taskbarManager == null) {
                    return;
                }
                taskbarManager.toggleAllAppsSearch();
            });
        }
    }
}
