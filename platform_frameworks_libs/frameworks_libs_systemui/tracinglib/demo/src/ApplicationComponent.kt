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
package com.example.tracing.demo

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.Trace
import androidx.core.app.AppComponentFactory
import com.example.tracing.demo.experiments.BasicTracingTutorial
import com.example.tracing.demo.experiments.CancellableSharedFlow
import com.example.tracing.demo.experiments.CollectFlow
import com.example.tracing.demo.experiments.CombineDeferred
import com.example.tracing.demo.experiments.CombineFlow
import com.example.tracing.demo.experiments.Experiment
import com.example.tracing.demo.experiments.FlowTracingTutorial
import com.example.tracing.demo.experiments.LaunchNested
import com.example.tracing.demo.experiments.LaunchSequentially
import com.example.tracing.demo.experiments.LaunchStressTest
import com.example.tracing.demo.experiments.LeakySharedFlow
import com.example.tracing.demo.experiments.SharedFlowUsage
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.newFixedThreadPoolContext

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread0

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread1

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread2

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread3

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread4

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedPool

// Initialize threads in the top-level to force their creation in a specific order:
internal val delayHandler = startThreadWithLooper("delay-thread")
private val thread0 = startThreadWithLooper("Thread:0")
private val thread1 = startThreadWithLooper("Thread:1")
private val thread2 = startThreadWithLooper("Thread:2")
private val thread3 = startThreadWithLooper("Thread:3")
private val thread4 = startThreadWithLooper("Thread:4")
@OptIn(DelicateCoroutinesApi::class)
private val fixedThreadPool = newFixedThreadPoolContext(4, "ThreadPool")

@Module
class ConcurrencyModule {
    @Provides
    @Singleton
    @FixedThread0
    fun provideDispatcher0(): CoroutineDispatcher = thread0.asCoroutineDispatcher()

    @Provides
    @Singleton
    @FixedThread1
    fun provideDispatcher1(): CoroutineDispatcher = thread1.asCoroutineDispatcher()

    @Provides
    @Singleton
    @FixedThread2
    fun provideDispatcher2(): CoroutineDispatcher = thread2.asCoroutineDispatcher()

    @Provides
    @Singleton
    @FixedThread3
    fun provideDispatcher3(): CoroutineDispatcher = thread3.asCoroutineDispatcher()

    @Provides
    @Singleton
    @FixedThread4
    fun provideDispatcher4(): CoroutineDispatcher = thread4.asCoroutineDispatcher()

    @Provides
    @Singleton
    @FixedPool
    fun provideFixedThreadPoolDispatcher(): CoroutineDispatcher = fixedThreadPool
}

@Module
class ExperimentModule {
    @Provides
    @Singleton
    fun provideExperimentList(
        basicTracingTutorial: BasicTracingTutorial,
        flowTracingTutorial: FlowTracingTutorial,
        launchSequentially: LaunchSequentially,
        launchNested: LaunchNested,
        launchStressTest: LaunchStressTest,
        combineDeferred: CombineDeferred,
        sharedFlowUsage: SharedFlowUsage,
        cancellableSharedFlow: CancellableSharedFlow,
        collectFlow: CollectFlow,
        combineFlow: CombineFlow,
        leakySharedFlow: LeakySharedFlow,
    ): List<Experiment> =
        listOf(
            basicTracingTutorial,
            flowTracingTutorial,
            launchSequentially,
            launchNested,
            launchStressTest,
            combineDeferred,
            sharedFlowUsage,
            cancellableSharedFlow,
            collectFlow,
            combineFlow,
            leakySharedFlow,
        )
}

@Singleton
@Component(modules = [ConcurrencyModule::class, ExperimentModule::class])
interface ApplicationComponent {
    /** Returns [Experiment]s that should be used with the application. */
    @Singleton fun getExperimentList(): List<Experiment>

    @Singleton @FixedThread0 fun getExperimentDefaultCoroutineDispatcher(): CoroutineDispatcher
}

class MainAppComponentFactory : AppComponentFactory() {

    init {
        Trace.registerWithPerfetto()
    }

    private val appComponent: ApplicationComponent = DaggerApplicationComponent.create()

    override fun instantiateActivityCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?,
    ): Activity {
        val activityClass = cl.loadClass(className)
        return if (activityClass == MainActivity::class.java) {
            MainActivity(appComponent)
        } else {
            super.instantiateActivityCompat(cl, className, intent)
        }
    }
}

private fun startThreadWithLooper(name: String): Handler {
    val thread = HandlerThread(name, Process.THREAD_PRIORITY_FOREGROUND)
    thread.start()
    val looper = thread.looper
    looper.setTraceTag(Trace.TRACE_TAG_APP)
    return Handler.createAsync(looper)
}
