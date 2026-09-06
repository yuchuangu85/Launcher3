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

package com.android.launcher3.homescreenfiles

import android.content.Context
import android.net.Uri
import android.os.UserHandle
import androidx.annotation.VisibleForTesting
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.Workspace
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.icons.IconCache
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_COUNT
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_FILE_SYSTEM_NOT_READY
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntSet
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.CompletableFuture

/**
 * Represents an update to file items shown on the home screen.
 *
 * If this update is a delayed initialization of all file items, any files absent from the update
 * will be removed from the workspace. Additionally, any file items which have already been
 * initialized as indicated by the absence of the [FLAG_DISABLED_FILE_SYSTEM_NOT_READY] runtime
 * status flag are ignored.
 *
 * @param filesByUri The collection of file items that were updated.
 * @param user The user for which file items were updated.
 * @param extras Used to apply special behaviors/properties when updating the launcher model.
 */
data class HomeScreenFilesUpdate(
    val filesByUri: CompletableFuture<Map<Uri, HomeScreenFile?>>,
    val user: UserHandle,
    val extras: Extras,
) {
    /**
     * Used to apply special behaviors/properties when updating the launcher model.
     *
     * @param findSpaceStartingFrom Coords passed to [WorkspaceItemSpaceFinder] when adding items.
     * @param isDelayedInit Whether this update is a delayed initialization of all file items.
     */
    data class Extras
    private constructor(
        val findSpaceStartingFrom: WorkspaceItemCoordinates,
        val isDelayedInit: Boolean,
    ) {
        companion object {
            @JvmStatic fun builder() = Builder()
        }

        private constructor(
            builder: Builder
        ) : this(builder.findSpaceStartingFrom, builder.isDelayedInit)

        // NOTE: We use the builder pattern because [Extras] are predominantly created from Java
        // which does not benefit from kotlin's support for default arguments. Using @JvmOverloads
        // would be onerous as the number of possible extras continues to grow.
        // TODO(b/449912243): Create extra for forcing page change animation when adding new items.
        class Builder {
            var findSpaceStartingFrom: WorkspaceItemCoordinates =
                WorkspaceItemCoordinates(screenId = Workspace.FIRST_SCREEN_ID, cellX = 0, cellY = 0)
                private set

            var isDelayedInit: Boolean = false
                private set

            fun build() = Extras(this)

            fun isDelayedInit(v: Boolean) = apply { isDelayedInit = v }

            fun findSpaceStartingFrom(v: WorkspaceItemCoordinates) = apply {
                findSpaceStartingFrom = v
            }
        }
    }
}

/**
 * A task which processes updates to file items shown on the home screen.
 *
 * @param context The application context used for stats logging.
 * @param iconCache The cache used to resolve file item icons.
 * @param idp The device profile used to conditionally reserve space for the search container.
 * @param update The update to file items to be processed.
 * @param workspaceItemSpaceFinder The finder used to place any newly created file items.
 * @param statsLogManagerFactory The manager factory used for stats logging.
 */
