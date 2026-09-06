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
package com.android.launcher3.dagger

import com.android.launcher3.concurrent.ExecutorsModule
import com.android.launcher3.util.dagger.LauncherExecutorsModule
import com.android.launcher3.widgetpicker.LauncherWidgetPickerModule
import dagger.Module

@Module(
    includes = [BootSafeModules::class, BootUnsafeModules::class],
    subcomponents = [ActivityContextComponent::class],
)
class LauncherAppModule

@Module(
    includes =
        [
            WindowManagerProxyModule::class,
            ApiWrapperModule::class,
            StaticObjectModule::class,
            AppModule::class,
            ExecutorsModule::class,
            LauncherExecutorsModule::class,
            LauncherModelModule::class,
            SettingsModule::class,
            HomeScreenFilesModule::class,
            DesktopModule::class,
            AutomationModule::class,
            TaskOverlayModule::class,
        ]
)
class BootSafeModules

@Module(
    includes =
        [
            PluginManagerWrapperModule::class,
            ProductionAppModule::class,
            PerDisplayModule::class,
            LauncherWidgetPickerModule::class,
            WidgetModule::class,
            StatsLoggerModule::class,
            WorkspaceFunctionsLauncherModule::class,
            OrganizerLauncherModule::class,
        ]
)
class BootUnsafeModules
