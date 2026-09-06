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

@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package com.android.test.tracing.coroutines

import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.app.tracing.coroutines.COROUTINE_EXECUTION
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.traceThreadLocal
import com.android.test.tracing.coroutines.util.FakeTraceState
import com.android.test.tracing.coroutines.util.FakeTraceState.getOpenTraceSectionsOnCurrentThread
import com.android.test.tracing.coroutines.util.ShadowTrace
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemProperties

class InvalidTraceStateException(message: String, cause: Throwable? = null) :
    AssertionError(message, cause)

internal val mainTestDispatcher = newSingleThreadContext("test-main")
internal val bgThread1 = newSingleThreadContext("test-bg-1")
internal val bgThread2 = newSingleThreadContext("test-bg-2")
internal val bgThread3 = newSingleThreadContext("test-bg-3")
internal val bgThread4 = newSingleThreadContext("test-bg-4")

sealed interface TraceStatus

object TraceCorrect : TraceStatus

class TraceError(val message: String) : TraceStatus

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowTrace::class, ShadowSystemProperties::class])
abstract class TestBase {
    companion object {
        @JvmField
        @ClassRule
        val setFlagsClassRule: SetFlagsRule.ClassRule =
            SetFlagsRule.ClassRule(com.android.systemui.Flags::class.java)

        @JvmStatic
        private fun isRobolectricTest(): Boolean {
            return System.getProperty("java.vm.name") != "Dalvik"
        }
    }

    // TODO(b/339471826): Robolectric does not execute @ClassRule correctly
    @get:Rule
    val setFlagsRule: SetFlagsRule =
        if (isRobolectricTest()) SetFlagsRule() else setFlagsClassRule.createSetFlagsRule()

    private val eventCounter = AtomicInteger(0)
    private val allEventCounter = AtomicInteger(0)
    private val finalEvent = AtomicInteger(INVALID_EVENT)
    private val allExceptions = mutableListOf<Throwable>()
    private val assertionErrors = mutableListOf<AssertionError>()

    /** The scope to be used by the test in [runTest] */
    val scope: CoroutineScope by lazy { CoroutineScope(extraContext + mainTestDispatcher) }

    /**
     * Context passed to the scope used for the test. If the returned [CoroutineContext] contains a
     * [CoroutineDispatcher] it will be overwritten.
     */
    open val extraContext: CoroutineContext by lazy {
        createCoroutineTracingContext("main", testMode = true)
    }

    @Before
    fun setup() {
        ShadowSystemProperties.reset()
        ShadowSystemProperties.override("persist.debug.coroutine_tracing", "true")
        ShadowSystemProperties.override("persist.debug.coroutine_tracing.walk_stack", "false")
        ShadowSystemProperties.override(
            "persist.debug.coroutine_tracing.count_continuations",
            "false",
        )

        FakeTraceState.isTracingEnabled = true
        FakeTraceState.clearAll()

        // Reset all thread-local state
        traceThreadLocal.remove()
        val dispatchers = listOf(mainTestDispatcher, bgThread1, bgThread2, bgThread3, bgThread4)
        runBlocking { dispatchers.forEach { withContext(it) { traceThreadLocal.remove() } } }

        // Initialize scope, which is a lazy type:
        assertTrue(scope.isActive)
    }

    @After
    fun tearDown() {
        val sw = StringWriter()
        val pw = PrintWriter(sw)

        allExceptions.forEach { it.printStackTrace(pw) }
        assertTrue("Test failed due to unexpected exception\n$sw", allExceptions.isEmpty())

        assertionErrors.forEach { it.printStackTrace(pw) }
        assertTrue("Test failed due to incorrect trace sections\n$sw", assertionErrors.isEmpty())
    }

    /**
     * Launches the test on the provided [scope], then uses [runBlocking] to wait for completion.
     * The test will timeout if it takes longer than 200ms.
     */
    protected fun runTest(
        isExpectedException: ((Throwable) -> Boolean)? = null,
        finalEvent: Int? = null,
        totalEvents: Int? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        var foundExpectedException = false
        try {
            val job =
                scope.launch(
                    context =
                        CoroutineExceptionHandler { _, e ->
                            if (e is CancellationException)
                                return@CoroutineExceptionHandler // ignore it
                            if (isExpectedException != null && isExpectedException(e)) {
                                foundExpectedException = true
                            } else {
                                allExceptions.add(e)
                            }
                        },
                    block = block,
                )

            runBlocking {
                val timeoutMs = 200L
                try {
                    withTimeout(timeoutMs) { job.join() }
                } catch (e: TimeoutCancellationException) {
                    fail("Timeout running test. Test should complete in less than $timeoutMs ms")
                    throw e
                } finally {
                    scope.cancel()
                }
            }
        } finally {
            if (isExpectedException != null && !foundExpectedException) {
                fail("Expected exceptions, but none were thrown")
            }
        }
        if (finalEvent != null) {
            checkFinalEvent(finalEvent)
        }
        if (totalEvents != null) {
            checkTotalEvents(totalEvents)
        }
    }

