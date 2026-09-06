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
package com.example.tracing.demo.experiments

import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.coroutines.withContextTraced
import com.example.tracing.demo.FixedPool
import com.example.tracing.demo.FixedThread1
import com.example.tracing.demo.FixedThread2
import com.example.tracing.demo.FixedThread3
import com.example.tracing.demo.FixedThread4
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@Singleton
class LaunchStressTest
@Inject
constructor(
    @param:FixedThread1 private var dispatcher1: CoroutineDispatcher,
    @param:FixedThread2 private var dispatcher2: CoroutineDispatcher,
    @param:FixedThread3 private val dispatcher3: CoroutineDispatcher,
    @param:FixedThread4 private val dispatcher4: CoroutineDispatcher,
    @param:FixedPool private var fixedPoolDispatcher: CoroutineDispatcher,
) : TracedExperiment() {

    override val description: String = "Simultaneous launch{} calls on different threads"

    override suspend fun runExperiment(): Unit = coroutineScope {
        repeat(16) { n ->
            launchTraced("launch#$n", fixedPoolDispatcher) {
                withContextTraced("context-switch-pool", fixedPoolDispatcher) {
                    withContextTraced("context-switch-1", dispatcher1) {
                        traceCoroutine("delay#$n:1") { delay(5) }
                    }
                    traceCoroutine("delay#$n:2") { delay(5) }
                    withContextTraced("context-switch-2", dispatcher2) {
                        traceCoroutine("delay#$n:3") { delay(5) }
                    }
                }
                withContextTraced("context-switch-3", dispatcher3) {
                    traceCoroutine("delay#$n:3") {
                        traceCoroutine("delay#$n:4") { delay(5) }
                        withContextTraced("context-switch-4", dispatcher4) {
                            traceCoroutine("delay#$n:5") { delay(5) }
                        }
                    }
                }
            }
        }
    }
}
