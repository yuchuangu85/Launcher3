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

package com.android.quickstep.recents.data

import com.android.quickstep.util.GroupTask

class FakeRecentTasksKeysDataSource : RecentTasksKeysDataSource {
    private var tasks = emptyList<GroupTask>()
    var taskKeysCalls = 0
        private set

    fun setGroupTasks(tasks: List<GroupTask>) {
        this.tasks = tasks
    }

    override fun getTaskKeys(numTasks: Int): List<GroupTask> =
        tasks.take(numTasks).also { taskKeysCalls++ }

    fun resetGetTaskKeysCalls() {
        taskKeysCalls = 0
    }
}