    /**
     * Same as [expect], but also call [delay] for 1ms, calling [expect] before and after the
     * suspension point.
     */
    protected suspend fun expectD(vararg expectedOpenTraceSections: String) {
        expect(*expectedOpenTraceSections)
        delay(1)
        expect(*expectedOpenTraceSections)
    }

    protected fun expectEndsWith(vararg expectedOpenTraceSections: String) {
        allEventCounter.getAndAdd(1)
        // Inspect trace output to the fake used for recording android.os.Trace API calls:
        val actualSections = getOpenTraceSectionsOnCurrentThread()
        if (expectedOpenTraceSections.size <= actualSections.size) {
            val lastSections =
                actualSections.takeLast(expectedOpenTraceSections.size).toTypedArray()
            assertTraceSectionsEquals(expectedOpenTraceSections, null, lastSections, null)
        } else {
            assertionErrors +=
                InvalidTraceStateException(
                    "Invalid length: expected size (${expectedOpenTraceSections.size}) <= actual size (${actualSections.size})"
                )
        }
    }

    protected fun expectEvent(expectedEvent: Collection<Int>): Int {
        val previousEvent = eventCounter.getAndAdd(1)
        val currentEvent = previousEvent + 1
        if (!expectedEvent.contains(currentEvent)) {
            assertionErrors +=
                InvalidTraceStateException(
                    if (previousEvent == FINAL_EVENT) {
                        "Expected event ${expectedEvent.prettyPrintList()}, but finish() was already called"
                    } else {
                        "Expected event ${expectedEvent.prettyPrintList()}," +
                            " but the event counter is currently at #$currentEvent"
                    }
                )
        }
        return currentEvent
    }

    /**
     * Checks the currently active trace sections on the current thread, and optionally checks the
     * order of operations if [expectedEvent] is not null.
     */
    internal fun expectAny(vararg possibleOpenSections: Array<out String>) {
        allEventCounter.getAndAdd(1)
        val actualOpenSections = getOpenTraceSectionsOnCurrentThread()
        val caughtExceptions = mutableListOf<AssertionError>()
        possibleOpenSections.forEach { expectedSections ->
            try {
                val status =
                    assertTraceSectionsEquals(
                        expectedSections,
                        expectedEvent = null,
                        actualOpenSections,
                        actualEvent = null,
                    )
                if (status is TraceError) {
                    throw InvalidTraceStateException(status.message)
                }
            } catch (e: AssertionError) {
                caughtExceptions.add(e)
            }
        }
        if (caughtExceptions.size == possibleOpenSections.size) {
            val e = caughtExceptions[0]
            val allLists =
                possibleOpenSections.joinToString(separator = ", OR ") { it.prettyPrintList() }
            assertionErrors.add(
                InvalidTraceStateException("Expected $allLists. For example, ${e.message}", e.cause)
            )
        }
    }

    private inline fun throwError(status: TraceStatus) {
        if (status is TraceError) {
            assertionErrors += InvalidTraceStateException(status.message)
        }
    }

    internal fun expect(vararg expectedOpenTraceSections: String) {
        val status = expectInternal(null, *expectedOpenTraceSections)
        throwError(status)
    }

    internal fun expect(expectedEvent: Int, vararg expectedOpenTraceSections: String) {
        val status = expectInternal(listOf(expectedEvent), *expectedOpenTraceSections)
        throwError(status)
    }

    internal fun expect(possibleEventPos: List<Int>?, vararg expectedOpenTraceSections: String) {
        val status = expectInternal(possibleEventPos, *expectedOpenTraceSections)
        throwError(status)
    }

