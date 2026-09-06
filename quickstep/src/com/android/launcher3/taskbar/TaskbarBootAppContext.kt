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

package com.android.launcher3.taskbar

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import com.android.app.displaylib.PerDisplayRepository
import com.android.launcher3.InMemoryLauncherPrefs
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.BasePerDisplayModule
import com.android.launcher3.dagger.BootSafeModules
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.dagger.NoOpLoggerModule
import com.android.launcher3.dagger.PerDisplayRepositoriesModule
import com.android.launcher3.dagger.WidgetModule
import com.android.launcher3.organizer.dagger.NoOpGeneratorModule
import com.android.launcher3.organizer.dagger.NoOpOrganizerModule
import com.android.launcher3.qsb.QsbWidgetFactory
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.SandboxContext
import com.android.launcher3.widgetpicker.NoOpWidgetPickerModule
import com.android.launcher3.workspacefunctions.NoOpWorkspaceFunctionsModule
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import javax.inject.Inject

/**
 * Sandbox for enabling Taskbar in direct boot mode.
 *
 * Swaps out dependencies that depend on encrypted storage for alternatives that still allow for
 * system navigation (e.g. back button) to show up on keyguard before the first unlock.
 */
class TaskbarBootAppContext(base: Context) : SandboxContext(base) {

    init {
        initDaggerComponent(
            DaggerTaskbarBootComponent.builder()
                .bindPrefs(InMemoryLauncherPrefs(this))
                .bindPluginManagerWrapper(PluginManagerWrapper())
                .bindTaskbarFeatureEvaluatorRepo(
                    base.appComponent.taskbarFeatureEvaluatorRepository
                )
        )
    }

    /**
     * Wrap a Taskbar window context for sandboxing it under this sandbox.
     *
     * This context should be a middle layer between [TaskbarActivityContext] and its window
     * context. In other words, [TaskbarActivityContext] should wrap the returned context, which
     * wraps [base].
     */
    fun wrapWindowContext(base: Context): Context = TaskbarBootContextWrapper(base)

    private inner class TaskbarBootContextWrapper(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext() = this@TaskbarBootAppContext
    }
}

class NoOpQsbFactory @Inject constructor() : QsbWidgetFactory() {

    override fun createView(container: ViewGroup): View =
        View(container.context).apply { layoutParams = ViewGroup.LayoutParams(0, 0) }
}

@Module
abstract class QsbWidgetModule {

    @Binds abstract fun bindQsbWidgetModule(impl: NoOpQsbFactory): QsbWidgetFactory
}

@LauncherAppSingleton
@Component(
    modules =
        [
            BasePerDisplayModule::class,
            PerDisplayRepositoriesModule::class,
            QsbWidgetModule::class,
            NoOpWidgetPickerModule::class,
            WidgetModule::class,
            NoOpLoggerModule::class,
            BootSafeModules::class,
            NoOpWorkspaceFunctionsModule::class,
            NoOpOrganizerModule::class,
            NoOpGeneratorModule::class,
        ]
)
interface TaskbarBootComponent : LauncherAppComponent {
    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindPrefs(prefs: LauncherPrefs): Builder

        @BindsInstance fun bindPluginManagerWrapper(wrapper: PluginManagerWrapper): Builder

        @BindsInstance
        fun bindTaskbarFeatureEvaluatorRepo(
            taskbarFeatureEvaluatorRepo: PerDisplayRepository<TaskbarFeatureEvaluator>
        ): Builder

        override fun build(): TaskbarBootComponent
    }
}
