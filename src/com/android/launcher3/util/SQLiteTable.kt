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
package com.android.launcher3.util

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction

/** Wrapper over [SQLiteDatabase] to simplify some API calls and limit access */
open class SQLiteTable(private val db: SQLiteDatabase, private val tableName: String) {

    fun query(
        columns: Array<String>?,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
    ): Cursor =
        db.query(
            tableName,
            columns,
            selection,
            selectionArgs,
            /* groupBy */ null,
            /* having */ null,
            sortOrder,
        )

    inline fun <T> query(
        columns: Array<String>?,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        callback: Cursor.() -> T,
    ): T = query(columns, selection, selectionArgs, sortOrder).use(callback)

    fun rawQuery(sql: String): Cursor = db.rawQuery(sql, null)

    inline fun <T> rawQuery(sql: String, callback: Cursor.() -> T): T = rawQuery(sql).use(callback)

    fun execSQL(sql: String) = db.execSQL(sql)

    fun newTransaction() = SQLiteTransaction(db)

    open fun update(
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<String>? = null,
    ): Int = db.update(tableName, values, selection, selectionArgs)

    inline fun update(
        selection: String?,
        selectionArgs: Array<String>? = null,
        valuesProvider: ContentValues.() -> Unit,
    ) = update(ContentValues().apply { valuesProvider() }, selection, selectionArgs)

    open fun delete(selection: String?, selectionArgs: Array<String>? = null): Int =
        db.delete(tableName, selection, selectionArgs)

    open fun insert(values: ContentValues): Long = db.insert(tableName, null, values)

    open fun insertOrReplace(values: ContentValues): Long =
        db.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Extension of [SQLiteTable] which wraps various mutation methods with try-catch */
open class WriteProtectedSQLiteTable(db: SQLiteDatabase, tableName: String) :
    SQLiteTable(db, tableName) {

    override fun update(
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = runCatching { super.update(values, selection, selectionArgs) }.getOrDefault(0)

    override fun delete(selection: String?, selectionArgs: Array<String>?): Int =
        runCatching { super.delete(selection, selectionArgs) }.getOrDefault(0)

    override fun insert(values: ContentValues): Long =
        runCatching { super.insert(values) }.getOrDefault(0)

    override fun insertOrReplace(values: ContentValues): Long =
        runCatching { super.insertOrReplace(values) }.getOrDefault(0)
}
