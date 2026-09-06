/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.test.tracing.coroutines

import com.android.app.tracing.coroutines.CoroutineTraceName
import com.android.app.tracing.coroutines.TraceContextElement
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.launchTraced
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.launch
import org.junit.Test

class CoroutineTraceNameTest : TestBase() {

    // BAD: CoroutineTraceName should not be installed on the root like this:
    override val extraContext: CoroutineContext by lazy { CoroutineTraceName("main") }

    @Test
    fun nameMergedWithTraceContext() = runTest {
        expectD()
        val otherTraceContext =
            createCoroutineTracingContext("other", testMode = true) as TraceContextElement
        // "main" is never used, it is overwritten by "other". But "other" is also never used
        // because it is a root `TraceContextElement`, and only child coroutines will have traced
        // named, which are typically determined by merging the `TraceContextElement` with a
        // `CoroutineTraceName`.
        launch(otherTraceContext) { expectD("1^") }
        expectD()
        launchTraced(null, otherTraceContext) { expectD("2^") }
        launch { expectD() }
        launch(otherTraceContext) {
            expectD("3^")
            launchTraced("name-a") { expectD("3^:1^name-a") }
        }
        expectD()
        launchTraced(null, otherTraceContext) {
            expectD("4^")
            launchTraced("name-b") { expectD("4^:1^name-b") }
        }
        launch { expectD() }
    }
}
