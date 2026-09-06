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

package com.android.app.tracing.coroutines

import android.annotation.SuppressLint
import android.os.PerfettoCategories
import android.os.SystemProperties
import android.os.Trace
import android.util.Log
import com.android.app.tracing.coroutines.DebugSysProps.coroutineTracingEnabled
import com.android.internal.dev.perfetto.sdk.PerfettoTrace as PerfettoTraceV3
import com.android.systemui.util.Compile
import java.lang.StackWalker.StackFrame
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.AbstractCoroutineContextKey
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.getPolymorphicElement
import kotlin.coroutines.minusPolymorphicKey
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Thread-local storage for tracking open trace sections in the current coroutine context; it should
 * only be used when paired with a [TraceContextElement].
 *
 * [traceThreadLocal] will be `null` if the code being executed is either 1) not part of coroutine,
 * or 2) part of a coroutine that does not have a [TraceContextElement] in its context. In both
 * cases, writing to this thread-local will result in undefined behavior. However, it is safe to
 * check if [traceThreadLocal] is `null` to determine if coroutine tracing is enabled.
 *
 * @see traceCoroutine
 */
@PublishedApi internal val traceThreadLocal: TraceDataThreadLocal = TraceDataThreadLocal()

/**
 * Object for holding the values of debug sysprops, which are used for modifying the behavior of
 * coroutine tracing. The sysprops are read when the library was first initialized.
 */
@PublishedApi
internal object DebugSysProps {
    /**
     * Value of `persist.debug.coroutine_tracing` sysprop
     *
     * @see TraceContextElement
     */
    @JvmField
    val coroutineTracingEnabled =
        Compile.IS_DEBUG && SystemProperties.getBoolean("persist.debug.coroutine_tracing", false)

    /**
     * Value of `persist.debug.coroutine_tracing.walk_stack` sysprop
     *
     * @see TraceContextElement
     */
    @JvmField
    val stackWalkerAlwaysEnabled =
        Compile.IS_DEBUG &&
            SystemProperties.getBoolean("persist.debug.coroutine_tracing.walk_stack", true)

    /**
     * Value of `persist.debug.coroutine_tracing.count_continuations` sysprop
     *
     * @see TraceContextElement
     */
    @JvmField
    val continuationCountingAlwaysEnabled =
        Compile.IS_DEBUG &&
            SystemProperties.getBoolean(
                "persist.debug.coroutine_tracing.count_continuations",
                false,
            )

    /**
     * Value of `persist.debug.coroutine_tracing.inherit_slices` sysprop
     *
     * @see TraceContextElement
     */
    @JvmField
    val inheritParentSlices =
        Compile.IS_DEBUG &&
            SystemProperties.getBoolean("persist.debug.coroutine_tracing.inherit_slices", false)

    /**
     * Value of `persist.debug.coroutine_tracing.dump_init_stack` sysprop
     *
     * @see TraceContextElement
     */
    @JvmField
    val dumpInitStack =
        Compile.IS_DEBUG &&
            SystemProperties.getBoolean("persist.debug.coroutine_tracing.dump_init_stack", false)

    /**
     * Value of `persist.debug.coroutine_tracing.dump_continuation_stack` sysprop
     *
     * @see TraceContextElement
     */
    @JvmField
    val dumpContinuationStack =
        Compile.IS_DEBUG &&
            SystemProperties.getBoolean(
                "persist.debug.coroutine_tracing.dump_continuation_stack",
                false,
            )

    /**
     * Value of `persist.debug.coroutine_tracing.flow_values` sysprop
     *
     * @see TraceContextElement
     */
    @JvmField
    val traceFlowValues =
        Compile.IS_DEBUG &&
            SystemProperties.getBoolean("persist.debug.coroutine_tracing.flow_values", false)
}

