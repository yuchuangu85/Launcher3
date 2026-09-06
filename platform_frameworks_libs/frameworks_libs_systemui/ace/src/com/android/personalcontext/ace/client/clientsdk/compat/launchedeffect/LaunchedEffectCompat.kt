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
package com.android.personalcontext.ace.client.clientsdk.compat.launchedeffect

import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * When [LaunchedEffectCompat] is [invoke]d it will launch the `block` effect within [scope]. If the
 * keys change upon a subsequent invocation, the previous coroutine is canceled and a new one is
 * launched.
 *
 * This lightly emulates the functionality provided by [androidx.compose.runtime.LaunchedEffect],
 * but only offers partial support:
 * - There is no equivalence for cancelling the coroutine when the
 *   [androidx.compose.runtime.LaunchedEffect] leaving the composition, so an optional [cancel] API
 *   has been provided instead.
 * - There is no equivalence for tracking the execution context of the
 *   [androidx.compose.runtime.LaunchedEffect], and separately keep track of the keys for each
 *   execution. Instead, you must create a new instance of [LaunchedEffectCompat] for each new
 *   execution context you are interested in tracking.
 *
 * Key Equality: The keys are compared using structural equality (`equals()`). All keys **must**
 * provide strong equality guarantees. For custom objects, use a `data class` or ensure you have
 * manually implemented a correct `equals()` and `hashCode()`. Primitives, strings, and other
 * standard library types already provide these guarantees.
 *
 * Thread Safety: This class is **not thread-safe**. It is designed to be created and used
 * exclusively from the main thread.
 *
 * @param scope The CoroutineScope in which the effect will be launched. The lifecycle of this scope
 *   controls the overall lifetime of the effect.
 */
class LaunchedEffectCompat(private val scope: CoroutineScope) {
    private var job: Job? = null
    private var lastKeys: Array<out Any?>? = null

    /**
     * Executes [block] only if [keys] is different from the last time this [LaunchedEffectCompat]
     * executed [block].
     *
     * If the keys are different, the previously running block (if any) is canceled and the new
     * [block] is launched as a new coroutine.
     */
    @MainThread
    operator fun invoke(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
        if (!lastKeys.contentDeepEquals(keys)) {
            lastKeys = keys

            job?.cancel()
            job = scope.launch(block = block)
        }
    }

    /**
     * Cancels the currently running effect, if any. This is useful for cleanup when the owner of
     * this LaunchedEffect is destroyed.
     */
    fun cancel() {
        job?.cancel()
        job = null
        lastKeys = null
    }
}
