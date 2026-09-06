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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.testutil.rule.LazyInitRule.Companion.lazyRule
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.quickstep.SystemUiProxy
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAVIGATION_BAR_DISABLED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_GOING_AWAY
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskbarManagerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val mockUsers = lazyRule { MockUsersRule(context.base) }
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val mockerUserRule: MockUsersRule by mockUsers

    private val taskbarManager by taskbarUnitTestRule::taskbarManager
    private val activityContext by taskbarUnitTestRule::activityContext

    @Test
    fun addDisplay_externalActivityContextInitialized() {
        val displayId = context.virtualDisplayRule.add()
        val activityContext = checkNotNull(taskbarManager.getTaskbarForDisplay(displayId))

        assertThat(activityContext.displayId).isEqualTo(displayId)
        // Allow drag layer to attach before checking.
        runOnTaskbarUiThreadSync {
            assertThat(activityContext.dragLayer.isAttachedToWindow).isTrue()
        }
    }

    @Test
    fun removeDisplay_externalActivityContextDestroyed() {
        val displayId = context.virtualDisplayRule.add()
        val activityContext = checkNotNull(taskbarManager.getTaskbarForDisplay(displayId))
        context.virtualDisplayRule.remove(displayId)

        assertThat(taskbarManager.getTaskbarForDisplay(displayId)).isNull()
        assertThat(activityContext.dragLayer.isAttachedToWindow).isFalse()
        assertThat(activityContext.isDestroyed).isTrue()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun userLocked_primaryDisplay_hasTaskbarInDirectBootSandbox() {
        assertThat(activityContext.applicationContext)
            .isInstanceOf(TaskbarBootAppContext::class.java)
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun userLocked_externalDisplay_missingTaskbarInDirectBootStage() {
        val displayId = context.virtualDisplayRule.add()
        assertThat(taskbarManager.getTaskbarForDisplay(displayId)).isNull()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun onUserUnlocked_directBootStage_taskbarRecreatedOutsideBootAppContext() {
        mockerUserRule.unlockMainUser()
        TestUtil.runOnExecutorSync(getTaskbarUiThread()) {}

        assertThat(activityContext.applicationContext).isInstanceOf(SandboxApplication::class.java)
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun onUserUnlocked_directBootStage_connectedDisplay_taskbarRecreatedOutsideBootAppContext() {
        val displayId = context.virtualDisplayRule.add()
        mockerUserRule.unlockMainUser()
        TestUtil.runOnExecutorSync(getTaskbarUiThread()) {}

        val application =
            checkNotNull(taskbarManager.getTaskbarForDisplay(displayId)).applicationContext
        assertThat(application).isInstanceOf(SandboxApplication::class.java)
    }

    @Test
    fun toggleAllAppsSearch_deviceLocked_allAppsNotOpened() {
        SystemUiProxy.INSTANCE[context].focusState.focusedDisplayId = context.displayId
        runOnTaskbarUiThreadSync {
            taskbarManager.onSystemUiFlagsChanged(
                SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                context.displayId,
            )
            taskbarManager.toggleAllAppsSearch()
        }
        assertThat(activityContext.controllers.taskbarAllAppsController.isOpen).isFalse()
    }

    @Test
    fun toggleAllAppsSearch_deviceUnlocked_allAppsOpened() {
        SystemUiProxy.INSTANCE[context].focusState.focusedDisplayId = context.displayId
        runOnTaskbarUiThreadSync {
            taskbarManager.onSystemUiFlagsChanged(
                SYSUI_STATE_STATUS_BAR_KEYGUARD_GOING_AWAY,
                context.displayId,
            )
            taskbarManager.toggleAllAppsSearch()
        }
        assertThat(activityContext.controllers.taskbarAllAppsController.isOpen).isTrue()
    }

    @Test
    fun onSystemUiFlagsChanged_navBarDisabled_taskbarDestroyed() {
        assertThat(taskbarManager.currentActivityContext).isNotNull()

        runOnTaskbarUiThreadSync {
            taskbarManager.onSystemUiFlagsChanged(
                SYSUI_STATE_NAVIGATION_BAR_DISABLED,
                context.displayId,
            )
        }

        assertThat(taskbarManager.currentActivityContext).isNull()
    }

    @Test
    fun onSystemUiFlagsChanged_navBarEnabled_taskbarCreated() {
        runOnTaskbarUiThreadSync {
            // Start with taskbar disabled
            taskbarManager.onSystemUiFlagsChanged(
                SYSUI_STATE_NAVIGATION_BAR_DISABLED,
                context.displayId,
            )
        }
        assertThat(taskbarManager.currentActivityContext).isNull()

        runOnTaskbarUiThreadSync {
            // Then enable it
            taskbarManager.onSystemUiFlagsChanged(0, context.displayId)
        }

        assertThat(taskbarManager.currentActivityContext).isNotNull()
    }

    @Test
    fun onSystemUiFlagsChanged_connectedDisplay_navBarDisabled_taskbarDestroyed() {
        val displayId = context.virtualDisplayRule.add()
        assertThat(taskbarManager.getTaskbarForDisplay(displayId)).isNotNull()

        runOnTaskbarUiThreadSync {
            taskbarManager.onSystemUiFlagsChanged(SYSUI_STATE_NAVIGATION_BAR_DISABLED, displayId)
        }

        assertThat(taskbarManager.getTaskbarForDisplay(displayId)).isNull()
    }

    @Test
    fun onSystemUiFlagsChanged_connectedDisplay_navBarEnabled_taskbarCreated() {
        val displayId = context.virtualDisplayRule.add()
        runOnTaskbarUiThreadSync {
            // Start with taskbar disabled
            taskbarManager.onSystemUiFlagsChanged(SYSUI_STATE_NAVIGATION_BAR_DISABLED, displayId)
        }
        assertThat(taskbarManager.getTaskbarForDisplay(displayId)).isNull()

        runOnTaskbarUiThreadSync {
            // Then enable it
            taskbarManager.onSystemUiFlagsChanged(0, displayId)
        }

        assertThat(taskbarManager.getTaskbarForDisplay(displayId)).isNotNull()
    }

    @Test
    fun recreateTaskbar_forExternalDisplay_recreatesDeviceProfile() {
        // Create an external display, which creates an initial DeviceProfile.
        val displayId = context.virtualDisplayRule.add()
        val initialActivityContext = checkNotNull(taskbarManager.getTaskbarForDisplay(displayId))
        val initialDeviceProfile = initialActivityContext.deviceProfile

        // Get the PerDisplayTaskbarResource for the external display.
        val perDisplayResource =
            checkNotNull(taskbarManager.getPerDisplayResourceForTest(displayId)) {
                "PerDisplayTaskbarResource not found for displayId: $displayId"
            }

        // Recreate the taskbar for the external display.
        runOnTaskbarUiThreadSync {
            taskbarManager.recreateTaskbarForDisplay(
                perDisplayResource,
                0 /* duration */,
                "recreateTaskbar_forExternalDisplay_recreatesDeviceProfile",
            )
        }

        // Verify that the DeviceProfile was recreated.
        val newActivityContext = checkNotNull(taskbarManager.getTaskbarForDisplay(displayId))
        val newDeviceProfile = newActivityContext.deviceProfile
        assertThat(newActivityContext).isNotSameInstanceAs(initialActivityContext)
        assertThat(newDeviceProfile).isNotSameInstanceAs(initialDeviceProfile)
    }
}