/**
 * Returns a new [TraceContextElement] (or [EmptyCoroutineContext] if `coroutine_tracing` feature is
 * flagged off). This context should only be installed in root [CoroutineScopes][CoroutineScope].
 *
 * See [TraceContextElement] for explanation of proper usage.
 *
 * **NOTE:** The sysprops `persist.debug.coroutine_tracing.walk_stack` and
 * `persist.debug.coroutine_tracing.count_continuations` can be used to override the parameters
 * `walkStackForDefaultNames` and `countContinuations` respectively, forcing them to always be
 * `true`. If the sysprop is `false` (or does not exist), the value of the parameter is passed here
 * is used. If `true`, all calls to [createCoroutineTracingContext] will be overwritten with that
 * parameter set to `true`. Importantly, this means that the sysprops can be used to globally turn
 * ON `walkStackForDefaultNames` or `countContinuations`, but they cannot be used to globally turn
 * OFF either parameter.
 *
 * @param name the name of the coroutine scope. Since this should only be installed on top-level
 *   coroutines, this should be the name of the root [CoroutineScope].
 * @param walkStackForDefaultNames whether to walk the stack and use the class name of the current
 *   suspending function if child does not have a name that was manually specified. Walking the
 *   stack is very expensive so this should not be used in production.
 * @param countContinuations whether to include extra info in the trace section indicating the total
 *   number of times a coroutine has suspended and resumed (e.g. ";n=#")
 * @param countDepth whether to include extra info in the trace section indicating the how far from
 *   the root trace context this coroutine is (e.g. ";d=#")
 * @param testMode changes behavior is several ways: 1) parent names and sibling counts are
 *   concatenated with the name of the child. This can result in extremely long trace names, which
 *   is why it is only for testing. 2) additional strict-mode checks are added to coroutine tracing
 *   machinery. These checks are expensive and should only be used for testing. 3) omits "coroutine
 *   execution" trace slices, and omits coroutine metadata slices. If [testMode] is enabled,
 *   [countContinuations] and [countDepth] are ignored.
 * @see TraceContextElement
 */
public fun createCoroutineTracingContext(
    name: String = "UnnamedScope",
    countContinuations: Boolean = false,
    countDepth: Boolean = false,
    usePerfettoSdk: Boolean = true,
    testMode: Boolean = false,
    walkStackForDefaultNames: Boolean = false,
): CoroutineContext {
    return if (Compile.IS_DEBUG && coroutineTracingEnabled) {
        TraceContextElement(
            // Minor perf optimization: no need to create TraceData() for root scopes since all
            // launches require creation of child via [copyForChild] or [mergeForChild].
            // Alos, we will use `contextTraceData = null` to indicate this is a root TCE.
            contextTraceData = null,
            name = name,
            countContinuations =
                !testMode &&
                    (countContinuations || DebugSysProps.continuationCountingAlwaysEnabled),
            walkStackForDefaultNames =
                walkStackForDefaultNames || DebugSysProps.stackWalkerAlwaysEnabled,
            parentId = null,
            // Only the `android.os.Trace` APIs currently have test shadows, so do not allow
            // Perfetto SDK usage when testMode=true
            usePerfettoSdk = !testMode && usePerfettoSdk,
            inheritedTracePrefix = if (testMode) "" else null,
            coroutineDepth = if (!testMode && countDepth) 0 else -1,
        )
    } else {
        EmptyCoroutineContext
    }
}

/**
 * Context element for naming _new_ coroutines. When a [CoroutineTraceName] is merged with a
 * [TraceContextElement], the new child's copy of the `TraceContextElement` will be assigned a name
 * equal to this context element's [name].
 *
 * `CoroutineTraceName` should not be confused with
 * [CoroutineName][kotlinx.coroutines.CoroutineName]; they are separate context elements with
 * different purposes.
 */
