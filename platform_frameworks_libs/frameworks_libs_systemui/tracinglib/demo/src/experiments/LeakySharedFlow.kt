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
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.flow.flowName
import com.example.tracing.demo.FixedThread1
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.job

@Singleton
class LeakySharedFlow
@Inject
constructor(@param:FixedThread1 private var handlerDispatcher: CoroutineDispatcher) :
    TracedExperiment() {

    override val description: String = "Create a shared flow that cannot be cancelled by the caller"

    private val counter = flow {
        var n = 0
        while (true) {
            emit(n++)
            forceSuspend(timeMillis = 5)
        }
    }

    override suspend fun runExperiment() {
        val cookie = Random.nextInt()
        Trace.asyncTraceForTrackBegin(Trace.TRACE_TAG_APP, TRACK_NAME, "leaky-flow", cookie)
        // BAD - does not follow structured concurrency. This creates a new job each time it is
        // called. There is no way to cancel the shared flow because the parent does not know about
        // it
        val leakedScope =
            CoroutineScope(
                handlerDispatcher +
                    createCoroutineTracingContext(
                        "leaky-flow-scope",
                        walkStackForDefaultNames = true,
                    )
            )
        counter
            .flowName("leakySharedFlow")
            .shareIn(leakedScope, SharingStarted.Eagerly, replay = 10)

        leakedScope.coroutineContext.job.invokeOnCompletion {
            Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_APP, TRACK_NAME, cookie)
        }
    }
}
