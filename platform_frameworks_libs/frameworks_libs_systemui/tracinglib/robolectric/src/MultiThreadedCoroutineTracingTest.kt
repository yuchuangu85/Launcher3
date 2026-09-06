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

import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.coroutines.traceThreadLocal
import com.android.app.tracing.coroutines.withContextTraced
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultiThreadedCoroutineTracingTest : TestBase() {

    @Test
    fun unconfinedLaunch() = runTest {
        val barrier1 = CompletableDeferred<Unit>()
        val barrier2 = CompletableDeferred<Unit>()
        val barrier3 = CompletableDeferred<Unit>()
        val thread1 = bgThread1
        val thread2 = bgThread2
        // Do NOT assert order. Doing so will make this test flaky due to its use of
        // Dispatchers.Unconfined
        expect("1^")
        launchTraced("unconfined-launch", Dispatchers.Unconfined) {
                launchTraced("thread2-launch", thread2) {
                    traceCoroutine("thread2-inner") {
                        barrier3.await()
                        expect("1^:1^unconfined-launch:1^thread2-launch", "thread2-inner")
                        barrier2.complete(Unit)
                    }
                }
                launchTraced("default-launch", Dispatchers.Unconfined) {
                    traceCoroutine("default-inner") {
                        barrier2.await()
                        expectAny(
                            arrayOf(
                                "1^",
                                "1^:1^unconfined-launch:2^default-launch",
                                "default-inner",
                            ),
                            arrayOf(
                                "1^:1^unconfined-launch:3^thread1-launch",
                                "thread1-inner",
                                "1^:1^unconfined-launch:2^default-launch",
                                "default-inner",
                            ),
                        )
                        barrier3.complete(Unit)
                    }
                }
                launchTraced("thread1-launch", thread1) {
                    traceCoroutine("thread1-inner") {
                        barrier1.await()
                        expect("1^:1^unconfined-launch:3^thread1-launch", "thread1-inner")
                        barrier2.complete(Unit)
                    }
                }
                withContextTraced("unconfined-withContext", Dispatchers.Unconfined) {
                    expect("1^", "1^:1^unconfined-launch", "unconfined-withContext")
                    barrier1.complete(Unit)
                    expect("1^", "1^:1^unconfined-launch", "unconfined-withContext")
                }
            }
            .join()
        expect("1^")
    }

    @Test
    fun nestedUpdateAndRestoreOnSingleThread_unconfinedDispatcher() =
        runTest(finalEvent = 5) {
            traceCoroutine("parent-span") {
                expect(1, "1^", "parent-span")
                launch(Dispatchers.Unconfined) {
                    // This may appear unusual, but it is expected behavior:
                    //   1) The parent has an open trace section called "parent-span".
                    //   2) The child launches, derives a new scope name from its parent, and
                    // resumes
                    //      immediately due to its use of the unconfined dispatcher.
                    //   3) The child emits all the trace sections known to its scope. The parent
                    //      does not have an opportunity to restore its context yet.
                    //   4) After the suspension point, the parent restores its context, and the
                    //      child
                    //
                    // [parent's active trace sections]
                    //               /           \      [new trace section for child scope]
                    //              /             \                \
                    expect(2, "1^", "parent-span", "1^:1^")
                    traceCoroutine("child-span") {
                        expect(3, "1^", "parent-span", "1^:1^", "child-span")
                        delay(10) // <-- delay will give parent a chance to restore its context
                        // After a delay, the parent resumes, finishing its trace section, so we are
                        // left with only those in the child's scope
                        expect(5, "1^:1^", "child-span")
                    }
                }
            }
            expect(4, "1^") // <-- because of the delay above, this is not the last event
        }

    /** @see nestedUpdateAndRestoreOnSingleThread_unconfinedDispatcher */
    @Test
    fun nestedUpdateAndRestoreOnSingleThread_undispatchedLaunch() {
        val barrier = CompletableDeferred<Unit>()
        runTest(finalEvent = 4) {
            expect(1, "1^")
            traceCoroutine("parent-span") {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    traceCoroutine("child-span") {
                        expect(2, "1^", "parent-span", "1^:1^", "child-span")
                        barrier.await() // <-- give parent a chance to restore its context
                        expect(4, "1^:1^", "child-span")
                    }
                }
            }
            expect(3, "1^")
            barrier.complete(Unit)
        }
    }

    @Test
    fun launchOnSeparateThread_defaultDispatcher() =
        runTest(finalEvent = 4) {
            val channel = Channel<Int>()
            val thread1 = bgThread1
            expect("1^")
            traceCoroutine("hello") {
                expect(1, "1^", "hello")
                launch(thread1) {
                    expect(2, "1^:1^")
                    traceCoroutine("world") {
                        expect("1^:1^", "world")
                        channel.send(1)
                        expect(3, "1^:1^", "world")
                    }
                }
                expect("1^", "hello")
            }
            expect("1^")
            assertEquals(1, channel.receive())
            expect(4, "1^")
        }

    @Test
    fun testTraceStorage() {
        val thread1 = bgThread1
        val thread2 = bgThread2
        val thread3 = bgThread3
        val thread4 = bgThread4
        val channel = Channel<Int>()
        val threadContexts = listOf(thread1, thread2, thread3, thread4)
        val finishedLaunches = Channel<Int>()
        // Start 1000 coroutines waiting on [channel]
        runTest {
            val job = launch {
                repeat(1000) {
                    launchTraced("span-for-launch", threadContexts[it % threadContexts.size]) {
                        assertNotNull(traceThreadLocal.get())
                        traceCoroutine("span-for-fetchData") {
                            channel.receive()
                            expectEndsWith("span-for-fetchData")
                        }
                        assertNotNull(traceThreadLocal.get())
                        finishedLaunches.send(it)
                    }
                    expect("1^:1^")
                }
            }
            // Resume half the coroutines that are waiting on this channel
            repeat(500) { channel.send(1) }
            var receivedClosures = 0
            repeat(500) {
                finishedLaunches.receive()
                receivedClosures++
            }
            // ...and cancel the rest
            job.cancel()
        }
    }

    @Test
    fun nestedTraceSectionsMultiThreaded() = runTest {
        launchTraced("launch#1", bgThread1) {
            expect("1^:1^launch#1")
            delay(1L)
            traceCoroutine("span-1") { expect("1^:1^launch#1", "span-1") }
            expect("1^:1^launch#1")
            expect("1^:1^launch#1")
            launchTraced("launch#2", bgThread2) {
                expect("1^:1^launch#1:1^launch#2")
                delay(1L)
                traceCoroutine("span-2") { expect("1^:1^launch#1:1^launch#2", "span-2") }
                expect("1^:1^launch#1:1^launch#2")
                expect("1^:1^launch#1:1^launch#2")
                launchTraced("launch#3", bgThread1) {
                    // "launch#3" is dropped because context has a TraceContextElement.
                    // The CoroutineScope (i.e. `this` in `this.launch {}`) should have a
                    // TraceContextElement, but using TraceContextElement in the passed context is
                    // incorrect.
                    expect("1^:1^launch#1:1^launch#2:1^launch#3")
                    launchTraced("launch#4", bgThread1) {
                        expect("1^:1^launch#1:1^launch#2:1^launch#3:1^launch#4")
                    }
                }
            }
            expect("1^:1^launch#1")
        }
        expect("1^")

        // Launching without the trace extension won't result in traces
        launch(bgThread1) { expect("1^:2^") }
        launch(bgThread2) { expect("1^:3^") }
    }

    @Test
    fun scopeReentry_withContextFastPath() = runTest {
        val thread1 = bgThread1
        val channel = Channel<Int>()
        val job =
            launchTraced("#1", thread1) {
                expect("1^:1^#1")
                var i = 0
                while (isActive) {
                    expect("1^:1^#1")
                    channel.send(i++)
                    expect("1^:1^#1")
                    // when withContext is passed the same scope, it takes a fast path, dispatching
                    // immediately. This means that in subsequent loops, if we do not handle reentry
                    // correctly in TraceContextElement, the trace may become deeply nested:
                    // "#1", "#1", "#1", ... "#2"
                    withContext(thread1) {
                        expect("1^:1^#1")
                        traceCoroutine("#2") {
                            expect("1^:1^#1", "#2")
                            channel.send(i++)
                            expect("1^:1^#1", "#2")
                        }
                        expect("1^:1^#1")
                    }
                }
            }
        repeat(1000) {
            expect("1^")
            traceCoroutine("receive") {
                expect("1^", "receive")
                val receivedVal = channel.receive()
                assertEquals(it, receivedVal)
                expect("1^", "receive")
            }
            expect("1^")
        }
        job.cancel()
    }
}
