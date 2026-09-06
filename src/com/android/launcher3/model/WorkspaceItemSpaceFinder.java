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
package com.android.launcher3.model;

import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;

import android.util.SparseArray;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemCoordinates;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntSet;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Utility class to help find space for new workspace items.
 */
public class WorkspaceItemSpaceFinder {

    private final BgDataModel mDataModel;
    private final InvariantDeviceProfile mIDP;
    private final LauncherModel mModel;

    @Inject
    WorkspaceItemSpaceFinder(
            BgDataModel dataModel, InvariantDeviceProfile idp, LauncherModel model) {
        mDataModel = dataModel;
        mIDP = idp;
        mModel = model;
    }

    /**
     * Find a position on the screen for the given size or adds a new screen.
     *
     * @param addItemsFinal Added items that are due to be added to the database.
     * @param spanX Item size along the x-axis.
     * @param spanY Item size along the y-axis.
     * @param excludedScreens Screens to exclude from the search.
     * @return {@link WorkspaceItemCoordinates} for the item.
     */
    public WorkspaceItemCoordinates findSpaceForItem(ArrayList<ItemInfo> addItemsFinal, int spanX,
            int spanY, IntSet excludedScreens) {
        return findSpaceForItem(
                addItemsFinal, spanX, spanY, excludedScreens,
                /* startingFrom= */ new WorkspaceItemCoordinates(
                        FIRST_SCREEN_ID, /* cellX= */ 0, /* cellY= */ 0));
    }

    /**
     * Find a position on the screen for the given size or adds a new screen.
     *
     * @param addItemsFinal Added items that are due to be added to the database.
     * @param spanX Item size along the x-axis.
     * @param spanY Item size along the y-axis.
     * @param excludedScreens Screens to exclude from the search.
     * @param startingFrom Coordinates at which to begin the search.
     * @return {@link WorkspaceItemCoordinates} for the item.
     */
    public WorkspaceItemCoordinates findSpaceForItem(ArrayList<ItemInfo> addItemsFinal, int spanX,
            int spanY, IntSet excludedScreens, WorkspaceItemCoordinates startingFrom) {
        if (startingFrom.getContainer() != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            throw new RuntimeException("Only `CONTAINER_DESKTOP` is supported.");
        }

        SparseArray<ArrayList<ItemInfo>> screenItems = new SparseArray<>();
        screenItems.put(FIRST_SCREEN_ID, new ArrayList<>());

        // Use `itemsIdMap` as all the items are already loaded.
        synchronized (mDataModel) {
            for (ItemInfo info : mDataModel.itemsIdMap) {
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    ArrayList<ItemInfo> items = screenItems.get(info.screenId);
                    if (items == null) {
                        items = new ArrayList<>();
                        screenItems.put(info.screenId, items);
                    }
                    items.add(info);
                }
            }
        }

        // Add items that are due to be added to the database.
        for (ItemInfo info : addItemsFinal) {
            if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                ArrayList<ItemInfo> items = screenItems.get(info.screenId);
                if (items == null) {
                    items = new ArrayList<>();
                    screenItems.put(info.screenId, items);
                }
                items.add(info);
            }
        }

        // Find appropriate space for the item.
        int screenId = -1;
        int[] cellXY = new int[2];
        boolean found = false;

        // Fall back to the first screen/cell if `startingFrom` coordinates do not exist.
        int startingFromScreenIndex = screenItems.indexOfKey(startingFrom.getScreenId());
        int startingFromCellX = startingFrom.getCellX();
        int startingFromCellY = startingFrom.getCellY();
        if (startingFromScreenIndex < 0
                || startingFromCellX < 0
                || startingFromCellX >= mIDP.numColumns
                || startingFromCellY < 0
                || startingFromCellY >= mIDP.numRows) {
            startingFromScreenIndex = 0;
            startingFromCellX = 0;
            startingFromCellY = 0;
        }

        // NOTE: The search algorithm intentionally does *not* consider screens/cells before the
        // requested `startingFrom` coordinates. No clients exist yet which require that behavior.
        for (int screenIndex = startingFromScreenIndex;
                screenIndex < screenItems.size();
                screenIndex++) {
            screenId = screenItems.keyAt(screenIndex);
            if (!excludedScreens.contains(screenId)
                    && findNextAvailableIconSpaceInScreen(
                            screenItems.get(screenId), startingFromCellX, startingFromCellY, spanX,
                            spanY, cellXY)) {
                found = true;
                break;
            }
            startingFromCellX = 0;
            startingFromCellY = 0;
        }

        if (!found) {
            // Still no position found. Add a new screen to the end.
            screenId = mModel.getModelDbController().getNewScreenId();

            // If we still can't find an empty space, then God help us all!!!
            if (!findNextAvailableIconSpaceInScreen(
                    screenItems.get(screenId), startingFromCellX, startingFromCellY, spanX, spanY,
                    cellXY)) {
                throw new RuntimeException("Can't find space to add the item");
            }
        }

        return new WorkspaceItemCoordinates(screenId, cellXY[0], cellXY[1]);
    }

    private boolean findNextAvailableIconSpaceInScreen(
            List<ItemInfo> occupiedPos, int startingFromCellX, int startingFromCellY, int spanX,
            int spanY, int[] cellXY) {
        GridOccupancy occupied = new GridOccupancy(mIDP.numColumns, mIDP.numRows);

        // Mark cells left-to-right, top-to-bottom as occupied from [0, 0] until
        // (startingFromCellX, startingFromCellY).
        //
        // For example: [0, 0] until (2, 3)
        //
        // 0 0 0 0 0       1 1 1 1 1       1 1 1 1 1
        // 0 0 0 0 0       1 1 1 1 1       1 1 1 1 1
        // 0 0 0 0 0  -->  1 1 1 1 1  -->  1 1 1 1 1
        // 0 0 0 0 0       0 0 0 0 0       1 1 0 0 0
        // 0 0 0 0 0       0 0 0 0 0       0 0 0 0 0
        occupied.markCells(0, 0, mIDP.numColumns, startingFromCellY, true);
        occupied.markCells(0, startingFromCellY, startingFromCellX, 1, true);

        // Mark any other occupied cells.
        if (occupiedPos != null) {
            for (ItemInfo r : occupiedPos) {
                occupied.markCells(r, true);
            }
        }

        return occupied.findVacantCell(cellXY, spanX, spanY);
    }
}
