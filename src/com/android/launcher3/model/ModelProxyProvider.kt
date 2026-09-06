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

package com.android.launcher3.model

import android.Manifest.permission.ACCESS_LAUNCHER_DATA
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.text.TextUtils
import android.util.Log
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_ID
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_PROVIDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.util.ContentProviderProxy.ProxyProvider
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LayoutImportExportHelper
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import javax.inject.Inject

/**
 * This provider facilitates the import and export of home screen metadata within the Launcher's
 * database. This includes managing shortcut placement, launch intents, and labels. Each row in the
 * Launcher3 database corresponds to a single item on the workspace (often referred to as
 * "favorites").
 *
 * <p>Only applications installed on the system partition or those possessing the platform's
 * signature can access this provider.
 *
 * <p>After data insertion into the Launcher's database, a row may be deleted during Launcher
 * startup if any of these conditions are true. This list is not exhaustive:
 * <ul>
 * <li>Missing or invalid launch intent: This includes a null intent, one without a target package,
 *   or one referencing a non-existent activity.</li>
 * <li>When an app previously installed doesn't exist or fails to restore properly.</li>
 * <li>If ShortcutManager/WidgetManager haven't finished restoring by the time Launcher loads</li>
 * <li>If the App Store hasn't finished restoring when Launcher starts loading.</li>
 * <li>The item is linked to a profile that no longer exists (e.g., a deleted work profile).</li>
 * <li>A widget's metadata specifies an invalid height or width.</li>
 * <li>Incorrect item container: For instance, widgets can only be on the desktop or hotseat<li>
 * <li>If the launcher is restoring, but the item isn't flagged as restoring/installing.</li>
 * <li>If a widget fails to inflate within AppWidgetManagerService for any reason.</li>
 * <li>When items in the database occupy the same or overlapping positions.</li>
 * </ul>
 *
 * <p>Although query, bulkInsert, and insert methods are available, their direct use is not
 * recommended. Instead, prefer the XML-based insertion methods accessible via the {@code call()}
 * method. This preference is due to several reasons, including:
 * <ul>
 * <li>The insert methods can can lead to unpredictable behavior if invoked while Launcher is in the
 *   process of loading.</li>
 * <li>The XML approach allows for custom tags which can be ingested for proprietary variants of
 *   workspace items.</li>
 * <li>The XML method clears old data and inserts new data as a single, atomic action. Direct
 *   Delete/Insert usage requires at least 2 binder calls that are not atomic.</li>
 * </ul>
 *
 * <p>It's important to note that the XML format has non-obvious and strict requirements. For
 * instance:
 * <ul>
 * <li>The Launcher uses the "screen" value to determine hot seat placement order.</li>
 * <li>Conversely, for items within a folder, the rank db column dictates their placement
 *   order.</li>
 * <li>When an item is on the top-level workspace (i.e., not in the hot seat), the "screen" value
 *   signifies its workspace page.</li>
 * </ul>
 *
 * <p>During a launcher restore, a grid migration might occur, either due to user preference or
 * design updates. This migration can cause items to be repositioned or moved to different pages,
 * depending on the old and new grid sizes. Therefore, precise placement cannot be guaranteed in all
 * situations.
 */
