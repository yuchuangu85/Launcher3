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
package com.android.launcher3.provider

import android.app.backup.BackupManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.BuildConfig
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.*
import com.android.launcher3.Utilities
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.Companion.newInstance
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.LoaderTask
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import com.android.launcher3.provider.LauncherDbUtils.asSequence
import com.android.launcher3.provider.LauncherDbUtils.dropTable
import com.android.launcher3.provider.LauncherDbUtils.queryIntArray
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.IntArray as LIntArray
import com.android.launcher3.util.LogConfig
import com.android.launcher3.util.SQLiteTable
import com.android.launcher3.widget.LauncherWidgetHolder
import java.io.InvalidObjectException
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.IntArray

/**
 * Utility class to update DB schema after it has been restored.
 *
 * This task is executed when Launcher starts for the first time and not immediately after restore.
 * This helps keep the model consistent if the launcher updates between restore and first startup.
 */
class RestoreDbTask {

    /**
     * Makes the following changes in the provider DB.
     * 1. Removes all entries belonging to any profiles that were not restored.
     * 2. Marks all entries as restored. The flags are updated during first load or as the restored
     *    apps get installed.
     * 3. If the user serial for any restored profile is different than that of the previous device,
     *    update the entries to the new profile id.
     * 4. If restored from a single display backup, remove gaps between screenIds
     * 5. Override shortcuts that need to be replaced.
     *
     * @return number of items deleted
     */
    @VisibleForTesting
    @Throws(Exception::class)
    fun sanitizeDB(
        context: Context,
        restoreDbTaskWriteDao: IRestoreDbTaskWriteDao,
        controller: ModelDbController,
        db: SQLiteDatabase,
        backupManager: BackupManager,
        restoreEventLogger: LauncherRestoreEventLogger,
    ): Int {
        logFavoritesTable(db, "Old Launcher Database before sanitizing:")
        // Primary user ids
        val myProfileId = controller.getSerialNumberForUser(Process.myUserHandle())
        val oldProfileId = getDefaultProfileId(db)
        FileLog.d(TAG, "sanitizeDB: myProfileId= $myProfileId, oldProfileId= $oldProfileId")
        val oldManagedProfileIds = getManagedProfileIds(db, oldProfileId)

        // Build mapping of restored profile ids to their new profile ids.
        val profileMapping = buildMap {
            put(oldProfileId, myProfileId)
            oldManagedProfileIds.forEach { oldManagedProfileId ->
                val user = backupManager.getUserForAncestralSerialNumber(oldManagedProfileId)
                if (user != null) {
                    val newManagedProfileId = controller.getSerialNumberForUser(user)
                    put(oldManagedProfileId, newManagedProfileId)
                    FileLog.d(
                        TAG,
                        "sanitizeDB: managed profile id=$oldManagedProfileId should be mapped to new id=$newManagedProfileId",
                    )
                } else {
                    FileLog.e(
                        TAG,
                        "sanitizeDB: No User found for old profileId, Ancestral Serial Number: $oldManagedProfileId",
                    )
                }
            }
        }

        // Delete all entries which do not belong to any restored profile(s).
        val selectionStr = "profileId NOT IN (${profileMapping.keys.joinToString()})"
        logFavoritesTable(db, "items to delete from unrestored profiles:", selectionStr)
        restoreEventLogger.sendMetricsForFailedMigration(
            controller.getTable(),
            RestoreError.PROFILE_NOT_RESTORED,
        )
        val itemsDeletedCount =
            restoreDbTaskWriteDao.deleteItemsFromUnrestoredProfiles(profileMapping.keys)
        FileLog.d(TAG, "$itemsDeletedCount total items from unrestored user(s) were deleted")

        // Mark all items as restored.
        val keepAllIcons = Utilities.isPropertyEnabled(LogConfig.KEEP_ALL_ICONS)
        val baseItemRestoreFlag =
            WorkspaceItemInfo.FLAG_RESTORED_ICON or
                (if (keepAllIcons) WorkspaceItemInfo.FLAG_RESTORE_STARTED else 0)
        restoreDbTaskWriteDao.bulkUpdateRestoredFlag(baseItemRestoreFlag)

        // Mark widgets with appropriate restore flag.
        val widgetRestoreFlag =
            LauncherAppWidgetInfo.FLAG_ID_NOT_VALID or
                LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY or
                LauncherAppWidgetInfo.FLAG_UI_NOT_READY or
                (if (keepAllIcons) LauncherAppWidgetInfo.FLAG_RESTORE_STARTED else 0)
        restoreDbTaskWriteDao.bulkUpdateRestoredFlag(
            widgetRestoreFlag,
            Favorites.ITEM_TYPE_APPWIDGET,
        )

        // Migrate ids. To avoid any overlap, we initially move conflicting ids to a temp
        // location. Using Long.MIN_VALUE since profile ids can not be negative, so there will
        // be no overlap.
        var tempLocationOffset = Long.MIN_VALUE
        val tempMigratedIds = mutableMapOf<Long, Long>()

        profileMapping.forEach { (oldId, newId) ->
            var shiftedNewId = newId
            if (oldId != newId) {
                if (profileMapping.containsKey(newId)) {
                    shiftedNewId = tempLocationOffset + newId
                    tempMigratedIds[shiftedNewId] = newId
                    tempLocationOffset++
                }
                restoreDbTaskWriteDao.migrateProfileId(oldId, shiftedNewId)
            }
        }

        // Migrate ids from their temporary id to their actual final id.
        tempMigratedIds.forEach { (oldId, newId) ->
            restoreDbTaskWriteDao.migrateProfileId(oldId, newId)
        }

        if (myProfileId != oldProfileId) {
            restoreDbTaskWriteDao.updateDefaultProfileId(myProfileId)
        }

        // If restored from a single display backup, remove gaps between screenIds
        if (
            LauncherPrefs.get(context).get(LauncherPrefs.RESTORE_DEVICE) !=
                InvariantDeviceProfile.TYPE_MULTI_DISPLAY
        ) {
            removeScreenIdGaps(db, restoreDbTaskWriteDao)
        }

        // Override shortcuts
        maybeOverrideShortcuts(context, controller, db, myProfileId, restoreDbTaskWriteDao)
        return itemsDeletedCount
    }

