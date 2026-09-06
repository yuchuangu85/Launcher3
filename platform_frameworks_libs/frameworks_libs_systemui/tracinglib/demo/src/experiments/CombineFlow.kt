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
package com.example.tracing.demo.experiments

import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.traceSection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.yield

@Singleton
class CombineFlow @Inject constructor() : TracedExperiment() {

    override val description: String = "Create a lot of combined flows"

    override suspend fun runExperiment() = coroutineScope {
        val stateFlows = mutableListOf<MutableStateFlow<Int>>()
        val combinedFlows = mutableListOf<Flow<Int>>()
        val flatMapLatestFlows = mutableListOf<Flow<Int>>()
        repeat(4) { flowNumber ->
            // create 1 new state flow
            val stateFlow = MutableStateFlow(flowNumber)
            stateFlows += stateFlow
            // Then, create 1 new combined flow that sums that values emitted from all the state
            // flows created up until this point
            val combinedFlow =
                combine(stateFlows) { flowValues ->
                    var sum = 0
                    traceSection("combinedFlow@$flowNumber combine-sum") {
                        flowValues.forEach { value -> sum += value }
                    }
                    sum
                }
            combinedFlows += combinedFlow
            // Finally, create a flow that performs flatMapLatest, either returning the same
            // state flow, or returning the state flow created on this iteration
            flatMapLatestFlows +=
                stateFlow.flatMapLatest {
                    if (it % 2 == 0) {
                        stateFlow
                    } else {
                        combinedFlow
                    }
                }
        }
        val consumerJob =
            launch("consumers") {
                repeat(2) { consumerNumber ->
                    flatMapLatestFlows.forEachIndexed { flowNumber, flow ->
                        launch("consumer#$consumerNumber") {
                            flow.collect { value ->
                                traceSection(
                                    "consumer=$consumerNumber flowNumber=$flowNumber collected value=$value"
                                ) {
                                    Thread.sleep(1)
                                }
                            }
                        }
                    }
                }
            }
        val producerJob =
            launch("producer") {
                repeat(100) { n ->
                    stateFlows.forEach {
                        traceSection("value = $n") { it.value = n }
                        traceCoroutine("yield") { yield() }
                    }
                }
            }
        delay(1500)
        producerJob.cancel()
        consumerJob.cancel()
    }
}
