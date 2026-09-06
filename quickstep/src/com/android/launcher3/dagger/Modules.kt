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

import android.annotation.ElapsedRealtimeLong
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.uilatencystats.UiLatencyStatsManager
import android.view.CrossWindowBlurListeners
import com.android.app.displaylib.PerDisplayRepository
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.internal.R
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.internal.util.LatencyTracker
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.Flags.enableSystemDrag
import com.android.launcher3.Launcher
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.display.DisplayController
import com.android.launcher3.display.DisplayControllerImpl
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.dragndrop.SystemDragControllerImpl
import com.android.launcher3.dragndrop.SystemDragControllerStub
import com.android.launcher3.homescreenfiles.HomeScreenFilesIconProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesIconProviderImpl
import com.android.launcher3.homescreenfiles.HomeScreenFilesMediaStoreProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIconProviderImpl
import com.android.launcher3.logging.StatsLogManager.StatsImpressionLogger
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger
import com.android.launcher3.logging.StatsLogManager.StatsLogger
import com.android.launcher3.model.WellbeingModel
import com.android.launcher3.qsb.QsbAppWidgetHost
import com.android.launcher3.qsb.QuickstepQsbHostImpl
import com.android.launcher3.secondarydisplay.SecondaryDisplayDelegate
import com.android.launcher3.secondarydisplay.SecondaryDisplayQuickstepDelegateImpl
import com.android.launcher3.testing.TestInformationHandler
import com.android.launcher3.uioverrides.QuickstepProvidersUpdateDispatcher
import com.android.launcher3.uioverrides.QuickstepWidgetHolder.QuickstepWidgetHolderFactory
import com.android.launcher3.uioverrides.SystemApiWrapper
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapperImpl
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.BlurBackgroundHelper
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.IMMEDIATE_EXECUTOR
import com.android.launcher3.util.InstantAppResolver
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.QuickstepBackgroundBlurHelper
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.launcher3.widget.ProvidersUpdateDispatcher
import com.android.quickstep.AspectRatioSystemShortcut
import com.android.quickstep.AutomationRepositoryImpl
import com.android.quickstep.DesktopShortcut
import com.android.quickstep.ExternalDisplayShortcut
import com.android.quickstep.InstantAppResolverImpl
import com.android.quickstep.LauncherRestoreEventLoggerImpl
import com.android.quickstep.QuickstepTestInformationHandler
import com.android.quickstep.TaskShortcutFactory
import com.android.quickstep.TaskUtils
import com.android.quickstep.WellbeingShortcut
import com.android.quickstep.logging.StatsLogCompatManager.StatsCompatImpressionLogger
import com.android.quickstep.logging.StatsLogCompatManager.StatsCompatLatencyLogger
import com.android.quickstep.logging.StatsLogCompatManager.StatsCompatLogger
import com.android.quickstep.util.ChoreographerFrameRateTracker
import com.android.quickstep.util.ContextualSearchStateManager
import com.android.quickstep.util.GestureExclusionManager
import com.android.quickstep.util.SystemWindowManagerProxy
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.wm.shell.shared.desktopmode.DesktopState
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import java.io.File
import java.util.function.Consumer
import javax.inject.Named

private object Modules {}

@Module
abstract class WindowManagerProxyModule {
    @Binds abstract fun bindWindowManagerProxy(proxy: SystemWindowManagerProxy): WindowManagerProxy
}

@Module(
    includes =
        [
            SystemDragModule::class,
            LauncherRecentsModule::class,
            PerDisplayScopedProviderModule::class,
        ]
)
abstract class ActivityContextModule {
    @Binds
    abstract fun bindSecondaryDisplayDelegate(
        impl: SecondaryDisplayQuickstepDelegateImpl
    ): SecondaryDisplayDelegate

    @Binds
    abstract fun bindBackgroundBlurHelper(
        quickstepBackgroundBlurHelper: QuickstepBackgroundBlurHelper
    ): BlurBackgroundHelper

