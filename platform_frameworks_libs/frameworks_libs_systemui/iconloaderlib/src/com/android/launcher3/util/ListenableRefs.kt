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

package com.android.launcher3.util

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Consumer

object ListenableRefs {

    /**
     * Combines two ListenableRefs.kt into a new ListenableRef by applying a transformation function
     * whenever either source emits a new value.
     *
     * @param source1 The first source ListenableRef.
     * @param source2 The second source ListenableRef.
     * @param transformer A function to combine the latest values from both sources.
     * @return A new ListenableRef emitting the results of the transformer.
     */
    fun <T1, T2, R> combine(
        source1: ListenableRef<T1>,
        source2: ListenableRef<T2>,
        transformer: BiFunction<T1, T2, R>,
    ): ListenableRef<R> {
        return object : ListenableRef<R> {
            override val value: R
                get() = transformer.apply(source1.value, source2.value)

            override fun forEach(executor: Executor, callback: (R) -> Unit): SafeCloseable {
                val currentValue = value
                val lastValue = AtomicReference(currentValue)
                executor.execute { callback(currentValue) }

                val listener = { _: Any? ->
                    val newValue = value
                    if (lastValue.getAndSet(newValue) != newValue) callback(newValue)
                }

                val c1 = source1.forEach(executor, listener)
                val c2 = source2.forEach(executor, listener)

                return SafeCloseable {
                    c1.close()
                    c2.close()
                }
            }
        }
    }

    /** Kotlin-friendly overload for the combine function. */
    fun <T1, T2, R> combine(
        source1: ListenableRef<T1>,
        source2: ListenableRef<T2>,
        transformer: (T1, T2) -> R,
    ): ListenableRef<R> {
        return combine(source1, source2, BiFunction { t1, t2 -> transformer(t1, t2) })
    }

    /** Utility method which allows callers to skip first [n] events of a [ListenableRef] */
    @JvmStatic
    fun <T> skip(n: Int, callback: Consumer<T>): (T) -> Unit {
        var count = n
        return { if (count > 0) count-- else callback.accept(it) }
    }
}
