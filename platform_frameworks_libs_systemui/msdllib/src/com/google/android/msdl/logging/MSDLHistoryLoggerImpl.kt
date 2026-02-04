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

package com.google.android.msdl.logging

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PACKAGE_PRIVATE

@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
class MSDLHistoryLoggerImpl(private val maxHistorySize: Int) : MSDLHistoryLogger {

    // Use an Array with a fixed size as the history structure. This will work as a ring buffer
    private val history: Array<MSDLEvent?> = arrayOfNulls(size = maxHistorySize)
    // The head will point to the next available position in the structure to add a new event
    private var head = 0

    override fun addEvent(event: MSDLEvent) {
        history[head] = event
        // Move the head pointer, wrapping if necessary
        head = (head + 1) % maxHistorySize
    }

    override fun getHistory(): List<MSDLEvent> {
        val result = mutableListOf<MSDLEvent>()
        repeat(times = maxHistorySize) { i ->
            history[(i + head) % maxHistorySize]?.let { result.add(it) }
        }
        return result
    }
}
