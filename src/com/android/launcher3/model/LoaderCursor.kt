/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.model

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.database.Cursor
import android.os.Process
import android.os.UserHandle
import android.provider.BaseColumns
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.RESTORED
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.Utilities
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserManagerState
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.ContentWriter
import com.android.launcher3.util.ContentWriter.CommitParams
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.PackageManagerHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.security.InvalidParameterException

/** Extension of [Cursor] with utility methods for workspace loading. */
class LoaderCursor
@AssistedInject
constructor(
    @Assisted cursor: Cursor,
    @param:Assisted private val userManagerState: UserManagerState,
    @param:Assisted private val restoreEventLogger: LauncherRestoreEventLogger?,
    @param:ApplicationContext private val context: Context,
    private val iconCache: IconCache,
    private val idp: InvariantDeviceProfile,
    private val model: LauncherModel,
    private val pmHelper: PackageManagerHelper,
    private val automationRepo: AutomationRepository,
) : ModelCursorWrapper(cursor) {

    private val itemsToRemove = IntArray()
    private val restoredRows = IntArray()
    private val occupied = IntSparseArrayMap<GridOccupancy>()

    // CollectionInfo objects, which have not yet been loaded from the DB, but are expected to
    // found eventually as the loading progresses
    private val pendingCollectionInfo = IntSparseArrayMap<CollectionInfo>()

    var restoreFlag: Int = 0

    var user: UserHandle = Process.myUserHandle()

    var launcherActivityInfo: LauncherActivityInfo? = null
        private set

    override fun moveToNext(): Boolean {
        val result = super.moveToNext()
        if (result) {
            launcherActivityInfo = null

            // Load common properties.
            user = userManagerState.getUser(serialNumber)
            restoreFlag = restoreFlagOnDisk
        }
        return result
    }

    fun parseIntent(): Intent? =
        runCatching { Intent.parseUri(intentString, 0) }
            .onFailure { Log.e(TAG, "Error parsing intent", it) }
            .getOrNull()

    @VisibleForTesting
    fun loadSimpleWorkspaceItem(): WorkspaceItemInfo {
        val info = WorkspaceItemInfo()
        info.intent = Intent()
        // Non-app shortcuts are only supported for current user.
        info.user = user
        info.itemType = itemType
        info.title = title
        // the fallback icon
        if (!loadIconFromDb(info)) {
            info.bitmap = iconCache.getDefaultIcon(info.user)
        }

        // TODO: If there's an explicit component and we can't install that, delete it.
        return info
    }

    /**
     * Loads the icon from the cursor and updates the {@param info} if the icon is an app resource.
     */
    fun loadIconFromDb(info: WorkspaceItemInfo): Boolean =
        createIconRequestInfo(info, false).loadIconFromDbBlob(context)

    fun createIconRequestInfo(
        wai: WorkspaceItemInfo,
        useLowResIcon: Boolean,
    ): IconRequestInfo<WorkspaceItemInfo> {
        val iconBlob =
            if (
                itemType == ITEM_TYPE_DEEP_SHORTCUT ||
                    restoreFlag != 0 ||
                    (wai.isInactiveArchive && Flags.restoreArchivedAppIconsFromDb())
            )
                iconBlob
            else null
        return IconRequestInfo(
            wai,
            launcherActivityInfo,
            iconBlob,
            wai.hasStatusFlag(WorkspaceItemInfo.FLAG_RESTORED_FULL_BLEED),
            DESKTOP_ICON_FLAG.withUseLowRes(useLowResIcon),
        )
    }

    /**
     * Makes a WorkspaceItemInfo object for a restored application or shortcut item that points to a
     * package that is not yet installed on the system.
     */
    fun getRestoredItemInfo(intent: Intent, isArchived: Boolean): WorkspaceItemInfo {
        val info = WorkspaceItemInfo()
        info.user = user
        info.intent = intent
        info.title = ""

        // the fallback icon
        if (!loadIconFromDb(info)) {
            Log.d(TAG, "loadIconFromDb failed, getting from cache - intent=$intent")
            iconCache.getTitleAndIcon(info, CacheLookupFlag.DEFAULT_LOOKUP_FLAG)
        }

        if (hasRestoreFlag(WorkspaceItemInfo.FLAG_RESTORED_ICON) || isArchived) {
            val title = title
            if (!TextUtils.isEmpty(title)) {
                info.title = Utilities.trim(title)
            }
        } else if (hasRestoreFlag(WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON)) {
            if (TextUtils.isEmpty(info.title)) {
                info.title = title
            }
        } else {
            throw InvalidParameterException("Invalid restoreType $restoreFlag")
        }

        info.contentDescription = iconCache.getUserBadgedLabel(info.title!!, info.user)
        info.itemType = itemType
        info.status = restoreFlag
        if (isArchived)
            info.runtimeStatusFlags = info.runtimeStatusFlags or ItemInfoWithIcon.FLAG_ARCHIVED
        return info
    }

    /** Makes a WorkspaceItemInfo object for a shortcut that is an application. */
    @JvmOverloads
    fun getAppShortcutInfo(
        intent: Intent,
        allowMissingTarget: Boolean,
        useLowResIcon: Boolean,
        loadIcon: Boolean = true,
    ): WorkspaceItemInfo? {
        val componentName = intent.component
        if (componentName == null) {
            Log.d(TAG, "Missing component found in getShortcutInfo")
            return null
        }

        val newIntent = Intent(Intent.ACTION_MAIN, null)
        newIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        newIntent.setComponent(componentName)
        launcherActivityInfo =
            context.getSystemService(LauncherApps::class.java).resolveActivity(newIntent, user)
        if ((launcherActivityInfo == null) && !allowMissingTarget) {
            Log.d(TAG, "Missing activity found in getShortcutInfo: $componentName")
            return null
        }

        val info = WorkspaceItemInfo()
        info.user = user
        info.intent = newIntent
        val userIconInfo = userManagerState.getUserInfo(user)
        if (launcherActivityInfo != null) {
            AppInfo.updateRuntimeFlagsForActivityTarget(
                info,
                launcherActivityInfo,
                userIconInfo,
                ApiWrapper.INSTANCE[context],
                pmHelper,
                automationRepo,
            )
        }
        loadWorkspaceTitleAndIcon(useLowResIcon, loadIcon, info)
        // from the db
        if (info.title.isNullOrEmpty()) {
            if (loadIcon) {
                // fall back to the class name of the activity
                info.title = title ?: componentName.className
            } else {
                info.title = ""
            }
        }

        info.contentDescription = iconCache.getUserBadgedLabel(info.title!!, info.user)
        return info
    }

    @VisibleForTesting
    fun loadWorkspaceTitleAndIcon(
        useLowResIcon: Boolean,
        loadIconFromCache: Boolean,
        info: WorkspaceItemInfo,
    ) {
        val isPreArchived =
            Flags.enableSupportForArchiving() &&
                Flags.restoreArchivedAppIconsFromDb() &&
                info.isInactiveArchive &&
                LauncherPrefs.get(context).get(LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE)
        val preArchivedIconNotFound = isPreArchived && !loadIconFromDb(info)
        if (preArchivedIconNotFound) {
            Log.d(
                TAG,
                "loadIconFromDb failed for pre-archived icon, loading from cache. Component=${info.targetComponent}",
            )
            iconCache.getTitleAndIcon(
                info,
                launcherActivityInfo,
                CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withUseLowRes(useLowResIcon),
            )
        } else if (loadIconFromCache && !info.isInactiveArchive) {
            iconCache.getTitleAndIcon(
                info,
                launcherActivityInfo,
                CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withUseLowRes(useLowResIcon),
            )
            if (iconCache.isDefaultIcon(info.bitmap, user)) {
                Log.d(
                    TAG,
                    "Default Icon found in cache, trying DB instead. Component=${info.targetComponent}",
                )
                loadIconFromDb(info)
            }
        }
    }

    /** Returns a [ContentWriter] which can be used to update the current item. */
    fun updater() =
        ContentWriter(
            context,
            CommitParams(model.modelDbController, BaseColumns._ID + "= ?", arrayOf(id.toString())),
        )

    /** Marks the current item for removal */
    fun markDeleted(reason: String?, @RestoreError errorType: String?) {
        FileLog.e(TAG, reason)
        itemsToRemove.add(id)
        restoreEventLogger?.logSingleFavoritesItemRestoreFailed(itemType, errorType)
    }

    /**
     * Removes any items marked for removal.
     *
     * @return true is any item was removed.
     */
    fun commitDeleted(): Boolean {
        if (itemsToRemove.size() > 0) {
            // Remove dead items
            model.modelDbController.delete(
                Utilities.createDbSelectionQuery(_ID, itemsToRemove),
                null,
            )
            return true
        }
        return false
    }

    /** Marks the current item as restored */
    fun markRestored() {
        if (restoreFlag != 0) {
            restoredRows.add(id)
            restoreFlag = 0
        }
    }

    fun hasRestoreFlag(flagMask: Int): Boolean = (restoreFlag and flagMask) != 0

    fun commitRestoredItems() {
        if (restoredRows.size() > 0) {
            // Update restored items that no longer require special handling
            val values = ContentValues()
            values.put(RESTORED, 0)
            model.modelDbController.update(
                values,
                Utilities.createDbSelectionQuery(_ID, restoredRows),
                null,
            )
        }
        restoreEventLogger?.reportLauncherRestoreResults()
    }

    /** Returns true is the item is on workspace or hotseat */
    val isOnWorkspaceOrHotseat: Boolean
        get() = container.isHotseatOrDesktopContainer()

    /**
     * Applies the following properties: [ItemInfo.id] [ItemInfo.container] [ItemInfo.screenId]
     * [ItemInfo.cellX] [ItemInfo.cellY]
     */
    fun applyCommonProperties(info: ItemInfo) {
        info.id = id
        info.container = container
        info.screenId = screen
        info.cellX = cellX
        info.cellY = cellY
    }

    /**
     * Return an existing FolderInfo object if we have encountered this ID previously, or make a new
     * one.
     */
    fun findOrMakeFolder(id: Int, loadedItems: IntSparseArrayMap<ItemInfo>): CollectionInfo {
        // See if a placeholder was created for us already
        val info = loadedItems[id]
        if (info is CollectionInfo) return info

        var pending = pendingCollectionInfo[id]
        if (pending != null) return pending

        // No placeholder -- create a new blank folder instance. At this point, we don't know
        // if the desired container is supposed to be a folder or an app pair. In the case that
        // it is an app pair, the blank folder will be replaced by a blank app pair when the app
        // pair is getting processed, in WorkspaceItemProcessor.processFolderOrAppPair().
        pending = FolderInfo()
        pending.id = id
        pendingCollectionInfo.put(id, pending)
        return pending
    }

    /**
     * Adds the {@param info} to {@param dataModel} if it does not overlap with any other item,
     * otherwise marks it for deletion.
     */
    fun checkAndAddItem(
        info: ItemInfo,
        loadedItems: IntSparseArrayMap<ItemInfo>,
        logger: LoaderMemoryLogger?,
    ) {
        if (info.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
            // Ensure that it is a valid intent. An exception here will cause the item loading to
            // get skipped
            ShortcutKey.fromItemInfo(info)
        }
        if (checkItemPlacement(info)) {
            logger?.addLog(
                Log.DEBUG,
                TAG,
                String.format("Adding item to ID map: %s", info),
                /* stackTrace= */ null,
            )
            loadedItems.put(info.id, info)
            if (
                (info.itemType == ITEM_TYPE_APP_GROUP ||
                    info.itemType == ITEM_TYPE_DEEP_SHORTCUT ||
                    info.itemType == ITEM_TYPE_APPLICATION) &&
                    !info.container.isHotseatOrDesktopContainer()
            ) {
                findOrMakeFolder(info.container, loadedItems).add(info)
            }
            restoreEventLogger?.logSingleFavoritesItemRestored(info.itemType)
        } else if (info.id != ItemInfo.NO_ID) {
            markDeleted("Item position overlap", RestoreError.OVERLAPPING_ITEM)
        }
    }

    /** check & update map of what's occupied; used to discard overlapping/invalid items */
    fun checkItemPlacement(item: ItemInfo): Boolean {
        val containerIndex = item.screenId
        if (item.container == CONTAINER_HOTSEAT) {
            val hotseatOccupancy = occupied[CONTAINER_HOTSEAT]

            if (item.screenId >= idp.numDatabaseHotseatIcons) {
                Log.e(
                    TAG,
                    "Error loading shortcut $item into hotseat position ${item.screenId}, position out of bounds: (0 to ${idp.numDatabaseHotseatIcons - 1})",
                )
                return false
            }

            if (hotseatOccupancy != null) {
                if (hotseatOccupancy.cells[item.screenId][0]) {
                    Log.e(
                        TAG,
                        "Error loading shortcut into hotseat $item into position (${item.screenId}:${item.cellX},${item.cellY}) already occupied",
                    )
                    return false
                } else {
                    hotseatOccupancy.cells[item.screenId][0] = true
                    return true
                }
            } else {
                val occupancy = GridOccupancy(idp.numDatabaseHotseatIcons, 1)
                occupancy.cells[item.screenId][0] = true
                occupied.put(CONTAINER_HOTSEAT, occupancy)
                return true
            }
        } else if (item.container != CONTAINER_DESKTOP) {
            // Skip further checking if it is not the hotseat or workspace container
            return true
        }

        val countX = idp.numColumns
        val countY = idp.numRows
        if (
            item.container == CONTAINER_DESKTOP && item.cellX < 0 ||
                item.cellY < 0 ||
                item.cellX + item.spanX > countX ||
                item.cellY + item.spanY > countY
        ) {
            Log.e(
                TAG,
                "Error loading shortcut $item into cell ($containerIndex-${item.screenId}:${item.cellX},${item.cellY}) out of screen bounds ($countX x $countY)",
            )
            return false
        }

        if (!occupied.containsKey(item.screenId)) {
            occupied.put(item.screenId, GridOccupancy(countX + 1, countY + 1))
        }
        val occupancy = occupied[item.screenId]

        // Check if any workspace icons overlap with each other
        if (occupancy.isRegionVacant(item.cellX, item.cellY, item.spanX, item.spanY)) {
            occupancy.markCells(item, true)
            return true
        } else {
            Log.e(
                TAG,
                "Error loading shortcut $item into cell ($containerIndex-${item.screenId}:${item.cellX},${item.cellX},${item.spanX},${item.spanY}) already occupied",
            )
            return false
        }
    }

    @AssistedFactory
    interface LoaderCursorFactory {
        fun createLoaderCursor(
            cursor: Cursor,
            userManagerState: UserManagerState,
            restoreEventLogger: LauncherRestoreEventLogger?,
        ): LoaderCursor
    }

    companion object {
        private const val TAG = "LoaderCursor"

        private fun Int.isHotseatOrDesktopContainer() =
            this == CONTAINER_HOTSEAT || this == CONTAINER_DESKTOP
    }
}
