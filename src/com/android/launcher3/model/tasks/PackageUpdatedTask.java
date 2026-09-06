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
package com.android.launcher3.model.tasks;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE;
import static com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;

import static java.util.Collections.emptyList;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.automation.AutomationRepository;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.ModelTaskController;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Handles model changes due to installation or update of an app */
@SuppressWarnings("NewApi")
public class PackageUpdatedTask implements ModelUpdateTask {

    private static final String TAG = "PackageUpdatedTask";

    public static final boolean OP_ADD = false;
    public static final boolean OP_UPDATE = true;

    private final boolean mIsUpdate;

    @NonNull
    private final UserHandle mUser;

    @NonNull
    private final Set<String> mPackages;

    public PackageUpdatedTask(boolean isUpdate, @NonNull final UserHandle user,
            @NonNull final String... packages) {
        mIsUpdate = isUpdate;
        mUser = user;
        mPackages = new HashSet<>(Arrays.asList(packages));
    }

    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList appsList) {
        final Context context = taskController.getContext();
        final IconCache iconCache = taskController.getIconCache();

        if (mIsUpdate) {
            // Mark disabled packages in the broadcast to be removed
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            List<String> disabledPackages = mPackages.stream()
                    .filter(pkg -> !launcherApps.isPackageEnabled(pkg, mUser)).toList();
            if (!disabledPackages.isEmpty()) {
                disabledPackages.forEach(mPackages::remove);
                PackageTaskFactory.INSTANCE.appsRemoved(mUser, new HashSet<>(disabledPackages))
                        .execute(taskController, dataModel, appsList);
            }
        }

        final HashMap<String, List<LauncherActivityInfo>> activitiesLists = new HashMap<>();
        for (String packageName : mPackages) {
            iconCache.updateIconsForPkg(packageName, mUser);
            activitiesLists.put(packageName, appsList.updatePackage(context, packageName, mUser));
        }

        taskController.bindApplicationsIfNeeded();

        final IntSet removedShortcuts = new IntSet();
        // Shortcuts to keep even if the corresponding app was removed
        final IntSet forceKeepShortcuts = new IntSet();

        // Update shortcut infos
        List<ItemInfo> updatedItems = dataModel.updateAndCollectWorkspaceItemInfos(
                mUser, itemInfo -> {
                    ComponentName cn = itemInfo.getTargetComponent();
                    if (cn == null) return false;
                    String packageName = cn.getPackageName();
                    if (!mPackages.contains(packageName)) return false;

                    if (itemInfo.itemType != ITEM_TYPE_APPLICATION
                            && itemInfo.itemType != ITEM_TYPE_DEEP_SHORTCUT) {
                        FileLog.d(TAG, "Ignoring unknown item-type: " + itemInfo);
                        // Should it be deleted?
                        return false;
                    }

                    if (itemInfo.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
                        // Deep shortcuts are handled via shortcut update task. Just handle promise
                        // shortcuts for now as they may not be registered in the system yet.
                        if (!itemInfo.isPromise()) return false;

                        // Avoid race condition where shortcut service has no record of
                        // unarchived shortcut being pinned after restore.
                        // Launcher should be source-of-truth for if shortcut is pinned.
                        List<ShortcutInfo> shortcut =
                                new ShortcutRequest(context, mUser)
                                        .forPackage(packageName, itemInfo.getDeepShortcutId())
                                        .query(ShortcutRequest.ALL);
                        if (!shortcut.isEmpty()) {
                            // Restore the shortcut and notify update
                            itemInfo.updateFromDeepShortcutInfo(shortcut.get(0), context);
                            itemInfo.status = WorkspaceItemInfo.DEFAULT;
                            itemInfo.runtimeStatusFlags &= ~FLAG_DISABLED_NOT_AVAILABLE;
                            taskController.getModelWriter().updateItemInDatabase(itemInfo);
                            return true;
                        } else if (!itemInfo.isArchived()) {
                            FileLog.e(TAG, "Removing unrestored shortcut: " + itemInfo);
                            removedShortcuts.add(itemInfo.id);
                        }
                        return false;
                    }

                    if (itemInfo.hasStatusFlag(WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI)) {
                        forceKeepShortcuts.add(itemInfo.id);
                    }

                    List<LauncherActivityInfo> activityList =
                            activitiesLists.getOrDefault(packageName, emptyList());

                    LauncherActivityInfo activityInfo = activityList.stream()
                            .filter(it -> it.getComponentName().equals(cn))
                            .findFirst()
                            .orElse(null);

                    if (activityInfo == null) {
                        if (!activityList.isEmpty()) {
                            // First activity is considered the default activity,
                            // similar to PackageManagerHelper.getAppLaunchInfo
                            activityInfo = activityList.get(0);
                            itemInfo.intent = AppInfo.makeLaunchIntent(activityInfo);
                        } else {
                            FileLog.e(TAG, "Removing shortcut with invalid target component."
                                    + itemInfo);
                            removedShortcuts.add(itemInfo.id);
                            return false;
                        }
                    }

                    // Restore if it's a promise icon
                    itemInfo.status = WorkspaceItemInfo.DEFAULT;
                    itemInfo.runtimeStatusFlags &= ~FLAG_DISABLED_NOT_AVAILABLE;
                    AppInfo.updateRuntimeFlagsForActivityTarget(
                            itemInfo, activityInfo,
                            UserCache.INSTANCE.get(context).getUserInfo(mUser),
                            ApiWrapper.INSTANCE.get(context),
                            PackageManagerHelper.INSTANCE.get(context),
                            AutomationRepository.INSTANCE.get(context)
                    );
                    iconCache.getTitleAndIcon(itemInfo, itemInfo.getMatchingLookupFlag());

                    if (itemInfo.id != ItemInfo.NO_ID) {
                        taskController.getModelWriter().updateItemInDatabase(itemInfo);
                    }
                    return true;
                }, widget -> {
                    if (widget.hasRestoreFlag(FLAG_PROVIDER_NOT_READY)
                            && mPackages.contains(widget.providerName.getPackageName())) {
                        widget.restoreStatus &=
                                ~FLAG_PROVIDER_NOT_READY
                                        & ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;

                        // adding this flag ensures that launcher shows 'click to setup'
                        // if the widget has a config activity. In case there is no config
                        // activity, it will be marked as 'restored' during bind.
                        widget.restoreStatus |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                        widget.installProgress = 100;
                        taskController.getModelWriter().updateItemInDatabase(widget);
                        return true;
                    }
                    return false;
                });

        taskController.bindUpdatedWorkspaceItems(updatedItems);
        if (!removedShortcuts.isEmpty()) {
            taskController.deleteAndBindComponentsRemoved(
                    ItemInfoMatcher.ofItemIds(removedShortcuts),
                    "removing shortcuts with invalid target components."
                            + " ids=" + removedShortcuts);
        }

        if (!mIsUpdate) {
            // Load widgets for the new package. Changes due to app updates are handled through
            // AppWidgetHost events, this is just to initialize the long-press options.
            for (String packageName : mPackages) {
                dataModel.widgetsModel.update(new PackageUserKey(packageName, mUser));
            }
            taskController.bindUpdatedWidgets(dataModel);
        }
    }
}
