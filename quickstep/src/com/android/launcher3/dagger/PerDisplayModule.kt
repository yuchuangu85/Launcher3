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

package com.android.launcher3.dagger

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Display
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.view.WindowManagerGlobal
import com.android.app.displaylib.DisplayInstanceLifecycleManager
import com.android.app.displaylib.DisplayLibBackground
import com.android.app.displaylib.DisplayLibComponent
import com.android.app.displaylib.DisplayLibMainThread
import com.android.app.displaylib.DisplayRepository
import com.android.app.displaylib.DisplaysWithDecorationsRepository
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat
import com.android.app.displaylib.PerDisplayInstanceProviderWithTeardown
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.displaylib.createDisplayLibComponent
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.concurrent.annotations.UiContext
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.util.LooperExecutor
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.window.RecentsWindowManager
import com.android.quickstep.window.RecentsWindowTracker
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

@Module(
    includes =
        [
            BasePerDisplayModule::class,
            PerDisplayRepositoriesModule::class,
            PerDisplayTaskbarRepositoriesModule::class,
        ]
)
interface PerDisplayModule

@Module(includes = [DisplayLibModule::class])
interface BasePerDisplayModule {
    @Binds
    @DisplayLibMainThread
    fun bindDisplayLibMainThread(@UiContext mainScope: CoroutineContext): CoroutineContext
}

@Module
object PerDisplayObjectsModule {
    @PerDisplaySingleton
    @Provides
    @DisplayId
    fun provideDisplayId(display: Display): Int = display.displayId

    @Provides
    @PerDisplaySingleton
    @WindowContext
    fun provideWindowContext(@ApplicationContext context: Context, display: Display) =
        context.createWindowContext(display, TYPE_APPLICATION_OVERLAY, /* options= */ null)
}

@Module
object PerDisplayRepositoriesModule {

    @Provides
    @LauncherAppSingleton
    fun providePerDisplayComponent(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<PerDisplayComponent>,
        displayRepository: DisplayRepository,
        instanceFactory: PerDisplayComponent.Factory,
        @DisplaysWithDecorations
        displaysWithDecorationsLifecycleManager: DisplayInstanceLifecycleManager,
    ): PerDisplayRepository<PerDisplayComponent> {
        val instanceProvider =
            object : PerDisplayInstanceProviderWithTeardown<PerDisplayComponent> {
                override fun destroyInstance(instance: PerDisplayComponent) =
                    instance.cleanupTasks.close()

                override fun createInstance(displayId: Int): PerDisplayComponent? =
                    displayRepository.getDisplay(displayId)?.let { instanceFactory.build(it) }
            }
        return repositoryFactory.create(
            "PerDisplayComponentRepo",
            instanceProvider,
            displaysWithDecorationsLifecycleManager,
        )
    }

    @Provides
    @LauncherAppSingleton
    fun provideRecentsAnimationDeviceStateRepo(
        repositoryFactory: PerDisplayComponentRepository.Factory<RecentsAnimationDeviceState>
    ): PerDisplayRepository<RecentsAnimationDeviceState> =
        repositoryFactory.create("TaskAnimationManagerRepo") { it.recentsAnimationDeviceState }

    @Provides
    @LauncherAppSingleton
    fun provideTaskAnimationManagerRepo(
        repositoryFactory: PerDisplayComponentRepository.Factory<TaskAnimationManager>
    ): PerDisplayRepository<TaskAnimationManager> =
        repositoryFactory.create("TaskAnimationManagerRepo") { it.taskAnimationManager }

    @Provides
    @LauncherAppSingleton
    fun provideRotationTouchHandlerRepo(
        repositoryFactory: PerDisplayComponentRepository.Factory<RotationTouchHelper>
    ): PerDisplayRepository<RotationTouchHelper> =
        repositoryFactory.create("RotationTouchHelperRepo") { it.rotationTouchHelper }

    @Provides
    @LauncherAppSingleton
    fun provideRecentsWindowManagerRepo(
        repositoryFactory: PerDisplayComponentRepository.Factory<RecentsWindowManager>
    ): PerDisplayRepository<RecentsWindowManager> =
        repositoryFactory.createOptional(
            "RecentsWindowManagerRepo",
            objectGetter = { it.recentsWindowManager },
            optionalObjectGetter = { it.fallbackWindowInterface.createdContainer },
        )

    @Provides
    @LauncherAppSingleton
    fun provideRecentsWindowTrackerRepo(
        repositoryFactory: PerDisplayComponentRepository.Factory<RecentsWindowTracker>
    ): PerDisplayRepository<RecentsWindowTracker> =
        repositoryFactory.create("RecentsWindowTrackerRepo") { it.recentsWindowTracker }

