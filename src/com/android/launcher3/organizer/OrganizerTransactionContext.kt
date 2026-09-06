/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.organizer

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.repository.HomeScreenRepository
import com.android.launcher3.util.IntSet
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.ArrayList

/** A specialized [TransactionContext] for organizer. */
class OrganizerTransactionContext
@AssistedInject
constructor(
    @Assisted private val delegate: TransactionContext,
    private val bgDataModel: BgDataModel,
    private val spaceFinder: WorkspaceItemSpaceFinder,
    private val homeScreenRepository: HomeScreenRepository,
) : TransactionContext by delegate {

    @AssistedFactory
    interface Factory {
        fun create(delegate: TransactionContext): OrganizerTransactionContext
    }

    /**
     * Adds a new screen to the workspace containing the provided [items].
     *
     * This method automatically determines the next available screen ID and places all items on
     * that screen within the desktop container.
     */
    fun addScreen(items: List<ItemInfo>) {
        val screens = bgDataModel.itemsIdMap.collectWorkspaceScreens()
        val maxScreenId = (0 until screens.size()).maxOfOrNull { screens.get(it) } ?: -1
        val nextScreenId = maxScreenId + 1

        for (item in items) {
            item.container = Favorites.CONTAINER_DESKTOP
            item.screenId = nextScreenId

            addItemToDatabase(item)

            if (item is FolderInfo) {
                for (folderItem in item.getContents()) {
                    folderItem.container = item.id
                    addItemToDatabase(folderItem)
                }
            }
        }
    }

    /**
     * Adds multiple folders to the workspace, automatically finding available space for each to
     * avoid collisions.
     */
    fun addFolders(folders: List<FolderInfo>) {
        val placedItems = ArrayList<ItemInfo>()
        for (folder in folders) {
            addFolder(folder, placedItems)
        }
    }

    /** Adds a single folder to the workspace, finding available space for it. */
    private fun addFolder(folder: FolderInfo, placedItems: ArrayList<ItemInfo>) {
        val coords = spaceFinder.findSpaceForItem(placedItems, folder.spanX, folder.spanY, IntSet())
        folder.container = Favorites.CONTAINER_DESKTOP
        folder.screenId = coords.screenId
        folder.cellX = coords.cellX
        folder.cellY = coords.cellY

        addItemToDatabase(folder)
        placedItems.add(folder)

        for (item in folder.getContents()) {
            item.container = folder.id
            addItemToDatabase(item)
        }
    }

    /**
     * Moves a screen to a new index.
     *
     * @param currentIndex The current index of the screen in the [orderedScreenIds] list.
     * @param targetIndex The new index for the screen.
     * @param orderedScreenIds The current list of screen IDs in their original order.
     */
    fun moveScreen(currentIndex: Int, targetIndex: Int, orderedScreenIds: List<Int>) {
        if (
            currentIndex == targetIndex ||
                currentIndex !in orderedScreenIds.indices ||
                targetIndex !in orderedScreenIds.indices
        )
            return

        val itemsInDesktop =
            homeScreenRepository.workspaceState.value.filter {
                it.container == Favorites.CONTAINER_DESKTOP
            }

        val updatedItems = mutableListOf<ItemInfo>()

        for (item in itemsInDesktop) {
            val itemScreenId = item.screenId
            val i = orderedScreenIds.indexOf(itemScreenId)
            if (i == -1) continue

            if (i == currentIndex) {
                item.screenId = orderedScreenIds[targetIndex]
                updatedItems.add(item)
            } else if (currentIndex < targetIndex && i in (currentIndex + 1)..targetIndex) {
                item.screenId = orderedScreenIds[i - 1]
                updatedItems.add(item)
            } else if (currentIndex > targetIndex && i in targetIndex until currentIndex) {
                item.screenId = orderedScreenIds[i + 1]
                updatedItems.add(item)
            }
        }

        if (updatedItems.isNotEmpty()) {
            this.moveItemsInDatabase(updatedItems, Favorites.CONTAINER_DESKTOP, -1)
        }
    }

    /**
     * Deletes a screen from the database.
     *
     * @param screenId The ID of the screen to delete.
     */
    fun deleteScreen(screenId: Int) {
        val itemsToDelete =
            homeScreenRepository.workspaceState.value.filter {
                it.container == Favorites.CONTAINER_DESKTOP && it.screenId == screenId
            }
        this.deleteItemsFromDatabase(itemsToDelete, "Screen deleted")

        val itemsToShift =
            homeScreenRepository.workspaceState.value.filter {
                it.container == Favorites.CONTAINER_DESKTOP && it.screenId > screenId
            }
        for (item in itemsToShift) {
            item.screenId -= 1
        }
        if (itemsToShift.isNotEmpty()) {
            this.moveItemsInDatabase(itemsToShift, Favorites.CONTAINER_DESKTOP, -1)
        }
    }
}
