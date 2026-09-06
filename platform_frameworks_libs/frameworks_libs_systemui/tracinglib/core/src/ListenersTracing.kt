/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.app.tracing

/** Utilities to trace automatically computations happening for each element of a list. */
public object ListenersTracing {

    /**
     * Like [forEach], but outputs a trace for each element.
     *
     * The ideal usage of this is to debug what's taking long in a list of Listeners. For example:
     * ```
     * listeners.forEach { it.dispatch(state) }
     * ```
     *
     * often it's tricky to understand which listener is causing delays. This can be used instead to
     * log how much each listener is taking:
     * ```
     * listeners.forEachTraced(TAG) { it.dispatch(state) }
     * ```
     */
    public inline fun <T : Any> Iterable<T>.forEachTraced(tag: String = "", f: (T) -> Unit) {
        forEach { traceSection({ "$tag#${it::javaClass.get().name}" }) { f(it) } }
    }
}
