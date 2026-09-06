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

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.SerializedItemItem
import com.android.launcher3.model.WorkspaceItemSerializer
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.tasks.BrowserIconMigrator.Companion.PREF_MIGRATION_PENDING
import com.android.launcher3.model.testing.FakeModelWriter
import com.android.launcher3.model.testing.WriterAction
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [BrowserIconMigrator] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BrowserIconMigratorTest {

    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock lateinit var evaluator: BrowserMigrationConditionEvaluator

    private val browserPkg = LauncherModelHelper.SETTINGS_PACKAGE
    private val targetPkg = LauncherModelHelper.TEST_PACKAGE

    private val appComponent: TestComponent
        get() = context.appComponent as TestComponent

    private val idp: InvariantDeviceProfile
        get() = appComponent.idp

    private val prefs: LauncherPrefs
        get() = appComponent.prefs

    private lateinit var fakeModelWriter: FakeModelWriter

    @Before
    fun setUp() {
        context.initDaggerComponent(
            DaggerBrowserIconMigratorTest_TestComponent.builder().bindEvaluator(evaluator)
        )
        fakeModelWriter = FakeModelWriter()
    }

    @Test
    fun testMigration_notNeeded_clearsPendingFlag() {
        // Set the pending flag to true
        prefs.put(PREF_MIGRATION_PENDING, true)
        whenever(evaluator.evaluateSourceAndTarget()).thenReturn(null)

        val changes = performMigration(mockEval = false)

        // Verify no changes were made
        assertThat(changes).isEqualTo(0)
        // Verify the migration complete callback was not called
        verify(evaluator, never()).notifyMigrationComplete(false)
        // Verify the pending flag is cleared
        assertThat(prefs.get(PREF_MIGRATION_PENDING)).isFalse()
    }

    @Test
    fun testMigration_targetOnHotseat_browserOnHotseat() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atHotseat(1)
                .putApp(targetPkg, null)
                .atHotseat(0)
                .putApp(browserPkg, null)
        )
        // Set the pending flag to true
        prefs.put(PREF_MIGRATION_PENDING, true)

        val changes = performMigration()
        // Nothing was changed
        assertThat(changes).isEqualTo(0)
        verify(evaluator).notifyMigrationComplete(false)
        // Verify the pending flag is cleared
        assertThat(prefs.get(PREF_MIGRATION_PENDING)).isFalse()
    }

    @Test
    fun testMigration_targetOnHotseat_browserOnWorkspace0() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atHotseat(3)
                .putApp(targetPkg, null)
                .atWorkspace(0, -1, 0)
                .putApp(browserPkg, null)
        )
        // Set the pending flag to true
        prefs.put(PREF_MIGRATION_PENDING, true)

        performMigration()
        verify(evaluator).notifyMigrationComplete(true)

        // Target moved to bottom-right corner
        val targetAppInfo = getUpdatedItem(targetPkg)
        assertThat(targetAppInfo.container).isEqualTo(CONTAINER_DESKTOP)
        assertThat(targetAppInfo.screenId).isEqualTo(0)
        assertThat(targetAppInfo.cellX).isEqualTo(idp.numColumns - 1)
        assertThat(targetAppInfo.cellY).isEqualTo(idp.numRows - 1)

        // Browser moved to hotseat
        val browserInfo = getUpdatedItem(browserPkg)
        assertThat(browserInfo.container).isEqualTo(CONTAINER_HOTSEAT)
        assertThat(browserInfo.screenId).isEqualTo(3)

        assertThat(prefs.get(PREF_MIGRATION_PENDING)).isFalse()
    }

    @Test
    fun testMigration_targetOnHotseat_browserOnWorkspace1() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atHotseat(3)
                .putApp(targetPkg, null)
                .atWorkspace(0, -1, 1)
                .putApp(browserPkg, null)
        )

        val changes = performMigration()
        verify(evaluator).notifyMigrationComplete(true)

        // Target moved to bottom-right corner and browser moved to browser's location
        assertThat(changes).isEqualTo(2)
        val targetAppInfo = getUpdatedItem(targetPkg)
        assertThat(targetAppInfo.container).isEqualTo(CONTAINER_DESKTOP)
        assertThat(targetAppInfo.screenId).isEqualTo(0)
        assertThat(targetAppInfo.cellX).isEqualTo(idp.numColumns - 1)
        assertThat(targetAppInfo.cellY).isEqualTo(idp.numRows - 1)

        val browserInfo = getUpdatedItem(browserPkg)
        assertThat(browserInfo.container).isEqualTo(CONTAINER_HOTSEAT)
        assertThat(browserInfo.screenId).isEqualTo(3)
    }

    @Test
    fun testMigration_targetOnWorkspace1_browserOnWorkspace0() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(0, -1, 1)
                .putApp(targetPkg, null)
                .atWorkspace(1, -1, 0)
                .putApp(browserPkg, null)
        )

        val changes = performMigration()
        // Nothing was changed
        assertThat(changes).isEqualTo(0)
        verify(evaluator).notifyMigrationComplete(false)
    }

    @Test
    fun testMigration_targetOnWorkspace0_browserOnWorkspace0() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(0, -1, 0)
                .putApp(targetPkg, null)
                .atWorkspace(1, -1, 0)
                .putApp(browserPkg, null)
        )

        val changes = performMigration()
        // Nothing was changed
        assertThat(changes).isEqualTo(0)
        verify(evaluator).notifyMigrationComplete(false)
    }

    @Test
    fun testMigration_targetOnWorkspace0_browserOnHotseat() {
        context.setModelLayout(
            LauncherLayoutBuilder()
                .atWorkspace(1, -1, 0)
                .putApp(targetPkg, null)
                .atHotseat(1)
                .putApp(browserPkg, null)
        )

        val changes = performMigration()
        // Nothing was changed
        assertThat(changes).isEqualTo(0)
        verify(evaluator).notifyMigrationComplete(false)
    }

    @Test
    fun testMigration_targetOnWorkspace0_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atWorkspace(0, 1, 0).putApp(targetPkg, null))

        val changes = performMigration()
        verify(evaluator).notifyMigrationComplete(true)

        // Browser is added at target location and target is moved to bottom-left corner
        assertThat(changes).isEqualTo(2)
        val targetAppInfo = getUpdatedItem(targetPkg)
        assertThat(targetAppInfo.container).isEqualTo(CONTAINER_DESKTOP)
        assertThat(targetAppInfo.screenId).isEqualTo(0)
        assertThat(targetAppInfo.cellX).isEqualTo(idp.numColumns - 1)
        assertThat(targetAppInfo.cellY).isEqualTo(idp.numRows - 1)

        val browserInfo = getAddedItem(browserPkg)
        assertThat(browserInfo.container).isEqualTo(CONTAINER_DESKTOP)
        assertThat(browserInfo.screenId).isEqualTo(0)
        assertThat(browserInfo.cellX).isEqualTo(0)
        assertThat(browserInfo.cellY).isEqualTo(1)
    }

    @Test
    fun testMigration_targetOnHotseat_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atHotseat(1).putApp(targetPkg, null))

        val changes = performMigration()
        verify(evaluator).notifyMigrationComplete(true)

        // Browser is added at target location and target is moved to bottom-left corner
        assertThat(changes).isEqualTo(2)

        val targetAppInfo = getUpdatedItem(targetPkg)
        assertThat(targetAppInfo.container).isEqualTo(CONTAINER_DESKTOP)
        assertThat(targetAppInfo.screenId).isEqualTo(0)
        assertThat(targetAppInfo.cellX).isEqualTo(idp.numColumns - 1)
        assertThat(targetAppInfo.cellY).isEqualTo(idp.numRows - 1)

        val browserInfo = getAddedItem(browserPkg)
        assertThat(browserInfo.container).isEqualTo(CONTAINER_HOTSEAT)
        assertThat(browserInfo.screenId).isEqualTo(1)
    }

    @Test
    fun testMigration_targetOnWorkspace1_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atWorkspace(0, 0, 1).putApp(targetPkg, null))

        val changes = performMigration()
        verify(evaluator).notifyMigrationComplete(true)

        // Browser is added at second page, target is unchanged
        assertThat(changes).isEqualTo(1)
        val browserInfo = getAddedItem(browserPkg)
        assertThat(browserInfo.container).isEqualTo(CONTAINER_DESKTOP)
        assertThat(browserInfo.screenId).isEqualTo(1)
    }

    @Test
    fun testMigration_noTarget_noBrowser() {
        context.setModelLayout(LauncherLayoutBuilder().atWorkspace(0, 0, 1).putApp(targetPkg, null))

        val changes = performMigration()
        verify(evaluator).notifyMigrationComplete(true)

        // Browser is added at second page, target is unchanged
        assertThat(changes).isEqualTo(1)
        val browserInfo = getAddedItem(browserPkg)
        assertThat(browserInfo.container).isEqualTo(CONTAINER_DESKTOP)
        assertThat(browserInfo.screenId).isEqualTo(1)
    }

    private fun performMigration(mockEval: Boolean = true): Int {
        if (mockEval) {
            doAnswer {
                    var targetItemInfo: ItemInfo? = null
                    TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
                        targetItemInfo =
                            appComponent.serializer.decode(
                                SerializedItemItem(
                                    packageName = browserPkg,
                                    userHandle = Process.myUserHandle(),
                                )
                            )
                    }

                    Pair(targetPkg, targetItemInfo!!)
                }
                .whenever(evaluator)
                .evaluateSourceAndTarget()
        }

        val itemIdMap = IntSparseArrayMap<ItemInfo>()
        context.appComponent.testableModelState.dataModel.itemsIdMap.forEach {
            itemIdMap[it.id] = it
        }

        appComponent.migratorFactory
            .createBrowserIconMigrator(itemIdMap, fakeModelWriter)
            .performMigration()

        return fakeModelWriter.actions.size
    }

    private fun getUpdatedItem(pkg: String): WorkspaceItemInfo {
        val action =
            fakeModelWriter.actions.filterIsInstance<WriterAction.UpdateItem>().first {
                a: WriterAction.UpdateItem ->
                (a.item as? WorkspaceItemInfo)?.targetPackage == pkg
            }
        return action.item as WorkspaceItemInfo
    }

    private fun getAddedItem(pkg: String): WorkspaceItemInfo {
        val action =
            fakeModelWriter.actions.filterIsInstance<WriterAction.AddItem>().first {
                a: WriterAction.AddItem ->
                (a.item as? WorkspaceItemInfo)?.targetPackage == pkg
            }
        return action.item as WorkspaceItemInfo
    }

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class, FakePrefsModule::class])
    interface TestComponent : LauncherAppComponent {

        val migratorFactory: BrowserIconMigratorFactory
        val serializer: WorkspaceItemSerializer
        val prefs: LauncherPrefs

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindEvaluator(evaluator: BrowserMigrationConditionEvaluator): Builder

            override fun build(): TestComponent
        }
    }
}
