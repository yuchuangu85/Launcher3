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

@file:OptIn(ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class)

package com.android.test.tracing.coroutines

import com.android.app.tracing.coroutines.runBlockingTraced
import com.android.app.tracing.traceSection
import com.android.test.tracing.coroutines.util.FakeTraceState
import com.android.test.tracing.coroutines.util.ShadowTrace
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

@Config(shadows = [ShadowTrace::class])
class RunBlockingTracedTest : TestBase() {

    @Test
    fun runBlockingTracedWithSpanNameLambda() =
        runTest(totalEvents = 2) {
            expect(1, "1^")

            val result =
                runBlockingTraced({ "hello" }) {
                    delay(1)
                    expect(2, "1^", "runBlocking:hello")
                    true
                }

            assertTrue(result)
        }

    @Test
    fun runBlockingTracedWithSpanNameString() =
        runTest(totalEvents = 2) {
            expect(1, "1^")

            val result =
                runBlockingTraced(spanName = "hello", context = EmptyCoroutineContext) {
                    delay(1)
                    expect(2, "1^", "runBlocking:hello")
                    true
                }

            assertTrue(result)
        }

    @Test
    fun runBlockingTracedWithDefaultSpanNameAndContext() =
        runTest(totalEvents = 2) {
            expect(1, "1^")

            val result =
                runBlockingTraced(spanName = null, context = EmptyCoroutineContext) {
                    delay(1)
                    expect(
                        2,
                        "1^",
                        "runBlocking:RunBlockingTracedTest\$runBlockingTracedWithDefaultSpanNameAndContext$1\$result$1",
                    )
                    true
                }
            assertTrue(result)
        }

    @Test
    fun runBlockingTracedNestedTraceSections() =
        runTest(totalEvents = 2) {
            expect(1, "1^")

            val result =
                runBlockingTraced(spanName = { "OuterSpan" }) {
                    traceSection("InnerSpan") {
                        delay(1)
                        expect(2, "1^", "runBlocking:OuterSpan", "InnerSpan")
                        true
                    }
                }
            assertTrue(result)
        }

    @Test
    fun runBlockingTracedWhenTracingDisabled() =
        runTest(totalEvents = 2) {
            FakeTraceState.isTracingEnabled = false

            expect(1, "1^")

            val result =
                runBlockingTraced(spanName = { "NoTraceSpan" }) {
                    delay(1)
                    expect(2, "1^")
                    true
                }
            assertTrue(result)
        }
}
