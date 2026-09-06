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

import dagger.Lazy
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.reflect.KProperty

/**
 * Utility class which makes it easier to inject objects that are available only after user is
 * unlocked
 */
class PostUnlockObject<T>
@Inject
constructor(private val item: Lazy<T>, private val lockedUserState: LockedUserState) :
    SafeCloseable {

    private val cleanupItems = ThreadSafeRunnableList()

    /**
     * Executes the [task] when user is unlocked. The returned [Runnable] is called when this
     * [PostUnlockObject] is cleaned up. Both the [task] and the cleanup run on the provided
     * [executor].
     */
    fun whenAvailable(executor: Executor, task: (T) -> Runnable) {
        // Runs the provided [task] and added the returned [Runnable] to [cleanupItems]
        val actualTask = Runnable { cleanupItems.addTask(executor, task.invoke(item.get())) }
        if (lockedUserState.isUserUnlocked) {
            executor.execute(actualTask)
        } else {
            lockedUserState.runOnUserUnlocked(executor, actualTask)
            // Since [lockedUserState] is thread safe, executor for cleanup doesn't matter
            cleanupItems.addTask(Runnable::run) {
                lockedUserState.removeOnUserUnlockedRunnable(actualTask)
            }
        }
    }

    fun get(): T = item.get()

    fun getIfReady(): T? = if (lockedUserState.isUserUnlocked) get() else null

    override fun close() = cleanupItems.complete()

    operator fun getValue(target: Any, property: KProperty<*>): T? = getIfReady()
}
