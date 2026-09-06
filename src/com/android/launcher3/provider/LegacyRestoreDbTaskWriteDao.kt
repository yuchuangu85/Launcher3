/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.model.data.LauncherAppWidgetInfo

class LegacyRestoreDbTaskWriteDao(private val db: SQLiteDatabase) : IRestoreDbTaskWriteDao {

    override fun deleteItemsFromUnrestoredProfiles(validProfileIds: Collection<Long>): Int {
        val selection = "profileId NOT IN (${validProfileIds.joinToString()})"
        return db.delete(TABLE_NAME, selection, null)
    }

    override fun bulkUpdateRestoredFlag(flag: Int, itemType: Int?): Int {
        val values = ContentValues().apply { put(Favorites.RESTORED, flag) }
        val whereClause = if (itemType != null) "${Favorites.ITEM_TYPE} = ?" else null
        val whereArgs = if (itemType != null) arrayOf(itemType.toString()) else null

        return db.update(Favorites.TABLE_NAME, values, whereClause, whereArgs)
    }

    override fun migrateProfileId(oldProfileId: Long, newProfileId: Long): Int {
        val values = ContentValues().apply { put(Favorites.PROFILE_ID, newProfileId) }
        val selection = "${Favorites.PROFILE_ID} = ?"
        val selectionArgs = arrayOf(oldProfileId.toString())

        return db.update(Favorites.TABLE_NAME, values, selection, selectionArgs)
    }

    override fun updateDefaultProfileId(newProfileId: Long) {
        val tableName = Favorites.TABLE_NAME
        val tempTableName = "${tableName}_old"

        db.execSQL("ALTER TABLE $tableName RENAME TO $tempTableName;")
        Favorites.addTableToDb(db, newProfileId, false)
        db.execSQL("INSERT INTO $tableName SELECT * FROM $tempTableName;")
        db.execSQL("DROP TABLE $tempTableName;")
    }

    override fun removeScreenIdGaps(
        containerId: Int,
        distinctScreens: IntArray,
        startScreenId: Int,
    ) {
        if (distinctScreens.isEmpty()) return

        // Builds the string: "WHEN screen == 3 THEN 0 WHEN screen == 4 THEN 1..."
        val screenIdMapClause =
            distinctScreens
                .mapIndexed { index, screenId ->
                    "WHEN ${Favorites.SCREEN} == $screenId THEN ${index + startScreenId}"
                }
                .joinToString(" ")

        val sql =
            """
            UPDATE ${Favorites.TABLE_NAME}
                SET ${Favorites.SCREEN} =
                    CASE
                        $screenIdMapClause
                        ELSE ${Favorites.SCREEN}
                    END
            WHERE ${Favorites.CONTAINER} = $containerId;
        """
                .trimIndent()

        db.execSQL(sql)
    }

    override fun updateAppWidgetId(
        oldWidgetId: Int,
        newWidgetId: Int,
        newRestoreState: Int,
        profileId: Long,
    ): Boolean {
        val values =
            ContentValues().apply {
                put(Favorites.APPWIDGET_ID, newWidgetId)
                put(Favorites.RESTORED, newRestoreState)
            }

        val flagNotValid = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
        val selection =
            "${Favorites.APPWIDGET_ID} = ? AND " +
                "(${Favorites.RESTORED} & $flagNotValid) = $flagNotValid AND " +
                "${Favorites.PROFILE_ID} = ?"

        val selectionArgs = arrayOf(oldWidgetId.toString(), profileId.toString())
        val rowsUpdated = db.update(Favorites.TABLE_NAME, values, selection, selectionArgs)
        return rowsUpdated > 0
    }

    override fun updateShortcutOverride(
        itemId: Int,
        newIntentUri: String,
        newProfileId: Long,
    ): Int {
        val values =
            ContentValues().apply {
                put(Favorites.INTENT, newIntentUri)
                put(Favorites.PROFILE_ID, newProfileId)
            }

        val selection = "${Favorites._ID} = ?"
        val selectionArgs = arrayOf(itemId.toString())

        return db.update(Favorites.TABLE_NAME, values, selection, selectionArgs)
    }
}
