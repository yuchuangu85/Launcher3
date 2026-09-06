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

package com.android.launcher3.util

import androidx.annotation.AnyThread
import java.util.concurrent.Executor

/** Alternate implementation of [RunnableList] with support for multiple thread. */
class ThreadSafeRunnableList {

    private val lock = Any()
    private var tasks: ArrayList<Pair<Executor, Runnable>>? = ArrayList()

    /**
     * Adds a task to the queue. If already [complete], the task is immediately executed on the
     * provided [executor].
     */
    @AnyThread
    fun addTask(executor: Executor, task: Runnable) =
        synchronized(lock) { tasks?.add(executor to task) ?: executor.execute(task) }

    @AnyThread
    fun addCloseable(executor: Executor, closeable: SafeCloseable?) =
        closeable?.let { addTask(executor, closeable::close) }

    /** Tries to remove a previously added task if not already executed */
    @AnyThread
    fun removeTask(task: Runnable) = synchronized(lock) { tasks?.removeIf { it.second == task } }

    /**
     * Completes the queue, executing all previously added tasks, and causing any future addition to
     * immediately get executed
     */
    fun complete() {
        val oldTasks: ArrayList<Pair<Executor, Runnable>>?
        synchronized(lock) {
            oldTasks = tasks
            tasks = null
        }
        oldTasks?.forEach { it.first.execute(it.second) }
    }
}
