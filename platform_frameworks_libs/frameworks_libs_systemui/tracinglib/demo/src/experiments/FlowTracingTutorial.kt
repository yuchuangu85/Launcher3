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
import com.android.app.tracing.TraceUtils.traceAsync
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.flow.asStateFlowTraced
import com.android.app.tracing.coroutines.flow.filterTraced
import com.android.app.tracing.coroutines.flow.flowName
import com.android.app.tracing.coroutines.flow.mapTraced
import com.android.app.tracing.coroutines.flow.shareInTraced
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.app.tracing.coroutines.flow.traceAs
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.example.tracing.demo.FixedThread1
import com.example.tracing.demo.FixedThread2
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job

@Singleton
class FlowTracingTutorial
@Inject
constructor(
    @param:FixedThread1 private var dispatcher1: CoroutineDispatcher,
    @param:FixedThread2 private var dispatcher2: CoroutineDispatcher,
) : Experiment() {

    override val description: String = "Flow tracing tutorial"

    private lateinit var scope: CoroutineScope
    private lateinit var bgScope: CoroutineScope

    @OptIn(ExperimentalContracts::class)
    private suspend inline fun runStep(stepName: String, crossinline block: () -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        traceAsync(TRACK_NAME, "Step #$stepName") {
            block()
            traceAsync(TRACK_NAME, "running") { forceSuspend(timeMillis = 40) }
            traceAsync(TRACK_NAME, "cleanup") {
                traceAsync(TRACK_NAME, "cancel-main") { scope.coroutineContext.cancelChildren() }
                traceAsync(TRACK_NAME, "cancel-bg") { bgScope.coroutineContext.cancelChildren() }
                forceSuspend(timeMillis = 10)
            }
        }
    }

    private fun createTracingContext(name: String): CoroutineContext {
        return createCoroutineTracingContext(
            name = name,
            walkStackForDefaultNames = true,
            countContinuations = true,
        )
    }

    /** 1.1: */
    private fun step1p1() {
        scope.launchTraced("LAUNCH_FOR_COLLECT_1.1") {
            fibFlow.collect { Trace.instant(Trace.TRACE_TAG_APP, "got:$it") }
        }
    }

    /** 1.2: */
    private fun step1p2() {
        fibFlow.launchInTraced("LAUNCH_FOR_COLLECT_1.2", scope)
    }

    /** 2.1: */
    private fun step2p1() {
        val coldFlow = fibFlow.flowName("FIB_FLOW_NAME_2.1")
        coldFlow.launchInTracedForDemo("LAUNCH_NAME_2.1", scope)
    }

    /** 2.2: */
    private fun step2p2() {
        val coldFlow = fibFlow.flowName("FIB_FLOW_NAME_2.2").flowOn(dispatcher2)
        coldFlow.launchInTracedForDemo("LAUNCH_NAME_2.2", scope)
    }

    /** 2.3: */
    private fun step2p3() {
        val coldFlow = fibFlow.flowOn(dispatcher2).flowName("FIB_FLOW_NAME_2.3")
        coldFlow.launchInTracedForDemo("LAUNCH_NAME_2.3", scope)
    }

    /** 2.4: */
    private fun step2p4() {
        val coldFlow = fibFlow.flowName("FIB_AAA").flowOn(dispatcher2).flowName("FIB_BBB")
        coldFlow.launchInTracedForDemo("LAUNCH_NAME_2.4", scope)
    }

    /** 3: */
    private fun step3() {
        val coldFlow =
            fibFlow
                .mapTraced("x2") { it * 2 }
                .filterTraced("%3==0") { it % 3 == 0 }
                .flowName("(fib x 2) % 3 == 0")
        coldFlow.launchInTracedForDemo("LAUNCH_NAME_3", scope)
    }

    /** 4: */
    private fun step4() {
        val sharedFlow =
            fibFlow.shareInTraced("SHARED_FLOW_NAME_4", bgScope, SharingStarted.Eagerly, 3)
        scope.launchTraced("LAUNCH_NAME_4") {
            forceSuspend("before-collect", 5)
            sharedFlow.collect(::traceInstant)
        }
    }

    /** 5.1: */
    private fun step5p1() {
        val sharedFlow =
            fibFlow.stateInTraced("STATE_FLOW_NAME_5.1", bgScope, SharingStarted.Eagerly, 3)
        scope.launchTraced("LAUNCH_NAME_5.1") {
            forceSuspend("before-collect", 5)
            sharedFlow.collect(::traceInstant)
        }
    }

    /** 5.2: */
    private fun step5p2() {
        val sharedFlow =
            fibFlow.shareInTraced("STATE_FLOW_NAME_5.2", bgScope, SharingStarted.Eagerly, 3)
        val stateFlow = sharedFlow.stateInTraced("", bgScope, SharingStarted.Eagerly, 2)
        scope.launchTraced("LAUNCH_NAME_5.2") {
            forceSuspend("before-collect", 5)
            stateFlow.collect(::traceInstant)
        }
    }

    /** 6.1: */
    private fun step6p1() {
        val state = MutableStateFlow(1).traceAs("MUTABLE_STATE_FLOW_6.1")
        state.launchInTraced("LAUNCH_FOR_STATE_FLOW_COLLECT_6.1", scope)
        bgScope.launchTraced("FWD_FIB_TO_STATE_6.1") {
            forceSuspend("before-collect", 5)
            fibFlow.collect {
                traceInstant(it)
                // Manually forward values from the cold flow to the MutableStateFlow
                state.value = it
            }
        }
    }

    /** 6.2: */
    private fun step6p2() {
        val state = MutableStateFlow(1).traceAs("MUTABLE_STATE_FLOW_6.2")
        val readOnlyState = state.asStateFlowTraced("READ_ONLY_STATE_6.2")
        readOnlyState.launchInTraced("LAUNCH_FOR_STATE_FLOW_COLLECT_6.2", scope)
        bgScope.launchTraced("FWD_FIB_TO_STATE_6.2") {
            fibFlow.collect {
                traceInstant(it)
                // Manually forward values from the cold flow to the MutableStateFlow
                state.value = it
            }
        }
    }

    override suspend fun runExperiment(): Unit = coroutineScope {
        val job = coroutineContext.job
        scope = CoroutineScope(job + dispatcher1 + createTracingContext("main-scope"))
        bgScope = CoroutineScope(job + dispatcher2 + createTracingContext("bg-scope"))
        runStep("1.1", ::step1p1)
        runStep("1.2", ::step1p2)
        runStep("2.1", ::step2p1)
        runStep("2.2", ::step2p2)
        runStep("2.3", ::step2p3)
        runStep("2.4", ::step2p4)
        runStep("3", ::step3)
        runStep("4", ::step4)
        runStep("5.1", ::step5p1)
        runStep("5.2", ::step5p2)
        runStep("6.1", ::step6p1)
        runStep("6.2", ::step6p2)
    }
}

private fun <T> Flow<T>.launchInTracedForDemo(name: String, scope: CoroutineScope) {
    scope.launchTraced(name) { collect(::traceInstant) }
}

private fun <T> traceInstant(value: T) {
    Trace.instant(Trace.TRACE_TAG_APP, "got:$value")
}

private val fibFlow = flow {
    var n0 = 0
    var n1 = 1
    while (true) {
        emit(n0)
        val n2 = n0 + n1
        n0 = n1
        n1 = n2
        forceSuspend("after-emit", 1)
    }
}
