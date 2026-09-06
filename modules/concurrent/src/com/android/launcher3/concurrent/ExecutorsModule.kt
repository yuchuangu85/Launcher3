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

package com.android.launcher3.concurrent

import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundContext
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.TaskbarUi
import com.android.launcher3.concurrent.annotations.ThreadPool
import com.android.launcher3.concurrent.annotations.ThreadPoolContext
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.concurrent.annotations.UiContext
import com.google.common.util.concurrent.ListeningExecutorService
import dagger.Binds
import dagger.Module
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Module that stipulates the executors that are usable by common launcher modules.
 *
 * This module does not provide the executors directly, but rather provides the qualifiers that are
 * used to inject the executors and their derivatives when modules themselves need to utilize them.
 *
 * The executors are provided as follows:
 * - @ThreadPool: Provides an unordered background executor or derivative backed by a thread pool.
 * - @Background: Provides an ordered background executor or derivative.
 * - @LightweightBackground: Provides a lightweight background executor or derivative that is either
 *   time sensitive or not depending on the [LightweightBackgroundPriority] flag.
 * - @Ui: Provides a UI executor or derivative.
 *
 * The executors are provided as [Executor], [ExecutorService] or [ListeningExecutorService].
 *
 * Implementations for the executors should be provided as [ListeningExecutorService] by the host.
 */
@Module
interface ExecutorsModule {

    // Unordered background executors
    @Binds
    @ThreadPool
    fun provideThreadPoolExecutor(
        @ThreadPool listeningExecutorService: ListeningExecutorService
    ): Executor

    @Binds
    @ThreadPool
    fun provideThreadPoolExecutorService(
        @ThreadPool listeningExecutorService: ListeningExecutorService
    ): ExecutorService

    // end unordered background executors

    // Ordered background executors
    @Binds
    @Background
    fun provideBackgroundExecutor(
        @Background listeningExecutorService: ListeningExecutorService
    ): Executor

    @Binds
    @Background
    fun provideBackgroundExecutorService(
        @Background listeningExecutorService: ListeningExecutorService
    ): ExecutorService

    @Binds
    @LightweightBackground(LightweightBackgroundPriority.UI)
    fun provideUiLightweightBackgroundExecutor(
        @LightweightBackground(LightweightBackgroundPriority.UI)
        listeningExecutorService: ListeningExecutorService
    ): Executor

    @Binds
    @LightweightBackground(LightweightBackgroundPriority.UI)
    fun provideUiLightweightBackgroundExecutorService(
        @LightweightBackground(LightweightBackgroundPriority.UI)
        listeningExecutorService: ListeningExecutorService
    ): ExecutorService

    @Binds
    @LightweightBackground(LightweightBackgroundPriority.DATA)
    fun provideDataLightweightBackgroundExecutor(
        @LightweightBackground(LightweightBackgroundPriority.DATA)
        listeningExecutorService: ListeningExecutorService
    ): Executor

    @Binds
    @LightweightBackground(LightweightBackgroundPriority.DATA)
    fun provideDataLightweightBackgroundExecutorService(
        @LightweightBackground(LightweightBackgroundPriority.DATA)
        listeningExecutorService: ListeningExecutorService
    ): ExecutorService

    // end ordered background executors

    // UI executors
    @Binds
    @Ui
    fun provideUiExecutor(@Ui listeningExecutorService: ListeningExecutorService): Executor

    @Binds
    @TaskbarUi
    fun provideTaskbarUiExecutor(
        @TaskbarUi listeningExecutorService: ListeningExecutorService
    ): Executor

    @Binds
    @Ui
    fun provideUiExecutorService(
        @Ui listeningExecutorService: ListeningExecutorService
    ): ExecutorService

    // end UI executors

    @Binds @UiContext fun provideUiContext(@Ui uiDispatcher: CoroutineDispatcher): CoroutineContext

    @Binds
    @LightweightBackgroundContext(LightweightBackgroundPriority.DATA)
    fun provideDataLightweightContext(
        @LightweightBackground(LightweightBackgroundPriority.DATA)
        dataLightweightDispatcher: CoroutineDispatcher
    ): CoroutineContext

    @Binds
    @LightweightBackgroundContext(LightweightBackgroundPriority.UI)
    fun provideUiLightweightContext(
        @LightweightBackground(LightweightBackgroundPriority.UI)
        uiLightweightDispatcher: CoroutineDispatcher
    ): CoroutineContext

    @Binds
    @BackgroundContext
    fun provideBackgroundContext(
        @Background backgroundDispatcher: CoroutineDispatcher
    ): CoroutineContext

    @Binds
    @ThreadPoolContext
    fun provideThreadPoolContext(
        @ThreadPool threadPoolDispatcher: CoroutineDispatcher
    ): CoroutineContext
}
