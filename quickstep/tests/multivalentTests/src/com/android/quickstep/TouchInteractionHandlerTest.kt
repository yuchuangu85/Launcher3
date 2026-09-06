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

package com.android.quickstep

import android.hardware.input.InputManager
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.LockedUserState
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.ThreadSafeRunnableList
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TouchInteractionHandlerTest {

    @get:Rule(order = 1) val app = SandboxApplication()
    @get:Rule(order = 2) val mockUser = MockUsersRule(app)

    @Before
    fun setup() {
        // Block methods which can conflict with an existing taskbar
        app.spyServiceForChildren<WindowManager>().stub { mock ->
            doNothing().whenever(mock).addView(any(), any())
            doNothing().whenever(mock).removeView(any())
            doNothing().whenever(mock).removeViewImmediate(any())
        }
        app.spyServiceForChildren<InputManager>().stub { mock ->
            doNothing().whenever(mock).registerKeyGestureEventHandler(any(), any())
            doNothing().whenever(mock).unregisterKeyGestureEventHandler(any())
        }
    }

    @Test
    fun handler_create_when_device_unlocked() {
        // Verify that the create and destroy happens without any crash
        val cleanupTask = ThreadSafeRunnableList()

        val connComponent =
            app.appComponent.sysUIConnectionComponentBuilder
                .setConnectionCleaner(cleanupTask)
                .build()

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val unused = connComponent.touchInteractionHandler
        }
        flushAllThreads()
        cleanupTask.complete()
        flushAllThreads()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun handler_created_when_device_locked() {
        // Verify that the create and destroy happens without any crash.
        // An invalid shared-pref access will be caught by SandboxApplication
        assertThat(LockedUserState.get(app).isUserUnlocked).isFalse()
        val cleanupTask = ThreadSafeRunnableList()

        val connComponent =
            app.appComponent.sysUIConnectionComponentBuilder
                .setConnectionCleaner(cleanupTask)
                .build()

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val unused = connComponent.touchInteractionHandler
        }
        flushAllThreads()
        cleanupTask.complete()
        flushAllThreads()
    }

    private fun flushAllThreads() {
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) {}
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        TestUtil.runOnExecutorSync(getTaskbarUiThread()) {}
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
    }
}
