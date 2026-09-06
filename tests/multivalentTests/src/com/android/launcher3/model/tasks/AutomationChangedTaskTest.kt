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

package com.android.launcher3.model.tasks

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AppFilter
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings
import com.android.launcher3.automation.AutomationChange
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel.ModificationSource.ModelTask
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_AUTOMATED
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.repository.AppsListRepository
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertWithMessage
import dagger.BindsInstance
import dagger.Component
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit tests for [AutomationChangedTask] */
@RunWith(AndroidJUnit4::class)
class AutomationChangedTaskTest {

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val mockito = MockitoJUnit.rule()

    private lateinit var mockTaskController: ModelTaskController
    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    private val user: UserHandle = Process.myUserHandle()
    private val pkgAutomated = "com.test.already.automated"
    private val pkgNotAutomated = "com.test.not.already.automated"
    private val component1 = ComponentName(pkgAutomated, "$pkgAutomated.TestActivity")
    private val component2 = ComponentName(pkgNotAutomated, "$pkgNotAutomated.TestActivity")
    private lateinit var appInfoAutomated: AppInfo
    private lateinit var appInfoNotAutomated: AppInfo
    private lateinit var workspaceItemAutomated: WorkspaceItemInfo
    private lateinit var workspaceItemNotAutomated: WorkspaceItemInfo

    @Mock lateinit var expectedActivityInfo: LauncherActivityInfo

    @Before
    fun setup() {
        val appsListRepo = AppsListRepository()
        val iconCache = mock<IconCache>()
        val appFilter = mock<AppFilter>()
        appInfoAutomated =
            AppInfo(component1, "Test App 1", user, AppInfo.makeLaunchIntent(component1)).apply {
                uid = 1
                runtimeStatusFlags = FLAG_AUTOMATED
            }
        appInfoNotAutomated =
            AppInfo(component2, "Test App 2", user, AppInfo.makeLaunchIntent(component2)).apply {
                uid = 2
                runtimeStatusFlags = 0
            }
        workspaceItemAutomated =
            WorkspaceItemInfo(appInfoAutomated).apply {
                id = 1
                container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                screenId = 0
                cellX = 0
                cellY = 0
                runtimeStatusFlags = FLAG_AUTOMATED
            }
        workspaceItemNotAutomated =
            WorkspaceItemInfo(appInfoNotAutomated).apply {
                id = 2
                container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                screenId = 0
                cellX = 1
                cellY = 0
                runtimeStatusFlags = 0
            }
        whenever(appFilter.shouldShowApp(any())).thenReturn(true)
        context.initDaggerComponent(
            DaggerAutomationChangedTaskTest_TestComponent.builder()
                .bindAppsRepository(appsListRepo)
                .bindAllAppsList(spy(AllAppsList(iconCache, appFilter) { appsListRepo }))
                .bindAppFilter(appFilter)
                .bindIconCache(iconCache)
        )
        mockTaskController = spy((context.appComponent as TestComponent).getTaskController())
        modelState.dataModel.addItems(
            context,
            listOf(workspaceItemAutomated, workspaceItemNotAutomated),
            ModelTask,
        )
        modelState.appsList.add(appInfoAutomated, expectedActivityInfo)
        modelState.appsList.add(appInfoNotAutomated, expectedActivityInfo)
    }

    @After fun tearDown() {}

