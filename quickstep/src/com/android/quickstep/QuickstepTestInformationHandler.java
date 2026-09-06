package com.android.quickstep;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.LauncherPrefs.SELECT_TIP_SEEN;
import static com.android.launcher3.desktop.DesktopStateProvider.getDesktopState;
import static com.android.launcher3.taskbar.TaskbarThresholdUtils.getFromNavThreshold;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_INFO_DISPLAY_ID;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.getTaskbarUiThread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.testing.TestInformationHandler;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.dagger.SysUIConnectionComponent;
import com.android.quickstep.sysuiconnection.SysUIConnectionTracker;
import com.android.quickstep.util.ActiveTrackpadList;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.bubbles.DeviceConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;

public class QuickstepTestInformationHandler extends TestInformationHandler {

    private final Context mContext;
    private final RecentsModel mRecentsModel;
    private final SystemUiProxy mSystemUiProxy;
    private final OverviewComponentObserver mOverviewComponentObserver;
    private final DesktopVisibilityController mDesktopVisibilityController;
    private final ActiveTrackpadList mActiveTrackpadList;
    private final SysUIConnectionTracker mSysUIConnectionTracker;
    private final PerDisplayRepository<Context> mDisplayContextRepository;
    private final Map<Integer, DeviceProfile> mDisplayDeviceProfile = new HashMap();

    @Inject
    public QuickstepTestInformationHandler(@ApplicationContext Context context,
            RecentsModel recentsModel,
            SystemUiProxy systemUiProxy,
            OverviewComponentObserver overviewComponentObserver,
            DesktopVisibilityController desktopVisibilityController,
            ActiveTrackpadList activeTrackpadList,
            SysUIConnectionTracker sysUIConnectionTracker,
            PerDisplayRepository<Context> displayContextRepository) {
        mContext = context;
        mRecentsModel = recentsModel;
        mSystemUiProxy = systemUiProxy;
        mOverviewComponentObserver = overviewComponentObserver;
        mDesktopVisibilityController = desktopVisibilityController;
        mActiveTrackpadList = activeTrackpadList;
        mSysUIConnectionTracker = sysUIConnectionTracker;
        mDisplayContextRepository = displayContextRepository;
    }

