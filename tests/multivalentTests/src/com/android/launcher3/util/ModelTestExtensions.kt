package com.android.launcher3.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import android.os.Process
import android.util.Log
import android.util.SparseArray
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_ID
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_PROVIDER
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_SOURCE
import com.android.launcher3.LauncherSettings.Favorites.CELLX
import com.android.launcher3.LauncherSettings.Favorites.CELLY
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.INTENT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID
import com.android.launcher3.LauncherSettings.Favorites.RESTORED
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.LauncherSettings.Favorites.SPANX
import com.android.launcher3.LauncherSettings.Favorites.SPANY
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.LauncherSettings.Favorites.TITLE
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.UtilitiesKt.isPersistedModelItem
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.views.ActivityContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ModelTestExtensions {

    private const val TAG = "ModelTestExtensions"

    /** Clears and reloads Launcher db to cleanup the workspace */
    fun LauncherModel.clearModelDb() {
        // Load the model once so that there is no pending migration:
        loadModelSync()
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            modelDbController.run {
                attemptMigrateDb(null /* restoreEventLogger */, modelDelegate)
                createEmptyDB()
                clearEmptyDbFlag()
            }
        }
        // Reload model
        loadModelSync()
    }

    /** Loads the model in memory synchronously */
    fun LauncherModel.loadModelSync() {
        // Prevent taskbar recreation from canceling loader task scheduled from test.
        forceReload("loadModelSync").toCompletableFuture().get()
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        TestUtil.runOnExecutorSync(getTaskbarUiThread()) {}
    }

    /** Adds and commits a new item to Launcher.db */
    fun LauncherModel.addItem(
        title: String = "LauncherTestApp",
        intent: String =
            "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=com.google.android.apps.nexuslauncher.tests/com.android.launcher3.testcomponent.BaseTestingActivity;launchFlags=0x10200000;end",
        type: Int = ITEM_TYPE_APPLICATION,
        restoreFlags: Int = 0,
        screen: Int = 0,
        container: Int = CONTAINER_DESKTOP,
        x: Int,
        y: Int,
        spanX: Int = 1,
        spanY: Int = 1,
        id: Int = 0,
        profileId: Int = Process.myUserHandle().identifier,
        appWidgetId: Int = -1,
        appWidgetSource: Int = -1,
        appWidgetProvider: String? = null,
    ) {
        loadModelSync()
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            val controller: ModelDbController = modelDbController
            controller.attemptMigrateDb(null /* restoreEventLogger */, modelDelegate)
            modelDbController.newTransaction().use { transaction ->
                val values =
                    ContentValues().apply {
                        values[_ID] = id
                        values[TITLE] = title
                        values[PROFILE_ID] = profileId
                        values[CONTAINER] = container
                        values[SCREEN] = screen
                        values[CELLX] = x
                        values[CELLY] = y
                        values[SPANX] = spanX
                        values[SPANY] = spanY
                        values[ITEM_TYPE] = type
                        values[RESTORED] = restoreFlags
                        values[INTENT] = intent
                        values[APPWIDGET_ID] = appWidgetId
                        values[APPWIDGET_SOURCE] = appWidgetSource
                        values[APPWIDGET_PROVIDER] = appWidgetProvider
                    }
                // Migrate any previous data so that the DB state is correct
                controller.insert(values)
                transaction.commit()
            }
        }
    }

    @JvmStatic
    val SandboxApplication.bgDataModel
        get() = appComponent.testableModelState.dataModel

    /**
     * Total number of items which are persisted in the model. This excludes any predicted item and
     * any dynamically injected item with an AAPT generated id.
     */
    @JvmStatic
    fun Iterable<ItemInfo>.countPersistedModelItems() = count {
        it.isPersistedModelItem() && it.container >= CONTAINER_HOTSEAT
    }

    /** Creates an in-memory sqlite DB and initializes with the data in [insertFile] */
    fun createInMemoryDb(context: Context, insertFile: String): SQLiteDatabase =
        SQLiteDatabase.createInMemory(SQLiteDatabase.OpenParams.Builder().build()).also { db ->
            BufferedReader(
                    InputStreamReader(
                        InstrumentationRegistry.getInstrumentation().context.assets.open(insertFile)
                    )
                )
                .lines()
                .forEach { sqlStatement -> db.execSQL(sqlStatement) }
            val mainProfileId =
                UserCache.INSTANCE.get(context).getSerialNumberForUser(Process.myUserHandle())
            if (mainProfileId != 0L) {
                db.execSQL(
                    "UPDATE $TABLE_NAME SET $PROFILE_ID = $mainProfileId WHERE $PROFILE_ID = 0;"
                )
            }
        }

    /** Initializes [BgDataModel.itemsIdMap] with provided [items] */
    fun BgDataModel.initItems(vararg items: ItemInfo) {
        dataLoadComplete(
            allItems = SparseArray<ItemInfo>().apply { items.forEach { this[it.id] = it } },
            reason = "test-initItems",
        )
    }

    /** Sets the workspace as an empty layout and loads it */
    @JvmStatic
    fun Context.setEmptyModelLayout() {
        // Launcher model reverts to using default layout when xml is empty. As a wrokaround we add
        // an invalid icon at a very far off location
        setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(Int.MAX_VALUE, Int.MAX_VALUE, 0)
                .putApp("missing-app", null)
        )
    }

    /** Sets and loads the workspace layout */
    @JvmStatic
    fun Context.setModelLayout(builder: LauncherLayoutBuilder) = setModelLayout(builder.build())

    fun Context.setModelLayout(xmlString: String) {
        val model = appComponent.testableModelState.model
        appComponent.layoutParserFactory.overrideXmlLayout(xmlString).use {
            TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
                try {
                    model.modelDbController.createEmptyDB()
                } catch (e: SQLiteReadOnlyDatabaseException) {
                    // This issue has only been observed in tests so far, likely due
                    // to less strict threading for accessing and writing to the
                    // launcher test DB.
                    Log.w(TAG, "Failed to clear Launcher DB. It was already deleted.", e)
                }
            }
            TestUtil.runOnExecutorSync(MAIN_EXECUTOR) { model.forceReload("setModelLayout") }
            model.loadModelSync()
        }
    }

    /** Preloads the provided data in model repository */
    fun Context.preloadModelData(vararg items: ItemInfo) {
        val state = appComponent.testableModelState
        state.dataModel.dataLoadComplete(
            allItems = SparseArray<ItemInfo>().apply { items.forEach { this[it.id] = it } },
            reason = "test-preloadModelData",
        )
        state.dbController.updateMaxIdForTest(items.maxOf { it.id })
        preloadAppList(
            items
                .filterIsInstance<WorkspaceItemInfo>()
                .map { item -> AppInfo(item.targetComponent, item.title, item.user, item.intent) }
                .toTypedArray()
        )
    }

    /** Preloads the provided data in model repository */
    @JvmOverloads
    @JvmStatic
    fun Context.preloadAppList(apps: Array<AppInfo>, flags: Int = 0) =
        appComponent.testableModelState.appsRepo.dispatchChange(AppsListData(apps, flags))

    /** Similar to [Context.preloadAppList] but ensures that the AppStore is also initialized */
    @JvmOverloads
    @JvmStatic
    fun ActivityContext.preloadAppStore(apps: Array<AppInfo>, flags: Int = 0) {
        if (LauncherModel.useModelRepositoryBinding()) {
            asContext().preloadAppList(apps, flags)
            TestUtil.runOnExecutorSync(uiExecutor) { activityComponent.appsStore }
        } else {
            activityComponent.appsStore.setApps(
                apps,
                flags,
                AppsListData(apps, flags).packageUserKeyToUidMap,
            )
        }
    }
}