@PublishedApi
internal open class CoroutineTraceName(internal val name: String?) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineTraceName>

    override val key: CoroutineContext.Key<*>
        get() = Key

    @OptIn(ExperimentalStdlibApi::class)
    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? =
        getPolymorphicElement(key)

    @OptIn(ExperimentalStdlibApi::class)
    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = minusPolymorphicKey(key)

    @Deprecated(
        message =
            """
         Operator `+` on two BaseTraceElement objects is meaningless. If used, the context element
         to the right of `+` would simply replace the element to the left. To properly use
         `BaseTraceElement`, `TraceContextElement` should be used when creating a top-level
         `CoroutineScope` and `CoroutineTraceName` should be passed to the child context that is
         under construction.
        """,
        level = DeprecationLevel.ERROR,
    )
    operator fun plus(other: CoroutineTraceName): CoroutineTraceName {
        return other
    }

    @Deprecated(
        message =
            """
         Operator `+` on two BaseTraceElement objects is meaningless. If used, the context element
         to the right of `+` would simply replace the element to the left. To properly use
         `BaseTraceElement`, `TraceContextElement` should be used when creating a top-level
         `CoroutineScope` and `CoroutineTraceName` should be passed to the child context that is
         under construction.
        """,
        level = DeprecationLevel.ERROR,
    )
    operator fun plus(other: TraceContextElement): TraceContextElement {
        return other
    }
}

private fun nextRandomLong(): Long {
    return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
}

internal class StackDump : Throwable()

/**
 * Coroutine context element for tracking parent-child relationships of coroutines and persisting
 * [TraceData] when coroutines are suspended and resumed.
 *
 * To use [TraceContextElement] call [createCoroutineTracingContext] and add the returned to your
 * root [CoroutineScope]. This will cause new coroutines in that scope (such as those created by
 * coroutine builder APIs such as [launch][kotlinx.coroutines.launch] and
 * [async][kotlinx.coroutines.async]) to _copy_ this trace context element into the newly created
 * child coroutine.
 *
 * Note: you should only ever need to call [createCoroutineTracingContext] _once_ per root
 * `CoroutineScope. To name the child coroutine, add [CoroutineTraceName] to the child's coroutine
 * context under construction. For example, the following would name the child coroutine
 * "name-for-child".
 *
 * ```
 * val scope = CoroutineScope(createCoroutineTracingContext("name-of-root"))
 * scope.launch(CoroutineTraceName("name-for-child")) {
 *   traceCoroutine("delay for 10ms") {
 *     delay(10)
 *   }
 * }
 * ```
 *
 * To make the process of naming children easier, this library provides APIs such as [launchTraced],
 * which takes a string and adds it to a [CoroutineTraceName] before launching. The `launch` call in
 * the above snippet is equivalent to:
 * ```
 * import com.android.app.tracing.coroutines.launchTraced
 * ...
 * scope.launchTraced("name-for-child") {
 *   ...
 * }
 * ```
 *
 * The behavior of `TraceContextElement` will vary depending on which system properties are set:
 * - `persist.debug.coroutine_tracing`: enable the coroutine tracing feature. Note: any application
 *   using this library will need to be restarted for this to take effect.
 * - `persist.debug.coroutine_tracing.walk_stack`: whether to walk the stack to infer a name for the
 *   coroutine.
 * - `persist.debug.coroutine_tracing.inherit_slices`: If set, coroutine-local slices from the
 *   parent are passed to the child. NOTE: If parent later closes a slice, it won't be reflected in
 *   the child coroutine. Also, slices named ~ will be used as a separator between the inherited
 *   slices and the child's.
 * - `persist.debug.coroutine_tracing.count_continuations`: Include a count of the number of times
 *   the coroutine was resumed, e.g. `;n=1234;`, inside the `coroutine execution;` slice.
 * - `persist.debug.coroutine_tracing.dump_init_stack`: If set, dump the call-stack at the time
 *   copy/merge for child is called.
 * - `persist.debug.coroutine_tracing.dump_continuation_stack`: If set, dump the call stack on each
 *   coroutine resumption.
 *
 * @property contextTraceData [TraceData] to be saved to thread-local storage.
 * @param name The name of the current coroutine. Since this should only be installed on top-level
 *   coroutines, this should be the name of the root [CoroutineScope].
 * @param parentId The ID of the parent coroutine
 * @param inheritedTracePrefix Prefix containing metadata for parent scopes. Each child is separated
 *   by a `:` and prefixed by a counter indicating the ordinal of this child relative to its
 *   siblings. Thus, the prefix such as `root-name:3^child-name` would indicate this is the 3rd
 *   child (of any name) to be started on `root-scope`. If the child has no name, an empty string
 *   would be used instead: `root-scope:3^`
 * @param coroutineDepth How deep the coroutine is relative to the top-level [CoroutineScope]
 *   containing the original [TraceContextElement] from which this [TraceContextElement] was copied.
 *   If -1, counting depth is disabled
 * @see createCoroutineTracingContext
 * @see CoroutineTraceName
 * @see traceCoroutine
 */
