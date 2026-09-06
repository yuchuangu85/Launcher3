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

import android.content.ClipDescription.MIMETYPE_UNKNOWN
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DATA
import android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
import android.provider.MediaStore.Files.FileColumns.IS_TRASHED
import android.provider.MediaStore.Files.FileColumns.MIME_TYPE
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import android.provider.MediaStore.Files.FileColumns._ID
import android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import androidx.core.database.getStringOrNull
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.ThreadPool
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.MutableListenableStream
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.runAsync
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/** MediaStore-based implementation of [HomeScreenFilesProvider]. */
class HomeScreenFilesMediaStoreProvider
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    @ThreadPool private val executorService: ExecutorService,
    @Assisted private val fileFactory: (path: String) -> File,
    private val environmentWrapper: EnvironmentWrapper,
    override val iconProvider: HomeScreenFilesIconProvider,
    lifecycle: DaggerSingletonTracker,
) : HomeScreenFilesProvider {
    override val updates = MutableListenableStream<HomeScreenFilesUpdate>()

    // Future that completes when the external storage directory mounts.
    private val externalStorageDirectoryMountedFuture = CompletableFuture<Void>()

    // Cache of extras to be applied during the next scheduled update task for a given URI. Extras
    // inform the task of special behaviors/properties to apply when updating the launcher model.
    private val inProgressChangeExtras = ConcurrentHashMap<Uri, HomeScreenFilesUpdate.Extras>()

    // Tracks URI movements originating from calls to [#moveToHomeScreen()]. This allows us to:
    // (1) Disallow overlapping attempts to move a given URI, and
    // (2) Reconcile media store URIs with URI aliases from other content provider authorities.
    private val inProgressMoveToHomeScreenUriAliases = ConcurrentHashMap<Uri, Uri>()

    init {
        if (environmentWrapper.isExternalStorageDirectoryMounted()) {
            externalStorageDirectoryMountedFuture.complete(null)
        }

        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                    if (selfChange || uri == null) {
                        return
                    }

                    // NOTE: The media provider dispatches an event during both mount and unmount.
                    with(externalStorageDirectoryMountedFuture) {
                        if (!isDone && environmentWrapper.isExternalStorageDirectoryMounted()) {
                            complete(null)
                        }
                    }

                    if (!uri.hasIdSegment()) {
                        return
                    }

                    val uriAlias = inProgressMoveToHomeScreenUriAliases.remove(uri)

                    updates.dispatchValue(
                        HomeScreenFilesUpdate(
                            supplyAsync({ query(uri) }, executorService).thenApply { file ->
                                buildMap {
                                    put(uri, file)
                                    uriAlias?.run { put(this, file) }
                                }
                            },
                            Process.myUserHandle(),
                            inProgressChangeExtras.remove(uri)
                                ?: uriAlias?.run(inProgressChangeExtras::remove)
                                ?: HomeScreenFilesUpdate.Extras.builder().build(),
                        )
                    )
                }
            }

        val uri = MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.registerContentObserver(uri, true, observer)
        lifecycle.addCloseable { context.contentResolver.unregisterContentObserver(observer) }
    }

    override fun onReady(): CompletableFuture<Void> =
        externalStorageDirectoryMountedFuture.thenRunAsync({}, executorService)

    override fun canCreateNewFolder(): Boolean =
        externalStorageDirectoryMountedFuture.isDone &&
            !externalStorageDirectoryMountedFuture.isCancelled &&
            !externalStorageDirectoryMountedFuture.isCompletedExceptionally

    override fun createNewFolder(extras: HomeScreenFilesUpdate.Extras): CompletableFuture<Boolean> {
        return supplyAsync(
            {
                if (!canCreateNewFolder()) {
                    Log.e(TAG, "Unable to create folder due to unmet preconditions")
                    return@supplyAsync false
                }

                try {
                    // NOTE: The media provider will create a disambiguated folder name if needed so
                    // as to ensure uniqueness (e.g. "New folder" -> "New folder (1)").
                    val uri =
                        context.contentResolver.insert(
                            MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY),
                            ContentValues().apply {
                                put(
                                    DISPLAY_NAME,
                                    context.getString(R.string.default_new_folder_name),
                                )
                                put(MIME_TYPE, MIME_TYPE_DIR)
                                put(RELATIVE_PATH, HOME_SCREEN_FOLDER_RELATIVE_PATH)
                            },
                        )

                    if (uri == null) {
                        Log.e(
                            TAG,
                            "Unable to create new folder due to failure to insert into media store",
                        )
                        return@supplyAsync false
                    }

                    // Cache extras to be applied during the next scheduled update task for [uri].
                    // These will be used when adding the new folder to the launcher model.
                    inProgressChangeExtras[uri] = extras

                    context.contentResolver.query(uri, arrayOf(DATA), null, null).use { c ->
                        if (c == null || !c.moveToFirst()) {
                            Log.e(
                                TAG,
                                "Unable to create new folder due to failure to query media store",
                            )
                            return@supplyAsync false
                        }

                        // NOTE: Insertion into the media store doesn't guarantee the creation of
                        // the new folder on the file system because it is empty. To ensure new
                        // folder creation, we must do so explicitly using file system operations.
                        val folder = fileFactory.invoke(c.getString(c.getColumnIndexOrThrow(DATA)))
                        if (!folder.exists() && !folder.mkdirs()) {
                            Log.e(TAG, "Unable to create new folder due to 'File#mkdirs()' failure")
                            return@supplyAsync false
                        }

                        return@supplyAsync true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to create new folder due to exception.", e)
                    return@supplyAsync false
                }
            },
            executorService,
        )
    }

    override fun canMoveToHomeScreen(uriList: List<Uri>?): Boolean =
        uriList?.run { isNotEmpty() && all { uri -> canMoveToHomeScreen(uri) } } == true

    /** NOTE: Currently only URIs which can be resolved by the media store are supported. */
    private fun canMoveToHomeScreen(uri: Uri): Boolean =
        isExternalStorageProviderUri(uri) || isExternalPrimaryMediaStoreUri(uri)

    override fun moveToHomeScreen(
        uriList: List<Uri>,
        extras: HomeScreenFilesUpdate.Extras,
        relativeFolderPath: String?,
    ): List<CompletableFuture<Boolean>> =
        uriList.map { uri: Uri ->
            supplyAsync({ moveToHomeScreen(uri, extras, relativeFolderPath) }, executorService)
        }

    @WorkerThread
    private fun moveToHomeScreen(
        uri: Uri,
        extras: HomeScreenFilesUpdate.Extras,
        relativeFolderPath: String?,
    ): Boolean {
        val mediaUri = getExternalPrimaryMediaStoreUri(context, uri)
        if (mediaUri == null) {
            Log.e(
                TAG,
                "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' due to unsupported URI",
            )
            return false
        }

        // NOTE: Overlapping move attempts for a given URI are disallowed.
        if (inProgressMoveToHomeScreenUriAliases.putIfAbsent(mediaUri, uri) != null) {
            Log.e(
                TAG,
                "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' due to overlapping attempt",
            )
            return false
        }

        // Cache extras to be applied during the next scheduled update task for [mediaUri]. These
        // will be used when adding the new file to the launcher model.
        inProgressChangeExtras[mediaUri] = extras

        var success = false
        try {
            val file = query(mediaUri, selection = null, selectionArgs = null)
            if (file == null) {
                Log.e(
                    TAG,
                    "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' due to inability to " +
                        "query the backing file.",
                )
            } else {
                // NOTE: The selection criteria below prevents moving a URI to a path it already
                // occupies; MediaProvider has additional protections to prevent recursive moves.
                val relativePath = "$HOME_SCREEN_FOLDER_RELATIVE_PATH${relativeFolderPath ?: ""}"
                success =
                    (context.contentResolver.update(
                        /*uri=*/ mediaUri,
                        /*contentValues=*/ ContentValues().apply {
                            put(RELATIVE_PATH, relativePath)
                            if (file.isDirectory) {
                                put(MIME_TYPE, MIME_TYPE_DIR)
                            }
                        },
                        /*where=*/ "$RELATIVE_PATH != ?",
                        /*selectionArgs=*/ arrayOf(relativePath),
                    ) == 1)
                if (!success) {
                    Log.e(
                        TAG,
                        "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' possibly due to " +
                            "unmet selection criteria or the media provider itself enforcing " +
                            "additional unmet conditions",
                    )
                }
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to move to '$HOME_SCREEN_FOLDER_RELATIVE_PATH' due to exception", e)
        } finally {
            if (!success) {
                inProgressChangeExtras.remove(mediaUri)
                inProgressMoveToHomeScreenUriAliases.remove(mediaUri)
            }
        }
        return success
    }

    override fun deletePermanently(uri: Uri) {
        runAsync(
                {
                    context.contentResolver.delete(
                        uri,
                        QUERY_DEFAULT_SELECTION,
                        QUERY_DEFAULT_SELECTION_ARGS,
                    )
                },
                executorService,
            )
            .exceptionally {
                Log.e(TAG, "Unable to permanently delete a single file or folder", it)
                null
            }
    }

    override fun moveToTrash(name: String): CompletableFuture<String?> {
        return supplyAsync(
                {
                    val path =
                        environmentWrapper
                            .getExternalStorageDirectory()
                            .resolve(HOME_SCREEN_FOLDER_RELATIVE_PATH)
                            .resolve(name)
                            .absolutePath
                    MediaStore.trashFile(context.contentResolver, path)
                },
                executorService,
            )
            .exceptionally {
                Log.e(TAG, "Unable to move a single file or folder to trash", it)
                null
            }
    }

    override fun restoreFromTrash(trashPath: String): CompletableFuture<Boolean> {
        return supplyAsync(
                {
                    MediaStore.restoreFileFromTrash(context.contentResolver, trashPath, null)
                    true
                },
                executorService,
            )
            .exceptionally {
                Log.e(TAG, "Unable to restore a single file or folder from trash", it)
                false
            }
    }

    /** Returns all file items presented in [HOME_SCREEN_FOLDER_RELATIVE_PATH]. */
    override fun query(): CompletableFuture<Map<Uri, HomeScreenFile>> {
        return supplyAsync(
            {
                val result = mutableMapOf<Uri, HomeScreenFile>()
                try {
                    context.contentResolver
                        .query(
                            MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY),
                            arrayOf(_ID).plus(QUERY_DEFAULT_PROJECTION),
                            QUERY_DEFAULT_SELECTION,
                            QUERY_DEFAULT_SELECTION_ARGS,
                            null,
                            null,
                        )
                        ?.use {
                            val idColumnIndex = it.getColumnIndex(_ID)
                            val displayNameColumnIndex = it.getColumnIndex(DISPLAY_NAME)
                            val mimeTypeColumnIndex = it.getColumnIndex(MIME_TYPE)
                            val dataColumnIndex = it.getColumnIndex(DATA)
                            val user = Process.myUserHandle()

                            while (it.moveToNext()) {
                                val id = it.getLong(idColumnIndex)
                                val uri =
                                    MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY, id)
                                val mimeType = it.getStringOrNull(mimeTypeColumnIndex)
                                result[uri] =
                                    HomeScreenFile(
                                        uri = uri,
                                        displayName = it.getString(displayNameColumnIndex),
                                        mimeType = mimeType,
                                        isDirectory =
                                            fileFactory.invoke(it.getString(dataColumnIndex)).let {
                                                file ->
                                                // Defer to [mimeType] when the file does not yet
                                                // exist.
                                                (file.exists() && file.isDirectory) ||
                                                    (mimeType == MIME_TYPE_DIR)
                                            },
                                        user = user,
                                    )
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query '$HOME_SCREEN_FOLDER_RELATIVE_PATH'", e)
                }
                result
            },
            executorService,
        )
    }

    /** Queries a single file from MediaStore by its URI. */
    @WorkerThread
    private fun query(
        uri: Uri,
        selection: String? = QUERY_DEFAULT_SELECTION,
        selectionArgs: Array<String>? = QUERY_DEFAULT_SELECTION_ARGS,
    ): HomeScreenFile? {
        if (!isExternalPrimaryMediaStoreUri(uri)) {
            return null
        }
        return runCatching {
                context.contentResolver
                    .query(uri, QUERY_DEFAULT_PROJECTION, selection, selectionArgs, null, null)
                    ?.use {
                        if (it.count == 1) {
                            it.moveToFirst()
                            val displayNameColumnIndex = it.getColumnIndex(DISPLAY_NAME)
                            val dataColumnIndex = it.getColumnIndex(DATA)
                            val mimeTypeColumnIndex = it.getColumnIndex(MIME_TYPE)
                            val mimeType = it.getStringOrNull(mimeTypeColumnIndex)
                            HomeScreenFile(
                                uri = uri,
                                displayName = it.getString(displayNameColumnIndex),
                                mimeType = mimeType,
                                isDirectory =
                                    fileFactory.invoke(it.getString(dataColumnIndex)).let { f ->
                                        // Defer to [mimeType] when the file does not yet exist.
                                        (f.exists() && f.isDirectory) || (mimeType == MIME_TYPE_DIR)
                                    },
                                user = Process.myUserHandle(),
                            )
                        } else {
                            null
                        }
                    }
            }
            .getOrElse {
                Log.e(TAG, "Unable to query a single file or folder by its URI", it)
                null
            }
    }

    override fun rename(uri: Uri, name: String): CompletableFuture<Boolean> =
        supplyAsync(
            {
                val mediaStoreUri = getExternalPrimaryMediaStoreUri(context, uri)
                if (mediaStoreUri == null) {
                    Log.e(TAG, "Unable to rename due to unsupported URI")
                    return@supplyAsync false
                }

                var success = false

                try {
                    val file = query(mediaStoreUri, null, null)
                    if (file == null) {
                        Log.e(TAG, "Unable to rename due to inability to query the backing file")
                    } else {
                        success =
                            context.contentResolver.update(
                                /*uri=*/ mediaStoreUri,
                                /*contentValues=*/ ContentValues().apply {
                                    // NOTE: The media provider performs its own sanitization/
                                    // validation of the [DISPLAY_NAME] column.
                                    put(DISPLAY_NAME, name)
                                    if (file.isDirectory) {
                                        put(MIME_TYPE, MIME_TYPE_DIR)
                                    } else {
                                        MimeTypeMap.getSingleton()
                                            .getMimeTypeFromExtension(File(name).extension)
                                            .run { put(MIME_TYPE, this ?: MIMETYPE_UNKNOWN) }
                                    }
                                },
                                /*where=*/ "$RELATIVE_PATH == ?",
                                /*selectionArgs=*/ arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
                            ) == 1
                        if (!success) {
                            Log.e(
                                TAG,
                                "Unable to rename possibly due to unmet selection criteria or " +
                                    "the media provider itself enforcing additional unmet " +
                                    "conditions",
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to rename due to exception", e)
                }

                return@supplyAsync success
            },
            executorService,
        )

    companion object {
        private const val MEDIA_STORE_VALUE_FALSE = "0"
        private val QUERY_DEFAULT_PROJECTION = arrayOf(DISPLAY_NAME, MIME_TYPE, DATA)
        private const val QUERY_DEFAULT_SELECTION = "$RELATIVE_PATH = ? AND $IS_TRASHED = ?"
        private val QUERY_DEFAULT_SELECTION_ARGS =
            arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH, MEDIA_STORE_VALUE_FALSE)
        private const val TAG = "HomeScreenFilesMediaStoreProvider"

        private fun getExternalPrimaryMediaStoreUri(context: Context, uri: Uri): Uri? {
            if (isExternalPrimaryMediaStoreUri(uri)) {
                return uri
            }
            try {
                val mediaUri = MediaStore.getMediaUri(context, uri)
                when {
                    mediaUri == null -> {
                        Log.e(
                            TAG,
                            "Unable to get external primary media store URI due to null media URI",
                        )
                        return null
                    }
                    isExternalPrimaryMediaStoreUri(mediaUri) -> {
                        return mediaUri
                    }
                    else -> {
                        Log.e(
                            TAG,
                            "Unable to get external primary media store URI due to unsupported " +
                                "media URI volume",
                        )
                        return null
                    }
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Unable to get external primary media store URI due to exception", e)
                return null
            }
        }

        private fun isExternalPrimaryMediaStoreUri(uri: Uri) =
            uri.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == MediaStore.AUTHORITY &&
                runCatching { MediaStore.getVolumeName(uri) == VOLUME_EXTERNAL_PRIMARY }
                    .getOrDefault(false)

        private fun isExternalStorageProviderUri(uri: Uri?) =
            uri?.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY

        private fun Uri.hasIdSegment(): Boolean =
            runCatching { ContentUris.parseId(this) != -1L }.getOrDefault(false)
    }

    @AssistedFactory
    interface Factory {
        fun create(fileFactory: (path: String) -> File): HomeScreenFilesMediaStoreProvider
    }
}
