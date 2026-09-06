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

package com.android.quickstep

import android.hardware.input.InputManager
import android.provider.Settings
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.taskbar.TaskbarManagerImpl
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCacheSandbox
import com.android.launcher3.util.TestUtil
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.whenever

private const val TIMEOUT = 5L
private val USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(USER_SETUP_COMPLETE)

@RunWith(AndroidJUnit4::class)
@UiThreadTest
@MutatedComponent(target = LauncherAppComponent::class)
class AllAppsActionManagerTest {
    private val callbackSemaphore = Semaphore(0)
    private val bgExecutor = UI_HELPER_EXECUTOR

    @get:Rule val mockto = MockitoJUnit.rule()
    @get:Rule val context = SandboxApplication()
    private val inputManager = context.spyService(InputManager::class.java)

    private val settingsCacheSandbox =
        SettingsCacheSandbox().also { it[USER_SETUP_COMPLETE_URI] = 1 }
    private val quickstepKeyGestureEventsManager by
        lazy(LazyThreadSafetyMode.NONE) {
            spy(context.appComponent.quickstepKeyGestureEventsManager)
        }

    @Mock lateinit var allAppsIntentSenderProvider: TaskbarManagerImpl.AllAppsIntentSender

    private val allAppsActionManager by
        lazy(LazyThreadSafetyMode.NONE) {
            AllAppsActionManager(context, bgExecutor, quickstepKeyGestureEventsManager) {
                callbackSemaphore.release()
                allAppsIntentSenderProvider
            }
        }

    @BindValue
    val settingsCache: SettingsCache
        get() = settingsCacheSandbox.cache

    @Before
    fun initDaggerGraphAndWaitForSettingUpdate() {
        context.initDaggerComponent(mutatedComponentBuilder())

        doNothing().whenever(inputManager).registerKeyGestureEventHandler(any(), any())
        doNothing().whenever(inputManager).unregisterKeyGestureEventHandler(any())

        // Trigger any property access to initialize allAppsActionManager
        allAppsActionManager.isActionRegistered
        // Wait for SettingCache update isUserSetupComplete on bgExecutor.
        bgExecutor.submit<Any?> { null }.get()
    }

    @Before fun unlockUser() = allAppsActionManager.onUserUnlocked()

    @After fun destroyManager() = allAppsActionManager.onDestroy()

    @Test
    fun taskbarPresent_actionRegistered() {
        allAppsActionManager.isTaskbarPresent = true
        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to register.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
        verify(quickstepKeyGestureEventsManager).registerAllAppsKeyGestureEvent(any())
    }

    @Test
    fun homeAndOverviewSame_actionRegistered() {
        allAppsActionManager.isHomeAndOverviewSame = true
        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to register.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
        verify(quickstepKeyGestureEventsManager).registerAllAppsKeyGestureEvent(any())
    }

    @Test
    fun toggleTaskbar_destroyedAfterActionRegistered_actionUnregistered() {
        allAppsActionManager.isTaskbarPresent = true
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()

        allAppsActionManager.isTaskbarPresent = false
        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to unregister.
        assertThat(allAppsActionManager.isActionRegistered).isFalse()
        verify(quickstepKeyGestureEventsManager).unregisterAllAppsKeyGestureEvent()
    }

    @Test
    fun toggleTaskbar_destroyedBeforeActionRegistered_pendingActionUnregistered() {
        allAppsActionManager.isTaskbarPresent = true
        allAppsActionManager.isTaskbarPresent = false

        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to unregister.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isFalse()
        verify(quickstepKeyGestureEventsManager).unregisterAllAppsKeyGestureEvent()
    }

    @Test
    fun changeHome_sameAsOverviewBeforeActionUnregistered_actionRegisteredAgain() {
        allAppsActionManager.isHomeAndOverviewSame = true // Initialize to same.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()

        allAppsActionManager.isHomeAndOverviewSame = false
        allAppsActionManager.isHomeAndOverviewSame = true
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
    }

    @Test
    fun taskbarPresent_userSetupIncomplete_actionUnregistered() {
        settingsCacheSandbox[USER_SETUP_COMPLETE_URI] = 0
        TestUtil.runOnExecutorSync(bgExecutor) {}
        allAppsActionManager.isTaskbarPresent = true
        assertThat(allAppsActionManager.isActionRegistered).isFalse()
    }

    @Test
    fun taskbarPresent_setupUiVisible_actionUnregistered() {
        allAppsActionManager.isSetupUiVisible = true
        allAppsActionManager.isTaskbarPresent = true
        assertThat(allAppsActionManager.isActionRegistered).isFalse()
    }

    @Test
    fun taskbarPresent_userSetupCompleted_actionRegistered() {
        settingsCacheSandbox[USER_SETUP_COMPLETE_URI] = 0
        allAppsActionManager.isTaskbarPresent = true
        TestUtil.runOnExecutorSync(bgExecutor) {}
        reset(quickstepKeyGestureEventsManager)

        settingsCacheSandbox[USER_SETUP_COMPLETE_URI] = 1
        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to register.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
        verify(quickstepKeyGestureEventsManager).registerAllAppsKeyGestureEvent(any())
    }

    @Test
    fun taskbarPresent_setupUiDismissed_actionRegistered() {
        allAppsActionManager.isSetupUiVisible = true
        allAppsActionManager.isTaskbarPresent = true
        TestUtil.runOnExecutorSync(bgExecutor) {}
        reset(quickstepKeyGestureEventsManager)

        allAppsActionManager.isSetupUiVisible = false
        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to register.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
        verify(quickstepKeyGestureEventsManager).registerAllAppsKeyGestureEvent(any())
    }

    @Test
    fun onDestroy_shouldUnregisterAllAppsKeyGestureHandler() {
        allAppsActionManager.onDestroy()

        verify(quickstepKeyGestureEventsManager).unregisterAllAppsKeyGestureEvent()
    }
}