@SuppressLint("UnclosedTrace")
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal class TraceContextElement(
    internal val contextTraceData: TraceData?,
    name: String,
    countContinuations: Boolean,
    private val walkStackForDefaultNames: Boolean,
    parentId: Long?,
    private val usePerfettoSdk: Boolean = true,
    inheritedTracePrefix: String?,
    coroutineDepth: Int,
) : CopyableThreadContextElement<TraceData?>, CoroutineTraceName(name), CoroutineContext.Element {
    @OptIn(ExperimentalStdlibApi::class)
    companion object Key :
        AbstractCoroutineContextKey<CoroutineTraceName, TraceContextElement>(
            CoroutineTraceName,
            { it as? TraceContextElement },
        )

    private val currentId: Long = nextRandomLong()
    private val nameWithId = "$name;c=$currentId;p=${parentId ?: "none"}"

    private var continuationId: Long = if (usePerfettoSdk) nextRandomLong() else 0

    private var initStack: String? = null

    init {
        val traceSection = "TCE#init;$nameWithId"
        debug { traceSection }
        if (usePerfettoSdk) {
            if (
                android.os.Flags.perfettoSdkTracingV3() &&
                    PerfettoCategories.CC_CATEGORY.isEnabled()
            ) {
                PerfettoTraceV3.begin(PerfettoCategories.CC_CATEGORY, traceSection).emit()
            }
        } else {
            Trace.traceBegin(Trace.TRACE_TAG_APP, traceSection) // begin: "TCE#init"
        }
        if (DebugSysProps.dumpInitStack) {
            initStack = StackDump().stackTraceToString()
        }
    }

    private var coroutineTraceName: String =
        if (inheritedTracePrefix == null) {
            COROUTINE_EXECUTION +
                nameWithId +
                (if (coroutineDepth == -1) "" else ";d=$coroutineDepth") +
                (if (countContinuations) ";n=" else "")
        } else {
            "$inheritedTracePrefix$name"
        }

    private var continuationCount = if (countContinuations) 0 else Int.MIN_VALUE
    private val childDepth =
        if (inheritedTracePrefix != null || coroutineDepth == -1) -1 else coroutineDepth + 1

    private val childCoroutineCount = if (inheritedTracePrefix != null) AtomicInteger(0) else null

    private val copyForChildTraceMessage = "TCE#copy;$nameWithId"
    private val mergeForChildTraceMessage = "TCE#merge;$nameWithId"

    init {
        if (usePerfettoSdk) {
            if (
                android.os.Flags.perfettoSdkTracingV3() &&
                    PerfettoCategories.CC_CATEGORY.isEnabled()
            ) {
                PerfettoTraceV3.end(PerfettoCategories.CC_CATEGORY).setFlow(continuationId).emit()
            }
        } else {
            Trace.traceEnd(Trace.TRACE_TAG_APP) // end: "TCE#init"
        }
    }

    /**
     * This function is invoked before the coroutine is resumed on the current thread. When a
     * multi-threaded dispatcher is used, calls to `updateThreadContext` may happen in parallel to
     * the prior `restoreThreadContext` in the same context. However, calls to `updateThreadContext`
     * will not run in parallel on the same context.
     *
     * ```
     * Thread #1 | [updateThreadContext]....^              [restoreThreadContext]
     * --------------------------------------------------------------------------------------------
     * Thread #2 |                           [updateThreadContext]...........^[restoreThreadContext]
     * ```
     *
     * (`...` indicate coroutine body is running; whitespace indicates the thread is not scheduled;
     * `^` is a suspension point)
     */
    override fun updateThreadContext(context: CoroutineContext): TraceData? {
        debug { "TCE#update;$nameWithId" }
        // Calls to `updateThreadContext` will not happen in parallel on the same context,
        // and they cannot happen before the prior suspension point. Additionally,
        // `restoreThreadContext` does not modify `traceData`, so it is safe to iterate over
        // the collection here:
        val storage = traceThreadLocal.get() ?: return null
        val oldState = storage.data
        if (oldState === contextTraceData) return oldState
        if (usePerfettoSdk) {
            if (
                android.os.Flags.perfettoSdkTracingV3() &&
                    PerfettoCategories.CC_CATEGORY.isEnabled()
            ) {
                val name = coroutineTraceName + if (continuationCount < 0) "" else continuationCount
                val slice = PerfettoTraceV3.begin(PerfettoCategories.CC_CATEGORY, name)
                initStack?.let { slice.addArg("init_stack", it) }
                if (DebugSysProps.dumpContinuationStack) {
                    slice.addArg("continuation_stack", StackDump().stackTraceToString())
                }
                slice.setTerminatingFlow(continuationId).emit()
            }
            continuationId = nextRandomLong()
        } else {
            Trace.traceBegin(Trace.TRACE_TAG_APP, coroutineTraceName)
        }
        if (continuationCount >= 0) continuationCount++
        storage.updateDataForContinuation(contextTraceData, continuationId)
        return oldState
    }

    /**
     * This function is invoked after the coroutine has suspended on the current thread. When a
     * multi-threaded dispatcher is used, calls to `restoreThreadContext` may happen in parallel to
     * the subsequent `updateThreadContext` and `restoreThreadContext` operations. The coroutine
     * body itself will not run in parallel, but `TraceData` could be modified by a coroutine body
     * after the suspension point in parallel to `restoreThreadContext` associated with the
     * coroutine body _prior_ to the suspension point.
     *
     * ```
     * Thread #1 | [updateThreadContext].x..^              [restoreThreadContext]
     * --------------------------------------------------------------------------------------------
     * Thread #2 |                           [updateThreadContext]..x..x.....^[restoreThreadContext]
     * ```
     *
     * OR
     *
     * ```
     * Thread #1 |  [update].x..^  [   ...    restore    ...   ]              [update].x..^[restore]
     * --------------------------------------------------------------------------------------------
     * Thread #2 |                 [update]...x....x..^[restore]
     * --------------------------------------------------------------------------------------------
     * Thread #3 |                                     [ ... update ... ] ...^  [restore]
     * ```
     *
     * (`...` indicate coroutine body is running; whitespace indicates the thread is not scheduled;
     * `^` is a suspension point; `x` are calls to modify the thread-local trace data)
     *
     * ```
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: TraceData?) {
        debug { "TCE#restore;$nameWithId restoring=${System.identityHashCode(oldState)}" }
        /**
         * We not use the [TraceData] object here because it may have been modified on another
         * thread after the last suspension point. This is why we use a [TraceStorage] object
         * instead: so we can end the correct number of trace sections, restoring the thread to its
         * state prior to the last call to [updateThreadContext].
         */
        val storage = traceThreadLocal.get() ?: return
        if (storage.data === oldState) return
        val contId = storage.restoreDataForSuspension(oldState)
        if (usePerfettoSdk && android.os.Flags.perfettoSdkTracingV3()) {
            PerfettoTraceV3.end(PerfettoCategories.CC_CATEGORY).setFlow(contId).emit()
        } else {
            Trace.traceEnd(Trace.TRACE_TAG_APP) // end: coroutineTraceName
        }
    }

    override fun copyForChild(): CopyableThreadContextElement<TraceData?> {
        debug { copyForChildTraceMessage }
        try {
            Trace.traceBegin(Trace.TRACE_TAG_APP, copyForChildTraceMessage) // begin: TCE#copy
            // Root is a special case in which the name is copied to the child by default.
            // Otherwise, everything launched on a coroutine would have an empty name by default
            return createChildContext(null)
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_APP) // end: TCE#copy
        }
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        debug { mergeForChildTraceMessage }
        try {
            Trace.traceBegin(Trace.TRACE_TAG_APP, mergeForChildTraceMessage) // begin: TCE#merge
            // Only names from `CoroutineTraceName` should be used for the child coroutine.
            // The name of a merged `TraceContextElement` should be ignored, as should its
            // parameters.
            val other = overwritingElement
            return createChildContext(
                if (other is CoroutineTraceName && other !is TraceContextElement) {
                    other.name
                } else {
                    null
                }
            )
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_APP) // end: TCE#merge
        }
    }

    private fun createChildContext(name: String?): TraceContextElement {
        val testMode = childCoroutineCount != null
        return TraceContextElement(
            contextTraceData =
                TraceData(
                    initialSlices =
                        if (DebugSysProps.inheritParentSlices)
                            traceThreadLocal.get()?.data?.slices?.clone().also {
                                // Add a slice to separate the current coroutine elements from the
                                // parent elements that existed at the time this coroutine was
                                // started.
                                it?.push("~")
                            }
                        else null,
                    strictMode = testMode,
                ),
            name =
                if (name == null && walkStackForDefaultNames) walkStackForClassName()
                else name ?: "",
            countContinuations = continuationCount >= 0,
            walkStackForDefaultNames = walkStackForDefaultNames,
            parentId = currentId,
            usePerfettoSdk = !testMode && usePerfettoSdk,
            inheritedTracePrefix =
                if (testMode) {
                    val currentTceIsRoot = contextTraceData == null
                    val childCount = childCoroutineCount.incrementAndGet()
                    "${if (currentTceIsRoot) "" else "$coroutineTraceName:"}$childCount^"
                } else null,
            coroutineDepth = childDepth,
        )
    }
}

