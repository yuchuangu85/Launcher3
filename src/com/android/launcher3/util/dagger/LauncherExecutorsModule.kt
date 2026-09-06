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
package com.android.launcher3.util.dagger

import com.android.launcher3.concurrent.ExecutorsModule
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.TaskbarUi
import com.android.launcher3.concurrent.annotations.ThreadPool
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.launcher3.util.coroutines.ProductionDispatchers
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import dagger.Binds
import dagger.Module
import dagger.Provides
import java.util.concurrent.Executor
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel

/** Module that provides the executors for Launcher3 as per the [ExecutorsModule] interface. */
@Module
abstract class LauncherExecutorsModule {

    // Include UI LooperExecutor bindings here since they're not provided
    // by the ExecutorsModule interface.
    @Binds
    @LauncherAppSingleton
    @Ui
    abstract fun provideUiExecutor(@Ui executor: LooperExecutor): ListeningExecutorService

    @Binds
    @LauncherAppSingleton
    @TaskbarUi
    abstract fun provideTaskbarUiExecutor(
        @TaskbarUi executor: LooperExecutor
    ): ListeningExecutorService

    @Binds
    @LauncherAppSingleton
    @LightweightBackground(LightweightBackgroundPriority.UI)
    abstract fun provideUiExecutorService(
        @LightweightBackground(LightweightBackgroundPriority.UI) executor: LooperExecutor
    ): ListeningExecutorService

    @Binds
    @LauncherAppSingleton
    @Background
    abstract fun provideBackgroundExecutorService(
        @Background executor: LooperExecutor
    ): ListeningExecutorService

    @Binds
    abstract fun provideDispatcherProviders(
        dispatcherProvider: ProductionDispatchers
    ): DispatcherProvider

    companion object {
        @Provides
        @LauncherAppSingleton
        @Ui
        fun provideUiLooperExecutor(): LooperExecutor = Executors.MAIN_EXECUTOR

        @Provides
        @LauncherAppSingleton
        @TaskbarUi
        fun provideTaskbarUiLooperExecutor(): LooperExecutor = getTaskbarUiThread()

        @Provides
        @LauncherAppSingleton
        @LightweightBackground(LightweightBackgroundPriority.UI)
        fun provideUiHelperLooperExecutor(): LooperExecutor = Executors.UI_HELPER_EXECUTOR

        @Provides
        @LauncherAppSingleton
        @LightweightBackground(LightweightBackgroundPriority.DATA)
        fun provideDataExecutorService(): ListeningExecutorService = Executors.DATA_HELPER_EXECUTOR

        @Provides
        @LauncherAppSingleton
        @Background
        fun provideBackgroundLooperExecutor(): LooperExecutor = Executors.ORDERED_BG_EXECUTOR

        @Provides
        @LauncherAppSingleton
        @ThreadPool
        fun provideThreadPoolExecutorService(): ListeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.THREAD_POOL_EXECUTOR)

        // The following methods provide the CoroutineContext for the executors.
        @Provides
        @Ui
        fun provideUiDispatcher(@Ui executor: Executor, lifecycle: DaggerSingletonTracker) =
            executor.asDispatcherWithLifecycle(lifecycle)

        @Provides
        @TaskbarUi
        fun provideTaskbarUiDispatcher(
            @TaskbarUi executor: Executor,
            lifecycle: DaggerSingletonTracker,
        ) = executor.asDispatcherWithLifecycle(lifecycle)

        @Provides
        @LightweightBackground(LightweightBackgroundPriority.DATA)
        fun provideDataLightweightDispatcher(
            @LightweightBackground(LightweightBackgroundPriority.DATA) executor: Executor,
            lifecycle: DaggerSingletonTracker,
        ) = executor.asDispatcherWithLifecycle(lifecycle)

        @Provides
        @LightweightBackground(LightweightBackgroundPriority.UI)
        fun provideUiLightweightDispatcher(
            @LightweightBackground(LightweightBackgroundPriority.UI) executor: Executor,
            lifecycle: DaggerSingletonTracker,
        ) = executor.asDispatcherWithLifecycle(lifecycle)

        @Provides
        @Background
        fun provideBackgroundDispatcher(
            @Background executor: Executor,
            lifecycle: DaggerSingletonTracker,
        ) = executor.asDispatcherWithLifecycle(lifecycle)

        @Provides
        @ThreadPool
        fun provideThreadPoolDispatcher(
            @ThreadPool executor: Executor,
            lifecycle: DaggerSingletonTracker,
        ) = executor.asDispatcherWithLifecycle(lifecycle)

        private fun Executor.asDispatcherWithLifecycle(lifecycle: DaggerSingletonTracker) =
            asCoroutineDispatcher().also { lifecycle.addCloseable(this) { it.cancel() } }
    }
}
