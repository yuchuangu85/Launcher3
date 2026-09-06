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
import android.companion.datatransfer.continuity.TaskContinuityManager
import android.util.Log
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllers
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.util.SafeCloseable
import java.io.PrintWriter

/**
 * Controller for the Handoff feature in the Taskbar.
 *
 * This controller is responsible for managing the Handoff suggestions and loading the metadata
 * (label and icon) for them. It also updates the suggestions in the UI when the metadata is loaded.
 */
class TaskbarHandoffController(val taskbarActivityContext: TaskbarActivityContext) :
    LoggableTaskbarController {

    private val handoffSuggestionRepository =
        taskbarActivityContext.appComponent.handoffSuggestionRepository
    private lateinit var taskbarControllers: TaskbarControllers
    private var handoffSuggestionLauncher: HandoffSuggestionLauncher? = null
    private var handoffRepositoryListener: SafeCloseable? = null

    /** A list of currently active Handoff suggestions. */
    val suggestions: List<HandoffSuggestion>
        get() {
            val suggestion = handoffSuggestionRepository.suggestion.value
            return if (suggestion != null) {
                listOf(suggestion)
            } else {
                emptyList()
            }
        }

    /** Starts the controller. */
    fun init(taskbarControllers: TaskbarControllers) {
        this.taskbarControllers = taskbarControllers
        if (taskContinuity()) {
            taskbarActivityContext.applicationContext
                .getSystemService(TaskContinuityManager::class.java)
                ?.let {
                    handoffSuggestionLauncher =
                        HandoffSuggestionLauncher(it, taskbarActivityContext.mainExecutor)
                }

            handoffRepositoryListener =
                handoffSuggestionRepository.suggestion.forEach(
                    taskbarActivityContext.mainExecutor,
                    { _ -> taskbarControllers.taskbarViewController.commitHandoffSuggestionsToUI() },
                )
        }
    }

    /** Stops the controller. */
    fun onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "Stopping controller.")
        }
        handoffRepositoryListener?.close()
    }

    fun launch(suggestion: HandoffSuggestion) {
        handoffSuggestionLauncher?.launch(suggestion)
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "TaskbarHandoffController:")
        pw.println(prefix + "\tsuggestions=" + suggestions)
    }

    private companion object {
        const val DEBUG = false
        const val TAG = "TaskbarHandoffController"
    }
}
