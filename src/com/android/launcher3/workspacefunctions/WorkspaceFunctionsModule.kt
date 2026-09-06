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
package com.android.launcher3.workspacefunctions

import android.content.pm.LauncherActivityInfo
import com.android.launcher3.Flags
import com.android.launcher3.appfunctions.workspace.UnplacedAppTypeTranslator
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetTypeTranslator
import com.android.launcher3.appfunctions.workspace.WorkspaceAppFunctions
import com.android.launcher3.appfunctions.workspace.WorkspaceRepository
import com.android.launcher3.appfunctions.workspace.WorkspaceTypeTranslator
import com.android.launcher3.appfunctions.workspace.provider.InstalledItemsProvider
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.workspacefunctions.translators.AppInFolderTranslator
import com.android.launcher3.workspacefunctions.translators.FolderInfoHotseatTranslator
import com.android.launcher3.workspacefunctions.translators.FolderInfoWorkspaceTranslator
import com.android.launcher3.workspacefunctions.translators.HotseatItemTranslator
import com.android.launcher3.workspacefunctions.translators.LauncherAppWidgetInfoWorkspaceTranslator
import com.android.launcher3.workspacefunctions.translators.LauncherUnplacedAppTranslator
import com.android.launcher3.workspacefunctions.translators.LauncherUnplacedWidgetTranslator
import com.android.launcher3.workspacefunctions.translators.LauncherWorkspaceTypeTranslator
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemInfoAppInFolderTranslator
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemInfoHotseatTranslator
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemInfoWorkspaceTranslator
import com.android.launcher3.workspacefunctions.translators.WorkspaceItemTranslator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Dagger module for binding workspace functions interfaces. */
@Module
abstract class WorkspaceFunctionsModule {

    @Binds
    abstract fun bindWorkspaceRepository(
        impl: WorkspaceRepositoryImpl
    ): WorkspaceRepository

    @Binds
    abstract fun bindInstalledAppsProvider(
        impl: LauncherInstalledAppsProvider
    ): InstalledItemsProvider<LauncherActivityInfo>

    @Binds
    abstract fun bindInstalledWidgetsProvider(
        impl: LauncherInstalledWidgetsProvider
    ): InstalledItemsProvider<LauncherAppWidgetProviderInfo>

    @Binds
    @IntoMap
    @ClassKey(WorkspaceData::class)
    abstract fun bindLauncherWorkspaceTypeTranslator(
        impl: LauncherWorkspaceTypeTranslator
    ): @JvmSuppressWildcards WorkspaceTypeTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(LauncherActivityInfo::class)
    abstract fun bindLauncherUnplacedAppTranslator(
        impl: LauncherUnplacedAppTranslator
    ): @JvmSuppressWildcards UnplacedAppTypeTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(LauncherAppWidgetProviderInfo::class)
    abstract fun bindLauncherUnplacedWidgetTranslator(
        impl: LauncherUnplacedWidgetTranslator
    ): @JvmSuppressWildcards UnplacedWidgetTypeTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(WorkspaceItemInfo::class)
    abstract fun bindWorkspaceItemInfoWorkspaceTranslator(
        impl: WorkspaceItemInfoWorkspaceTranslator
    ): @JvmSuppressWildcards WorkspaceItemTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(WorkspaceItemInfo::class)
    abstract fun bindWorkspaceItemInfoHotseatTranslator(
        impl: WorkspaceItemInfoHotseatTranslator
    ): @JvmSuppressWildcards HotseatItemTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(WorkspaceItemInfo::class)
    abstract fun bindWorkspaceItemInfoFolderTranslator(
        impl: WorkspaceItemInfoAppInFolderTranslator
    ): @JvmSuppressWildcards AppInFolderTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(FolderInfo::class)
    abstract fun bindFolderInfoWorkspaceTranslator(
        impl: FolderInfoWorkspaceTranslator
    ): @JvmSuppressWildcards WorkspaceItemTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(FolderInfo::class)
    abstract fun bindFolderInfoHotseatTranslator(
        impl: FolderInfoHotseatTranslator
    ): @JvmSuppressWildcards HotseatItemTranslator<*>

    @Binds
    @IntoMap
    @ClassKey(LauncherAppWidgetInfo::class)
    abstract fun bindLauncherAppWidgetInfoWorkspaceTranslator(
        impl: LauncherAppWidgetInfoWorkspaceTranslator
    ): @JvmSuppressWildcards WorkspaceItemTranslator<*>

    companion object {

        @Provides
        fun provideWorkspaceAppFunctions(
            repository: WorkspaceRepository
        ): WorkspaceAppFunctions {
            return WorkspaceAppFunctions(repository)
        }
    }
}
