/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:OptIn(ExperimentalTypeInference::class)

package com.android.app.tracing

import android.os.Trace
import com.android.app.tracing.TraceUtils.traceAsync
import java.util.concurrent.atomic.AtomicInteger
import kotlin.experimental.ExperimentalTypeInference
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach

/** Utilities to trace Flows */
public object FlowTracing {

    private const val TAG = "FlowTracing"
    private const val DEFAULT_ASYNC_TRACK_NAME = TAG
    private val counter = AtomicInteger(0)

    /** Logs each flow element to a trace. */
    public inline fun <T> Flow<T>.traceEach(
        flowName: String,
        logcat: Boolean = false,
        traceEmissionCount: Boolean = false,
        crossinline valueToString: (T) -> String = { it.toString() },
    ): Flow<T> {
        val stateLogger = TraceStateLogger(flowName, logcat = logcat)
        val baseFlow = if (traceEmissionCount) traceEmissionCount(flowName) else this
        return baseFlow.onEach { stateLogger.log(valueToString(it)) }
    }

    /** Records value of a given numeric flow as a counter track in traces. */
    public fun <T : Number> Flow<T>.traceAsCounter(
        counterName: String,
        traceEmissionCount: Boolean = false,
        valueToInt: (T) -> Int = { it.toInt() },
    ): Flow<T> {
        val baseFlow = if (traceEmissionCount) traceEmissionCount(counterName) else this
        return baseFlow.onEach {
            if (Trace.isEnabled()) {
                Trace.traceCounter(Trace.TRACE_TAG_APP, counterName, valueToInt(it))
            }
        }
    }

    /** Adds a counter track to monitor emissions from a specific flow.] */
    public fun <T> Flow<T>.traceEmissionCount(
        flowName: String,
        uniqueSuffix: Boolean = false,
    ): Flow<T> {
        val trackName by lazy {
            "$flowName#emissionCount" + if (uniqueSuffix) "\$${counter.addAndGet(1)}" else ""
        }
        var count = 0
        return onEach {
            count += 1
            Trace.traceCounter(Trace.TRACE_TAG_APP, trackName, count)
        }
    }

    /**
     * Adds a counter track to monitor emissions from a specific flow.
     *
     * [flowName] is lazy: it would be computed only if tracing is enabled and only the first time.
     */
    public fun <T> Flow<T>.traceEmissionCount(
        flowName: () -> String,
        uniqueSuffix: Boolean = false,
    ): Flow<T> {
        val trackName by lazy {
            "${flowName()}#emissionCount" + if (uniqueSuffix) "\$${counter.addAndGet(1)}" else ""
        }
        var count = 0
        return onEach {
            count += 1
            if (Trace.isEnabled()) {
                Trace.traceCounter(Trace.TRACE_TAG_APP, trackName, count)
            }
        }
    }

    /**
     * Makes [awaitClose] output Perfetto traces.
     *
     * There will be 2 traces:
     * - One in the thread this is being executed on
     * - One in a track having [DEFAULT_ASYNC_TRACK_NAME] name.
     *
     * This allows to easily have visibility into what's happening in awaitClose.
     */
    public suspend fun ProducerScope<*>.tracedAwaitClose(name: String, block: () -> Unit = {}) {
        awaitClose {
            val traceName = { "$name#TracedAwaitClose" }
            traceAsync(DEFAULT_ASYNC_TRACK_NAME, traceName) { traceSection(traceName) { block() } }
        }
    }

    /**
     * Traced version of [callbackFlow].
     *
     * Adds tracing in 2 ways:
     * - An async slice will appear in the [DEFAULT_ASYNC_TRACK_NAME] named track.
     * - A counter will be increased at every emission
     *
     * Should be used with [tracedAwaitClose] (when needed).
     */
    public fun <T> tracedConflatedCallbackFlow(
        name: String,
        @BuilderInference block: suspend ProducerScope<T>.() -> Unit,
    ): Flow<T> {
        return callbackFlow {
                traceAsync(DEFAULT_ASYNC_TRACK_NAME, { "$name#CallbackFlowBlock" }) {
                    block(this@callbackFlow)
                }
            }
            .conflate()
            .traceEmissionCount(name, uniqueSuffix = true)
    }
}