    /**
     * Checks the currently active trace sections on the current thread, and optionally checks the
     * order of operations if [expectedEvent] is not null.
     */
    private fun expectInternal(
        possibleEventPos: List<Int>?,
        vararg expectedOpenTraceSections: String,
    ): TraceStatus {
        var currentEvent: Int? = null
        allEventCounter.getAndAdd(1)
        if (possibleEventPos != null) {
            currentEvent = expectEvent(possibleEventPos)
        }
        val actualOpenSections = getOpenTraceSectionsOnCurrentThread()
        return assertTraceSectionsEquals(
            expectedOpenTraceSections,
            possibleEventPos,
            actualOpenSections,
            currentEvent,
        )
    }

    private fun assertTraceSectionsEquals(
        expectedOpenTraceSections: Array<out String>,
        expectedEvent: List<Int>?,
        actualOpenSections: Array<String>,
        actualEvent: Int?,
    ): TraceStatus {
        val expectedSize = expectedOpenTraceSections.size
        val actualSize = actualOpenSections.size
        if (expectedSize != actualSize) {
            return TraceError(
                createFailureMessage(
                    expectedOpenTraceSections,
                    expectedEvent,
                    actualOpenSections,
                    actualEvent,
                    "Size mismatch, expected size $expectedSize but was size $actualSize",
                )
            )
        } else {
            expectedOpenTraceSections.forEachIndexed { n, expected ->
                val actualTrace = actualOpenSections[n]
                val actual = actualTrace.getTracedName()
                if (expected != actual) {
                    return TraceError(
                        createFailureMessage(
                            expectedOpenTraceSections,
                            expectedEvent,
                            actualOpenSections,
                            actualEvent,
                            "Differed at index #$n, expected \"$expected\" but was \"$actual\"",
                        )
                    )
                }
            }
        }
        return TraceCorrect
    }

    private fun createFailureMessage(
        expectedOpenTraceSections: Array<out String>,
        expectedEventNumber: List<Int>?,
        actualOpenSections: Array<String>,
        actualEventNumber: Int?,
        extraMessage: String,
    ): String {
        val locationMarker =
            if (expectedEventNumber == null || actualEventNumber == null) ""
            else if (expectedEventNumber.contains(actualEventNumber))
                " at event #$actualEventNumber"
            else
                ", expected event ${expectedEventNumber.prettyPrintList()}, actual event #$actualEventNumber"
        return """
               Incorrect trace$locationMarker. $extraMessage
                 Expected : {${expectedOpenTraceSections.prettyPrintList()}}
                 Actual   : {${actualOpenSections.prettyPrintList()}}
               """
            .trimIndent()
    }

    private fun checkFinalEvent(expectedEvent: Int): Int {
        finalEvent.compareAndSet(INVALID_EVENT, expectedEvent)
        val previousEvent = eventCounter.getAndSet(FINAL_EVENT)
        if (expectedEvent != previousEvent) {
            assertionErrors +=
                InvalidTraceStateException(
                    "Expected to finish with event #$expectedEvent, but " +
                        if (previousEvent == FINAL_EVENT)
                            "finish() was already called with event #${finalEvent.get()}"
                        else "the event counter is currently at #$previousEvent"
                )
        }
        return previousEvent
    }

    private fun checkTotalEvents(totalEvents: Int): Int {
        allEventCounter.compareAndSet(INVALID_EVENT, totalEvents)
        val previousEvent = allEventCounter.getAndSet(FINAL_EVENT)
        if (totalEvents != previousEvent) {
            assertionErrors +=
                InvalidTraceStateException(
                    "Expected test to end with a total of $totalEvents events, but " +
                        if (previousEvent == FINAL_EVENT)
                            "finish() was already called at event #${finalEvent.get()}"
                        else "instead there were $previousEvent events"
                )
        }
        return previousEvent
    }
}

private fun String.getTracedName(): String =
    if (startsWith(COROUTINE_EXECUTION))
    // For strings like "coroutine execution;scope-name;c=1234;p=5678", extract:
    // "scope-name"
    substringAfter(";").substringBefore(";")
    else substringBefore(";")

private const val INVALID_EVENT = -1

private const val FINAL_EVENT = Int.MIN_VALUE

private fun Collection<Int>.prettyPrintList(): String {
    return if (isEmpty()) ""
    else if (size == 1) "#${iterator().next()}"
    else {
        "{${
            toList().joinToString(
                separator = ", #",
                prefix = "#",
                postfix = "",
            ) { it.toString() }
        }}"
    }
}

private fun Array<out String>.prettyPrintList(): String {
    return if (isEmpty()) ""
    else
        toList().joinToString(separator = "\", \"", prefix = "\"", postfix = "\"") {
            it.getTracedName()
        }
}