    @SuppressLint("VisibleForTests")
    @Override
    public Bundle call(String method, String arg, @Nullable Bundle extras) {
        final Bundle response = new Bundle();
        final int displayId = getDisplayIdForRequest(extras);

        switch (method) {
            case TestProtocol.REQUEST_RECENT_TASKS_LIST: {
                ArrayList<String> taskBaseIntentComponents = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(1);
                mRecentsModel.getTasks((taskGroups) -> {
                    for (GroupTask group : taskGroups) {
                        for (Task t : group.getTasks()) {
                            taskBaseIntentComponents.add(
                                    t.key.baseIntent.getComponent().flattenToString());
                        }
                    }
                    latch.countDown();
                });
                try {
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                response.putStringArrayList(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        taskBaseIntentComponents);
                return response;
            }

            case TestProtocol.REQUEST_SWIPE_TO_OVERVIEW_HEIGHT: {
                final float swipeHeight =
                        getDeviceProfile(displayId).getDeviceProperties().getHeightPx() / 2f;
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                return response;
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_TASK_SIZE: {
                return getUIProperty(Bundle::putParcelable,
                        recentsViewContainer ->
                                recentsViewContainer.<RecentsView<?, ?>>getOverviewPanel()
                                        .getLastComputedTaskSize(),
                        this::getRecentsViewContainer);
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_GRID_TASK_SIZE: {
                return getUIProperty(Bundle::putParcelable,
                        recentsViewContainer ->
                                recentsViewContainer.<RecentsView<?, ?>>getOverviewPanel()
                                        .getLastComputedGridTaskSize(),
                        this::getRecentsViewContainer);
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_PAGE_SPACING: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        getDeviceProfile(displayId).getOverviewProfile().getPageSpacing());
                return response;
            }

            case TestProtocol.REQUEST_GET_BUBBLE_BAR_DROP_TARGET_SIZE: {
                int dimenResId = DeviceConfig.isSmallTablet(mContext)
                        ? R.dimen.drag_zone_bubble_fold : R.dimen.drag_zone_bubble_tablet;
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mContext.getResources().getDimensionPixelSize(dimenResId));
                return response;
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_CURRENT_PAGE_INDEX: {
                return getLauncherUIProperty(Bundle::putInt,
                        launcher -> launcher.<RecentsView>getOverviewPanel().getCurrentPage());
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_FIRST_TASKVIEW_INDEX: {
                return getLauncherUIProperty(Bundle::putInt,
                        launcher ->
                                launcher.<RecentsView<?, ?>>getOverviewPanel()
                                        .getFirstTaskViewIndex());
            }

            case TestProtocol.REQUEST_HAS_TIS: {
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, true);
                return response;
            }

            case TestProtocol.REQUEST_UNSTASH_TASKBAR_IF_STASHED:
                return getTaskbarProperty(
                        Bundle::putBoolean, TaskbarManager::unstashTaskbarIfStashed);

            case TestProtocol.REQUEST_COLLAPSE_BUBBLE_BAR:
                runOnTaskbar(TaskbarManager::removeAllBubbles);
                return response;

            case TestProtocol.REQUEST_TASKBAR_FROM_NAV_THRESHOLD: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        getFromNavThreshold(resources, getDeviceProfile(displayId)));
                return response;
            }

            case TestProtocol.REQUEST_STASHED_TASKBAR_SCALE: {
                return getTaskbarProperty(Bundle::putFloat, TaskbarManager::getStashedTaskbarScale);
            }

            case TestProtocol.REQUEST_TASKBAR_ALL_APPS_TOP_PADDING: {
                return getCurrentTaskbarActivityContextProperty(Bundle::putInt,
                        TaskbarActivityContext::getTaskbarAllAppsTopPadding);
            }

            case TestProtocol.REQUEST_TASKBAR_APPS_LIST_SCROLL_Y: {
                return getCurrentTaskbarActivityContextProperty(
                        Bundle::putInt,
                        TaskbarActivityContext::getTaskbarAllAppsScroll);
            }

            case TestProtocol.REQUEST_LIMIT_MAX_TASKBAR_ICON_NUMBER: {
                runOnTaskbar(t -> t.limitMaxTaskbarIconsNum(Integer.parseInt(arg)));
                return response;
            }

            case TestProtocol.REQUEST_ENABLE_BLOCK_TIMEOUT:
                runOnTaskbar(t -> t.enableBlockingTimeoutDuringTests(true));
                return response;

            case TestProtocol.REQUEST_DISABLE_BLOCK_TIMEOUT:
                runOnTaskbar(t -> t.enableBlockingTimeoutDuringTests(false));
                return response;

            case TestProtocol.REQUEST_ENABLE_TRANSIENT_TASKBAR:
                enableTransientTaskbar(true);
                return response;

            case TestProtocol.REQUEST_DISABLE_TRANSIENT_TASKBAR:
                enableTransientTaskbar(false);
                return response;

            case TestProtocol.REQUEST_SHELL_DRAG_READY:
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mSystemUiProxy.isDragAndDropReady());
                return response;

            case TestProtocol.REQUEST_REFRESH_OVERVIEW_TARGET:
                runOnSysUIConnection(MAIN_EXECUTOR, c -> {
                    c.getAllAppsActionManager().onDestroy();
                    mOverviewComponentObserver.dispatchOverviewState();

                    var launcher = Launcher.ACTIVITY_TRACKER.getCreatedContext();
                    if (launcher != null) c.getTaskbarManager().setActivity(launcher);
                    waitForTaskbarUiThreadSync();
                });
                return response;
            case TestProtocol.INJECT_TEST_INSIGHTS:
                runOnTaskbar(TaskbarManager::injectTestInsights);
                return response;
            case TestProtocol.REQUEST_RECREATE_TASKBAR:
                runOnTaskbar(TaskbarManager::recreateTaskbars);
                return response;
            case TestProtocol.REQUEST_TASKBAR_IME_DOCKED:
                return getCurrentTaskbarActivityContextProperty(
                        Bundle::putBoolean, TaskbarActivityContext::isImeDocked);
            case TestProtocol.REQUEST_UNSTASH_BUBBLE_BAR_IF_STASHED:
                runOnTaskbar(TaskbarManager::unstashBubbleBarIfStashed);
                return response;
            case TestProtocol.REQUEST_REMOVE_ALL_BUBBLES:
                runOnTaskbar(TaskbarManager::removeAllSystemUiBubbles);
                return response;
            case TestProtocol.REQUEST_INJECT_FAKE_TRACKPAD:
                runOnSysUIConnection(MAIN_EXECUTOR, c -> {
                    mActiveTrackpadList.addInputDeviceUnchecked(1000);
                    c.getTouchInteractionHandler().initInputMonitor("tapl testing");
                });
                return response;
            case TestProtocol.REQUEST_EJECT_FAKE_TRACKPAD:
                runOnSysUIConnection(MAIN_EXECUTOR, c -> {
                    mActiveTrackpadList.onInputDeviceRemoved(1000);
                    c.getTouchInteractionHandler().initInputMonitor("tapl testing");
                });
                return response;
            case TestProtocol.REQUEST_DISMISS_MAGNETIC_DETACH_THRESHOLD: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(R.dimen.task_dismiss_detach_threshold));
                return response;
            }

            case TestProtocol.REQUEST_TASKBAR_ACTION_CORNER_PADDING: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(
                                R.dimen.transient_taskbar_action_corner_padding));
                return response;
            }
            case TestProtocol.REQUEST_TASKBAR_UNSTASHED_INPUT_AREA: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(
                                R.dimen.taskbar_unstash_input_area));
                return response;
            }
            case TestProtocol.REQUEST_IS_TRANSIENT_TASKBAR:
                return getTaskbarProperty(Bundle::putBoolean, t -> t.isTransient(displayId));
            case TestProtocol.REQUEST_FLAG_IS_DESKTOP_MODE_SUPPORTED: {
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        getDesktopState(mContext).isDesktopModeSupportedOnDisplay(
                                Integer.parseInt(arg)));
                return response;
            }
            case TestProtocol.REQUEST_GET_ACTIVE_DESK_ID: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mDesktopVisibilityController.getActiveDeskId(Integer.parseInt(arg)));
                return response;
            }
            case TestProtocol.REQUEST_GET_DESK_ID: {
                final Rect taskBounds = extras.getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
                return getUIProperty(Bundle::putInt,
                        recentsViewContainer -> {
                            RecentsView recentsView = recentsViewContainer.getOverviewPanel();
                            for (TaskView taskView : recentsView.getTaskViews()) {
                                Rect bounds = new Rect();
                                taskView.getGlobalVisibleRect(bounds);
                                if (bounds.equals(taskBounds)
                                        && taskView instanceof DesktopTaskView) {
                                    return ((DesktopTaskView) taskView).getDeskId();
                                }
                            }
                            return -1;
                        },
                        this::getRecentsViewContainer);
            }
            case TestProtocol.REQUEST_GET_DESK_COUNT: {
                return getUIProperty(Bundle::putInt,
                        recentsViewContainer -> {
                            final RecentsView recentsView = recentsViewContainer.getOverviewPanel();
                            return recentsView.getDesktopTaskViewCount();
                        },
                        this::getRecentsViewContainer);
            }
            case TestProtocol.REQUEST_MARK_OVERVIEW_SELECT_TIP_SEEN: {
                LauncherPrefs.get(mContext).put(SELECT_TIP_SEEN, true);
                return response;
            }
            case TestProtocol.REQUEST_DISPLAY_BOUNDS: {
                Rect bounds = mDisplayContextRepository.get(getDisplayIdForRequest(extras))
                        .getSystemService(WindowManager.class)
                        .getMaximumWindowMetrics().getBounds();
                response.putParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        new Point(bounds.width(), bounds.height()));
                return response;
            }
        }

        return super.call(method, arg, extras);
    }

    @Override
    protected WindowInsets getWindowInsets() {
        RecentsViewContainer container = getRecentsViewContainer();
        WindowInsets insets = container == null || container.getRootView() == null
                ? null : container.getRootView().getRootWindowInsets();
        return insets == null ? super.getWindowInsets() : insets;
    }

    @Nullable
    private RecentsViewContainer getRecentsViewContainer() {
        // TODO (b/400647896): support per-display container in e2e tests
        BaseContainerInterface<?, ?> containerInterface =
                mOverviewComponentObserver.getContainerInterface(DEFAULT_DISPLAY);
        if (containerInterface != null) {
            return containerInterface.getCreatedContainer();
        } else {
            return null;
        }
    }

    @Override
    protected boolean isLauncherInitialized() {
        return super.isLauncherInitialized() && mSystemUiProxy.isActive();
    }

    private void enableTransientTaskbar(boolean enable) {
        LauncherPrefs.get(mContext).put(LauncherPrefs.TASKBAR_PINNING, !enable);
    }

    /**
     * Runs the given command on the provided executor, after ensuring that the sysui connection
     * is set up
     */
    private void runOnSysUIConnection(
            Executor executor, Consumer<SysUIConnectionComponent> callback) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try (var ignored = mSysUIConnectionTracker.getActiveComponent()
                .forEach(executor, component -> {
                    if (component != null && countDownLatch.getCount() > 0) {
                        callback.accept(component);
                        countDownLatch.countDown();
                    }
                    return null;
                })) {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs the given command on Taskbar UI thread, after ensuring TaskbarManager is created
     */
    private void runOnTaskbar(Consumer<TaskbarManager> callback) {
        runOnSysUIConnection(
                getTaskbarUiThread(), c -> callback.accept(c.getTaskbarManager()));
    }

    private <T> Bundle getCurrentTaskbarActivityContextProperty(
            BundleSetter<T> bundleSetter, Function<TaskbarActivityContext, T> provider) {
        return getTaskbarProperty(bundleSetter,
                t -> t.getFromImplSync(impl -> provider.apply(impl.getCurrentActivityContext())));
    }

    private <T> Bundle getTaskbarProperty(
            BundleSetter<T> bundleSetter, Function<TaskbarManager, T> provider) {
        Bundle response = new Bundle();
        runOnTaskbar(taskbarManager -> bundleSetter.set(
                response,
                TestProtocol.TEST_INFO_RESPONSE_FIELD,
                provider.apply(taskbarManager)));
        return response;
    }

    private void waitForTaskbarUiThreadSync() {
        try {
            getTaskbarUiThread().submit(() -> null).get();
        } catch (Exception ignored) { }
    }

    @Override
    protected DeviceProfile getDeviceProfile(int displayId) {
        if (displayId == DEFAULT_DISPLAY) {
            return getDeviceProfile();
        } else {
            return mDisplayDeviceProfile.computeIfAbsent(displayId,
                    id -> InvariantDeviceProfile
                            .INSTANCE
                            .get(mContext)
                            .createDeviceProfileForSecondaryDisplay(
                                    mDisplayContextRepository.get(id)
                            ));
        }
    }

    private int getDisplayIdForRequest(@Nullable Bundle extras) {
        if (extras == null || !extras.containsKey(REQUEST_INFO_DISPLAY_ID)) {
            return DEFAULT_DISPLAY;
        } else {
            return extras.getInt(REQUEST_INFO_DISPLAY_ID);
        }
    }
}
