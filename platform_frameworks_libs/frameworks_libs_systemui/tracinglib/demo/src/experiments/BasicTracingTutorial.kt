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

import com.android.app.tracing.TraceUtils.traceAsync
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.launchTraced
import com.example.tracing.demo.FixedThread1
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@Singleton
class BasicTracingTutorial
@Inject
constructor(@param:FixedThread1 private var handlerDispatcher: CoroutineDispatcher) : Experiment() {

    override val description: String = "Basic tracing tutorial"

    @OptIn(ExperimentalContracts::class)
    private suspend inline fun runStep(stepNumber: Int = 0, crossinline block: (Job) -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        traceAsync(TRACK_NAME, "Step #$stepNumber") { block(coroutineContext.job) }
        traceAsync(TRACK_NAME, "cooldown") { delay(10) }
    }

    /** 1: Untraced coroutine on default dispatcher */
    private fun step1UntracedCoroutineOnDefaultDispatcher(job: Job) {
        // First, we will start with a basic coroutine that has no tracing:
        val scope = CoroutineScope(job + EmptyCoroutineContext)
        scope.launch { delay(1) }

        /*
        Expected trace output (image alt text):
        Trace showing a coroutine launched on the default dispatcher. The thread runs,
        then stops, and then runs again

        There is not much useful information in the trace. We see the thread runs, then
        stops running due to the `delay(1)` call, then runs again after the delay.
         */
    }

    /** 2: Untraced coroutine on traced Looper thread */
    private fun step2UntracedCoroutineOnTracedLooperThread(job: Job) {
        /*
        Next, we will switch from the default dispatcher to a single-threaded dispatcher
        backed by an Android `Looper`. We will also set a trace tag for the `Looper` so
        that the `Runnable` class names appear in the trace:
        Next, we'll launch a coroutine with a delay:
        */
        val scope = CoroutineScope(job + handlerDispatcher)
        scope.launch { delay(1) }

        /*
        Expected trace output (image alt text):
        Trace showing a coroutine launched on the "demo-main-thread" thread. The
        trace shows trace sections with the names `android.os.Handler:
        kotlinx.coroutines.internal.DispatchedContinuation` and `android.os.Handler:
        kotlinx.coroutines.android.HandlerContext$scheduleResumeAfterDelay$$inlined$Runnable$1`.
        This is better; we now trace sections for `android.os.Handler:
        kotlinx.coroutines.internal.DispatchedContinuation` and `android.os.Handler:
        kotlinx.coroutines.android.HandlerContext$scheduleResumeAfterDelay$$inlined$Runnable$1`,
        but this still does not give us much useful information.
        */
    }

    /** 3: Replacing `delay` with `forceSuspend` */
    private fun step3ReplaceDelayWithForceSuspend(job: Job) {
        /*
        Next, for clarity, we will replace `delay()` with our own implementation called
        `forceSuspend`.

        `forceSuspend` is similar to `delay` except that it is guaranteed to always
        suspend. It also has backed by a `Looper`, and it emits trace sections for
        demonstration purposes. We will also pass it a tag, "A", to make our call
        identifiable in the trace later.
        */
        val scope = CoroutineScope(job + handlerDispatcher)
        scope.launch { forceSuspend("A", 1) }

        /*
        Expected trace output (image alt text):
        Trace showing a coroutine launched on a handler dispatcher. The trace shows
        the
        kotlinx.coroutines.android.HandlerContext$scheduleResumeAfterDelay$$inlined$Runnable$1
        coroutine

        We see a trace section when `forceSuspend` schedules a `Runnable` on the
        `Handler`, and later there is a trace section when the `Runnable` resumes the
        continuation.
        */
    }

    /** 4: Coroutine with `TraceContextElement` installed */
    private fun step4CoroutineWithTraceContextElement(job: Job) {
        // Next, we'll install a `TraceContextElement` to the top-level coroutine:
        val scope = CoroutineScope(job + handlerDispatcher + createCoroutineTracingContext())
        scope.launch { forceSuspend("A", 1) }

        /*
        Expected trace output (image alt text):
        Trace showing a coroutine launched on a handler dispatcher with a
        `TraceContextElement` installed

        A new trace section named `coroutine execution` appears. Underneath it, an
        additional slice contains metadata, which in this case looks like:
        `;d=1;c=988384889;p=1577051477`

        The string before the first semicolon (`;`) is the name of the resumed
        coroutine. In the above example, no name was given to `launch`, and the tracing
        context was not created with options to automatically infer a name, so the name
        is blank.

        The other fields are as follows:

        *   `d=` is the depth, or how many parent coroutines there are until we reach
            the top-level coroutine.
        *   `c=` is the ID of the current coroutine.
        *   `p=` is the ID of the parent coroutine.

        Thus, in the above example slice, if we want to find slices belonging to the
        parent coroutine, we would search the trace for `;c=1577051477;`.

        Note: The parent coroutine will only be included in the Perfetto trace if it
        happens to run sometime during when the trace was captured. Parent coroutines
        may run in parallel to their children, so it is not necessarily the case that
        the child has to be created *after* tracing has started to know the parent name
        (although that helps).

        In the above trace, we also see `delay(1) ["A"]` is now traced as well. That's
        because `forceSuspend()` calls `traceCoroutine("forceSuspend") {}`.

        `traceCoroutine("[trace-name]") { }` can be used for tracing sections of
        suspending code. The trace name will start and end as the coroutine suspends and
        resumes.
         */
    }

    /** 5: Enable `walkStackForDefaultNames` */
    private fun step5EnableStackWalker(job: Job) {
        // Next, we'll enable `walkStackForDefaultNames`:
        val scope =
            CoroutineScope(
                job +
                    handlerDispatcher +
                    createCoroutineTracingContext(walkStackForDefaultNames = true)
            )
        scope.launch { forceSuspend("A", 1) }

        /*
        Expected trace output (image alt text):
        Trace showing a coroutine launched on a handler dispatcher with
        `walkStackForDefaultNames` enabled. The trace shows that `launch` has the name
        `BasicTracingTutorial.step5EnableStackWalker;d=1;c=1560424941;p=1105235868`

        Now, we can see our coroutine has a name:
        `BasicTracingTutorial.step5EnableStackWalker;d=1;c=1560424941;p=1105235868`.

        We can also see a slice named `walkStackForClassName` that occurs before the
        `launch`. This is where the coroutine trace context infers the name of the
        newly launched coroutine by inspecting the stack trace to see where it was
        created.

        One downside of using `walkStackForDefaultNames = true` is that it is expensive,
        sometimes taking longer than 1 millisecond to infer the name of a class,
        so it should be used sparingly. As we'll see further on, some parts of
        `kotlinx.coroutines` are written such that there is no way for us to insert a
        custom coroutine context, thus making `walkStackForClassName` unavoidable.
         */
    }

    /** 6: Replace `launch` with `launchTraced` */
    private fun step6UseLaunchedTraced(job: Job) {
        // Walking the stack is an expensive operation, so next we'll replace our call to
        // `launch` with `launchTraced`:
        val scope =
            CoroutineScope(
                job +
                    handlerDispatcher +
                    createCoroutineTracingContext(walkStackForDefaultNames = true)
            )
        scope.launchTraced { forceSuspend("A", 1) }
        /*
        Expected trace output (image alt text):
        Trace showing a coroutine launched on a handler dispatcher with `launchTraced` instead of
        `launch`

        Now we see the trace section is named:
        `BasicTracingTutorial$step6$1;d=1;c=1529321599;p=1334272881`. This is almost the
        same as the name in the previous step, except this name is derived using the
        classname of the supplied lambda, `block::class.simpleName`, which is much
        faster. We also see that `walkStackForClassName` was not called.
         */
    }

    /** 7: Call `launchTraced` with an explicit name */
    private fun step7ExplicitLaunchName(job: Job) {
        // Finally, we'll pass an explicit name to `launchTraced` instead of using the
        // inline name:
        val scope =
            CoroutineScope(
                job +
                    handlerDispatcher +
                    createCoroutineTracingContext(walkStackForDefaultNames = true)
            )
        scope.launchTraced("my-launch") { forceSuspend("A", 1) }

        /*
        Expected trace output (image alt text):
        Trace showing a coroutine launched on a handler dispatcher with an explicit name

        Now we see the trace name is: `my-launch;d=1;c=1148426666;p=1556983557`.
         */
    }

    /** 8: Enable `countContinuations` */
    private fun step8CountContinuations(job: Job) {
        // The config parameter `countContinuations` can be used to count how many times a
        // coroutine has run, in total, since its creation:
        val scope =
            CoroutineScope(
                job +
                    handlerDispatcher +
                    createCoroutineTracingContext(
                        walkStackForDefaultNames = true,
                        countContinuations = true,
                    )
            )
        scope.launchTraced("my-launch") {
            forceSuspend("A", 1)
            forceSuspend("B", 1)
            forceSuspend("C", 1)
        }
        /*
        Expected trace output (image alt text):
        Trace showing a coroutine resuming after a delay, in which the continuation
        counter has incremented to 3

        In the above trace, the coroutine suspends 3 times. The counter is `0` for the
        first resumption, and the last resumption is `3`.
         */
    }

    override suspend fun runExperiment() {
        runStep(1, ::step1UntracedCoroutineOnDefaultDispatcher)
        runStep(2, ::step2UntracedCoroutineOnTracedLooperThread)
        runStep(3, ::step3ReplaceDelayWithForceSuspend)
        runStep(4, ::step4CoroutineWithTraceContextElement)
        runStep(5, ::step5EnableStackWalker)
        runStep(6, ::step6UseLaunchedTraced)
        runStep(7, ::step7ExplicitLaunchName)
        runStep(8, ::step8CountContinuations)
    }
}
