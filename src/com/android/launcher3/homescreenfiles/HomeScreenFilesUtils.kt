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

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import com.android.launcher3.Flags.enableHomeScreenFilesTrashing
import com.android.launcher3.Flags.showFilesOnHomeScreen
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.model.data.ItemInfo
import com.android.providers.media.flags.Flags.enableTrashAndRestoreByFilePathApi

/** Other utility methods related to managing files on the home screen. */
class HomeScreenFilesUtils {
    companion object {
        const val LAUNCH_INTENT_DEFAULT_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Returns `true` if the feature to show files on the home screen is enabled. */
        @JvmStatic
        val isFeatureEnabled: Boolean by lazy {
            showFilesOnHomeScreen() && Environment.isExternalStorageManager()
        }

        /** Returns `true` if the "Move to trash" feature is enabled. */
        fun isTrashingEnabled() =
            enableHomeScreenFilesTrashing() && enableTrashAndRestoreByFilePathApi()

        /** Returns the appropriate item type for the given [homeScreenFile]. */
        fun buildItemType(homeScreenFile: HomeScreenFile) =
            if (homeScreenFile.isDirectory) {
                ITEM_TYPE_FILE_SYSTEM_FOLDER
            } else {
                ITEM_TYPE_FILE_SYSTEM_FILE
            }

        /**
         * Creates an [Intent] to open [homeScreenFile] in the app associated with its MIME type.
         */
        @JvmOverloads
        fun buildLaunchIntent(uri: Uri, homeScreenFile: HomeScreenFile? = null) =
            Intent(Intent.ACTION_VIEW).apply {
                addFlags(LAUNCH_INTENT_DEFAULT_FLAGS)
                setDataAndType(
                    uri,
                    if (homeScreenFile?.isDirectory == true) MIME_TYPE_DIR
                    else homeScreenFile?.mimeType,
                )
            }
    }
}

/** Creates a [HomeScreenFile] from [ItemInfo]. */
val ItemInfo.homeScreenFile: HomeScreenFile?
    get() {
        return if (isFileSystemItem()) {
            HomeScreenFile(
                uri = requireNotNull(requireNotNull(intent).data),
                displayName = title?.toString() ?: "",
                mimeType = requireNotNull(intent).type,
                isDirectory = itemType == ITEM_TYPE_FILE_SYSTEM_FOLDER,
                user = user,
            )
        } else null
    }

/** Returns whether an [ItemInfo] represents a file system item. */
fun ItemInfo.isFileSystemItem(): Boolean = isFileSystemFileItem() || isFileSystemFolderItem()

/** Returns whether an [ItemInfo] represents a file system file. */
fun ItemInfo.isFileSystemFileItem(): Boolean = itemType == ITEM_TYPE_FILE_SYSTEM_FILE

/** Returns whether an [ItemInfo] represents a file system folder. */
fun ItemInfo.isFileSystemFolderItem(): Boolean = itemType == ITEM_TYPE_FILE_SYSTEM_FOLDER
