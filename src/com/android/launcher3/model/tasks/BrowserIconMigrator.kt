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

package com.android.launcher3.model.tasks

import android.content.Context
import com.android.launcher3.EncryptionType
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.nonRestorableItem
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.TransactionContext
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfo.NO_ID
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.util.IntSparseArrayMap
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject

/** Task to re-organize browser icons based on certain rules */
class BrowserIconMigrator
@AssistedInject
constructor(
    @Assisted private val allModelItems: IntSparseArrayMap<ItemInfo>,
    @Assisted private val modelWriter: IModelWriter,
    @ApplicationContext private val context: Context,
    private val idp: InvariantDeviceProfile,
    private val evaluator: BrowserMigrationConditionEvaluator,
    private val prefs: LauncherPrefs,
) {

    private var itemsModified = false

    /** Performs the data migration, and updates the pending flag */
    fun performMigration() {
        modelWriter.scheduleTransaction(
            onComplete = { success, migrationDone ->
                if (success) {
                    prefs.put(PREF_MIGRATION_PENDING, false)
                    if (migrationDone == true) evaluator.notifyMigrationComplete(itemsModified)
                }
            }
        ) { txContext ->
            processItems(txContext)
        }
    }

    private fun processItems(txContext: TransactionContext): Boolean {
        val (srcBrowser, targetAppIcon) = evaluator.evaluateSourceAndTarget() ?: return false
        val targetPkg = targetAppIcon.targetPackage ?: return false

        //  Find the existing browser icon on the hotseat or first page
        val browserIcon =
            getExistingIcon(srcBrowser) { it.container == CONTAINER_HOTSEAT || it.screenId == 0 }
        val appIcon = getExistingIcon(targetPkg) { true }

        when {
            // app icon is already at same or better location than browser, ignore
            browserIcon != null &&
                appIcon != null &&
                compareValuesBy(appIcon, browserIcon, { it.container }, { it.screenId }) <= 0 -> {}

            // move app icon to browser location and browser to some location starting at page 0
            browserIcon != null && appIcon != null -> {
                val newBrowserLocation = getFirstPageEmptyLocation() ?: findNextAvailableSpace()
                val oldBrowserLocation = browserIcon.getLocation()
                browserIcon.addOrMoveTo(txContext, newBrowserLocation)
                appIcon.addOrMoveTo(txContext, oldBrowserLocation)
            }

            // Add app icon to browser location and browser to some location starting at page 0
            browserIcon != null && appIcon == null -> {
                val newBrowserLocation = getFirstPageEmptyLocation() ?: findNextAvailableSpace()
                val oldBrowserLocation = browserIcon.getLocation()
                browserIcon.addOrMoveTo(txContext, newBrowserLocation)
                targetAppIcon.addOrMoveTo(txContext, oldBrowserLocation)
            }

            // Add app to some location starting at page 1
            browserIcon == null && appIcon == null ->
                targetAppIcon.addOrMoveTo(txContext, findNextAvailableSpace())

            // App icon already exists at some location, ignore
            // browserIcon == null && appIcon != null
            else -> {}
        }

        return true
    }

    private fun ItemInfo.addOrMoveTo(
        txContext: TransactionContext,
        location: WorkspaceItemCoordinates,
    ) {
        location.applyTo(this)

        if (id == NO_ID) {
            txContext.addItemToDatabase(this)
            allModelItems.put(id, this)
        } else {
            txContext.updateItemInDatabase(this)
        }

        itemsModified = true
    }

    private fun ItemInfo.getLocation() =
        WorkspaceItemCoordinates(
            screenId = screenId,
            cellX = cellX,
            cellY = cellY,
            container = container,
        )

    private fun getFirstPageEmptyLocation(): WorkspaceItemCoordinates? {
        val occupancy = GridOccupancy(idp.numColumns, idp.numRows)
        allModelItems.forEach {
            if (it.container == CONTAINER_DESKTOP && it.screenId == 0) occupancy.markCells(it, true)
        }

        for (y in idp.numRows - 1 downTo 0) {
            for (x in idp.numColumns - 1 downTo 0) {
                if (!occupancy.cells[x][y]) return WorkspaceItemCoordinates(0, x, y)
            }
        }
        return null
    }

    private fun findNextAvailableSpace(): WorkspaceItemCoordinates {
        val sortedItems =
            allModelItems
                .filter { it.container == CONTAINER_DESKTOP && it.screenId > 0 }
                .sortedBy { it.screenId }

        val foundLocation =
            sortedItems
                .groupBy { it.screenId }
                .firstNotNullOfOrNull { (screenId, screenItems) ->
                    val occupancy = GridOccupancy(idp.numColumns, idp.numRows)
                    screenItems.forEach { occupancy.markCells(it, true) }

                    val outLocation = IntArray(2)
                    if (occupancy.findVacantCell(outLocation, 1, 1))
                        WorkspaceItemCoordinates(screenId, outLocation[0], outLocation[1])
                    else null
                }

        if (foundLocation != null) return foundLocation
        // Add a new screenId as max of all screen id
        val newScreenId = ((sortedItems.lastOrNull()?.screenId ?: 0) + 1).coerceAtLeast(1)
        return WorkspaceItemCoordinates(newScreenId, 0, 0)
    }

    /**
     * Returns the existing icon for [appPkg] with highest location priority
     *
     * Although [ItemInfo.cellX] and [ItemInfo.cellX] do not affect location priority including
     * those in sorting allows us to have deterministic result.
     */
    private fun getExistingIcon(appPkg: String, predicate: (ItemInfo) -> Boolean): ItemInfo? =
        allModelItems
            .filter {
                it.itemType == ITEM_TYPE_APPLICATION &&
                    it is WorkspaceItemInfo &&
                    appPkg == it.targetPackage &&
                    (it.container == CONTAINER_HOTSEAT || it.container == CONTAINER_DESKTOP)
            }
            .sortedWith(compareBy({ it.container }, { it.screenId }, { it.cellY }, { it.cellX }))
            .firstOrNull(predicate)

    companion object {

        @JvmField
        val PREF_MIGRATION_PENDING =
            nonRestorableItem("browser_migration_pending", false, EncryptionType.DEVICE_PROTECTED)
    }
}

@AssistedFactory
interface BrowserIconMigratorFactory {

    fun createBrowserIconMigrator(
        @Assisted allModelItems: IntSparseArrayMap<ItemInfo>,
        @Assisted modelWriter: IModelWriter,
    ): BrowserIconMigrator
}

open class BrowserMigrationConditionEvaluator @Inject constructor() {

    /**
     * Returns a pair of source packageName that should be replaced with the target [ItemInfo] or
     * null if browser migration is not required
     */
    open fun evaluateSourceAndTarget(): Pair<String, ItemInfo>? = null

    /** Notifies that the browser migration is complete */
    open fun notifyMigrationComplete(itemsModified: Boolean) {}
}
