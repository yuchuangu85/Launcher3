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

package com.android.launcher3.taskbar.allapps

import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.dot.DotInfo
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.ModelTestExtensions.preloadAppList
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskbarAllAppsControllerTest {

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(context)
    @get:Rule(order = 3) val animatorTestRule = TaskbarAnimatorTestRule(this)

    private val allAppsController by taskbarUnitTestRule.delegate { it.taskbarAllAppsController }
    private val overlayController by taskbarUnitTestRule.delegate { it.taskbarOverlayController }

    @Test
    fun testToggle_once_showsAllApps() {
        runOnTaskbarUiThreadSync { allAppsController.toggle() }
        assertThat(allAppsController.isOpen).isTrue()
    }

    @Test
    fun testToggle_twice_closesAllApps() {
        runOnTaskbarUiThreadSync {
            allAppsController.toggle()
            allAppsController.toggle()
        }
        assertThat(allAppsController.isOpen).isFalse()
    }

    @Test
    fun testToggle_taskbarRecreated_allAppsReopened() {
        runOnTaskbarUiThreadSync { allAppsController.toggle() }
        taskbarUnitTestRule.recreateTaskbar()
        assertThat(allAppsController.isOpen).isTrue()
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_BIND_MODEL_USING_REPOSITORY)
    fun testSetApps_beforeOpened_cachesInfo() {
        val overlayContext =
            TestUtil.getOnTaskbarUiThread {
                allAppsController.setApps(TEST_APPS, 0, emptyMap())
                allAppsController.toggle()
                overlayController.requestWindow()
            }

        assertThat(overlayContext.appsView.appsStore.apps).isEqualTo(TEST_APPS)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_BIND_MODEL_USING_REPOSITORY)
    fun testSetApps_afterOpened_updatesStore() {
        val overlayContext =
            TestUtil.getOnTaskbarUiThread {
                allAppsController.toggle()
                allAppsController.setApps(TEST_APPS, 0, emptyMap())
                overlayController.requestWindow()
            }

        assertThat(overlayContext.appsView.appsStore.apps).isEqualTo(TEST_APPS)
    }

    @Test
    fun testSetPredictedApps_beforeOpened_cachesInfo() {
        val predictedApps =
            TestUtil.getOnTaskbarUiThread {
                allAppsController.setPredictedApps(TEST_PREDICTED_APPS)
                allAppsController.toggle()

                overlayController
                    .requestWindow()
                    .appsView
                    .floatingHeaderView
                    .findFixedRowByType(PredictionRowView::class.java)
                    .predictedApps
            }

        assertThat(predictedApps).isEqualTo(TEST_PREDICTED_APPS)
    }

    @Test
    fun testSetPredictedApps_afterOpened_cachesInfo() {
        val predictedApps =
            TestUtil.getOnTaskbarUiThread {
                allAppsController.toggle()
                allAppsController.setPredictedApps(TEST_PREDICTED_APPS)

                overlayController
                    .requestWindow()
                    .appsView
                    .floatingHeaderView
                    .findFixedRowByType(PredictionRowView::class.java)
                    .predictedApps
            }

        assertThat(predictedApps).isEqualTo(TEST_PREDICTED_APPS)
    }

    @Test
    fun testUpdateNotificationDots_appInfo_hasDot() {
        if (LauncherModel.useModelRepositoryBinding()) {
            context.preloadAppList(TEST_APPS)
        }
        runOnTaskbarUiThreadSync {
            allAppsController.setApps(TEST_APPS, 0, emptyMap())
            allAppsController.toggle()
            val key = PackageUserKey.fromItemInfo(TEST_APPS[0])!!
            context.appComponent.notificationRepository.dispatchUpdate(
                mapOf(
                    key to
                        DotInfo().apply { addOrUpdateNotificationKey(NotificationKeyData("key")) }
                )
            ) {
                it == key
            }
        }

        // Ensure the recycler view fully inflates before trying to grab an icon.
        val btv =
            TestUtil.getOnTaskbarUiThread {
                overlayController
                    .requestWindow()
                    .appsView
                    .activeRecyclerView
                    .findViewHolderForAdapterPosition(0)
                    ?.itemView as? BubbleTextView
            }
        assertThat(btv?.hasDot()).isTrue()
    }

    @Test
    fun testUpdateNotificationDots_predictedApp_hasDot() {
        runOnTaskbarUiThreadSync {
            allAppsController.setPredictedApps(TEST_PREDICTED_APPS)
            allAppsController.toggle()
            val key = PackageUserKey.fromItemInfo(TEST_PREDICTED_APPS[0])!!
            context.appComponent.notificationRepository.dispatchUpdate(
                mapOf(
                    key to
                        DotInfo().apply { addOrUpdateNotificationKey(NotificationKeyData("key")) }
                )
            ) {
                it == key
            }
        }

        val btv =
            TestUtil.getOnTaskbarUiThread {
                overlayController
                    .requestWindow()
                    .appsView
                    .floatingHeaderView
                    .findFixedRowByType(PredictionRowView::class.java)
                    .getChildAt(0) as BubbleTextView
            }
        assertThat(btv.hasDot()).isTrue()
    }

    @Test
    fun testToggleSearch_searchEditTextFocused() {
        runOnTaskbarUiThreadSync { allAppsController.toggleSearch() }
        runOnTaskbarUiThreadSync {
            // All Apps is now attached to window. Open animation is posted but not started.
        }

        runOnTaskbarUiThreadSync {
            // Animation has started. Advance to end of animation.
            animatorTestRule.advanceTimeBy(overlayController.openDuration.toLong())
        }
        val hasFocus =
            TestUtil.getOnTaskbarUiThread {
                overlayController.requestWindow().appsView.searchUiManager.editText?.hasFocus()
            }
        assertThat(hasFocus).isTrue()
    }

    companion object {
        val TEST_APPS =
            Array(16) {
                AppInfo(
                    ComponentName(
                        getInstrumentation().context,
                        "com.android.launcher3.tests.Activity$it",
                    ),
                    "Test App $it",
                    Process.myUserHandle(),
                    Intent(),
                )
            }

        val TEST_PREDICTED_APPS = TEST_APPS.take(4).map { WorkspaceItemInfo(it) }
    }
}
