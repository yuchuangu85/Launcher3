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

import android.database.Cursor
import android.database.CursorWrapper
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_ID
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_PROVIDER
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_SOURCE
import com.android.launcher3.LauncherSettings.Favorites.CELLX
import com.android.launcher3.LauncherSettings.Favorites.CELLY
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.ICON
import com.android.launcher3.LauncherSettings.Favorites.INTENT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.OPTIONS
import com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID
import com.android.launcher3.LauncherSettings.Favorites.RANK
import com.android.launcher3.LauncherSettings.Favorites.RESTORED
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.LauncherSettings.Favorites.SPANX
import com.android.launcher3.LauncherSettings.Favorites.SPANY
import com.android.launcher3.LauncherSettings.Favorites.TITLE
import com.android.launcher3.LauncherSettings.Favorites._ID
import kotlin.reflect.KProperty

/** Helper class to easily read model properties from a cursor */
open class ModelCursorWrapper(cursor: Cursor, private val queriedColumns: Array<String>? = null) :
    CursorWrapper(cursor) {

    /** Returns the title or empty string */
    val title: String? by TITLE.delegateString()

    val id: Int by _ID.delegateInt()
    val container: Int by CONTAINER.delegateInt()
    val cellX: Int by CELLX.delegateInt()
    val cellY: Int by CELLY.delegateInt()
    val spanX: Int by SPANX.delegateInt()
    val spanY: Int by SPANY.delegateInt()
    val rank: Int by RANK.delegateInt()
    val options: Int by OPTIONS.delegateInt()
    val screen: Int by SCREEN.delegateInt()
    val itemType: Int by ITEM_TYPE.delegateInt()

    val restoreFlagOnDisk: Int by RESTORED.delegateInt()

    /** When loading an app widget for the workspace, returns it's app widget id */
    val appWidgetId: Int by APPWIDGET_ID.delegateInt()
    /** When loading an app widget for the workspace, returns it's app widget source */
    val appWidgetSource: Int by APPWIDGET_SOURCE.delegateInt()
    /** When loading an app widget for the workspace, returns the widget provider */
    val appWidgetProvider: String? by APPWIDGET_PROVIDER.delegateString()

    val intentString: String? by INTENT.delegateString()
    val serialNumber: Long by PROFILE_ID.delegate { getLong(it) }

    /** Returns the icon data for at the current position */
    val iconBlob: ByteArray? by ICON.delegate { getBlob(it) }

    private fun String.delegateInt() = delegate { getInt(it) }

    private fun String.delegateString() = delegate { getString(it) }

    private fun <T : Any?> String.delegate(reader: Cursor.(Int) -> T) =
        CursorProvider(
            if (queriedColumns == null || queriedColumns.contains(this)) getColumnIndexOrThrow(this)
            else -1,
            reader,
        )

    private class CursorProvider<T : Any?>(
        private val index: Int,
        private val reader: Cursor.(Int) -> T,
    ) {

        operator fun getValue(thisRef: Cursor, property: KProperty<*>): T =
            reader.invoke(thisRef, index)
    }
}
