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

package com.android.launcher3.icons

/**
 * Represents a state for a persisted item which can be used to determine its uniqueness without
 * comparing the actual item itself
 */
class PersistedItemState private constructor(private val values: Array<String>) {

    constructor() : this(Array(COUNT_BASE_VALUES) { "" })

    override fun toString() = values.joinToString(STATE_SEPARATOR)

    override fun hashCode() = values.contentHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PersistedItemState) return false
        return values.contentEquals(other.values)
    }

    fun withLocaleAndSdk(locale: String, sdk: Int) = copy(locale = locale, sdk = sdk.toString())

    fun withTheme(theme: String, isCircle: Boolean) =
        copy(theme = theme, isCircle = isCircle.toString())

    fun withAdditionalValues(vararg extras: String) = PersistedItemState(values + extras)

    fun copy(
        locale: String = values[INDEX_LOCALE],
        sdk: String = values[INDEX_SDK],
        theme: String = values[INDEX_THEME],
        isCircle: String = values[INDEX_IS_CIRCLE],
    ): PersistedItemState {

        val newValue = values.copyOf()
        newValue[INDEX_LOCALE] = locale
        newValue[INDEX_SDK] = sdk
        newValue[INDEX_THEME] = theme
        newValue[INDEX_IS_CIRCLE] = isCircle

        return PersistedItemState(newValue)
    }

    companion object {
        private const val STATE_SEPARATOR = ";;"

        private const val INDEX_LOCALE = 0
        private const val INDEX_SDK = 1
        private const val INDEX_THEME = 2
        private const val INDEX_IS_CIRCLE = 3

        private const val COUNT_BASE_VALUES = 4

        fun String.toPersistedItemState(): PersistedItemState? {
            val values = split(STATE_SEPARATOR)
            return if (values.size >= COUNT_BASE_VALUES) PersistedItemState(values.toTypedArray())
            else null
        }
    }
}
