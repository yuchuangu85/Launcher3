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

@file:OptIn(
    DelicateCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalStdlibApi::class,
)

package com.android.test.tracing.coroutines

import com.android.app.tracing.coroutines.CoroutineTraceName
import com.android.app.tracing.coroutines.TraceContextElement
import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.withContextTraced
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundThreadTracingTest : TestBase() {

    @Test
    fun withContext_reuseOuterDispatcher() =
        runTest(finalEvent = 5) {
            val originalDispatcher = currentCoroutineContext()[CoroutineDispatcher]!!
            val otherScope = scope.plus(bgThread1)
            expect(1, "1^")
            otherScope
                .launchTraced("AAA") {
                    expect(2, "2^AAA")
                    withContextTraced("inside-withContext", originalDispatcher) {
                        assertTrue(coroutineContext[CoroutineTraceName] is TraceContextElement)
                        expect(3, "2^AAA", "inside-withContext")
                        delay(1)
                        expect(4, "2^AAA", "inside-withContext")
                    }
                    expect(5, "2^AAA")
                }
                .join()
        }

    @Test
    fun withContext_reentryToSameContext() =
        runTest(totalEvents = 10) {
            val otherScope = scope.plus(bgThread1)
            expect("1^")
            otherScope
                .launchTraced("AAA") {
                    expect("2^AAA")
                    var job: Job? = null
                    launchTraced("BBB") {
                            expect("2^AAA:1^BBB")
                            job =
                                scope.launchTraced("CCC") {
                                    withContextTraced("DDD", bgThread1) {
                                        expect("3^CCC", "DDD")
                                        delay(1)
                                        expect("3^CCC", "DDD")
                                    }
                                    withContextTraced("EEE", EmptyCoroutineContext) {
                                        expect("3^CCC", "EEE")
                                        delay(1)
                                        expect("3^CCC", "EEE")
                                    }
                                }
                            expect("2^AAA:1^BBB")
                        }
                        .join()
                    job!!.join()
                    expect("2^AAA")
                }
                .join()
            expect("1^")
        }
}