class ModelProxyProvider
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val model: LauncherModel,
    private val modelDbController: ModelDbController,
    @Ui private val uiExecutor: Executor,
    private val layoutImportExportHelper: LayoutImportExportHelper,
) : ProxyProvider {

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        executeControllerTask(uri, selection, selectionArgs) { parsedWhere, parsedArgs ->
            modelDbController.query(projection?.asSubtype(), parsedWhere, parsedArgs, sortOrder)
        }

    override fun insert(uri: Uri, values: ContentValues): Uri? =
        executeControllerTask(uri) { _, _ ->
            val itemType = values.getAsInteger(ITEM_TYPE) ?: return@executeControllerTask null

            // 1. Ensure that externally added items have a valid item id. Don't update Folder ids
            // because items inside the folder need to reference the original ID as their container
            // id, or else be deleted.
            if (
                values.containsKey(_ID) &&
                    ITEM_TYPE_FOLDER != itemType &&
                    ITEM_TYPE_APP_GROUP != itemType
            )
                values.put(_ID, modelDbController.generateNewItemId())

            // 2. In the case of an app widget, and if no app widget id is specified, we
            // attempt allocate and bind the widget.
            if (itemType == ITEM_TYPE_APPWIDGET && !values.containsKey(APPWIDGET_ID)) {
                values.put(
                    APPWIDGET_ID,
                    ComponentName.unflattenFromString(values.getAsString(APPWIDGET_PROVIDER))
                        ?.bindAppWidgetId() ?: return@executeControllerTask null,
                )
            }
            ContentUris.withAppendedId(uri, modelDbController.insert(values).toLong())
        }

    private fun ComponentName.bindAppWidgetId(): Int? {
        val widgetHolder = LauncherWidgetHolder.newInstance(context)
        try {
            val id = widgetHolder.allocateAppWidgetId()
            if (!AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id, this)) {
                widgetHolder.deleteAppWidgetId(id)
                return null
            }
            return id
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to initialize external widget", e)
            return null
        } finally {
            // Necessary to destroy the holder to free up possible activity context
            widgetHolder.destroy()
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        executeControllerTask(uri, selection, selectionArgs) { parsedWhere, parsedArgs ->
            modelDbController.delete(parsedWhere, parsedArgs)
        }

    override fun update(
        uri: Uri,
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<out String>?,
        extras: Bundle?,
    ): Int =
        executeControllerTask(uri, selection, selectionArgs) { parsedWhere, parsedArgs ->
            modelDbController.update(values, parsedWhere, parsedArgs)
        }

    /** Parses the [url] and executes the [task] with the parsed where and arg clause. */
    private inline fun <reified T> executeControllerTask(
        url: Uri,
        where: String? = null,
        args: Array<out String>? = null,
        crossinline task: (String?, Array<String>?) -> T,
    ): T {
        require(Binder.getCallingPid() != Process.myPid()) {
            "Same process should call model directly"
        }
        val (parsedWhere, parsedArgs) =
            when (url.pathSegments.size) {
                1 -> where to args?.asSubtype()
                2 -> {
                    require(TextUtils.isEmpty(where)) { "WHERE clause not supported: $url" }
                    "_id=${ContentUris.parseId(url)}" to null
                }
                else -> throw IllegalArgumentException("Invalid URI: $url")
            }

        try {
            val future =
                FutureTask(
                    Callable {
                        task.invoke(parsedWhere, parsedArgs).also {
                            if (it is Int && it > 0)
                                uiExecutor.execute { model.reloadIfActive("ModelProxyProvider") }
                        }
                    }
                )
            Executors.MODEL_EXECUTOR.execute(future)
            return future.get()
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    private fun Array<out String>.asSubtype(): Array<String> = this as Array<String>

    /**
     * The caller must have the read or write permission for this content provider to access the
     * "call" method at all. We also enforce the appropriate per-method permissions.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (context.checkCallingOrSelfPermission(ACCESS_LAUNCHER_DATA) != PERMISSION_GRANTED) {
            throw SecurityException("Caller doesn't have read permission")
        }
        return when (method) {
            METHOD_EXPORT_LAYOUT_XML -> {
                val resultFuture = layoutImportExportHelper.exportModelDbAsXmlFuture()
                Bundle().apply {
                    try {
                        putString(KEY_LAYOUT, resultFuture.get())
                        putString(KEY_RESULT, SUCCESS)
                    } catch (e: Exception) {
                        putString(KEY_RESULT, FAILURE)
                    }
                }
            }
            METHOD_IMPORT_LAYOUT_XML -> {
                layoutImportExportHelper.importModelFromXml(arg!!)
                Bundle().apply { putString(KEY_RESULT, SUCCESS) }
            }
            else -> null
        }
    }

    companion object {
        private const val TAG = "ModelProvider"

        // Method API For Provider#call method.
        const val METHOD_EXPORT_LAYOUT_XML = "EXPORT_LAYOUT_XML"
        const val METHOD_IMPORT_LAYOUT_XML = "IMPORT_LAYOUT_XML"
        const val KEY_RESULT = "KEY_RESULT"
        const val KEY_LAYOUT = "KEY_LAYOUT"
        const val SUCCESS = "success"
        const val FAILURE = "failure"
    }
}
