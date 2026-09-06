/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.util

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.AtomicFile
import androidx.annotation.WorkerThread
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.pm.UserCache

/**
 * Utility class to read/write a list of [com.android.launcher3.model.data.ItemInfo] on disk. This
 * class is not thread safe, the caller should ensure proper threading
 */
class PersistedItemArray<T : ItemInfo>(fileName: String) {

    private val store = ListStore<T>(fileName)

    /** Writes the provided list of items on the disk */
    @WorkerThread
    fun write(context: Context, items: List<T>) {
        val userCache = UserCache.INSTANCE[context]
        store.write(context, items) { xmlSerializer, item ->
            xmlSerializer.attribute(null, Favorites.ITEM_TYPE, item.itemType.toString())
            xmlSerializer.attribute(
                null,
                Favorites.PROFILE_ID,
                userCache.getSerialNumberForUser(item.user).toString(),
            )
            xmlSerializer.attribute(null, Favorites.INTENT, item.intent?.toUri(0) ?: "")
        }
    }

    /** Reads the items from the disk */
    @WorkerThread
    fun read(context: Context, factory: ItemFactory<T>): MutableList<T> {
        val userCache = UserCache.INSTANCE[context]
        return store.read(context) { element ->
            factory.createInfo(
                itemType = element[Favorites.ITEM_TYPE]!!.toInt(),
                user = userCache.getUserForSerialNumber(element[Favorites.PROFILE_ID]!!.toLong()),
                intent = Intent.parseUri(element[Favorites.INTENT], 0),
            )
        }
    }

    /** Returns the underlying file used for persisting data */
    fun getFile(context: Context): AtomicFile = store.getFile(context)

    /** Interface to create an ItemInfo during parsing */
    interface ItemFactory<T : ItemInfo?> {
        /** Returns an item info or null in which case the entry is ignored */
        fun createInfo(itemType: Int, user: UserHandle, intent: Intent): T?
    }
}
