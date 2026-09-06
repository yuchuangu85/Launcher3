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

package com.android.launcher3.dagger

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.LauncherApps
import android.hardware.input.InputManager
import android.view.IWindowManager
import android.view.WindowManagerGlobal
import androidx.core.content.getSystemService
import com.android.launcher3.DeviceProfile
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.RecentTasksList
import com.android.quickstep.RecentsModel
import com.android.quickstep.recents.data.AppTimersRepository
import com.android.quickstep.recents.data.AppTimersRepositoryImpl
import com.android.quickstep.recents.data.HighResLoadingStateNotifier
import com.android.quickstep.recents.data.InputDeviceDataSource
import com.android.quickstep.recents.data.InputManagerWrapper
import com.android.quickstep.recents.data.PointerRepository
import com.android.quickstep.recents.data.PointerRepositoryImpl
import com.android.quickstep.recents.data.RecentTasksDataSource
import com.android.quickstep.recents.data.RecentTasksKeysDataSource
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.data.RecentsDeviceProfileRepository
import com.android.quickstep.recents.data.RecentsDeviceProfileRepositoryImpl
import com.android.quickstep.recents.data.RecentsRotationStateRepository
import com.android.quickstep.recents.data.RecentsRotationStateRepositoryImpl
import com.android.quickstep.recents.data.TaskVisualsChangeNotifier
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate
import com.android.quickstep.recents.data.TaskVisualsChangedDelegateImpl
import com.android.quickstep.recents.data.TasksRepository
import com.android.quickstep.recents.data.UserLockedRepository
import com.android.quickstep.recents.data.UserLockedStateRepository
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.views.RecentsViewContainer
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Module that provides dependencies required for an instance of Recents. */
@Module
interface LauncherRecentsModule {
    @ActivityContextSingleton
    @Binds
    fun bindRecentTasksRepository(impl: TasksRepository): RecentTasksRepository

    @ActivityContextSingleton
    @Binds
    fun bindRecentTasksDataSource(impl: RecentsModel): RecentTasksDataSource

    @Binds
    fun bindTaskVisualsChangedDelegate(
        impl: TaskVisualsChangedDelegateImpl
    ): TaskVisualsChangedDelegate

    @Binds fun bindTaskVisualsChangeNotifier(impl: RecentsModel): TaskVisualsChangeNotifier

    @ActivityContextSingleton
    @Binds
    fun bindUserLockedStateRepository(impl: UserLockedRepository): UserLockedStateRepository

    @ActivityContextSingleton
    @Binds
    fun bindInputDeviceDataSource(impl: InputManagerWrapper): InputDeviceDataSource

    @ActivityContextSingleton
    @Binds
    fun bindAppTimersRepository(impl: AppTimersRepositoryImpl): AppTimersRepository

    @ActivityContextSingleton
    @Binds
    fun bindPointerRepository(impl: PointerRepositoryImpl): PointerRepository

    @ActivityContextSingleton
    @Binds
    fun bindRecentsDeviceProfileRepository(
        impl: RecentsDeviceProfileRepositoryImpl
    ): RecentsDeviceProfileRepository

    @Binds fun bindRecentTasksKeysDataSource(impl: RecentTasksList): RecentTasksKeysDataSource

    companion object {
        @Provides
        fun provideRecentsViewContainer(context: ActivityContext): RecentsViewContainer =
            context as RecentsViewContainer

        @ActivityContextSingleton
        @Provides
        fun provideTaskIconDataSource(model: RecentsModel): TaskIconDataSource = model.iconCache

        @ActivityContextSingleton
        @Provides
        fun provideRecentsCoroutineScope(
            @DisplayId displayId: Int,
            @LightweightBackground(UI) lightweightBackgroundDispatcher: CoroutineDispatcher,
        ): CoroutineScope =
            CoroutineScope(
                SupervisorJob() +
                    lightweightBackgroundDispatcher +
                    CoroutineName("RecentsView-Display$displayId")
            )

        @ActivityContextSingleton
        @Provides
        fun provideTaskThumbnailDataSource(model: RecentsModel): TaskThumbnailDataSource =
            model.thumbnailCache

        @ActivityContextSingleton
        @Provides
        fun provideHighResLoadingStateNotifier(model: RecentsModel): HighResLoadingStateNotifier =
            model.thumbnailCache.highResLoadingState

        @ActivityContextSingleton
        @Provides
        fun provideKeyguardManager(@ApplicationContext context: Context): KeyguardManager =
            context.getSystemService<KeyguardManager>()!!

        @ActivityContextSingleton
        @Provides
        fun provideInputManager(@ApplicationContext context: Context): InputManager =
            context.getSystemService<InputManager>()!!

        @ActivityContextSingleton
        @Provides
        fun provideLauncherApps(@ApplicationContext context: Context): LauncherApps =
            context.getSystemService<LauncherApps>()!!

        @ActivityContextSingleton
        @Provides
        fun provideWindowManager(): IWindowManager = WindowManagerGlobal.getWindowManagerService()!!

        @ActivityContextSingleton
        @Provides
        fun provideRecentsOrientedState(
            @ApplicationContext context: Context,
            recentsViewContainer: RecentsViewContainer,
        ): RecentsOrientedState =
            RecentsOrientedState(context, recentsViewContainer.getContainerInterface())

        @ActivityContextSingleton
        @Provides
        fun provideRecentsRotationStateRepository(
            recentsOrientedState: RecentsOrientedState
        ): RecentsRotationStateRepository = RecentsRotationStateRepositoryImpl(recentsOrientedState)

        @ActivityContextSingleton
        @Provides
        fun provideDeviceProfileGetter(
            recentsViewContainer: RecentsViewContainer
        ): DeviceProfile.Getter = DeviceProfile.Getter { recentsViewContainer.deviceProfile }
    }
}
