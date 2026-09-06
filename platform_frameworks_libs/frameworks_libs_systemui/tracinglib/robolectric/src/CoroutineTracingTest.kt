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

@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.android.test.tracing.coroutines

import com.android.app.tracing.coroutines.CoroutineTraceName
import com.android.app.tracing.coroutines.TraceContextElement
import com.android.app.tracing.coroutines.coroutineScopeTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.coroutines.withContextTraced
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoroutineTracingTest : TestBase() {

    @Test
    fun simpleTraceSection() =
        runTest(finalEvent = 2) {
            expect(1, "1^")
            delay(1)
            expect(2, "1^")
        }

    @Test
    fun traceSectionFromScope() =
        runTest(finalEvent = 2) {
            traceCoroutine("hello") {
                expect(1, "1^", "hello")
                delay(1)
                expect(2, "1^", "hello")
            }
        }

    @Test
    fun testCoroutineScope() =
        runTest(finalEvent = 2) {
            coroutineScope { expect(1, "1^") }
            expect(2, "1^")
        }

    @Test
    fun simpleNestedTraceSection() =
        runTest(finalEvent = 10) {
            expect(1, "1^")
            delay(1)
            expect(2, "1^")
            traceCoroutine("hello") {
                expect(3, "1^", "hello")
                delay(1)
                expect(4, "1^", "hello")
                traceCoroutine("world") {
                    expect(5, "1^", "hello", "world")
                    delay(1)
                    expect(6, "1^", "hello", "world")
                }
                expect(7, "1^", "hello")
                delay(1)
                expect(8, "1^", "hello")
            }
            expect(9, "1^")
            delay(1)
            expect(10, "1^")
        }

    @Test
    fun simpleLaunch() {
        val barrier = CompletableDeferred<Unit>()
        runTest(finalEvent = 7) {
            expect(1, "1^")
            delay(1)
            expect(2, "1^")
            traceCoroutine("hello") {
                expect(3, "1^", "hello")
                delay(1)
                expect(4, "1^", "hello")
                launch {
                    expect(5, "1^:1^")
                    delay(1)
                    // "hello" is not passed to child scope
                    expect(6, "1^:1^")
                    barrier.complete(Unit)
                }
            }
            barrier.await()
            expect(7, "1^")
        }
    }

    @Test
    fun launchWithSuspendingLambda() =
        runTest(finalEvent = 5) {
            val fetchData: suspend () -> String = {
                expect(3, "1^:1^span-for-launch")
                delay(1L)
                traceCoroutine("span-for-fetchData") {
                    expect(4, "1^:1^span-for-launch", "span-for-fetchData")
                }
                "stuff"
            }
            expect(1, "1^")
            launchTraced("span-for-launch") {
                assertEquals("stuff", fetchData())
                expect(5, "1^:1^span-for-launch")
            }
            expect(2, "1^")
        }

    @Test
    fun stressTestContextSwitches() =
        runTest(totalEvents = 800) {
            repeat(200) {
                listOf(bgThread1, bgThread2, bgThread3, bgThread4).forEach {
                    launch(it) {
                        traceCoroutine("a") {
                            delay(1)
                            expectEndsWith("a")
                        }
                    }
                }
            }
        }

    @Test
    fun stressTestContextSwitches_depth() {
        fun CoroutineScope.recursivelyLaunch(n: Int) {
            if (n == 0) return
            launchTraced("launch#$n", start = CoroutineStart.UNDISPATCHED) {
                traceCoroutine("a") {
                    recursivelyLaunch(n - 1)
                    delay(1)
                    expectEndsWith("a")
                }
            }
        }
        runTest(totalEvents = 400) { recursivelyLaunch(400) }
    }

    @Test
    fun withContext_incorrectUsage() =
        runTest(finalEvent = 4) {
            assertTrue(coroutineContext[CoroutineTraceName] is TraceContextElement)
            expect(1, "1^")
            withContext(CoroutineTraceName("inside-withContext")) { // <-- BAD, DON'T DO THIS
                // This is why CoroutineTraceName() should not be used this way, it overwrites the
                // TraceContextElement. Because it is not a CopyableThreadContextElement, it is
                // not given opportunity to merge with the parent trace context.
                // While we could make CoroutineTraceName a CopyableThreadContextElement, it would
                // add too much overhead to tracing, especially for flows where operation fusion
                // is common.
                assertTrue(coroutineContext[CoroutineTraceName] is CoroutineTraceName)

                // The result of replacing the `TraceContextElement` with `CoroutineTraceName` is
                // that
                // tracing doesn't happen:
                expect(2, "1^") // <-- Trace section from before withContext is open until the
                //                          first suspension
                delay(1)
                expect(3)
            }
            expect(4, "1^")
        }

    @Test
    fun withContext_correctUsage() =
        runTest(finalEvent = 4) {
            expect(1, "1^")
            withContextTraced("inside-withContext", EmptyCoroutineContext) {
                assertTrue(coroutineContext[CoroutineTraceName] is TraceContextElement)
                expect(2, "1^", "inside-withContext")
                delay(1)
                expect(3, "1^", "inside-withContext")
            }
            expect(4, "1^")
        }

    @Test
    fun launchInCoroutineScope() = runTest {
        launchTraced("launch#0") {
            expect("1^:1^launch#0")
            delay(1)
            expect("1^:1^launch#0")
        }
        coroutineScopeTraced("span-for-coroutineScope-1") {
            launchTraced("launch#1") {
                expect("1^:2^launch#1")
                delay(1)
                expect("1^:2^launch#1")
            }
            launchTraced("launch#2") {
                expect("1^:3^launch#2")
                delay(1)
                expect("1^:3^launch#2")
            }
            coroutineScopeTraced("span-for-coroutineScope-2") {
                launchTraced("launch#3") {
                    expect("1^:4^launch#3")
                    delay(1)
                    expect("1^:4^launch#3")
                }
                launchTraced("launch#4") {
                    expect("1^:5^launch#4")
                    delay(1)
                    expect("1^:5^launch#4")
                }
            }
        }
        launchTraced("launch#5") {
            expect("1^:6^launch#5")
            delay(1)
            expect("1^:6^launch#5")
        }
    }

    @Test
    fun namedScopeMerging() = runTest {
        // to avoid race conditions in the test leading to flakes, avoid calling expectD() or
        // delaying before launching (e.g. only call expectD() in leaf blocks)
        expect("1^")
        launchTraced("A") {
            expect("1^:1^A")
            traceCoroutine("span") { expectD("1^:1^A", "span") }
            launchTraced("B") { expectD("1^:1^A:1^B") }
            launchTraced("C") {
                expect("1^:1^A:2^C")
                launch { expectD("1^:1^A:2^C:1^") }
                launchTraced("D") { expectD("1^:1^A:2^C:2^D") }
                launchTraced("E") {
                    expect("1^:1^A:2^C:3^E")
                    launchTraced("F") { expectD("1^:1^A:2^C:3^E:1^F") }
                    expect("1^:1^A:2^C:3^E")
                }
            }
            launchTraced("G") { expectD("1^:1^A:3^G") }
        }
        launch { launch { launch { expectD("1^:2^:1^:1^") } } }
        delay(2)
        launchTraced("H") { launch { launch { expectD("1^:3^H:1^:1^") } } }
        delay(2)
        launch {
            launch {
                launch {
                    launch { launch { launchTraced("I") { expectD("1^:4^:1^:1^:1^:1^:1^I") } } }
                }
            }
        }
        delay(2)
        launchTraced("J") {
            launchTraced("K") { launch { launch { expectD("1^:5^J:1^K:1^:1^") } } }
        }
        delay(2)
        launchTraced("L") {
            launchTraced("M") { launch { launch { expectD("1^:6^L:1^M:1^:1^") } } }
        }
        delay(2)
        launchTraced("N") {
            launchTraced("O") { launch { launchTraced("D") { expectD("1^:7^N:1^O:1^:1^D") } } }
        }
        delay(2)
        launchTraced("P") {
            launchTraced("Q") { launch { launchTraced("R") { expectD("1^:8^P:1^Q:1^:1^R") } } }
        }
        delay(2)
        launchTraced("S") { launchTraced("T") { launch { expectD("1^:9^S:1^T:1^") } } }
        delay(2)
        launchTraced("U") { launchTraced("V") { launch { expectD("1^:10^U:1^V:1^") } } }
        delay(2)
        expectD("1^")
    }

    @Test
    fun launchIntoSelf() =
        runTest(finalEvent = 11) {
            expect(1, "1^")
            delay(1)
            expect(2, "1^")
            val reusedNameContext = CoroutineTraceName("my-coroutine")
            launch(reusedNameContext) {
                expect(3, "1^:1^my-coroutine")
                delay(1)
                expect(4, "1^:1^my-coroutine")
                launch(reusedNameContext) {
                    expect(5, "1^:1^my-coroutine:1^my-coroutine")
                    delay(5)
                    expect(8, "1^:1^my-coroutine:1^my-coroutine")
                }
                delay(1)
                expect(6, "1^:1^my-coroutine")
                launch(reusedNameContext) {
                    expect(7, "1^:1^my-coroutine:2^my-coroutine")
                    delay(7)
                    expect(9, "1^:1^my-coroutine:2^my-coroutine")
                }
                delay(10)
                expect(10, "1^:1^my-coroutine")
            }
            launch(reusedNameContext) {
                delay(20)
                expect(11, "1^:2^my-coroutine")
            }
        }

    @Test
    fun undispatchedLaunch() =
        runTest(totalEvents = 4) {
            launchTraced("AAA", start = CoroutineStart.UNDISPATCHED) {
                expect("1^", "1^:1^AAA")
                launchTraced("BBB", start = CoroutineStart.UNDISPATCHED) {
                    traceCoroutine("delay-5") {
                        expect("1^", "1^:1^AAA", "1^:1^AAA:1^BBB", "delay-5")
                        delay(5)
                        expect("1^:1^AAA:1^BBB", "delay-5")
                    }
                }
            }
            expect("1^")
        }

    @Test
    fun undispatchedLaunch_cancelled() =
        runTest(totalEvents = 11) {
            traceCoroutine("hello") { expect("1^", "hello") }
            val job =
                launchTraced("AAA", start = CoroutineStart.UNDISPATCHED) {
                    expect("1^", "1^:1^AAA")
                    traceCoroutine("delay-50") {
                        expect("1^", "1^:1^AAA", "delay-50")
                        launchTraced("BBB", start = CoroutineStart.UNDISPATCHED) {
                                traceCoroutine("BBB:delay-25") {
                                    expect(
                                        "1^",
                                        "1^:1^AAA",
                                        "delay-50",
                                        "1^:1^AAA:1^BBB",
                                        "BBB:delay-25",
                                    )
                                    delay(25)
                                    expect("1^:1^AAA:1^BBB", "BBB:delay-25")
                                }
                            }
                            .join()
                        expect("1^:1^AAA", "delay-50")
                        delay(25)
                    }
                }
            launchTraced("CCC") {
                traceCoroutine("delay-35") {
                    expect("1^:2^CCC", "delay-35")
                    delay(35)
                    expect("1^:2^CCC", "delay-35")
                }
                job.cancelChildren()
                expect("1^:2^CCC")
                job.join()
                expect("1^:2^CCC")
            }
            expect("1^")
        }
}