/**
 * Walks the stack to create a name for a coroutine based on the suspend functions in the call
 * stack. This is used to automatically generate a descriptive name when one isn't provided
 * explicitly.
 */
private fun walkStackForClassName(): String {
    Trace.traceBegin(Trace.TRACE_TAG_APP, "walkStackForClassName")
    try {
        return StackWalker.getInstance().walk { stackStream -> parseStackForName(stackStream) }
    } catch (e: Exception) {
        if (DEBUG) Log.e(TAG, "Error walking stack to infer a trace name", e)
        return ""
    } finally {
        Trace.traceEnd(Trace.TRACE_TAG_APP)
    }
}

internal fun parseStackForName(stackStream: Stream<StackFrame>): String {
    val sb = StringBuilder()
    stackStream.forEach { f: StackFrame ->
        if (f.methodName.startsWith("invokeSuspend")) {
            if (!sb.isEmpty()) {
                sb.append("<~")
            }
            sb.append(f.className.substringAfterLast('.'))
        }
    }
    return sb.toString()
}

@PublishedApi internal const val COROUTINE_EXECUTION: String = "coroutine execution;"

@PublishedApi internal const val TAG: String = "CoroutineTracing"

@PublishedApi internal const val DEBUG: Boolean = false

@OptIn(ExperimentalContracts::class)
private inline fun debug(message: () -> String) {
    contract { callsInPlace(message, InvocationKind.AT_MOST_ONCE) }
    if (DEBUG) {
        val msg = message()
        Trace.instant(Trace.TRACE_TAG_APP, msg)
        Log.d(TAG, msg)
    }
}
