/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;
import static com.android.launcher3.WorkspaceLayoutManager.TAG;

import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.ModelTaskController;
import com.android.launcher3.model.IModelWriter;
import com.android.launcher3.model.WorkspaceItemSpaceFinder;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemCoordinates;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ApplicationInfoWrapper;
import com.android.launcher3.util.IntSet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

/**
 * Task to add auto-created workspace items.
 */
public class AddWorkspaceItemsTask implements ModelUpdateTask {

    private static final String LOG = "AddWorkspaceItemsTask";

    @NonNull
    private final List<Supplier<ItemInfo>> mItemList;

    @NonNull
    private final WorkspaceItemSpaceFinder mItemSpaceFinder;

    /**
     * @param itemList items to add on the workspace
     * @param itemSpaceFinder inject WorkspaceItemSpaceFinder dependency for testing
     */
    public AddWorkspaceItemsTask(@NonNull final List<Supplier<ItemInfo>> itemList,
            @NonNull final WorkspaceItemSpaceFinder itemSpaceFinder) {
        mItemList = itemList;
        mItemSpaceFinder = itemSpaceFinder;
    }


    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList apps) {
        if (mItemList.isEmpty()) {
            return;
        }

        final ArrayList<ItemInfo> addedItemsFinal = new ArrayList<>();
        final IntSet excludedScreens = IntSet.wrap(FIRST_SCREEN_ID);
        final Context context = taskController.getContext();

        synchronized (dataModel) {
            IModelWriter writer = taskController.getModelWriter();
            for (Supplier<ItemInfo> itemProvider : mItemList) {
                ItemInfo item = itemProvider.get();
                if (item instanceof WorkspaceItemFactory factory) {
                    item = factory.makeWorkspaceItem(context);
                }
                if (item == null) continue;
                if (item.itemType == ITEM_TYPE_APPLICATION) {
                    var targetPackage = item.getTargetPackage();
                    if (targetPackage == null) continue;

                    var user = item.user;
                    // Short-circuit this logic if a similar icon exists somewhere on the workspace
                    if (containsAppTarget(dataModel.itemsIdMap, targetPackage, user)
                            || containsAppTarget(addedItemsFinal, targetPackage, user)) {
                        continue;
                    }

                    // b/139663018 Short-circuit this logic if the icon is a system app
                    if (new ApplicationInfoWrapper(context, targetPackage, item.user).isSystem()) {
                        continue;
                    }

                    if (item instanceof ItemInfoWithIcon iiwi && iiwi.isArchived()) {
                        continue;
                    }
                }
                if (!(item instanceof WorkspaceItemInfo
                        || item instanceof CollectionInfo
                        || item instanceof LauncherAppWidgetInfo)) {
                    FileLog.e(TAG, "Unexpected info type: " + item);
                    continue;
                }

                // Find appropriate space for the item.
                WorkspaceItemCoordinates coords = mItemSpaceFinder.findSpaceForItem(addedItemsFinal,
                        item.spanX, item.spanY, excludedScreens);

                // Save the WorkspaceItemInfo for binding in the workspace
                coords.applyTo(item);
                addedItemsFinal.add(item);

                // log bitmap and label
                FileLog.d(LOG, "Adding item info to workspace: " + item);
            }

            // Add the shortcut to the db
            writer.addItemsToDatabase(addedItemsFinal);
        }

        if (!addedItemsFinal.isEmpty()) {
            taskController.scheduleCallbackTask(cb -> cb.bindItemsAdded(addedItemsFinal));
        }
    }

    private boolean containsAppTarget(Iterable<ItemInfo> container, String pkg, UserHandle user) {
        return StreamSupport.stream(container.spliterator(), false).anyMatch(i ->
                i.itemType == ITEM_TYPE_APPLICATION
                        && user.equals(i.user) && pkg.equals(i.getTargetPackage()));
    }
}
