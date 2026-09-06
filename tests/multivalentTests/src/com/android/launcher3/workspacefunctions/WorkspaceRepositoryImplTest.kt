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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.appfunctions.workspace.UnplacedAppSpec
import com.android.launcher3.appfunctions.workspace.UnplacedAppTypeTranslator
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetSpec
import com.android.launcher3.appfunctions.workspace.UnplacedWidgetTypeTranslator
import com.android.launcher3.appfunctions.workspace.WorkspaceSpec
import com.android.launcher3.appfunctions.workspace.WorkspaceTypeTranslator
import com.android.launcher3.appfunctions.workspace.provider.InstalledItemsProvider
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.workspacefunctions.translators.TranslatorRegistry
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [WorkspaceRepositoryImpl]. */
@RunWith(AndroidJUnit4::class)
class WorkspaceRepositoryImplTest {

    private val workspaceProvider = mock<LauncherWorkspaceProvider>()
    private val installedAppsProvider = mock<InstalledItemsProvider<LauncherActivityInfo>>()
    private val installedWidgetsProvider =
        mock<InstalledItemsProvider<LauncherAppWidgetProviderInfo>>()

    private val workspaceTypeTranslator = mock<WorkspaceTypeTranslator<WorkspaceData>>()
    private val unplacedAppTypeTranslator = mock<UnplacedAppTypeTranslator<LauncherActivityInfo>>()
    private val unplacedWidgetTypeTranslator =
        mock<UnplacedWidgetTypeTranslator<LauncherAppWidgetProviderInfo>>()

    private lateinit var translators: TranslatorRegistry
    private lateinit var repository: WorkspaceRepositoryImpl

    @Before
    fun setUp() {
        translators =
            TranslatorRegistry(
                workspaceItemTranslators = emptyMap(),
                hotseatItemTranslators = emptyMap(),
                appInFolderTranslators = emptyMap(),
                workspaceTypeTranslators =
                    mapOf(
                        WorkspaceData::class.java to
                            Provider<WorkspaceTypeTranslator<*>> { workspaceTypeTranslator }
                    ),
                unplacedAppTypeTranslators =
                    mapOf(
                        LauncherActivityInfo::class.java to
                            Provider<UnplacedAppTypeTranslator<*>> { unplacedAppTypeTranslator }
                    ),
                unplacedWidgetTypeTranslators =
                    mapOf(
                        LauncherAppWidgetProviderInfo::class.java to
                            Provider<UnplacedWidgetTypeTranslator<*>> {
                                unplacedWidgetTypeTranslator
                            }
                    ),
            )

        repository =
            WorkspaceRepositoryImpl(
                workspaceProvider,
                installedAppsProvider,
                installedWidgetsProvider,
                translators,
            )
    }

    @Test
    fun getWorkspace_delegatesToProviderAndTranslator() = runTest {
        val workspaceData = WorkspaceData.MutableWorkspaceData()
        val workspaceSpec = mock<WorkspaceSpec>()
        whenever(workspaceProvider.getWorkspace()).thenReturn(workspaceData)
        whenever(workspaceTypeTranslator.toSpec(workspaceData)).thenReturn(workspaceSpec)

        val result = repository.getWorkspace()

        assertThat(result).isEqualTo(workspaceSpec)
    }

    @Test
    fun getInstalledApps_delegatesToProviderAndTranslator() = runTest {
        val appInfo = mock<LauncherActivityInfo>()
        val appSpec = mock<UnplacedAppSpec>()
        whenever(installedAppsProvider.getInstalledItems(any())).thenReturn(listOf(appInfo))
        whenever(unplacedAppTypeTranslator.toSpec(appInfo)).thenReturn(appSpec)

        val result = repository.getInstalledApps(false)

        assertThat(result).containsExactly(appSpec)
    }

    @Test
    fun getInstalledWidgets_delegatesToProviderAndTranslator() = runTest {
        val widgetInfo = mock<LauncherAppWidgetProviderInfo>()
        val widgetSpec = mock<UnplacedWidgetSpec>()
        whenever(installedWidgetsProvider.getInstalledItems(any())).thenReturn(listOf(widgetInfo))
        whenever(unplacedWidgetTypeTranslator.toSpec(widgetInfo)).thenReturn(widgetSpec)

        val result = repository.getInstalledWidgets(false)

        assertThat(result).containsExactly(widgetSpec)
    }
}
