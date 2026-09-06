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

package com.android.launcher3.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import com.android.launcher3.LauncherFiles.SHARED_PREFERENCES_KEY
import com.android.launcher3.LauncherPrefs.Companion.BOOT_AWARE_PREFS_KEY
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class SandboxApplicationTest {
    @get:Rule(order = 1) val app = SandboxApplication()
    @get:Rule(order = 2) val mockUser = MockUsersRule(app)

    private val display: Display
        get() {
            return checkNotNull(app.getSystemService(DisplayManager::class.java))
                .getDisplay(DEFAULT_DISPLAY)
        }

    @Test
    fun testCreateDisplayContext_isSandboxed() {
        val displayContext = app.createDisplayContext(display)
        assertThat(displayContext.applicationContext).isEqualTo(app)
    }

    @Test
    fun testCreateWindowContext_fromSandboxedDisplayContext_isSandboxed() {
        val displayContext = app.createDisplayContext(display)
        val nestedContext = displayContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null)
        assertThat(nestedContext.applicationContext).isEqualTo(app)
    }

    @Test
    fun testGetApplicationContext_beforeManualInit_throwsException() {
        val manualApp = SandboxApplication()

        assertThrows(IllegalStateException::class.java) {
            assertThat(manualApp.applicationContext).isEqualTo(manualApp)
        }
    }

    @Test
    fun testGetApplicationContext_afterManualInit_isApplication() {
        SandboxApplication().run {
            init()
            assertThat(applicationContext).isEqualTo(this)
            onDestroy()
        }
    }

    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    @Test
    fun testGetSharedPreferences_userLocked_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            app.createCredentialProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        }
    }

    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    @Test
    fun testGetSharedPreferences_windowContextAndUserLocked_throwsException() {
        val windowContext =
            app.createCredentialProtectedStorageContext()
                .createDisplayContext(display)
                .createWindowContext(TYPE_APPLICATION_OVERLAY, null)

        assertThrows(IllegalStateException::class.java) {
            windowContext.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        }
    }

    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    @Test
    fun testGetSharedPreferences_deviceProtectedStorageContextAndUserLocked_returnsPreferences() {
        val deviceProtectedStorageContext = app.createDeviceProtectedStorageContext()
        val sharedPreferences =
            deviceProtectedStorageContext.getSharedPreferences(
                BOOT_AWARE_PREFS_KEY,
                Context.MODE_PRIVATE,
            )
        assertThat(sharedPreferences).isNotNull()
    }

    @MockUser(userType = UserType.MAIN, isUserUnlocked = true)
    @Test
    fun testGetSharedPreferences_userUnlocked_returnsPreferences() {
        val sharedPreferences =
            app.createCredentialProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        assertThat(sharedPreferences).isNotNull()
    }
}