    companion object {
        @JvmStatic
        @Provides
        @ActivityContextSingleton
        @DisplayId
        fun provideDisplayId(activityContext: ActivityContext): Int =
            activityContext.asContext().displayId
    }
}

@Module
abstract class StatsLoggerModule {
    @Binds abstract fun bindStatsLogger(impl: StatsCompatLogger): StatsLogger

    @Binds abstract fun bindStatsLatencyLogger(impl: StatsCompatLatencyLogger): StatsLatencyLogger

    @Binds
    abstract fun bindStatsImpressionLogger(impl: StatsCompatImpressionLogger): StatsImpressionLogger
}

@Module
abstract class ApiWrapperModule {

    @Binds abstract fun bindApiWrapper(systemApiWrapper: SystemApiWrapper): ApiWrapper

    @Binds
    abstract fun bindIconProvider(iconProviderImpl: LauncherIconProviderImpl): LauncherIconProvider

    @Binds abstract fun bindInstantAppResolver(impl: InstantAppResolverImpl): InstantAppResolver

    @Binds
    abstract fun bindRestoreEventLogger(
        impl: LauncherRestoreEventLoggerImpl
    ): LauncherRestoreEventLogger

    @Binds
    abstract fun bindTestInformationHandler(
        impl: QuickstepTestInformationHandler
    ): TestInformationHandler

    @Binds abstract fun bindDisplayController(impl: DisplayControllerImpl): DisplayController

    companion object {
        @Provides
        @LauncherAppSingleton
        fun provideRecentsWindowContextRepo(
            repositoryFactory: PerDisplayComponentRepository.Factory<Context>
        ): PerDisplayRepository<Context> =
            repositoryFactory.create("WindowContext", PerDisplayComponent::getWindowContext)
    }
}

@Module
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetHolderFactory(factor: QuickstepWidgetHolderFactory): WidgetHolderFactory

    @Binds
    abstract fun bindUpdateDispatcher(
        dispatcher: QuickstepProvidersUpdateDispatcher
    ): ProvidersUpdateDispatcher
}

@Module
abstract class PluginManagerWrapperModule {
    @Binds
    abstract fun bindPluginManagerWrapper(impl: PluginManagerWrapperImpl): PluginManagerWrapper
}

@Module
object StaticObjectModule {

    @Provides
    fun provideGestureExclusionManager(): GestureExclusionManager = GestureExclusionManager.INSTANCE

    @Provides fun provideRefreshRateTracker(): RefreshRateTracker = ChoreographerFrameRateTracker

    @Provides
    fun provideActivityManagerWrapper(): ActivityManagerWrapper =
        ActivityManagerWrapper.getInstance()

    @Provides
    @JvmStatic
    fun provideTaskStackChangeListeners(): TaskStackChangeListeners =
        TaskStackChangeListeners.getInstance()

    @Provides
    @JvmStatic
    @ElapsedRealtimeLong
    fun provideElapsedRealTime(): () -> Long = SystemClock::elapsedRealtime

    @Provides
    @ElementsIntoSet
    @Named("SETTINGS_ENABLED_BY_DEFAULT")
    fun provideSearchEntryPointsDefault(@ApplicationContext ctx: Context): Set<Uri> =
        if (ctx.resources.getBoolean(R.bool.config_searchAllEntrypointsEnabledDefault)) {
            setOf(ContextualSearchStateManager.SEARCH_ALL_ENTRYPOINTS_ENABLED_URI)
        } else emptySet()

    @Provides
    @JvmStatic
    @LauncherAppSingleton
    @Named(WINDOW_BLUR_STATE)
    fun provideWindowBlurState(lifecycle: DaggerSingletonTracker): ListenableRef<Boolean> {
        val blurListeners = CrossWindowBlurListeners.getInstance()
        val value = MutableListenableRef(blurListeners.isCrossWindowBlurEnabled)

        val callback = Consumer<Boolean> { value.dispatchValue(it) }
        blurListeners.addListener(IMMEDIATE_EXECUTOR, callback)
        lifecycle.addCloseable { blurListeners.removeListener(callback) }
        return value.asListenable()
    }

