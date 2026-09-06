/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.net.Uri.Builder
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.ConstantItem
import com.android.launcher3.DefaultLayoutParser
import com.android.launcher3.EncryptionType.ENCRYPTED
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState.Companion.getIDP
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.LauncherPrefs.Companion.get
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.*
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError.Companion.GRID_MIGRATION_FAILURE
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.GridMigrationOption.Companion.from
import com.android.launcher3.model.GridSizeMigrationDBController.needsToMigrate
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import com.android.launcher3.provider.LauncherDbUtils.tableExists
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.provider.RestoreDbTask.Companion.sendMetricsForFailedMigration
import com.android.launcher3.util.SQLiteTable
import com.android.launcher3.util.WriteProtectedSQLiteTable
import com.android.launcher3.widget.LauncherWidgetHolder
import javax.inject.Inject
import javax.inject.Provider

/**
 * Utility class which maintains an instance of Launcher database and provides utility methods
 * around it.
 */
@LauncherAppSingleton
class ModelDbController
@Inject
internal constructor(
    @ApplicationContext private val context: Context,
    private val idp: InvariantDeviceProfile,
    private val prefs: LauncherPrefs,
    private val userCache: UserCache,
    private val layoutParserFactory: LayoutParserFactory,
    private val migrationLogicFactory: Provider<GridSizeMigrationLogic>,
) {

    private lateinit var openHelper: DatabaseHelper

    @Synchronized
    private fun createDbIfNotExists() {
        if (!::openHelper.isInitialized) {
            // Initialize the restore task before opening the DB
            val restoreTask = RestoreDbTask.createRestoreTask(context)
            val dbFile = prefs.get(LauncherPrefs.DB_FILE).ifEmpty { idp.dbFile }
            openHelper = createDatabaseHelper(forMigration = false, dbFile)
            restoreTask.accept(this)
        }
    }

    private fun createDatabaseHelper(forMigration: Boolean, dbFile: String): DatabaseHelper {
        // Set the flag for empty DB
        val onEmptyDbCreateCallback =
            if (forMigration) Runnable {}
            else Runnable { prefs.putSync(getEmptyDbCreatedKey(dbFile).to(true)) }

        val databaseHelper = DatabaseHelper(context, dbFile, onEmptyDbCreateCallback)
        // Table creation sometimes fails silently, which leads to a crash loop.
        // This way, we will try to create a table every time after crash, so the device
        // would eventually be able to recover.
        if (!tableExists(databaseHelper.readableDatabase, Favorites.TABLE_NAME)) {
            Log.e(TAG, "Tables are missing after onCreate has been called. Trying to recreate")
            // This operation is a no-op if the table already exists.
            Favorites.addTableToDb(
                databaseHelper.writableDatabase,
                getSerialNumberForUser(Process.myUserHandle()),
                true, /* optional */
            )
        }
        databaseHelper.initIds()
        return databaseHelper
    }

    /** Refer [SQLiteDatabase.query] */
    @WorkerThread
    fun query(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor =
        getTable().query(projection, selection, selectionArgs, sortOrder).apply {
            extras = Bundle().apply { putString(EXTRA_DB_NAME, openHelper.databaseName) }
        }

    /** Refer [SQLiteDatabase.insert] */
    @WorkerThread
    fun insert(initialValues: ContentValues?): Int {
        createDbIfNotExists()
        return openHelper.insertAndCheck(openHelper.writableDatabase, initialValues)
    }

    /** Refer [SQLiteDatabase.delete] */
    @WorkerThread
    fun delete(selection: String?, selectionArgs: Array<String>?): Int =
        getTable().delete(selection, selectionArgs)

    /** Refer [SQLiteDatabase.update] */
    @WorkerThread
    fun update(values: ContentValues, selection: String?, selectionArgs: Array<String>?): Int =
        getTable().update(values, selection, selectionArgs)

    /** Clears a previously set flag corresponding to empty db creation */
    @WorkerThread
    fun clearEmptyDbFlag() {
        createDbIfNotExists()
        clearFlagEmptyDbCreated()
    }

    /**
     * Updates the generated item ID counter for test, so that next item addition doesn't conflict
     * with preloaded data
     */
    @VisibleForTesting
    fun updateMaxIdForTest(itemId: Int) {
        createDbIfNotExists()
        openHelper.updateMaxId(itemId)
    }

    /** Generates an id to be used for new item in the favorites table */
    @WorkerThread
    fun generateNewItemId(): Int {
        createDbIfNotExists()
        return openHelper.generateNewItemId()
    }

    /** Generates an id to be used for new workspace screen */
    @get:WorkerThread
    val newScreenId: Int
        get() {
            createDbIfNotExists()
            return openHelper.newScreenId
        }

    /** Creates an empty DB clearing all existing data */
    @WorkerThread
    fun createEmptyDB() {
        createDbIfNotExists()
        openHelper.createEmptyDB(openHelper.writableDatabase)
        prefs.putSync(emptyDbCreatedKey.to(true))
    }

    /** Removes any widget which are present in the framework, but not in out internal DB */
    @WorkerThread
    fun removeGhostWidgets() {
        createDbIfNotExists()
        openHelper.removeGhostWidgets(openHelper.writableDatabase)
    }

    /** Returns a new [SQLiteTransaction] */
    @WorkerThread
    fun newTransaction(): SQLiteTransaction {
        createDbIfNotExists()
        return SQLiteTransaction(openHelper.writableDatabase)
    }

    /** Resets the launcher DB if we should reset it. */
    private fun resetLauncherDb(restoreEventLogger: LauncherRestoreEventLogger?) {
        restoreEventLogger?.sendMetricsForFailedMigration(getTable(), GRID_MIGRATION_FAILURE)
        FileLog.d(TAG, "resetLauncherDb: Migration failed: resetting launcher database")
        createEmptyDB()
        prefs.putSync(getEmptyDbCreatedKey(openHelper.databaseName).to(true))

        // Write the grid state to avoid another migration
        DeviceGridState(idp).writeToPrefs(context)
    }

    /** Determines if we should reset the DB. */
    private fun shouldResetDb(): Boolean {
        // If we already have a new DB, ignore migration
        if (prefs.get(emptyDbCreatedKey)) {
            FileLog.d(TAG, "isThereExistingDb: new DB already created, skipping migration")
            return true
        }
        if (!needsToMigrate(context, idp)) {
            FileLog.d(TAG, "isGridMigrationNecessary: no grid migration needed")
            return false
        }
        val targetDbName = DeviceGridState(idp).dbFile
        val currentDbName = openHelper.databaseName
        if (TextUtils.equals(targetDbName, currentDbName)) {
            FileLog.e(
                TAG,
                "isCurrentDbSameAsTarget: target db is same as current current db: $currentDbName target db: $targetDbName",
            )
            return true
        }
        return false
    }

    /** Migrates the DB. If the migration failed, it clears the DB. */
    @Throws(Exception::class)
    fun attemptMigrateDb(
        restoreEventLogger: LauncherRestoreEventLogger?,
        modelDelegate: ModelDelegate,
    ) {
        createDbIfNotExists()
        if (shouldResetDb()) {
            resetLauncherDb(restoreEventLogger)
            return
        }

        val oldHelper = openHelper

        try {
            // This is the current grid we have, given by the mContext
            val srcDeviceState = DeviceGridState(context)
            // This is the state we want to migrate to that is given by the idp
            val destDeviceState = DeviceGridState(idp)

            val isAfterRestore = get(context).get(LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE)
            val gridSizeMigrationLogic = migrationLogicFactory.get()

            // Check if the migration path from source to destination is valid before migrating.
            val sourceGridMigrationOption = from(srcDeviceState.columns, srcDeviceState.rows)
            val destinationGridMigrationOption = from(destDeviceState.columns, destDeviceState.rows)
            if (
                sourceGridMigrationOption != null &&
                    destinationGridMigrationOption != null &&
                    sourceGridMigrationOption.canMigrate(
                        destinationGridMigrationOption,
                        isAfterRestore,
                    )
            ) {
                openHelper = createDatabaseHelper(forMigration = true, destDeviceState.dbFile)
                gridSizeMigrationLogic.migrateGrid(
                    srcDeviceState,
                    destDeviceState,
                    openHelper,
                    oldHelper.writableDatabase,
                    modelDelegate,
                )
            } else {
                FileLog.e(
                    TAG,
                    ("Cannot migrate from source: " +
                        srcDeviceState +
                        " to destination: " +
                        destDeviceState),
                )
            }
        } catch (e: Exception) {
            resetLauncherDb(restoreEventLogger)
            throw Exception("attemptMigrateDb: Failed to migrate grid", e)
        } finally {
            if (openHelper !== oldHelper) {
                oldHelper.close()
            }
        }
    }

    /** Returns the underlying model database */
    @Deprecated("Use [getTable] instead")
    val db: SQLiteDatabase
        get() {
            createDbIfNotExists()
            return openHelper.writableDatabase
        }

    /** Returns the underlying [SQLiteTable] for making persistent model changes */
    fun getTable(): SQLiteTable {
        createDbIfNotExists()
        return WriteProtectedSQLiteTable(openHelper.writableDatabase, TABLE_NAME)
    }

    private fun clearFlagEmptyDbCreated() {
        prefs.removeSync(emptyDbCreatedKey)
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     * 1) From the app restrictions
     * 2) From a package provided by play store
     * 3) From a partner configuration APK, already in the system image
     * 4) The default configuration for the particular device
     *
     * Returns true if default favorites was loaded, false if a valid data already exists
     */
    @WorkerThread
    @Synchronized
    fun loadDefaultFavoritesIfNecessary(): Boolean {
        createDbIfNotExists()

        if (prefs.get(emptyDbCreatedKey)) {
            Log.d(TAG, "loading default workspace")

            val widgetHolder = openHelper.newLauncherWidgetHolder()
            try {
                var loader =
                    layoutParserFactory.createExternalLayoutParser(widgetHolder, openHelper)

                val usingExternallyProvidedLayout = loader != null
                if (loader == null) {
                    loader = getDefaultLayoutParser(widgetHolder)
                }

                // There might be some partially restored DB items, due to buggy restore logic in
                // previous versions of launcher.
                openHelper.createEmptyDB(openHelper.writableDatabase)
                // Populate favorites table with initial favorites
                if (
                    (openHelper.loadFavorites(openHelper.writableDatabase, loader) <= 0) &&
                        usingExternallyProvidedLayout
                ) {
                    // Unable to load external layout. Cleanup and load the internal layout.
                    openHelper.createEmptyDB(openHelper.writableDatabase)
                    openHelper.loadFavorites(
                        openHelper.writableDatabase,
                        getDefaultLayoutParser(widgetHolder),
                    )
                }
                clearFlagEmptyDbCreated()
                return true
            } finally {
                widgetHolder.destroy()
            }
        }
        return false
    }

    private fun getDefaultLayoutParser(widgetHolder: LauncherWidgetHolder): DefaultLayoutParser {
        return DefaultLayoutParser(
            context,
            widgetHolder,
            openHelper,
            context.resources,
            idp.defaultLayoutId,
        )
    }

    private val emptyDbCreatedKey: ConstantItem<Boolean>
        get() = getEmptyDbCreatedKey(openHelper.databaseName)

    /**
     * Re-composite given key in respect to database. If the current db is
     * [LauncherFiles.LAUNCHER_DB], return the key as-is. Otherwise append the db name to given key.
     * e.g. consider key="EMPTY_DATABASE_CREATED", dbName="minimal.db", the returning string will be
     * "EMPTY_DATABASE_CREATED@minimal.db".
     */
    private fun getEmptyDbCreatedKey(dbName: String): ConstantItem<Boolean> {
        val key =
            if (TextUtils.equals(dbName, LauncherFiles.LAUNCHER_DB)) EMPTY_DATABASE_CREATED
            else "$EMPTY_DATABASE_CREATED@$dbName"
        return backedUpItem(key, false, /* default value */ ENCRYPTED)
    }

    /** Returns the serial number for the provided user */
    fun getSerialNumberForUser(user: UserHandle): Long {
        return userCache.getSerialNumberForUser(user)
    }

    companion object {
        private const val TAG = "ModelDbController"

        private const val EMPTY_DATABASE_CREATED = "EMPTY_DATABASE_CREATED"
        const val EXTRA_DB_NAME: String = "db_name"

        fun getLayoutUri(authority: String?, ctx: Context): Uri {
            val grid = getIDP(ctx)
            return Builder()
                .scheme("content")
                .authority(authority)
                .path("launcher_layout")
                .appendQueryParameter("version", "1")
                .appendQueryParameter("gridWidth", grid.numColumns.toString())
                .appendQueryParameter("gridHeight", grid.numRows.toString())
                .appendQueryParameter("hotseatSize", grid.numDatabaseHotseatIcons.toString())
                .build()
        }
    }
}
