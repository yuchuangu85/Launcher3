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

@file:OptIn(ExperimentalContracts::class, ExperimentalContracts::class)

package com.android.app.tracing.coroutines

import com.android.app.tracing.coroutines.DebugSysProps.coroutineTracingEnabled
import com.android.app.tracing.traceSection
import com.android.systemui.util.Compile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** @see kotlinx.coroutines.coroutineScope */
public suspend inline fun <R> coroutineScopeTraced(
    crossinline spanName: () -> String,
    crossinline block: suspend CoroutineScope.() -> R,
): R {
    contract {
        callsInPlace(spanName, InvocationKind.AT_MOST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return coroutineScope { traceCoroutine(spanName) { block() } }
}

/** @see kotlinx.coroutines.coroutineScope */
public suspend inline fun <R> coroutineScopeTraced(
    traceName: String,
    crossinline block: suspend CoroutineScope.() -> R,
): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return coroutineScopeTraced({ traceName }, block)
}

/**
 * Convenience function for calling [CoroutineScope.launch] with an additional context element for
 * naming the new coroutine for tracing.
 *
 * @param spanName lazy String to be used to name the new coroutine
 * @see launch
 * @see CoroutineTraceName
 */
public inline fun CoroutineScope.launchTraced(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    noinline block: suspend CoroutineScope.() -> Unit,
): Job {
    contract { callsInPlace(spanName, InvocationKind.AT_MOST_ONCE) }
    return launch(addName(spanName, context), start, block)
}

/**
 * Convenience function for calling [CoroutineScope.launch] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
public fun CoroutineScope.launchTraced(
    spanName: String? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    return launchTraced({ spanName ?: block.traceName }, context, start, block)
}

/** @see kotlinx.coroutines.flow.launchIn */
public inline fun <T> Flow<T>.launchInTraced(
    crossinline spanName: () -> String,
    scope: CoroutineScope,
): Job {
    contract { callsInPlace(spanName, InvocationKind.AT_MOST_ONCE) }
    return scope.launchTraced(spanName) { collect() }
}

/** @see kotlinx.coroutines.flow.launchIn */
public fun <T> Flow<T>.launchInTraced(spanName: String, scope: CoroutineScope): Job {
    return scope.launchTraced({ spanName }) { collect() }
}

/**
 * Convenience function for calling [CoroutineScope.async] with an additional context element for
 * naming the new coroutine for tracing.
 *
 * @param spanName lazy String to be used to name the new coroutine
 * @see async
 * @see CoroutineTraceName
 */
public inline fun <T> CoroutineScope.asyncTraced(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    noinline block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    contract { callsInPlace(spanName, InvocationKind.AT_MOST_ONCE) }
    return async(addName(spanName, context), start, block)
}

/**
 * Convenience function for calling [CoroutineScope.async] with [traceCoroutine] enable tracing.
 *
 * @see traceCoroutine
 */
public fun <T> CoroutineScope.asyncTraced(
    spanName: String? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    return asyncTraced({ spanName ?: block.traceName }, context, start, block)
}

/**
 * Convenience function for calling [withContext] with [traceCoroutine].
 *
 * @param spanName lazy String used to name of the section of coroutine execution
 * @see withContext
 */
public suspend inline fun <T> withContextTraced(
    crossinline spanName: () -> String,
    context: CoroutineContext,
    noinline block: suspend CoroutineScope.() -> T,
): T {
    contract {
        callsInPlace(spanName, InvocationKind.AT_MOST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return traceCoroutine(spanName) { withContext(context, block) }
}

/**
 * Convenience function for calling [withContext] with [traceCoroutine].
 *
 * @param spanName name of the section of coroutine execution. If `null`, the class name of the
 *   `block` will be traced instead
 * @see withContext
 */
public suspend inline fun <T> withContextTraced(
    spanName: String? = null,
    context: CoroutineContext,
    noinline block: suspend CoroutineScope.() -> T,
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withContextTraced({ spanName ?: block.traceName }, context, block)
}

/** @see kotlinx.coroutines.runBlocking */
public inline fun <T> runBlockingTraced(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    noinline block: suspend CoroutineScope.() -> T,
): T {
    contract {
        callsInPlace(spanName, InvocationKind.AT_MOST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return traceSection({ "runBlocking:${spanName()}" }) { runBlocking(context, block) }
}

/** @see kotlinx.coroutines.runBlocking */
public fun <T> runBlockingTraced(
    spanName: String? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return runBlockingTraced({ spanName ?: block.traceName }, context, block)
}

/**
 * Traces a section of work of a `suspend` [block]. The trace sections will appear on the thread
 * that is currently executing the [block] of work. If the [block] is suspended, all trace sections
 * added using this API will end until the [block] is resumed, which could happen either on this
 * thread or on another thread. If a child coroutine is started, it will *NOT* inherit the trace
 * sections of its parent; however, it will include metadata in the trace section pointing to the
 * parent.
 *
 * The current [CoroutineContext] must have a [TraceContextElement] for this API to work. Otherwise,
 * the trace sections will be dropped.
 *
 * For example, in the following trace, Thread #1 starts a coroutine, suspends, and continues the
 * coroutine on Thread #2. Next, Thread #2 start a child coroutine in an unconfined manner. Then,
 * still on Thread #2, the original coroutine suspends, the child resumes, and the child suspends.
 * Then, the original coroutine resumes on Thread#1.
 *
 * ```
 * -----------------------------------------------------------------------------------------------|
 * Thread #1 | [== Slice A ==]                                            [==== Slice A ====]
 *           |       [== B ==]                                            [=== B ===]
 * -----------------------------------------------------------------------------------------------|
 * Thread #2 |                     [==== Slice A ====]    [=== C ====]
 *           |                     [======= B =======]
 *           |                         [=== C ====]
 * -----------------------------------------------------------------------------------------------|
 * ```
 *
 * @param spanName The name of the code section to appear in the trace
 * @see traceCoroutine
 */
public inline fun <T, R> R.traceCoroutine(crossinline spanName: () -> String, block: R.() -> T): T {
    contract {
        callsInPlace(spanName, InvocationKind.AT_MOST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // For coroutine tracing to work, trace spans must be added and removed even when
    // tracing is not active (i.e. when TRACE_TAG_APP is disabled). Otherwise, when the
    // coroutine resumes when tracing is active, we won't know its name.
    try {
        if (Compile.IS_DEBUG && coroutineTracingEnabled) {
            traceThreadLocal.get()?.beginCoroutineTrace(spanName())
        }
        return block()
    } finally {
        if (Compile.IS_DEBUG && coroutineTracingEnabled) {
            traceThreadLocal.get()?.endCoroutineTrace()
        }
    }
}

public inline fun <T> traceCoroutine(crossinline spanName: () -> String, block: () -> T): T {
    contract {
        callsInPlace(spanName, InvocationKind.AT_MOST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // For coroutine tracing to work, trace spans must be added and removed even when
    // tracing is not active (i.e. when TRACE_TAG_APP is disabled). Otherwise, when the
    // coroutine resumes when tracing is active, we won't know its name.
    try {
        if (Compile.IS_DEBUG && coroutineTracingEnabled) {
            traceThreadLocal.get()?.beginCoroutineTrace(spanName())
        }
        return block()
    } finally {
        if (Compile.IS_DEBUG && coroutineTracingEnabled) {
            traceThreadLocal.get()?.endCoroutineTrace()
        }
    }
}

/** @see traceCoroutine */
public inline fun <T, R> R.traceCoroutine(spanName: String, block: R.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return traceCoroutine({ spanName }, block)
}

/** @see traceCoroutine */
public inline fun <T> traceCoroutine(spanName: String, block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return traceCoroutine({ spanName }, block)
}

/**
 * Returns the given `CoroutineContext` if coroutine tracing is disabled. Otherwise, returns a new
 * context by adding [CoroutineTraceName] to the given context. If the given [CoroutineTraceName]
 * already has a [CoroutineTraceName], it will be used instead of the one that would have been
 * created here, and it will preserve its original name.
 */
@PublishedApi
internal inline fun addName(
    crossinline spanName: () -> String,
    context: CoroutineContext,
): CoroutineContext {
    contract { callsInPlace(spanName, InvocationKind.AT_MOST_ONCE) }
    return if (Compile.IS_DEBUG && coroutineTracingEnabled) {
        CoroutineTraceName(spanName()) + context
    } else {
        context
    }
}

@PublishedApi
internal inline val <reified T : Any> T.traceName: String
    inline get() = this::class.java.name.substringAfterLast(".")
