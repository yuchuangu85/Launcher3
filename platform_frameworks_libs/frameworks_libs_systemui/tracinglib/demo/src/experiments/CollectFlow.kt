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

import android.os.Trace
import com.android.app.tracing.coroutines.flow.collectTraced
import com.android.app.tracing.coroutines.flow.filterTraced as filter
import com.android.app.tracing.coroutines.flow.filterTraced
import com.android.app.tracing.coroutines.flow.flowName
import com.android.app.tracing.coroutines.flow.mapTraced as map
import com.android.app.tracing.coroutines.flow.mapTraced
import com.android.app.tracing.coroutines.flow.onEachTraced
import com.example.tracing.demo.FixedThread1
import com.example.tracing.demo.FixedThread2
import com.example.tracing.demo.FixedThread3
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class CollectFlow
@Inject
constructor(
    @param:FixedThread1 private var dispatcher1: CoroutineDispatcher,
    @param:FixedThread2 private var dispatcher2: CoroutineDispatcher,
    @param:FixedThread3 private val dispatcher3: CoroutineDispatcher,
) : TracedExperiment() {
    override val description: String = "Collect a cold flow with intermediate operators"

    private val coldFlow =
        flow {
                var n = 0
                while (true) {
                    Trace.instant(Trace.TRACE_TAG_APP, "emit:$n")
                    emit(n++)
                    forceSuspend(timeMillis = 8)
                }
            }
            .mapTraced("A") {
                Trace.instant(Trace.TRACE_TAG_APP, "map:$it")
                it
            }
            .onEachTraced("B") { Trace.instant(Trace.TRACE_TAG_APP, "onEach:$it") }
            .filterTraced("C") {
                Trace.instant(Trace.TRACE_TAG_APP, "filter:$it")
                true
            }
            .flowOn(dispatcher3)
            .flowName("inner-flow")
            .filter("evens") {
                forceSuspend(timeMillis = 4)
                Trace.instant(Trace.TRACE_TAG_APP, "filter-evens")
                it % 2 == 0
            }
            .flowName("middle-flow")
            .flowOn(dispatcher2)
            .map("3x") {
                forceSuspend(timeMillis = 2)
                Trace.instant(Trace.TRACE_TAG_APP, "3x")
                it * 3
            }
            .flowOn(dispatcher1)
            .flowName("outer-flow")

    override suspend fun runExperiment() {
        coldFlow.collectTraced("collect-flow") { Trace.instant(Trace.TRACE_TAG_APP, "got: $it") }
    }
}
