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
package com.android.launcher3.util

import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.LauncherAppSingleton
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * A tracker class for keeping track of Dagger created singletons. Dagger will take care of creating
 * singletons. But we should take care of unregistering callbacks if at all registered during
 * singleton construction. All singletons should be declared as SafeCloseable so that we can call
 * close() method.
 */
@LauncherAppSingleton
class DaggerSingletonTracker
@Inject
internal constructor(@Ui private val mainExecutor: LooperExecutor) {

    private val tasks = ThreadSafeRunnableList()

    /** Adds a closable task to be performed when the dagger graph instance is destroyed */
    fun addCloseable(closeable: SafeCloseable) = addCloseable(mainExecutor, closeable)

    /** Adds a closable task to be performed when the dagger graph instance is destroyed */
    fun addCloseable(executor: Executor, closeable: SafeCloseable) =
        tasks.addCloseable(executor, closeable)

    fun close() = tasks.complete()
}
