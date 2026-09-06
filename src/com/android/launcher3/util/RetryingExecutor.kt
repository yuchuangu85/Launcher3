/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.util

import android.os.Handler
import androidx.annotation.VisibleForTesting
import androidx.core.os.postDelayed

/** Utility class to execute a task with retires */
class RetryingExecutor(private val handler: Handler = Handler()) {

    private val token = Any()

    private var task: ((Int) -> Boolean)? = null

    /** Cancels the previously scheduled task */
    fun cancel() {
        task = null
        handler.removeCallbacksAndMessages(token)
    }

    /** Schedules the provided [task] to be executed with retires */
    fun execute(task: (Int) -> Boolean) {
        cancel()
        handler.removeCallbacksAndMessages(token)
        this.task = task
        tryAddWithBackoff(tryCount = 0)
    }

    private fun tryAddWithBackoff(tryCount: Int) {
        val result = task?.invoke(tryCount) ?: return
        if (result) return

        val nextTryCount = tryCount + 1
        handler.postDelayed(
            delayInMillis = BACKOFF_DELAYS_MS.getOrElse(tryCount) { FINAL_BACKOFF_MS },
            token = token,
        ) {
            tryAddWithBackoff(nextTryCount)
        }
    }

    companion object {
        @VisibleForTesting val BACKOFF_DELAYS_MS = arrayOf(10, 100L, 200L, 500L, 1000L, 2000L)
        private const val FINAL_BACKOFF_MS = 5000L
    }
}
