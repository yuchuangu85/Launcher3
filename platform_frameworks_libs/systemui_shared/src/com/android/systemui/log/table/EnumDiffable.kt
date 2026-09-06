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

package com.android.systemui.log.table

/** Interface for easier logging of basic enum classes using [logDiffsForTable]. */
interface EnumDiffable<T> : Diffable<T> {
    val columnName: String
    /**
     * Function that returns the value to log to the column. Should almost always be equal to this:
     * `{ this.name }`.
     */
    val valueFetcher: () -> String

    override fun logDiffs(prevVal: T, row: TableRowLogger) {
        if (prevVal != this) {
            logFull(row)
        }
    }

    override fun logFull(row: TableRowLogger) {
        row.logChange(columnName = columnName, value = valueFetcher.invoke())
    }
}
