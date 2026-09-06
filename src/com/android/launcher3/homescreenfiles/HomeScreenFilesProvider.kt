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

import android.net.Uri
import android.os.UserHandle
import androidx.annotation.VisibleForTesting
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.ListenableStream
import java.util.concurrent.CompletableFuture

/** Represents a single file or folder item queried by [HomeScreenFilesProvider]. */
data class HomeScreenFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val user: UserHandle,
)

/** An interface for managing file items to be shown on the home screen. */
interface HomeScreenFilesProvider {
    /** Icon provider for home screen files/folders. */
    val iconProvider: HomeScreenFilesIconProvider

    /** A stream of updates to file items shown on the home screen. */
    val updates: ListenableStream<HomeScreenFilesUpdate>

    /** Resolves when the home screen files provider is ready to service calls. */
    fun onReady(): CompletableFuture<Void>

    /** Returns whether a new folder can be created. */
    fun canCreateNewFolder(): Boolean

    /** Attempts to asynchronously create a new folder. */
    fun createNewFolder(extras: HomeScreenFilesUpdate.Extras): CompletableFuture<Boolean>

    /**
     * Returns whether all URIs in the specified list can be moved to the home screen.
     *
     * @param uriList The list of URIs to consider.
     * @return Whether all URIs in the list can be moved.
     */
    fun canMoveToHomeScreen(uriList: List<Uri>?): Boolean

    /**
     * Attempts to asynchronously move all URIs in the specified list to the home screen.
     *
     * @param uriList The list of URIs to move.
     * @param extras The extras to be applied when adding items to the launcher model.
     * @param relativeFolderPath If specified, URIs will be moved to
     *   [HOME_SCREEN_FOLDER_RELATIVE_PATH]/{relativeFolderPath}.
     * @return List of futures indicating the success or failure of each move attempt. Futures are
     *   provided in the same order as the original list of URIs.
     */
    fun moveToHomeScreen(
        uriList: List<Uri>,
        extras: HomeScreenFilesUpdate.Extras,
        relativeFolderPath: String?,
    ): List<CompletableFuture<Boolean>>

    /**
     * Permanently deletes a single file/folder in the [HOME_SCREEN_FOLDER_RELATIVE_PATH] directory.
     *
     * @param uri The URI of the item to be deleted.
     */
    fun deletePermanently(uri: Uri)

    /**
     * Moves a single file/folder in the [HOME_SCREEN_FOLDER_RELATIVE_PATH] directory to trash.
     *
     * @param name The name of the item to be deleted.
     * @return The new path of the item after being moved to trash, or `null` if the operation
     *   failed.
     */
    fun moveToTrash(name: String): CompletableFuture<String?>

    /**
     * Restores a single file/folder from trash to its original location.
     *
     * @param trashPath The path of the item in trash.
     * @return The success/failure of the restore attempt.
     */
    fun restoreFromTrash(trashPath: String): CompletableFuture<Boolean>

    /** Returns all eligible file items to be shown on the home screen. */
    fun query(): CompletableFuture<Map<Uri, HomeScreenFile>>

    /**
     * Renames a single file/folder in the [HOME_SCREEN_FOLDER_RELATIVE_PATH] directory.
     *
     * @param uri The URI of the item to be renamed.
     * @param name The new name of the item.
     * @return The success/failure of the rename attempt.
     */
    fun rename(uri: Uri, name: String): CompletableFuture<Boolean>

    companion object {
        @JvmField
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        val HOME_SCREEN_FOLDER_RELATIVE_PATH = "Home screen/"

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getHomeScreenFilesProvider)
    }
}