    @Provides fun provideAbstractFloatingViewHelper() = AbstractFloatingViewHelper

    @Provides fun provideTaskUtils() = TaskUtils

    @Provides
    @JvmStatic
    fun provideComputerControlExtensions(
        @ApplicationContext context: Context
    ): ComputerControlExtensions? = ComputerControlExtensions.getInstance(context)

    @Provides
    fun provideUiLatencyStatsManager(@ApplicationContext context: Context): UiLatencyStatsManager? =
        if (com.android.server.ui_latency_stats.Flags.uiLatencyStatsService()) {
            context.getSystemService(UiLatencyStatsManager::class.java)
        } else {
            null
        }

    @Provides
    fun provideLatencyTracker(@ApplicationContext context: Context): LatencyTracker =
        LatencyTracker.getInstance(context)

    @Provides fun provideQsbAppWidgetHost(): QsbAppWidgetHost = QuickstepQsbHostImpl.instance
}

@Module
object SystemDragModule {
    @Provides
    @ActivityContextSingleton
    fun provideSystemDragController(
        context: ActivityContext,
        factory: SystemDragControllerImpl.Factory,
    ): SystemDragController =
        // TODO(b/456787959): Fix drop targets and enable for other contexts.
        if (enableSystemDrag() && context is Launcher) {
            factory.create(HomeScreenFilesUtils.isFeatureEnabled)
        } else {
            SystemDragControllerStub()
        }
}

/** A dagger module responsible for managing files on the home screen. */
@Module
interface HomeScreenFilesModule {
    @Binds
    fun bindHomeScreenFilesIconProvider(
        impl: HomeScreenFilesIconProviderImpl
    ): HomeScreenFilesIconProvider

    companion object {
        @Provides
        @LauncherAppSingleton
        fun provideHomeScreenFilesProvider(
            factory: HomeScreenFilesMediaStoreProvider.Factory
        ): HomeScreenFilesProvider =
            if (HomeScreenFilesUtils.isFeatureEnabled) factory.create(::File)
            else HomeScreenFilesNoOpProvider()
    }
}

@Module
object DesktopModule {
    @Provides
    @LauncherAppSingleton
    fun provideDesktopModeCompatPolicy(@ApplicationContext context: Context) =
        DesktopModeCompatPolicy(context)

    @Provides
    @LauncherAppSingleton
    fun provideDesktopState(
        @ApplicationContext context: Context,
        lifecycle: DaggerSingletonTracker,
    ): DesktopState =
        DesktopState.fromContext(context).also { lifecycle.addCloseable { it.destroy() } }
}

@Module
object TaskOverlayModule {

    @Provides
    @LauncherAppSingleton
    fun provideWellbeingShortcutFactory(): WellbeingShortcut.Factory {
        return WellbeingShortcut.Factory(WellbeingModel.SHORTCUT_FACTORY)
    }

    @Provides
    @LauncherAppSingleton
    fun providePerTaskShortcutFactories(
        desktopShortcutFactory: DesktopShortcut.Factory,
        externalDisplayShortcutFactory: ExternalDisplayShortcut.Factory,
        aspectRatioSystemShortcutFactory: AspectRatioSystemShortcut.Factory,
        wellbeingShortcutFactory: WellbeingShortcut.Factory,
    ): List<TaskShortcutFactory> =
        listOf(
            TaskShortcutFactory.APP_INFO,
            TaskShortcutFactory.SPLIT_SELECT,
            TaskShortcutFactory.PIN,
            TaskShortcutFactory.INSTALL,
            TaskShortcutFactory.FREE_FORM,
            desktopShortcutFactory,
            externalDisplayShortcutFactory,
            aspectRatioSystemShortcutFactory,
            wellbeingShortcutFactory,
            TaskShortcutFactory.SAVE_APP_PAIR,
            TaskShortcutFactory.SCREENSHOT,
            TaskShortcutFactory.MODAL,
        )
}

/** Used by both Recents and Launcher for package automation */
@Module
interface AutomationModule {
    @Binds fun bindAutomatedRepository(impl: AutomationRepositoryImpl): AutomationRepository
}
