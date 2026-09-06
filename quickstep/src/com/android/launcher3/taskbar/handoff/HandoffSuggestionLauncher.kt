/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.taskbar.handoff

import android.companion.Flags.taskContinuity
import android.companion.datatransfer.continuity.RemoteTask
import android.companion.datatransfer.continuity.TaskContinuityManager
import android.util.Log
import java.util.concurrent.Executor

/** Launches a [HandoffSuggestion] by starting the corresponding [RemoteTask]. */
class HandoffSuggestionLauncher(
    private val taskContinuityManager: TaskContinuityManager,
    private val executor: Executor,
) : TaskContinuityManager.HandoffRequestCallback {

    fun launch(suggestion: HandoffSuggestion) {
        if (taskContinuity()) {
            if (DEBUG) {
                Log.d(TAG, "Launching suggestion.")
            }
            taskContinuityManager.requestHandoff(
                suggestion.associationId,
                suggestion.taskId,
                executor,
                this,
            )
        } else if (DEBUG) {
            Log.w(TAG, "Handoff feature flag is disabled - not launching suggestion.")
        }
    }

    override fun onHandoffRequestFinished(associationId: Int, taskId: Int, resultCode: Int) {
        if (DEBUG) {
            Log.d(TAG, "onHandoffRequestFinished: resultCode=$resultCode")
        }
    }

    private companion object {
        const val TAG = "HandoffSuggestionLauncher"
        const val DEBUG = false
    }
}
