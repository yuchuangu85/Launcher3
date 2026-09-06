/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.client.clientsdk.compat.observable

import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Provides property delegates that are useful for observing changes on distinct values. */
object DistinctObservableDelegates {

    /**
     * Similar to [Delegates.observable], but only invokes the [onChange] when the new value does
     * not equal the old value.
     */
    fun <T> observable(initialValue: T, onChange: (T) -> Unit): ReadWriteProperty<Any?, T> =
        Delegates.observable(initialValue) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                onChange(newValue)
            }
        }

    /**
     * A combination of [Delegates.notNull] and `observable`, invokes the [onChange] when the new
     * value does not equal the old value.
     */
    fun <T : Any> observable(onChange: (T) -> Unit): ReadWriteProperty<Any?, T> =
        NotNullDistinctObservable(onChange)

    /** A combination of [kotlin.properties.ObservableProperty] and [Delegates.NotNullVar]. */
    private class NotNullDistinctObservable<T : Any>(private val onChange: (T) -> Unit) :
        ReadWriteProperty<Any?, T> {
        private var value: T? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return value
                ?: throw IllegalStateException(
                    "Property ${property.name} should be initialized before get"
                )
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            val oldValue = this.value
            if (oldValue != value) {
                this.value = value
                onChange(value)
            }
        }

        override fun toString(): String =
            "NotNullDistinctObservable(${if (value != null) "value=$value" else "value not initialized yet"})"
    }
}
