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

import com.android.app.tracing.coroutines.flow.collectTraced as collect
import com.android.app.tracing.coroutines.flow.collectTraced
import com.android.app.tracing.coroutines.flow.filterTraced as filter
import com.android.app.tracing.coroutines.flow.flowName
import com.android.app.tracing.coroutines.flow.mapTraced as map
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.app.tracing.coroutines.traceCoroutine
import com.example.tracing.demo.FixedThread1
import com.example.tracing.demo.FixedThread2
import com.example.tracing.demo.FixedThread3
import com.example.tracing.demo.FixedThread4
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class SharedFlowUsage
@Inject
constructor(
    @param:FixedThread1 private var dispatcher1: CoroutineDispatcher,
    @param:FixedThread2 private var dispatcher2: CoroutineDispatcher,
    @param:FixedThread3 private var dispatcher3: CoroutineDispatcher,
    @param:FixedThread4 private var dispatcher4: CoroutineDispatcher,
) : TracedExperiment() {

    override val description: String = "Create a shared flow and collect from it"

    private val coldFlow =
        flow {
                var n = 0
                while (n < 20) {
                    emit(n++)
                    forceSuspend(timeMillis = 5)
                }
            }
            .map("pow2") {
                val rv = it * it
                forceSuspend("map($it) -> $rv", 50)
                rv
            }
            // this trace name is used here because the dispatcher changed
            .flowOn(dispatcher3)
            .filter("mod4") {
                val rv = it % 4 == 0
                forceSuspend("filter($it) -> $rv", 50)
                rv
            }
            .flowName("COLD_FLOW")

    override suspend fun runExperiment(): Unit = coroutineScope {
        val stateFlow = coldFlow.stateInTraced("My-StateFlow", this, SharingStarted.Eagerly, 10)
        launch("launchAAAA", dispatcher1) {
            stateFlow.collect("collectAAAA") {
                traceCoroutine("AAAA collected: $it") { forceSuspend("AAAA", 15) }
            }
        }
        launch("launchBBBB", dispatcher2) {
            // Don't pass a string. Instead, rely on default behavior to walk the stack for the
            // name. This results in trace sections like:
            // `collect:SharedFlowUsage$start$1$2:emit`
            // NOTE: `Flow.collect` is a member function and takes precedence, so we need
            // to invoke `collectTraced` using its original name instead of its `collect` alias
            stateFlow.collectTraced {
                traceCoroutine("BBBB collected: $it") { forceSuspend("BBBB", 30) }
            }
        }
        launch("launchCCCC", dispatcher3) {
            stateFlow.collect("collectCCCC") {
                traceCoroutine("CCCC collected: $it") { forceSuspend("CCCC", 60) }
            }
        }
        launch("launchDDDD", dispatcher4) {
            // Uses Flow.collect member function instead of collectTraced:
            stateFlow.collect { traceCoroutine("DDDD collected: $it") { forceSuspend("DDDD", 90) } }
        }
    }
}
