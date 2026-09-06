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

package com.android.test.tracing.coroutines

import com.android.app.tracing.coroutines.TraceContextElement
import com.android.app.tracing.coroutines.TraceData
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.coroutines.traceThreadLocal
import com.android.test.tracing.coroutines.util.FakeTraceState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagEnabledTest : TestBase() {

    override val extraContext: CoroutineContext by lazy { EmptyCoroutineContext }

    @Test
    fun lazyStringIsAlwaysCalledOnDebugBuilds() {
        FakeTraceState.isTracingEnabled = false
        runTest {
            assertNotNull(traceThreadLocal.get())
            // Because nothing was traced yet on this thread, data should be null:
            assertNull(traceThreadLocal.get()?.data)

            val originalTraceContext = createCoroutineTracingContext(testMode = true)
            withContext(originalTraceContext) {
                assertNotSame(
                    "withContext() should copy the passed TraceContextElement",
                    originalTraceContext,
                    coroutineContext[TraceContextElement],
                )
                assertNotNull(traceThreadLocal.get())

                // It is expected that the lazy-String is called even when tracing is disabled
                // because
                // otherwise the coroutine resumption points would be missing names.
                var lazyStringCalled = false
                traceCoroutine({
                    lazyStringCalled = true
                    "hello"
                }) {
                    assertTrue(
                        "Lazy string should be called when coroutine tracing is enabled, " +
                            "even when Trace.isEnabled()=false",
                        lazyStringCalled,
                    )
                    val traceData = traceThreadLocal.get()!!.data as TraceData
                    assertEquals(traceData.slices?.size, 1)
                }
            }
        }
    }
}
