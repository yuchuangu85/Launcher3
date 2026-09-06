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

package com.android.launcher3.taskbar.bubbles

import android.app.ActivityTaskManager
import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW

/** Helper to determine bubbled task state from other components in Launcher. */
object BubbleHelper {
    // For app bubbles, if a task is in a bubble, it is under the bubble root task. In which case
    // the parent task id is the bubble root task id.
    // Bubble root task is managed by WMShell and it only knows what the root task id is.
    // The root task is created as part of WMShell init flow and is considered static during
    // WMShell lifetime.
    // To avoid querying WMShell each time we need to check if a task is in a bubble, cache the
    // bubble root task id in Launcher.
    // TODO (b/474654831): remove static bubble root task id
    private var bubbleRootTaskId = ActivityTaskManager.INVALID_TASK_ID

    /** Update cached bubble root task id. */
    @JvmStatic
    fun updateBubbleRootTaskId(taskId: Int) {
        bubbleRootTaskId = taskId
    }

    /** Returns true if the task is in a bubble */
    @JvmStatic
    fun isBubbleTask(task: TaskInfo?): Boolean =
        when {
            task == null -> false
            isAppBubbleTask(task) -> true
            task.windowingMode == WINDOWING_MODE_MULTI_WINDOW &&
                task.configuration.windowConfiguration.isAlwaysOnTop() -> true
            else -> false
        }

    /** Returns true if the task is an app bubble */
    @JvmStatic
    fun isAppBubbleTask(task: TaskInfo?): Boolean =
        when {
            task == null -> false
            task.isAppBubble -> true
            task.hasParentTask() && task.parentTaskId == bubbleRootTaskId -> true
            else -> false
        }
}
