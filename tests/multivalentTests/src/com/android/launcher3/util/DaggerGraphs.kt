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

package com.android.launcher3.util

import com.android.launcher3.FakeLauncherPrefs
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.ExecutorsModule
import com.android.launcher3.dagger.ApiWrapperModule
import com.android.launcher3.dagger.AppModule
import com.android.launcher3.dagger.AutomationModule
import com.android.launcher3.dagger.DesktopModule
import com.android.launcher3.dagger.HomeScreenFilesModule
import com.android.launcher3.dagger.LauncherModelModule
import com.android.launcher3.dagger.OrganizerLauncherModule
import com.android.launcher3.dagger.PerDisplayModule
import com.android.launcher3.dagger.SettingsModule
import com.android.launcher3.dagger.StaticObjectModule
import com.android.launcher3.dagger.StatsLoggerModule
import com.android.launcher3.dagger.TaskOverlayModule
import com.android.launcher3.dagger.WidgetModule
import com.android.launcher3.dagger.WindowManagerProxyModule
import com.android.launcher3.dagger.WorkspaceFunctionsLauncherModule
import com.android.launcher3.util.dagger.LauncherExecutorsModule
import com.android.launcher3.widgetpicker.NoOpWidgetPickerModule
import dagger.Binds
import dagger.Module

private class DaggerGraphs {}

@Module
abstract class FakePrefsModule {
    @Binds abstract fun bindLauncherPrefs(prefs: FakeLauncherPrefs): LauncherPrefs
}

@Module(
    includes =
        [
            StaticObjectModule::class,
            WidgetModule::class,
            AppModule::class,
            PerDisplayModule::class,
            ExecutorsModule::class,
            LauncherExecutorsModule::class,
            NoOpWidgetPickerModule::class,
            LauncherModelModule::class,
            HomeScreenFilesModule::class,
            DesktopModule::class,
            SettingsModule::class,
            AutomationModule::class,
            TaskOverlayModule::class,
            WorkspaceFunctionsLauncherModule::class,
            StatsLoggerModule::class,
            OrganizerLauncherModule::class,
        ]
)
class CommonModulesForTest

/** All modules. We also exclude the plugin module from tests */
@Module(
    includes =
        [ApiWrapperModule::class, CommonModulesForTest::class, WindowManagerProxyModule::class]
)
class AllModulesForTest

/** All modules except the WMProxy */
@Module(includes = [ApiWrapperModule::class, CommonModulesForTest::class])
class AllModulesMinusWMProxy