    private fun executeTaskUnderTest(change: AutomationChange) {
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            AutomationChangedTask(change)
                .execute(mockTaskController, modelState.dataModel, modelState.appsList)
        }
    }

    private fun ItemInfoWithIcon.assertIsAutomated() {
        assertWithMessage("Expected FLAG_AUTOMATED to be set")
            .that(runtimeStatusFlags and FLAG_AUTOMATED)
            .isNotEqualTo(0)
    }

    private fun ItemInfoWithIcon.assertNotAutomated() {
        assertWithMessage("Expected FLAG_AUTOMATED to not be set")
            .that(runtimeStatusFlags and FLAG_AUTOMATED)
            .isEqualTo(0)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun automationAdded_setsAutomatedFlag() {
        val change =
            AutomationChange(
                user,
                addedPackages = setOf(pkgNotAutomated, pkgAutomated),
                removedPackages = emptySet(),
            )

        executeTaskUnderTest(change)

        workspaceItemAutomated.assertIsAutomated()
        workspaceItemNotAutomated.assertIsAutomated()
        appInfoAutomated.assertIsAutomated()
        appInfoNotAutomated.assertIsAutomated()
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(workspaceItemNotAutomated))
        verify(mockTaskController).bindApplicationsIfNeeded()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun automationRemoved_removesAutomatedFlag() {
        val change =
            AutomationChange(
                user,
                addedPackages = emptySet(),
                removedPackages = setOf(pkgAutomated, pkgNotAutomated),
            )

        executeTaskUnderTest(change)

        workspaceItemAutomated.assertNotAutomated()
        workspaceItemNotAutomated.assertNotAutomated()
        appInfoAutomated.assertNotAutomated()
        appInfoNotAutomated.assertNotAutomated()
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(workspaceItemAutomated))
        verify(mockTaskController).bindApplicationsIfNeeded()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun automationChangedForMultiple_updatesCorrectFlags() {
        val change =
            AutomationChange(
                user,
                addedPackages = setOf(pkgNotAutomated),
                removedPackages = setOf(pkgAutomated),
            )

        executeTaskUnderTest(change)

        workspaceItemAutomated.assertNotAutomated()
        workspaceItemNotAutomated.assertIsAutomated()
        appInfoAutomated.assertNotAutomated()
        appInfoNotAutomated.assertIsAutomated()
        verify(mockTaskController)
            .bindUpdatedWorkspaceItems(listOf(workspaceItemAutomated, workspaceItemNotAutomated))
        verify(mockTaskController).bindApplicationsIfNeeded()
    }

    @DisableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    @Test
    fun automationIndicatorFlagDisabled_doesNothing() {
        val change =
            AutomationChange(
                user,
                addedPackages = setOf(pkgAutomated),
                removedPackages = emptySet(),
            )

        executeTaskUnderTest(change)

        workspaceItemAutomated.assertIsAutomated()
        appInfoAutomated.assertIsAutomated()
        verify(mockTaskController, never()).bindUpdatedWorkspaceItems(any())
        verify(mockTaskController, never()).bindApplicationsIfNeeded()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun noMatchingItems_doesNothing() {
        val change =
            AutomationChange(
                user,
                addedPackages = setOf("com.test.nonexistent"),
                removedPackages = emptySet(),
            )

        executeTaskUnderTest(change)

        workspaceItemAutomated.assertIsAutomated()
        workspaceItemNotAutomated.assertNotAutomated()
        appInfoAutomated.assertIsAutomated()
        appInfoNotAutomated.assertNotAutomated()
        verify(mockTaskController, never()).bindUpdatedWorkspaceItems(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun automationAdded_alreadyAutomated_doesNothing() {
        val change =
            AutomationChange(
                user,
                addedPackages = setOf(pkgAutomated),
                removedPackages = emptySet(),
            )

        executeTaskUnderTest(change)

        workspaceItemAutomated.assertIsAutomated()
        appInfoAutomated.assertIsAutomated()
        verify(mockTaskController, never()).bindUpdatedWorkspaceItems(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_AUTOMATION_INDICATOR)
    fun automationRemoved_alreadyNotAutomated_doesNothing() {
        val change =
            AutomationChange(
                user,
                addedPackages = setOf(),
                removedPackages = setOf(pkgNotAutomated),
            )

        executeTaskUnderTest(change)

        workspaceItemNotAutomated.assertNotAutomated()
        appInfoNotAutomated.assertNotAutomated()
        verify(mockTaskController, never()).bindUpdatedWorkspaceItems(any())
    }

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {

        fun getTaskController(): ModelTaskController

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindAppsRepository(appsListRepo: AppsListRepository): Builder

            @BindsInstance fun bindAppFilter(appFilter: AppFilter): Builder

            @BindsInstance fun bindIconCache(iconCache: IconCache): Builder

            @BindsInstance fun bindAllAppsList(list: AllAppsList): Builder

            override fun build(): TestComponent
        }
    }
}
