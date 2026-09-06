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

package com.android.launcher3.model

import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller.SessionInfo
import android.content.pm.PackageManager.INSTALL_REASON_USER
import android.graphics.Bitmap
import android.os.Process.myUserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.model.ItemInstallQueue.FLAG_ACTIVITY_PAUSED
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.ModelTestExtensions.setEmptyModelLayout
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestActivityContext
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ItemInstallQueueTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val uiContext = TestActivityContext(app)

    val installQueue: ItemInstallQueue
        get() = app.appComponent.itemInstallQueue

    @Test
    fun adds_icon_to_workspace() {
        app.setEmptyModelLayout()
        assertThat(findAllIcons(TEST_PACKAGE)).isEmpty()

        installQueue.queueItem(SerializedItemItem(TEST_PACKAGE, myUserHandle()))
        awaitTaskCompletion()
        assertThat(findAllIcons(TEST_PACKAGE)).hasSize(1)
    }

    @Test
    fun adds_multiple_items_together() {
        setupMockInstallSession()
        app.setEmptyModelLayout()
        assertThat(findAllIcons(TEST_PACKAGE)).isEmpty()
        assertThat(findAllIcons(RANDOM_PKG)).isEmpty()

        installQueue.queueItem(SerializedItemItem(RANDOM_PKG, myUserHandle()))
        installQueue.queueItem(SerializedItemItem(TEST_PACKAGE, myUserHandle()))
        awaitTaskCompletion()

        assertThat(findAllIcons(TEST_PACKAGE)).hasSize(1)
        assertThat(findAllIcons(RANDOM_PKG)).hasSize(1)
    }

    @Test
    fun ignores_multiple_app_requests() {
        app.setEmptyModelLayout()
        assertThat(findAllIcons(TEST_PACKAGE)).isEmpty()

        installQueue.queueItem(SerializedItemItem(TEST_PACKAGE, myUserHandle()))
        installQueue.queueItem(SerializedItemItem(TEST_PACKAGE, myUserHandle()))
        installQueue.queueItem(SerializedItemItem(TEST_PACKAGE, myUserHandle()))
        installQueue.queueItem(SerializedItemItem(TEST_PACKAGE, myUserHandle()))
        awaitTaskCompletion()

        assertThat(findAllIcons(TEST_PACKAGE)).hasSize(1)
    }

    @Test
    fun ignores_existing_items() {
        setupMockInstallSession()
        app.setModelLayout(LauncherLayoutBuilder().atHotseat(0).putApp(TEST_PACKAGE, null))
        assertThat(findAllIcons(RANDOM_PKG)).isEmpty()
        assertThat(findAllIcons(TEST_PACKAGE)).hasSize(1)

        installQueue.queueItem(SerializedItemItem(RANDOM_PKG, myUserHandle()))
        installQueue.queueItem(SerializedItemItem(TEST_PACKAGE, myUserHandle()))
        awaitTaskCompletion()

        assertThat(findAllIcons(TEST_PACKAGE)).hasSize(1)
        assertThat(findAllIcons(RANDOM_PKG)).hasSize(1)
    }

    @Test
    fun ignores_non_existent_app() {
        app.setEmptyModelLayout()
        assertThat(findAllIcons(RANDOM_PKG)).isEmpty()

        installQueue.queueItem(SerializedItemItem(RANDOM_PKG, myUserHandle()))
        awaitTaskCompletion()
        assertThat(findAllIcons(RANDOM_PKG)).isEmpty()
    }

    @Test
    fun adds_pending_icon() {
        setupMockInstallSession()
        app.setEmptyModelLayout()
        assertThat(findAllIcons(RANDOM_PKG)).isEmpty()

        installQueue.queueItem(SerializedItemItem(RANDOM_PKG, myUserHandle()))
        awaitTaskCompletion()
        assertThat(findAllIcons(RANDOM_PKG)).hasSize(1)
        assertThat((findAllIcons(RANDOM_PKG).get(0) as WorkspaceItemInfo).isPromise).isTrue()
    }

    private fun setupMockInstallSession() {
        val sessionInfo =
            mock<SessionInfo> {
                on(it.getAppIcon()).thenReturn(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
                on(it.getAppLabel()).thenReturn("Test")
                on(it.getUser()).thenReturn(myUserHandle())
                on(it.getAppPackageName()).thenReturn(RANDOM_PKG)
                on(it.getInstallReason()).thenReturn(INSTALL_REASON_USER)
                on(it.getInstallerPackageName()).thenReturn(app.packageName)
            }
        val la = app.spyService<LauncherApps>()
        doReturn(listOf(sessionInfo)).whenever(la).allPackageInstallerSessions
    }

    private fun awaitTaskCompletion() {
        installQueue.setIconUISurface(uiContext)
        installQueue.resumeModelPush(FLAG_ACTIVITY_PAUSED)

        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
    }

    private fun findAllIcons(pkg: String) =
        app.appComponent.testableModelState.homeRepo.workspaceState.value.filter {
            it.itemType == ITEM_TYPE_APPLICATION && it.targetPackage == pkg
        }

    companion object {
        const val RANDOM_PKG = "com.android.launcher3..non_existent.app"
    }
}