class HomeScreenFilesUpdateTask
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val iconCache: IconCache,
    private val idp: InvariantDeviceProfile,
    @Assisted @get:VisibleForTesting val update: HomeScreenFilesUpdate,
    private val workspaceItemSpaceFinder: WorkspaceItemSpaceFinder,
    private val statsLogManagerFactory: StatsLogManager.StatsLogManagerFactory,
) : LauncherModel.ModelUpdateTask {
    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val filesByUri = update.filesByUri.get()
        val addedItems = filesByUri.values.filterNotNull().distinct().toMutableList()
        val deletedItems = mutableSetOf<Int>()
        val isDelayedInit = update.extras.isDelayedInit

        dataModel
            .updateAndCollectWorkspaceItemInfos(
                update.user,
                { itemInfo ->
                    // NOTE: Updates only affect file system items.
                    if (!itemInfo.isFileSystemItem()) {
                        return@updateAndCollectWorkspaceItemInfos false
                    }

                    val uri = itemInfo.intent?.data
                    val file = if (uri != null) filesByUri[uri] else null

                    if (file == null) {
                        // NOTE: File items which are absent from an update should only result in
                        // removal from the workspace if: (a) this is a delayed initialization of
                        // all file system items, or (b) we are processing a file item deletion.
                        if (isDelayedInit || filesByUri.containsKey(uri)) {
                            deletedItems.add(itemInfo.id)
                        }
                        return@updateAndCollectWorkspaceItemInfos false
                    }

                    addedItems.remove(file)

                    // NOTE: If a file item is not disabled due to file system readiness, it has
                    // already been initialized and can be ignored during delayed initialization.
                    // This avoids unnecessary rework and interruption of any scheduled animations.
                    if (isDelayedInit && !itemInfo.hasDisabledFileSystemNotReadyFlag()) {
                        return@updateAndCollectWorkspaceItemInfos false
                    }

                    // NOTE: The presence of a file item in an update implies that the file system
                    // is ready so the associated shortcut no longer needs to be disabled.
                    itemInfo.applyCommonProperties(file)
                    itemInfo.removeDisabledFileSystemNotReadyFlag()

                    return@updateAndCollectWorkspaceItemInfos true
                },
            )
            .also { updatedItems ->
                processUpdatedItems(updatedItems, taskController)
                processDeletedItems(deletedItems, taskController)
                processAddedItems(addedItems, update.extras, taskController)
            }

        if (isDelayedInit) {
            filesByUri.values
                .count { it != null }
                .takeIf { it > 0 }
                ?.let { count ->
                    statsLogManagerFactory
                        .create(context)
                        .logger()
                        .withCardinality(count)
                        .log(LAUNCHER_HOME_SCREEN_FILES_COUNT)
                }
        }
    }

    private fun processAddedItems(
        addedItems: List<HomeScreenFile>,
        extras: HomeScreenFilesUpdate.Extras,
        taskController: ModelTaskController,
    ) {
        if (addedItems.isEmpty()) {
            return
        }

        val knownItems = ArrayList<ItemInfo>()
        addedItems
            .map { file ->
                WorkspaceItemInfo()
                    .apply { applyCommonProperties(file) }
                    .also { itemInfo ->
                        workspaceItemSpaceFinder
                            .findSpaceForItem(
                                knownItems,
                                itemInfo.spanX,
                                itemInfo.spanY,
                                /* excludedScreens= */ IntSet(),
                                extras.findSpaceStartingFrom,
                            )
                            .also { coords ->
                                itemInfo.screenId = coords.screenId
                                itemInfo.cellX = coords.cellX
                                itemInfo.cellY = coords.cellY
                                knownItems.add(itemInfo)
                            }
                    }
            }
            .toList()
            .run {
                taskController.getModelWriter().addItemsToDatabase(this)
                taskController.scheduleCallbackTask { it.bindItemsAdded(this) }
            }
    }

    private fun processDeletedItems(deletedItems: Set<Int>, taskController: ModelTaskController) {
        if (deletedItems.isNotEmpty()) {
            taskController.deleteAndBindComponentsRemoved(
                { deletedItems.contains(it?.id) },
                "The file system item no longer exists",
            )
        }
    }

    private fun processUpdatedItems(
        updatedItems: List<ItemInfo>,
        taskController: ModelTaskController,
    ) {
        if (updatedItems.isNotEmpty()) {
            taskController.getModelWriter().run { updatedItems.forEach(::updateItemInDatabase) }
            taskController.bindUpdatedWorkspaceItems(updatedItems)
        }
    }

    private fun WorkspaceItemInfo.applyCommonProperties(file: HomeScreenFile) {
        container = CONTAINER_DESKTOP
        intent = HomeScreenFilesUtils.buildLaunchIntent(file.uri, file)
        itemType = HomeScreenFilesUtils.buildItemType(file)
        title = file.displayName
        user = file.user
        iconCache.addIconToDBAndMemCache(
            file,
            HomeScreenFilesCachingLogic,
            iconCache.getSerialNumberForUser(file.user),
        )
        iconCache.getTitleAndIcon(this, matchingLookupFlag)
    }

    private fun WorkspaceItemInfo.hasDisabledFileSystemNotReadyFlag(): Boolean =
        (runtimeStatusFlags and FLAG_DISABLED_FILE_SYSTEM_NOT_READY) != 0

    private fun WorkspaceItemInfo.removeDisabledFileSystemNotReadyFlag() {
        runtimeStatusFlags = runtimeStatusFlags and FLAG_DISABLED_FILE_SYSTEM_NOT_READY.inv()
    }

    @AssistedFactory
    interface Factory {
        fun create(update: HomeScreenFilesUpdate): HomeScreenFilesUpdateTask
    }
}