    @Provides
    @LauncherAppSingleton
    @DisplaysWithDecorations
    fun provideDisplaysWithDecorationsLifecycleManager(
        displaysWithDecorationsRepository: DisplaysWithDecorationsRepository
    ) =
        object : DisplayInstanceLifecycleManager {
            override val displayIds: StateFlow<Set<Int>>
                get() = displaysWithDecorationsRepository.displayIdsWithSystemDecorations
        }
}

/**
 * Keep the taskbar features separate from PerDisplayRepositoriesModule, as it is override in
 * TaskbarBootComponent
 */
@Module
object PerDisplayTaskbarRepositoriesModule {
    @Provides
    @LauncherAppSingleton
    fun provideTaskbarFeatureEvaluatorRepo(
        repositoryFactory: PerDisplayComponentRepository.Factory<TaskbarFeatureEvaluator>
    ): PerDisplayRepository<TaskbarFeatureEvaluator> =
        repositoryFactory.create(
            "TaskbarFeatureEvaluator",
            PerDisplayComponent::getTaskbarFeatureEvaluator,
        )
}

/**
 * This is essentially an inverse of PerDisplayRepositoriesModule. It provides binding within a
 * scope with [DisplayId] already defined
 */
@Module
object PerDisplayScopedProviderModule {

    @Provides
    fun providePerDisplayComponent(
        @DisplayId displayId: Int,
        repo: PerDisplayRepository<PerDisplayComponent>,
    ): PerDisplayComponent = requireNotNull(repo[displayId])

    @Provides
    fun provideRecentsAnimationDeviceState(
        component: PerDisplayComponent
    ): RecentsAnimationDeviceState = component.recentsAnimationDeviceState

    @Provides
    fun provideTaskAnimationManager(component: PerDisplayComponent): TaskAnimationManager =
        component.taskAnimationManager

    @Provides
    fun provideRotationTouchHelper(component: PerDisplayComponent): RotationTouchHelper =
        component.rotationTouchHelper

    @Provides
    fun provideRecentsWindowManager(component: PerDisplayComponent): RecentsWindowManager =
        component.recentsWindowManager

    @Provides
    fun provideRecentsWindowTracker(component: PerDisplayComponent): RecentsWindowTracker =
        component.recentsWindowTracker

    @Provides
    fun provideTaskbarFeatureEvaluator(
        @DisplayId displayId: Int,
        repo: PerDisplayRepository<TaskbarFeatureEvaluator>,
    ): TaskbarFeatureEvaluator = requireNotNull(repo[displayId])
}

/**
 * Module to bind the DisplayRepository from displaylib to the LauncherAppSingleton dagger graph.
 */
@Module
object DisplayLibModule {
    @Provides
    @LauncherAppSingleton
    @DisplayLibBackground
    fun provideBgCoroutineScope(@BackgroundContext backgroundContext: CoroutineContext) =
        CoroutineScope(SupervisorJob() + backgroundContext + CoroutineName("LauncherBg"))

    @Provides
    @LauncherAppSingleton
    fun displayLibComponent(
        @ApplicationContext context: Context,
        @Background looperExecutor: LooperExecutor,
        @DisplayLibBackground backgroundScope: CoroutineScope,
        @Background backgroundDispatcher: CoroutineDispatcher,
    ): DisplayLibComponent {
        val displayManager = context.getSystemService(DisplayManager::class.java)!!
        val windowManager = WindowManagerGlobal.getWindowManagerService()!!
        return createDisplayLibComponent(
            context,
            displayManager,
            windowManager,
            Handler(looperExecutor.looper),
            backgroundScope,
            backgroundDispatcher,
        )
    }

    @Provides
    @LauncherAppSingleton
    fun providesDisplayRepositoryFromLib(
        displayLibComponent: DisplayLibComponent
    ): DisplayRepository {
        return displayLibComponent.displayRepository
    }

    @Provides
    @LauncherAppSingleton
    fun providesDisplaysWithDecorationsRepository(
        displayLibComponent: DisplayLibComponent
    ): DisplaysWithDecorationsRepository {
        return displayLibComponent.displaysWithDecorationsRepository
    }

    @Provides
    @LauncherAppSingleton
    fun providesDisplaysWithDecorationsRepositoryCompat(
        displayLibComponent: DisplayLibComponent
    ): DisplaysWithDecorationsRepositoryCompat {
        return displayLibComponent.displaysWithDecorationsRepositoryCompat
    }

    @Provides
    fun dumpRegistrationLambda(): PerDisplayRepository.InitCallback =
        PerDisplayRepository.InitCallback { debugName, _ ->
            Log.d("PerDisplayInitCallback", debugName)
        }
}