    /**
     * Remove gaps between screenIds to make sure no empty pages are left in between.
     *
     * e.g. [0, 3, 4, 6, 7] -> [0, 1, 2, 3, 4]
     */
    fun removeScreenIdGaps(db: SQLiteDatabase, writeDao: IRestoreDbTaskWriteDao) {
        FileLog.d(TAG, "Removing gaps between screenIds")
        val distinctScreens =
            queryIntArray(
                true,
                db,
                Favorites.TABLE_NAME,
                Favorites.SCREEN,
                "$CONTAINER = $CONTAINER_DESKTOP",
                null,
                Favorites.SCREEN,
            )
        if (distinctScreens.isEmpty) return

        // If there is no 0-screen, let there be an empty screen 0
        val startScreenId = if (distinctScreens.contains(0)) 0 else 1
        writeDao.removeScreenIdGaps(
            Favorites.CONTAINER_DESKTOP,
            distinctScreens.toArray(),
            startScreenId,
        )
    }

    /** Updates profile id of all entries from {@param oldProfileId} to {@param newProfileId}. */
    fun migrateProfileId(db: SQLiteDatabase, oldProfileId: Long, newProfileId: Long) {
        FileLog.d(TAG, "Changing profile user id from $oldProfileId to $newProfileId")
        // Update existing entries.
        val values = ContentValues()
        values.put(Favorites.PROFILE_ID, newProfileId)
        db.update(Favorites.TABLE_NAME, values, "profileId = ?", arrayOf(oldProfileId.toString()))
    }

    /** Changes the default value for the column. */
    fun changeDefaultColumn(db: SQLiteDatabase, newProfileId: Long) {
        db.execSQL("ALTER TABLE favorites RENAME TO favorites_old;")
        Favorites.addTableToDb(db, newProfileId, false)
        db.execSQL("INSERT INTO favorites SELECT * FROM favorites_old;")
        dropTable(db, "favorites_old")
    }

