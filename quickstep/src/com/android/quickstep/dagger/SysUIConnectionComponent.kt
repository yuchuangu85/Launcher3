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

package com.android.quickstep.dagger

import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarManagerImplWrapper
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks
import com.android.launcher3.taskbar.navbutton.TaskbarNavButtonCallbacksImpl
import com.android.launcher3.util.PostUnlockObject
import com.android.launcher3.util.ThreadSafeRunnableList
import com.android.quickstep.AllAppsActionManager
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.TouchInteractionHandler
import com.android.quickstep.sysuiconnection.TISBinder
import dagger.Binds
import dagger.BindsInstance
import dagger.Module
import dagger.Subcomponent
import javax.inject.Named
import javax.inject.Scope

/** Scope annotation for singletons associated with SysUI connection lifecycle. */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Scope
annotation class SysUIConnectionSingleton

@SysUIConnectionSingleton
@Subcomponent(modules = [SysUIConnectionModule::class])
interface SysUIConnectionComponent {

    val touchInteractionHandler: TouchInteractionHandler
    val overviewCommandHelper: PostUnlockObject<OverviewCommandHelper>
    val allAppsActionManager: AllAppsActionManager
    val taskbarManager: TaskbarManager
    val binder: TISBinder

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun setConnectionCleaner(@Named(CONNECTION_CLEANER) list: ThreadSafeRunnableList): Builder

        fun build(): SysUIConnectionComponent
    }
}

const val CONNECTION_CLEANER = "CONNECTION_CLEANER"

@Module
abstract class SysUIConnectionModule {

    @Binds abstract fun bindTaskBar(wrapper: TaskbarManagerImplWrapper): TaskbarManager

    @Binds
    abstract fun bindTaskbarNavButtonCallbacks(
        impl: TaskbarNavButtonCallbacksImpl
    ): TaskbarNavButtonCallbacks
}
