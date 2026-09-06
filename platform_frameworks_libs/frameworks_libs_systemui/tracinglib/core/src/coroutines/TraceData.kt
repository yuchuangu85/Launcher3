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

@file:OptIn(ExperimentalContracts::class)

package com.android.app.tracing.coroutines

import android.os.Trace
import com.android.app.tracing.beginSlice
import com.android.app.tracing.coroutines.DebugSysProps.coroutineTracingEnabled
import com.android.app.tracing.endSlice
import com.android.systemui.util.Compile
import java.util.ArrayDeque
import kotlin.contracts.ExperimentalContracts
import kotlin.math.max
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED

/**
 * Represents a section of code executing in a coroutine. This may be split up into multiple slices
 * on different threads as the coroutine is suspended and resumed.
 *
 * @see traceCoroutine
 */
internal typealias TraceSection = String

/** Use a final subclass to avoid virtual calls (b/316642146). */
@PublishedApi
internal class TraceDataThreadLocal : ThreadLocal<TraceStorage?>() {
    override fun initialValue(): TraceStorage? {
        return if (Compile.IS_DEBUG && coroutineTracingEnabled) {
            TraceStorage()
        } else {
            null
        }
    }
}

/**
 * There should only be one instance of this class per thread. This class is stored in a
 * [ThreadLocal] variable.
 */
@PublishedApi
internal class TraceStorage() {
    /** Open trace sections on the current thread for the current coroutine. */
    internal var data: TraceData? = null

    /**
     * Counter for tracking which index to use in the [continuationIds] and [openSliceCount] arrays.
     * `contIndex` is used to keep track of the stack used for managing tracing state when
     * coroutines are resumed and suspended in a nested way.
     * * `-1` indicates no coroutine is currently running
     * * `0` indicates one coroutine is running
     * * `>1` indicates the current coroutine is resumed inside another coroutine, e.g. due to an
     *   unconfined dispatcher or [UNDISPATCHED] launch.
     */
    private var contIndex = -1

    /**
     * Count of slices opened on the current thread due to current [TraceData] that must be closed
     * when it is removed. If another [data] overwrites the current one, all trace sections due to
     * current [data] must be closed. The overwriting [data] will handle updating itself when
     * [TraceContextElement.updateThreadContext] is called for it.
     *
     * This is needed because on a multi-threaded dispatcher, one of the threads could be slow, and
     * [restoreThreadContext][TraceContextElement.restoreThreadContext] might be invoked _after_ the
     * coroutine has already resumed and modified [TraceData] (either adding or removing trace
     * sections, thus changing the count). If we do not store this count, then we might incorrectly
     * end too many or too few trace sections.
     *
     * Expected nesting should never exceed 255, so use a [ByteArray] to save memory. If nesting
     * _does_ exceed 255, it indicates there is something very wrong with the trace that will be
     * apparent when it's viewed in Perfetto, so we will not bother wasting CPU cycles to error
     * check that condition.
     */
    private var openSliceCount = ByteArray(INITIAL_THREAD_LOCAL_STACK_SIZE)

    private var continuationIds: LongArray = LongArray(INITIAL_THREAD_LOCAL_STACK_SIZE)

    private val debugCounterTrack: String? =
        if (DEBUG) "TCE#${Thread.currentThread().threadId()}" else null

    /**
     * Adds a new trace section to the current trace data. The slice will be traced on the current
     * thread immediately. This slice will not propagate to parent coroutines, or to child
     * coroutines that have already started.
     */
    @PublishedApi
    internal fun beginCoroutineTrace(name: String) {
        val data = data ?: return
        data.beginSpan(name)
        if (0 <= contIndex && contIndex < openSliceCount.size) {
            openSliceCount[contIndex]++
        }
    }

    /**
     * Ends the trace section and validates it corresponds with an earlier call to
     * [beginCoroutineTrace]. The trace slice will immediately be removed from the current thread.
     * This information will not propagate to parent coroutines, or to child coroutines that have
     * already started.
     *
     * @return true if span was ended, `false` if not
     */
    @PublishedApi
    internal fun endCoroutineTrace() {
        if (data?.endSpan() == true && 0 <= contIndex && contIndex < openSliceCount.size) {
            openSliceCount[contIndex]--
        }
    }

