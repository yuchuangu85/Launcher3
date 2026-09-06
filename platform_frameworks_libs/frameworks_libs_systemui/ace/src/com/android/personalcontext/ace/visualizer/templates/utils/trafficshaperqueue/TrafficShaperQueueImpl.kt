/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.visualizer.templates.utils.trafficshaperqueue

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Listens to a queue and depletes the actions in the queue at a fixed interval. */
class TrafficShaperQueueImpl
constructor(private val scope: CoroutineScope, private val interval: Duration) :
    TrafficShaperQueue {
    private val queue = Channel<() -> Unit>(Channel.UNLIMITED)
    private val depletionJob: Job = startDepletion()

    /**
     * Call this function to start depleting the queue. This should only be called once on
     * initialization.
     */
    private fun startDepletion(): Job =
        scope.launch {
            try {
                // This loop suspends passively when the queue is empty
                for (action in queue) {
                    // Execute the queued action
                    action.invoke()

                    // Force a delay before the loop can grab the next item
                    // This smooths out the burst into a steady, metered flow
                    delay(interval)
                }
            } catch (e: CancellationException) {
                // Scope has been cancelled, close the queue
                queue.close()
                throw e
            }
        }

    /**
     * Enqueues an action to be executed later.
     *
     * The action will be executed by the depletion job. Exceptions thrown by the action will be
     * handled by the [CoroutineScope] provided in the constructor.
     *
     * @param action The action to enqueue.
     * @return True if the action was successfully enqueued, false otherwise.
     */
    override fun enqueue(action: () -> Unit) {
        val unused = queue.trySend(action)
    }

    /**
     * Enqueues an action to be executed later with a custom failure handler.
     *
     * The action will be executed by the depletion job. Exceptions thrown by the action will be
     * caught and passed to the provided [handleFailure] function.
     *
     * @param action The action to enqueue.
     * @param handleFailure A function to handle any exceptions thrown by the action.
     * @return True if the action was successfully enqueued, false otherwise.
     */
    override fun enqueue(action: () -> Unit, handleFailure: ((Exception) -> Unit)) {
        val unused =
            queue.trySend({
                try {
                    action.invoke()
                } catch (e: Exception) {
                    handleFailure(e)
                }
            })
    }
}
