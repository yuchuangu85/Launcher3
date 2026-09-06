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

import static com.android.launcher3.LauncherModel.useModelRepositoryBinding;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.util.Executors.getTaskbarUiThread;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.model.data.WorkspaceChangeEvent.AddEvent;
import com.android.launcher3.model.data.WorkspaceChangeEvent.FullRefresh;
import com.android.launcher3.model.data.WorkspaceChangeEvent.RemoveEvent;
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent;
import com.android.launcher3.model.data.WorkspaceData;
import com.android.launcher3.taskbar.TaskbarView.TaskbarLayoutParams;
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerLayoutParams;
import com.android.launcher3.taskbar.handoff.HandoffSuggestion;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.GroupTask;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Launcher model Callbacks for rendering taskbar.
 */
public class TaskbarModelCallbacks implements
        BgDataModel.Callbacks, LauncherBindableItemsContainer {

    private final IntSparseArrayMap<ItemInfo> mHotseatItems = new IntSparseArrayMap<>();
    private List<ItemInfo> mPredictedItems = Collections.emptyList();

    private final TaskbarActivityContext mContext;
    private final TaskbarView mContainer;

    // Initialized in init.
    protected TaskbarControllers mControllers;

    // Used to defer any UI updates during the SUW unstash animation.
    private boolean mDeferUpdatesForSUW;
    private Runnable mDeferredUpdates;

    public TaskbarModelCallbacks(
            TaskbarActivityContext context, TaskbarView container) {
        mContext = context;
        mContainer = container;
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    /** Starts listening for model repository changes to update the UI */
    @UiThread
    public void bindWorkspaceRepository() {
        if (!useModelRepositoryBinding()) return;
        Preconditions.assertTaskbarUiThread();
        var repo = LauncherComponentProvider.get(mContext).getHomeScreenRepository();
        var state = repo.getWorkspaceState();

        mContext.closeOnDestroy(state.getChanges().forEach(getTaskbarUiThread(), ev -> {
            switch (ev) {
                case AddEvent ae -> bindWorkspaceItemsAdded(ae.getItems());
                case UpdateEvent ue ->  {
                    // Items may get updated in response to drag and drop within taskbar - taskbar's
                    // view of hotseat model updates get handled directly using
                    // [updateItemsForDragAndDrop] so all updates that drop event produces get
                    // handled in one swoop.
                    if (ev.isSource(mContext)) return null;
                    bindWorkspaceItemsUpdated(ue.getItems());
                }
                case RemoveEvent re -> bindWorkspaceItemsRemoved(re.getItems());
                case FullRefresh fr -> bindCompleteModel(state.getValue());
                default -> { }
            }
            return null;
        }));
        if (state.getValue().getVersion() > 0) {
            bindCompleteModel(state.getValue());
        }
    }

    @AnyThread
    @Override
    public void bindCompleteModel(WorkspaceData itemIdMap, boolean isBindingSync) {
        if (useModelRepositoryBinding()) return;
        getTaskbarUiThread().execute(() -> bindCompleteModel(itemIdMap));
    }

    @UiThread
    private void bindCompleteModel(WorkspaceData itemIdMap) {
        Preconditions.assertTaskbarUiThread();
        mHotseatItems.clear();
        mPredictedItems = itemIdMap.getPredictedContents(CONTAINER_HOTSEAT_PREDICTION);
        handleItemsAdded(itemIdMap);

        if (itemIdMap.get(CONTAINER_ALL_APPS_PREDICTION)
                instanceof PredictedContainerInfo pci) {
            mControllers.taskbarAllAppsController.setPredictedApps(pci.getContents());
        }
        commitItemsToUI(/* forceUpdateHotseat = */ true);
    }

    @AnyThread
    @Override
    public void bindItemsAdded(List<ItemInfo> items) {
        if (useModelRepositoryBinding()) return;
        getTaskbarUiThread().execute(() -> bindWorkspaceItemsAdded(items));
    }

    @UiThread
    private void bindWorkspaceItemsAdded(List<ItemInfo> items) {
        Preconditions.assertTaskbarUiThread();
        if (handleItemsAdded(items)) {
            commitItemsToUI();
        }
    }

    private boolean handleItemsAdded(Iterable<ItemInfo> items) {
        Preconditions.assertTaskbarUiThread();
        boolean modified = false;
        for (ItemInfo item : items) {
            if (item.container == Favorites.CONTAINER_HOTSEAT) {
                mHotseatItems.put(item.screenId, item);
                modified = true;
            }
        }
        return modified;
    }

    @AnyThread
    @Override
    public void bindItemsUpdated(@NonNull Set<ItemInfo> updates) {
        if (useModelRepositoryBinding()) return;
        getTaskbarUiThread().execute(() -> bindWorkspaceItemsUpdated(updates));
    }

    /**
     * Updates the model with updates produced by an item drop event in taskbar.
     * @param updates List of item updates to be applied.
     */
    public void updateItemsForDragAndDrop(@NonNull Set<ItemInfo> updates) {
        getTaskbarUiThread().execute(() -> bindWorkspaceItemsUpdated(updates));
    }

    @UiThread
    private void bindWorkspaceItemsUpdated(@NonNull Set<ItemInfo> updates) {
        Preconditions.assertTaskbarUiThread();
        Set<ItemInfo> itemsToRebind = updateContainerItems(updates, mContext);
        boolean removed = handleItemsRemoved(ItemInfoMatcher.ofItems(itemsToRebind));
        boolean added = handleItemsAdded(itemsToRebind);

        boolean predictionsUpdated = false;
        for (ItemInfo update: updates) {
            if (update instanceof PredictedContainerInfo pci) {
                if (pci.id == Favorites.CONTAINER_HOTSEAT_PREDICTION) {
                    mPredictedItems = pci.getContents();
                    predictionsUpdated = true;
                } else if (pci.id == CONTAINER_ALL_APPS_PREDICTION) {
                    mControllers.taskbarAllAppsController.setPredictedApps(pci.getContents());
                }
            }
        }
        if (removed || added || (predictionsUpdated
                // Avoid committing for prediction updates if they are not shown.
                && !mControllers.taskbarRecentAppsController.isReplacingPredictions())) {
            commitItemsToUI();
        }
    }

    @Nullable
    @Override
    public CellInfo getCellInfoForView(@NonNull View view) {
        // This method is passed as ItemOperator to mapOverItems(), which is already run on taskbar
        // ui thread.
        Preconditions.assertTaskbarUiThread();
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (Flags.enableTaskbarIconContainer()) {
            return layoutParams instanceof TaskbarIconContainerLayoutParams tlp
                    ? tlp.getBindInfo()
                    : null;

        }
        return layoutParams instanceof TaskbarLayoutParams tlp
                ? tlp.bindInfo
                : null;
    }

    @AnyThread
    @Override
    public boolean isContainerSupported(int container) {
        return container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION;
    }

    @Override
    public View mapOverItems(@NonNull ItemOperator op) {
        // This method should only be called on mModelCallbacks.getFirstMatch from
        // TaskbarViewController
        Preconditions.assertTaskbarUiThread();
        return mContainer.mapOverItems(mContainer, op);
    }

    @AnyThread
    @Override
    public void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) {
        if (useModelRepositoryBinding()) return;
        getTaskbarUiThread().execute(() -> bindWorkspaceItemsRemoved(matcher));
    }

    @UiThread
    private void bindWorkspaceItemsRemoved(Predicate<ItemInfo> matcher) {
        Preconditions.assertTaskbarUiThread();
        if (handleItemsRemoved(matcher)) commitItemsToUI();
    }

    private boolean handleItemsRemoved(Predicate<ItemInfo> matcher) {
        Preconditions.assertTaskbarUiThread();
        boolean modified = false;
        for (int i = mHotseatItems.size() - 1; i >= 0; i--) {
            if (matcher.test(mHotseatItems.valueAt(i))) {
                modified = true;
                mHotseatItems.removeAt(i);
            }
        }
        return modified;
    }

    private void commitItemsToUI() {
        commitItemsToUI(/* forceUpdateHotseat = */ false);
    }

    private void commitItemsToUI(boolean forceUpdateHotseat) {
        Preconditions.assertTaskbarUiThread();
        int taskbarSize = mContext.getTaskbarSpecsEvaluator().getMaxPinnableCount();
        ItemInfo[] hotseatItemInfos = new ItemInfo[taskbarSize];
        int predictionSize = mPredictedItems.size();
        int predictionNextIndex = 0;

        for (int i = 0; i < hotseatItemInfos.length; i++) {
            hotseatItemInfos[i] = mHotseatItems.get(i);
            if (hotseatItemInfos[i] == null && predictionNextIndex < predictionSize) {
                hotseatItemInfos[i] = mPredictedItems.get(predictionNextIndex);
                hotseatItemInfos[i].screenId = i;
                predictionNextIndex++;
            }
        }

        final TaskbarRecentAppsController recentAppsController =
                mControllers.taskbarRecentAppsController;
        hotseatItemInfos = recentAppsController.updateHotseatItemInfos(hotseatItemInfos);

        final List<HandoffSuggestion> handoffSuggestions
            = android.companion.Flags.taskContinuity()
                ? mControllers.taskbarHandoffController.getSuggestions()
                : Collections.emptyList();

        if (mDeferUpdatesForSUW) {
            ItemInfo[] finalHotseatItemInfos = hotseatItemInfos;
            mDeferredUpdates = () ->
                    commitHotseatItemUpdates(finalHotseatItemInfos,
                            recentAppsController.getShownTasks(),
                            handoffSuggestions,
                            forceUpdateHotseat);
        } else {
            commitHotseatItemUpdates(
                    hotseatItemInfos,
                    recentAppsController.getShownTasks(),
                    handoffSuggestions,
                    forceUpdateHotseat);
        }
    }

    /**
     * Commits all updates throughout Taskbar.
     *
     * @param forceUpdateHotseat Whether to force update every hotseat icon.
     */
    private void commitHotseatItemUpdates(
            ItemInfo[] hotseatItemInfos,
            List<GroupTask> recentTasks,
            List<HandoffSuggestion> handoffSuggestions,
            boolean forceUpdateHotseat) {
        Preconditions.assertTaskbarUiThread();
        mContainer.updateItems(
                hotseatItemInfos, recentTasks, handoffSuggestions, forceUpdateHotseat);
        mControllers.taskbarViewController.updateIconViewsRunningStates();
        mControllers.taskbarPopupController.setTaskbarInfoList(mHotseatItems);
    }

    /**
     * This is used to defer UI updates after SUW builds the unstash animation.
     * @param defer if true, defers updates to the UI
     *              if false, posts updates (if any) to the UI
     */
    public void setDeferUpdatesForSUW(boolean defer) {
        // This API is only exposed to taskbar
        Preconditions.assertTaskbarUiThread();
        mDeferUpdatesForSUW = defer;

        if (!mDeferUpdatesForSUW) {
            if (mDeferredUpdates != null) {
                mContainer.post(mDeferredUpdates);
                mDeferredUpdates = null;
            }
        }
    }

    /** Called when there's a change in running apps to update the UI. */
    public void commitRunningAppsToUI() {
        // This API is only exposed to taskbar
        Preconditions.assertTaskbarUiThread();
        commitItemsToUI();
    }

    /** Called when there's a change in handoff suggestions to update the UI. */
    public void commitHandoffSuggestionsToUI() {
        // This API is only exposed to taskbar
        Preconditions.assertTaskbarUiThread();
        if (!android.companion.Flags.taskContinuity()) {
            return;
        }

        commitItemsToUI();
    }

    /** Returns the current hotseat items in Taskbar. */
    public IntSparseArrayMap<ItemInfo> getHotseatItems() {
        return mHotseatItems;
    }

    @AnyThread
    @Override
    public void bindAllApplications(AppInfo[] apps, int flags,
            Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        if (useModelRepositoryBinding()) return;
        getTaskbarUiThread().execute(() -> {
            mContext.getActivityComponent().getAppsStore().setApps(
                    apps, flags, packageUserKeytoUidMap);
            mControllers.taskbarAllAppsController.setApps(apps, flags, packageUserKeytoUidMap);
            mControllers.taskbarPopupController.setApps(apps);
        });
    }

    protected void dumpLogs(String prefix, PrintWriter pw) {
        // This API is only exposed to taskbar
        pw.println(prefix + "TaskbarModelCallbacks:");

        pw.println(String.format("%s\thotseat items count=%s", prefix, mHotseatItems.size()));
        if (mPredictedItems != null) {
            pw.println(
                    String.format("%s\tpredicted items count=%s", prefix, mPredictedItems.size()));
        }
        pw.println(String.format("%s\tmDeferUpdatesForSUW=%b", prefix, mDeferUpdatesForSUW));
        pw.println(String.format("%s\tupdates pending=%b", prefix, (mDeferredUpdates != null)));
    }
}
