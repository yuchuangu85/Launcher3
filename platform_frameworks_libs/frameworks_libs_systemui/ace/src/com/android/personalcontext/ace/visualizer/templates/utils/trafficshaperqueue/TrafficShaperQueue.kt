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

/** Interface for a queue that depletes actions */
interface TrafficShaperQueue {
    /**
     * Adds an action to the queue and returns immediately. Returns true if the action was
     * successfully enqueued, false otherwise.
     */
    fun enqueue(action: () -> Unit)

    /**
     * Adds an action to the queue with custom error handling and returns immediately. Returns true
     * if the action was successfully enqueued, false otherwise.
     */
    fun enqueue(action: () -> Unit, handleFailure: ((Exception) -> Unit))
}
