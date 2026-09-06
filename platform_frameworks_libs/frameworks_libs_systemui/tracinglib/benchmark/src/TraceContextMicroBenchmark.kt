/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.app.tracing.benchmark

import android.os.Trace
import android.perftests.utils.BenchmarkState
import android.perftests.utils.PerfStatusReporter
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.EnsureDeviceSettingsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.traceCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TraceContextMicroBenchmark {

    @get:Rule val setFlagsRule = SetFlagsRule()

    @get:Rule val ensureDeviceSettingsRule = EnsureDeviceSettingsRule()

    @get:Rule val perfStatusReporter = PerfStatusReporter()

    @Before
    fun before() {
        Assert.assertTrue(Trace.isEnabled())
    }

    @After
    fun after() {
        Assert.assertTrue(Trace.isEnabled())
    }

    private suspend fun ensureSuspend(state: BenchmarkState) {
        state.pauseTiming()
        delay(1)
        state.resumeTiming()
    }

    @Test
    fun testSingleTraceSection() {
        val state = perfStatusReporter.benchmarkState
        runBlocking(createCoroutineTracingContext("root")) {
            while (state.keepRunning()) {
                traceCoroutine("hello-world") { ensureSuspend(state) }
            }
        }
    }

    @Test
    fun testNestedContext() {
        val state = perfStatusReporter.benchmarkState

        val context1 = createCoroutineTracingContext("scope1")
        val context2 = createCoroutineTracingContext("scope2")
        runBlocking {
            while (state.keepRunning()) {
                withContext(context1) {
                    traceCoroutine("hello") {
                        traceCoroutine("world") {
                            withContext(context2) {
                                traceCoroutine("hallo") {
                                    traceCoroutine("welt") { ensureSuspend(state) }
                                    ensureSuspend(state)
                                }
                            }
                            ensureSuspend(state)
                        }
                        ensureSuspend(state)
                    }
                }
            }
        }
    }

    @Test
    fun testInterleavedLaunch() {
        val state = perfStatusReporter.benchmarkState

        runBlocking(createCoroutineTracingContext("root")) {
            val job1 =
                launchTraced("scope1") {
                    while (true) {
                        traceCoroutine("hello") {
                            traceCoroutine("world") { yield() }
                            yield()
                        }
                    }
                }
            val job2 =
                launchTraced("scope2") {
                    while (true) {
                        traceCoroutine("hallo") {
                            traceCoroutine("welt") { yield() }
                            yield()
                        }
                    }
                }
            while (state.keepRunning()) {
                repeat(10_000) { traceCoroutine("main-loop") { yield() } }
            }
            job1.cancel()
            job2.cancel()
        }
    }
}
