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
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.automation.AutomationNoOpRepository
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.display.DisplayController
import com.android.launcher3.display.DisplayControllerImpl
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.dragndrop.SystemDragControllerStub
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.qsb.QsbAppWidgetHost
import com.android.launcher3.qsb.QsbAppWidgetHostImpl
import com.android.launcher3.util.BaseDefaultsValueProvider
import com.android.launcher3.util.DefaultsValueProvider
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.RefreshRateTracker.RefreshRateTrackerImpl
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Named

private object Modules

@Module abstract class WindowManagerProxyModule

@Module(includes = [SystemDragModule::class])
abstract class ActivityContextModule {
    companion object {
        @JvmStatic
        @Provides
        @ActivityContextSingleton
        @DisplayId
        fun provideDisplayId(activityContext: ActivityContext): Int =
            (activityContext as Context).display.displayId
    }
}

@Module(includes = [NoOpLoggerModule::class]) abstract class StatsLoggerModule {}

@Module
abstract class ApiWrapperModule {

    @Binds abstract fun bindDisplayController(impl: DisplayControllerImpl): DisplayController
}

@Module
abstract class WidgetModule {
    @Binds
    abstract fun bindWidgetHolderFactory(factor: WidgetHolderFactoryImpl): WidgetHolderFactory
}

@Module abstract class PluginManagerWrapperModule

@Module
object StaticObjectModule {
    @Provides
    fun provideRefreshRateTracker(tracker: RefreshRateTrackerImpl): RefreshRateTracker = tracker

    @Provides fun provideAbstractFloatingViewHelper() = AbstractFloatingViewHelper

    @Provides
    fun provideQsbAppWidgetHost(@ApplicationContext context: Context): QsbAppWidgetHost =
        QsbAppWidgetHostImpl.getStaticInstance(context)
}

@Module
object SystemDragModule {
    @Provides
    @ActivityContextSingleton
    fun provideSystemDragController(): SystemDragController = SystemDragControllerStub()
}

// Module containing bindings for the final derivative app
@Module
object AppModule {

    @Provides
    @JvmStatic
    @LauncherAppSingleton
    @Named(WINDOW_BLUR_STATE)
    fun provideWindowBlurState() = MutableListenableRef<Boolean>(false).asListenable()

    @Provides
    @JvmStatic
    @LauncherAppSingleton
    fun provideDefaultsValueProvider(impl: BaseDefaultsValueProvider): DefaultsValueProvider = impl
}

@Module abstract class ProductionAppModule

// Module containing bindings of [ActivityContext] for the final derivative app
@Module abstract class AppActivityContextModule

@Module abstract class PerDisplayModule

@Module abstract class LauncherConcurrencyModule {}

/** A dagger module responsible for managing files on the home screen. */
@Module
object HomeScreenFilesModule {
    @Provides
    @LauncherAppSingleton
    fun provideHomeScreenFilesProvider(): HomeScreenFilesProvider = HomeScreenFilesNoOpProvider()
}

// This module is empty in the no_quickstep variant as desktop mode is not supported.
@Module object DesktopModule

// This module is empty in the no_quickstep variant as task overlay is not supported.
@Module object TaskOverlayModule

// Bind no-op version in no_quickstep as automation not supported.
@Module
abstract class AutomationModule {
    @Binds
    abstract fun bindAutomationRepository(impl: AutomationNoOpRepository): AutomationRepository
}