    /**
     * Returns a list of the managed profile id(s) used in the favorites table of the provided db.
     */
    private fun getManagedProfileIds(db: SQLiteDatabase, defaultProfileId: Long): List<Long> =
        buildList {
            db.rawQuery(
                    "SELECT profileId from favorites WHERE profileId != ? GROUP BY profileId",
                    arrayOf(defaultProfileId.toString()),
                )
                .use { c ->
                    while (c.moveToNext()) {
                        add(c.getLong(c.getColumnIndex(PROFILE_ID)))
                    }
                }
        }

    /** Returns the profile id used in the favorites table of the provided db. */
    @Throws(Exception::class)
    fun getDefaultProfileId(db: SQLiteDatabase): Long {
        db.rawQuery("PRAGMA table_info (favorites)", null).use { c ->
            val nameIndex = c.getColumnIndex(INFO_COLUMN_NAME)
            while (c.moveToNext()) {
                if (Favorites.PROFILE_ID == c.getString(nameIndex)) {
                    return c.getLong(c.getColumnIndex(INFO_COLUMN_DEFAULT_VALUE))
                }
            }
            throw InvalidObjectException("Table does not have a profile id column")
        }
    }

    @WorkerThread
    @VisibleForTesting
    fun restoreAppWidgetIdsIfExists(
        context: Context,
        controller: ModelDbController,
        restoreEventLogger: LauncherRestoreEventLogger,
        writeDao: IRestoreDbTaskWriteDao,
        hostSupplier: Supplier<AppWidgetHost>,
    ) {
        val lp = LauncherPrefs.get(context)
        if (lp.has(LauncherPrefs.APP_WIDGET_IDS, LauncherPrefs.OLD_APP_WIDGET_IDS)) {
            restoreAppWidgetIds(
                context,
                controller,
                restoreEventLogger,
                writeDao,
                LIntArray.fromConcatString(lp.get(LauncherPrefs.OLD_APP_WIDGET_IDS)).toArray(),
                LIntArray.fromConcatString(lp.get(LauncherPrefs.APP_WIDGET_IDS)).toArray(),
                hostSupplier.get(),
            )
        } else {
            FileLog.d(TAG, "Did not receive new app widget id map during Launcher restore")
        }

        lp.remove(LauncherPrefs.APP_WIDGET_IDS, LauncherPrefs.OLD_APP_WIDGET_IDS)
    }

    /** Updates the app widgets whose id has changed during the restore process. */
    @WorkerThread
    private fun restoreAppWidgetIds(
        context: Context,
        controller: ModelDbController,
        launcherRestoreEventLogger: LauncherRestoreEventLogger,
        writeDao: IRestoreDbTaskWriteDao,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray,
        host: AppWidgetHost,
    ) {
        if (!BuildConfig.WIDGETS_ENABLED) {
            FileLog.e(TAG, "Skipping widget ID remap as widgets not supported")
            host.deleteHost()
            launcherRestoreEventLogger.logFavoritesItemsRestoreFailed(
                Favorites.ITEM_TYPE_APPWIDGET,
                oldWidgetIds.size,
                RestoreError.WIDGETS_DISABLED,
            )
            return
        }
        if (!isPending(context)) {
            // Someone has already gone through our DB once, probably LoaderTask. Skip any further
            // modifications of the DB.
            FileLog.e(TAG, "Skipping widget ID remap as DB already in use")
            for (widgetId in newWidgetIds) {
                FileLog.d(TAG, "Deleting widgetId: $widgetId")
                host.deleteAppWidgetId(widgetId)
            }
            return
        }

        val widgets = AppWidgetManager.getInstance(context)
        FileLog.d(
            TAG,
            "restoreAppWidgetIds: oldWidgetIds=[${oldWidgetIds.joinToString()}], newWidgetIds=[${newWidgetIds.joinToString()}]",
        )

        // TODO(b/234700507): Remove the logs after the bug is fixed
        logDatabaseWidgetInfo(controller)

        for (i in oldWidgetIds.indices) {
            FileLog.i(TAG, "migrating appWidgetId: ${oldWidgetIds[i]} => ${newWidgetIds[i]}")

            val provider = widgets.getAppWidgetInfo(newWidgetIds[i])
            val state =
                if (LoaderTask.isValidProvider(provider)) {
                    // This will ensure that we show 'Click to setup' UI if required.
                    LauncherAppWidgetInfo.FLAG_UI_NOT_READY
                } else {
                    LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY
                }

            // b/135926478: Work profile widget restore is broken in platform. This forces us to
            // recreate the widget during loading with the correct host provider.
            val mainProfileId =
                UserCache.INSTANCE[context].getSerialNumberForUser(Process.myUserHandle())

            val controllerProfileId = controller.getSerialNumberForUser(Process.myUserHandle())
            val oldWidgetId = oldWidgetIds[i].toString()
            FileLog.d(
                TAG,
                "restoreAppWidgetIds: querying profile id=$mainProfileId with controller profile ID=$controllerProfileId",
            )
            val wasUpdated =
                writeDao.updateAppWidgetId(
                    oldWidgetId = oldWidgetIds[i],
                    newWidgetId = newWidgetIds[i],
                    newRestoreState = state,
                    profileId = mainProfileId,
                )
            if (!wasUpdated) {
                // TODO(b/234700507): Remove the logs after the bug is fixed
                FileLog.e(
                    TAG,
                    "restoreAppWidgetIds: remapping failed since the widget is not in" +
                        " the database anymore",
                )
                controller.db
                    .query(
                        Favorites.TABLE_NAME,
                        arrayOf(Favorites.APPWIDGET_ID),
                        "appWidgetId=?",
                        arrayOf(oldWidgetId),
                        null,
                        null,
                        null,
                    )
                    .use { cursor ->
                        if (!cursor.moveToFirst()) {
                            // The widget no long exists.
                            FileLog.d(
                                TAG,
                                ("Deleting widgetId: " +
                                    newWidgetIds[i] +
                                    " with old id: " +
                                    oldWidgetId),
                            )
                            host.deleteAppWidgetId(newWidgetIds[i])
                            launcherRestoreEventLogger.logSingleFavoritesItemRestoreFailed(
                                Favorites.ITEM_TYPE_APPWIDGET,
                                RestoreError.WIDGET_REMOVED,
                            )
                        }
                    }
            }
        }

        logFavoritesTable(controller.db, "launcher db after remap widget ids")
        LauncherAppState.INSTANCE[context].model.reloadIfActive("restoreAppWidgetIds")
    }