    /** Update [data] for continuation */
    fun updateDataForContinuation(contextTraceData: TraceData?, contId: Long) {
        data = contextTraceData
        val n = ++contIndex
        if (DEBUG) Trace.traceCounter(Trace.TRACE_TAG_APP, debugCounterTrack!!, n)
        if (n !in 0..<MAX_THREAD_LOCAL_STACK_SIZE) return // fail-safe
        var size = openSliceCount.size
        if (n >= size) {
            size = max(2 * size, MAX_THREAD_LOCAL_STACK_SIZE)
            openSliceCount = openSliceCount.copyInto(ByteArray(size))
            continuationIds = continuationIds.copyInto(LongArray(size))
        }
        openSliceCount[n] = data?.beginAllOnThread() ?: 0
        if (0 < contId) continuationIds[n] = contId
    }

    /** Update [data] for suspension */
    fun restoreDataForSuspension(oldState: TraceData?): Long {
        data = oldState
        val n = contIndex--
        if (DEBUG) Trace.traceCounter(Trace.TRACE_TAG_APP, debugCounterTrack!!, n)
        if (n < 0 || openSliceCount.size <= n) return 0 // fail-safe
        if (Trace.isTagEnabled(Trace.TRACE_TAG_APP)) {
            val lastState = openSliceCount[n]
            var i = 0
            while (i < lastState) {
                endSlice()
                i++
            }
        }
        return continuationIds.let { if (n < it.size) it[n] else null } ?: 0
    }
}

/**
 * Used for storing trace sections so that they can be added and removed from the currently running
 * thread when the coroutine is suspended and resumed.
 *
 * @property strictMode Whether to add additional checks to the coroutine machinery, throwing a
 *   `ConcurrentModificationException` if TraceData is modified from the wrong thread. This should
 *   only be set for testing.
 * @see traceCoroutine
 */
@PublishedApi
internal class TraceData(
    private val strictMode: Boolean,
    initialSlices: ArrayDeque<TraceSection>?,
) {

    internal var slices: ArrayDeque<TraceSection>? = initialSlices

    /**
     * Adds current trace slices back to the current thread. Called when coroutine is resumed.
     *
     * @return number of new trace sections started
     */
    internal fun beginAllOnThread(): Byte {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_APP)) {
            strictModeCheck()
            slices?.let {
                var count: Byte = 0
                it.descendingIterator().forEach { sectionName ->
                    beginSlice(name = sectionName)
                    count++
                }
                return count
            }
        }
        return 0
    }

    /**
     * Creates a new trace section with a unique ID and adds it to the current trace data. The slice
     * will also be added to the current thread immediately. This slice will not propagate to parent
     * coroutines, or to child coroutines that have already started. The unique ID is used to verify
     * that the [endSpan] is corresponds to a [beginSpan].
     */
    internal fun beginSpan(name: String) {
        strictModeCheck()
        val curSlices = slices ?: ArrayDeque<TraceSection>(4).also { slices = it }
        curSlices.push(name)
        beginSlice(name = name)
    }

    /**
     * Ends the trace section and validates it corresponds with an earlier call to [beginSpan]. The
     * trace slice will immediately be removed from the current thread. This information will not
     * propagate to parent coroutines, or to child coroutines that have already started.
     *
     * @return `true` if [endSlice] was called, `false` otherwise
     */
    internal fun endSpan(): Boolean {
        strictModeCheck()
        // Should never happen, but we should be defensive rather than crash the whole application
        slices.let {
            if (it != null && !it.isEmpty()) {
                it.pop()
                endSlice()
                return true
            } else if (strictMode) {
                throw IllegalStateException(INVALID_SPAN_END_CALL_ERROR_MESSAGE)
            }
            return false
        }
    }

    public override fun toString(): String =
        if (DEBUG) {
            (slices?.let {
                "{${it.joinToString(separator = "\", \"", prefix = "\"", postfix = "\"")}}"
            } ?: "{<uninitialized>}") + "@${hashCode()}"
        } else super.toString()

    private fun strictModeCheck() {
        if (strictMode && traceThreadLocal.get()?.data !== this) {
            throw ConcurrentModificationException(STRICT_MODE_ERROR_MESSAGE)
        }
    }
}

private const val INITIAL_THREAD_LOCAL_STACK_SIZE = 4

/**
 * The maximum allowed stack size for coroutine re-entry. Anything above this will cause malformed
 * traces. It should be set to a high number that should never happen, meaning if it were to occur,
 * there is likely an underlying bug.
 */
private const val MAX_THREAD_LOCAL_STACK_SIZE = 512

private const val INVALID_SPAN_END_CALL_ERROR_MESSAGE =
    "TraceData#endSpan called when there were no active trace sections in its scope."

private const val STRICT_MODE_ERROR_MESSAGE =
    "TraceData should only be accessed using " +
        "the ThreadLocal: CURRENT_TRACE.get(). Accessing TraceData by other means, such as " +
        "through the TraceContextElement's property may lead to concurrent modification."
