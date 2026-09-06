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

package com.android.launcher3.remoteanimations

import android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_NONE
import com.android.wm.shell.startingsurface.IStartingWindowListener

class StartingWindowListener : IStartingWindowListener.Stub() {

    var backgroundColor = 0
        private set

    /** FIFO map of task id to starting window information */
    private val taskStartParams =
        object : LinkedHashMap<Int, TaskLaunchInfo>() {

            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TaskLaunchInfo>?) =
                size > MAX_NUM_TASKS
        }

    override fun onTaskLaunching(taskId: Int, supportedType: Int, color: Int) {
        taskStartParams[taskId] =
            TaskLaunchInfo(windowType = supportedType, backgroundColor = color)
        backgroundColor = color
    }

    fun consumeTaskLaunchInfo(taskId: Int): TaskLaunchInfo =
        taskStartParams.remove(taskId) ?: defaultInfo

    data class TaskLaunchInfo(@JvmField val windowType: Int, @JvmField val backgroundColor: Int)

    companion object {
        private const val MAX_NUM_TASKS: Int = 5

        private val defaultInfo =
            TaskLaunchInfo(windowType = STARTING_WINDOW_TYPE_NONE, backgroundColor = 0)
    }
}