    companion object {
        private const val TAG = "RestoreDbTask"
        const val RESTORED_DEVICE_TYPE: String = "restored_task_pending"
        const val FIRST_LOAD_AFTER_RESTORE_KEY: String = "first_load_after_restore"

        private const val INFO_COLUMN_NAME = "name"
        private const val INFO_COLUMN_DEFAULT_VALUE = "dflt_value"

        const val APPWIDGET_OLD_IDS: String = "appwidget_old_ids"
        const val APPWIDGET_IDS: String = "appwidget_ids"

        @VisibleForTesting
        val DB_COLUMNS_TO_LOG: Array<String> =
            arrayOf(
                "profileId",
                "title",
                "itemType",
                "screen",
                "container",
                "cellX",
                "cellY",
                "spanX",
                "spanY",
                "intent",
                "appWidgetProvider",
                "appWidgetId",
                "restored",
            )

        /**
         * Creates a task for restoring the backed up DB if needed. It performs the initial disk
         * validation immediately and returns a callback which can be used to complete any database
         * updates.
         */
        fun createRestoreTask(context: Context): Consumer<ModelDbController> {
            if (!isPending(context)) {
                Log.d(TAG, "No restore task pending, exiting RestoreDbTask")
                return Consumer { c: ModelDbController? -> }
            }

            // Perform any disk updates before accessing the actual database.
            val deviceGridState = DeviceGridState(context)
            FileLog.d(TAG, "restoreIfNeeded: deviceGridState from context: $deviceGridState")
            val oldPhoneFileName = deviceGridState.dbFile
            removeOldDBs(context, oldPhoneFileName)

            return Consumer { dbController: ModelDbController ->
                if (!performRestore(context, dbController)) {
                    dbController.createEmptyDB()
                }
                // Set is pending to false irrespective of the result, so that it doesn't get
                // executed again.
                LauncherPrefs.get(context).removeSync(LauncherPrefs.RESTORE_DEVICE)
            }
        }

        /** Returns a list of paths of the existing launcher dbs. */
        @VisibleForTesting
        fun existingDbs(context: Context): List<String> {
            // At this point idp.dbFile contains the name of the dbFile from the previous phone
            return LauncherFiles.GRID_DB_FILES.stream()
                .filter { dbName: String? -> context.getDatabasePath(dbName).exists() }
                .collect(Collectors.toList())
        }

        /** Only keep the last database used on the previous device. */
        @VisibleForTesting
        fun removeOldDBs(context: Context, oldPhoneDbFileName: String) {
            // At this point idp.dbFile contains the name of the dbFile from the previous phone
            LauncherFiles.GRID_DB_FILES.stream()
                .filter { dbName: String -> dbName != oldPhoneDbFileName }
                .forEach { dbName: String ->
                    if (context.deleteDatabase(dbName)) {
                        FileLog.d(TAG, "Removed old grid db file: $dbName")
                    }
                }
        }

        private fun performRestore(context: Context, controller: ModelDbController): Boolean {
            val db = controller.db

            val restoreDbTaskWriteDao = LegacyRestoreDbTaskWriteDao(db)
            FileLog.d(TAG, "performRestore: starting restore from db")
            try {
                SQLiteTransaction(db).use { t ->
                    val task = RestoreDbTask()
                    val backupManager = BackupManager(context)
                    val restoreEventLogger = newInstance(context)
                    task.sanitizeDB(
                        context,
                        restoreDbTaskWriteDao,
                        controller,
                        db,
                        backupManager,
                        restoreEventLogger,
                    )
                    task.restoreAppWidgetIdsIfExists(
                        context,
                        controller,
                        restoreEventLogger,
                        restoreDbTaskWriteDao,
                    ) {
                        AppWidgetHost(context, LauncherWidgetHolder.APPWIDGET_HOST_ID)
                    }
                    t.commit()
                    return true
                }
            } catch (e: Exception) {
                FileLog.e(TAG, "Failed to verify db", e)
                return false
            }
        }

        @JvmStatic
        fun isPending(context: Context): Boolean {
            return isPending(LauncherPrefs.get(context))
        }

        @JvmStatic
        fun isPending(prefs: LauncherPrefs): Boolean {
            return prefs.has(LauncherPrefs.RESTORE_DEVICE)
        }

        /** Marks the DB state as pending restoration */
        @JvmStatic
        fun setPending(context: Context) {
            val deviceGridState = DeviceGridState(context)
            FileLog.d(TAG, "restore initiated from backup: DeviceGridState=$deviceGridState")
            LauncherPrefs.get(context)
                .putSync(
                    LauncherPrefs.RESTORE_DEVICE.to(deviceGridState.deviceType),
                    LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE.to(true),
                )
        }

        private fun logDatabaseWidgetInfo(controller: ModelDbController) {
            try {
                controller.db
                    .query(
                        Favorites.TABLE_NAME,
                        arrayOf(Favorites.APPWIDGET_ID, Favorites.RESTORED, Favorites.PROFILE_ID),
                        Favorites.APPWIDGET_ID + "!=" + LauncherAppWidgetInfo.NO_ID,
                        null,
                        null,
                        null,
                        null,
                    )
                    .use { cursor ->
                        val builder = StringBuilder()
                        builder.append("[")

                        if (cursor.moveToFirst()) {
                            val widgetIdColumnIndex = cursor.getColumnIndex(Favorites.APPWIDGET_ID)
                            val widgetRestoredColumnIndex =
                                cursor.getColumnIndex(Favorites.RESTORED)
                            val widgetProfileIdIndex = cursor.getColumnIndex(Favorites.PROFILE_ID)
                            while (!cursor.isAfterLast) {
                                val widgetId = cursor.getInt(widgetIdColumnIndex)
                                val widgetRestoredFlag = cursor.getInt(widgetRestoredColumnIndex)
                                val widgetProfileId = cursor.getInt(widgetProfileIdIndex)
                                builder.append(
                                    "[appWidgetId=$widgetId, restoreFlag=$widgetRestoredFlag, profileId=$widgetProfileId]"
                                )
                                cursor.moveToNext()
                            }
                        }
                        builder.append("]")
                        Log.d(TAG, "restoreAppWidgetIds: all widget ids in database: $builder")
                    }
            } catch (ex: Exception) {
                Log.e(TAG, "Getting widget ids from the database failed", ex)
            }
        }

        private fun maybeOverrideShortcuts(
            context: Context,
            controller: ModelDbController,
            db: SQLiteDatabase,
            currentUser: Long,
            writeDao: IRestoreDbTaskWriteDao,
        ) {
            val activityOverrides = ApiWrapper.INSTANCE[context].activityOverrides
            if (activityOverrides == null || activityOverrides.isEmpty()) return

            try {
                db.query(
                        Favorites.TABLE_NAME,
                        arrayOf(Favorites._ID, Favorites.INTENT),
                        "$ITEM_TYPE=$ITEM_TYPE_APPLICATION AND $PROFILE_ID=$currentUser AND ( ${getTelephonyIntentSQLLiteSelection(activityOverrides.keys)} )",
                        null,
                        null,
                        null,
                        null,
                    )
                    .use { c ->
                        SQLiteTransaction(db).use { t ->
                            val idIndex = c.getColumnIndexOrThrow(Favorites._ID)
                            val intentIndex = c.getColumnIndexOrThrow(Favorites.INTENT)
                            while (c.moveToNext()) {
                                val activityOverride =
                                    activityOverrides[
                                        Intent.parseUri(c.getString(intentIndex), 0)
                                            .component
                                            ?.packageName] ?: continue

                                val newProfileId =
                                    controller.getSerialNumberForUser(activityOverride.user)
                                val newIntentUri =
                                    AppInfo.makeLaunchIntent(activityOverride).toUri(0)

                                writeDao.updateShortcutOverride(
                                    itemId = c.getInt(idIndex),
                                    newIntentUri = newIntentUri,
                                    newProfileId = newProfileId,
                                )
                            }
                            t.commit()
                        }
                    }
            } catch (ex: Exception) {
                Log.e(TAG, "Error while overriding shortcuts", ex)
            }
        }

        private fun getTelephonyIntentSQLLiteSelection(packages: Collection<String>) =
            packages.joinToString(" OR ") { "intent LIKE '%' || '$it' || '%' " }

        /**
         * Queries and logs the items from the Favorites table in the launcher db. This is to
         * understand why items might be missing during the restore process for Launcher.
         *
         * @param database The Launcher db to query from.
         * @param logHeader First line in log statement, used to explain what is being logged.
         * @param whereClause The SELECT statement to query items.
         * @param profileIds The profile ID's for each user profile.
         */
        private fun logFavoritesTable(
            database: SQLiteDatabase,
            logHeader: String,
            selection: String? = null,
        ) {
            try {
                database
                    .query(
                        /* table */ TABLE_NAME,
                        /* columns */ DB_COLUMNS_TO_LOG,
                        /* selection */ selection,
                        /* selection args */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null,
                    )
                    .use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnNames = cursor.columnNames
                            val stringBuilder = StringBuilder(logHeader + "\n")
                            do {
                                for (columnName in columnNames) {
                                    stringBuilder
                                        .append(columnName)
                                        .append("=")
                                        .append(cursor.getString(cursor.getColumnIndex(columnName)))
                                        .append(" ")
                                }
                                stringBuilder.append("\n")
                            } while (cursor.moveToNext())
                            FileLog.d(TAG, stringBuilder.toString())
                        } else {
                            FileLog.d(
                                TAG,
                                "logFavoritesTable: No items found from query for \"$logHeader\"",
                            )
                        }
                    }
            } catch (e: Exception) {
                FileLog.e(TAG, "logFavoritesTable: Error reading from database", e)
            }
        }

        /**
         * Queries and reports the count of each itemType to be removed due to unrestored profiles.
         *
         * @param selection Query being used for to find unrestored profiles
         */
        fun LauncherRestoreEventLogger.sendMetricsForFailedMigration(
            table: SQLiteTable,
            @RestoreError error: String,
            selection: String? = null,
        ) {
            try {
                table.query(arrayOf(ITEM_TYPE), selection) {
                    asSequence()
                        .map { getInt(0) }
                        .groupingBy { it }
                        .eachCount()
                        .forEach { (type, count) ->
                            logFavoritesItemsRestoreFailed(type, count, error)
                        }
                }
            } catch (e: Exception) {
                FileLog.e(TAG, "sendMetricsForFailedMigration: Error reading from database", e)
            }
        }
    }
}
