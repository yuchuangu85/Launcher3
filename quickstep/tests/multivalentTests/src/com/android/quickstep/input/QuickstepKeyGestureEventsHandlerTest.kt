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

package com.android.quickstep.input

import android.Manifest.permission.MANAGE_KEY_GESTURES
import android.app.PendingIntent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_COMPLETE
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_START
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.testutil.rule.LazyInitRule.Companion.lazyRule
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.TestUtil
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.dagger.SysUIConnectionComponent
import com.android.quickstep.sysuiconnection.SysUIConnectionTracker
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class QuickstepKeyGestureEventsHandlerTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val contextSpy = lazyRule { spy(SandboxApplication()) }

    private val context: SandboxApplication by contextSpy
    private lateinit var inputManager: InputManager

    private val keyGestureEventsCaptor: KArgumentCaptor<List<Int>> = argumentCaptor()

    private val sysUIConnectionTracker = SysUIConnectionTracker()

    @Mock lateinit var settingsCache: SettingsCache
    @Mock lateinit var allAppsPendingIntent: PendingIntent
    @Mock lateinit var overviewCommandHelper: OverviewCommandHelper
    @Mock(answer = RETURNS_DEEP_STUBS) lateinit var sysUIComponent: SysUIConnectionComponent

    private var userSetupComplete = true

    private val keyGestureEventsManager: QuickstepKeyGestureEventsManager by lazy {
        QuickstepKeyGestureEventsManager(
            context = context,
            sysUIConnectionTracker = sysUIConnectionTracker,
            uiExecutor = MAIN_EXECUTOR,
            lifecycle = context.appComponent.daggerSingletonTracker,
            settingsCache = settingsCache,
        )
    }

    @Before
    fun setup() {
        doReturn(PERMISSION_GRANTED).whenever(context).checkSelfPermission(eq(MANAGE_KEY_GESTURES))
        inputManager =
            context.spyService(InputManager::class.java).stub {
                doNothing().whenever(it).registerKeyGestureEventHandler(any(), any())
                doNothing().whenever(it).unregisterKeyGestureEventHandler(any())
            }

        doAnswer { userSetupComplete }.whenever(settingsCache).getValue(any())
        val nullableHelper = sysUIComponent.overviewCommandHelper
        doReturn(overviewCommandHelper).whenever(nullableHelper).getIfReady()
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerAllAppsHandler_flagEnabled_registerWithExpectedKeyGestureEvents() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.allAppsKeyGestureHelper),
            )
        assertThat(keyGestureEventsCaptor.firstValue).containsExactly(KEY_GESTURE_TYPE_ALL_APPS)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerAllAppsHandlerTwice_flagEnabled_registerWithExpectedKeyGestureEventsOnce() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.allAppsKeyGestureHelper),
            )
        assertThat(keyGestureEventsCaptor.firstValue).containsExactly(KEY_GESTURE_TYPE_ALL_APPS)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerAllAppsHandler_flagDisabled_noRegister() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerAllAppsHandler_noPermission_noRegister() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerOverviewHandler_flagEnabled_registerWithExpectedKeyGestureEvents() {
        // Initialize the event manager
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        keyGestureEventsManager.overviewKeyGestureHelper
        context.appComponent.sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.overviewKeyGestureHelper),
            )
        assertThat(keyGestureEventsCaptor.firstValue)
            .containsExactly(
                KEY_GESTURE_TYPE_RECENT_APPS,
                KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER,
                KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY,
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerOverviewHandler_flagDisabled_noRegister() {
        // Initialize the event manager
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        keyGestureEventsManager.overviewKeyGestureHelper
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerOverviewHandler_noPermission_unregisterHandler() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)

        // Initialize the event manager
        keyGestureEventsManager.overviewKeyGestureHelper
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterAllAppsHandler_flagEnabled_unregisterHandler() {
        keyGestureEventsManager.unregisterAllAppsKeyGestureEvent()
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verify(inputManager)
            .unregisterKeyGestureEventHandler(eq(keyGestureEventsManager.allAppsKeyGestureHelper))
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterAllAppsHandler_flagDisabled_noUnregister() {
        keyGestureEventsManager.unregisterAllAppsKeyGestureEvent()
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterAllAppsHandler_noPermission_noUnregister() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

        keyGestureEventsManager.unregisterAllAppsKeyGestureEvent()
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterOverviewHandler_flagEnabled_unregisterHandler() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        keyGestureEventsManager.onDestroy()
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verify(inputManager)
            .unregisterKeyGestureEventHandler(eq(keyGestureEventsManager.overviewKeyGestureHelper))
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterOverviewHandler_flagDisabled_noUnregister() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        keyGestureEventsManager.onDestroy()
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleAllAppsEvent_flagEnabled_toggleAllAppsSearch() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.allAppsKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verify(allAppsPendingIntent).send()
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleAllAppsEvent_flagEnabled_userSetupIncomplete_noInteractionWithTaskbar() {
        userSetupComplete = false
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.allAppsKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(allAppsPendingIntent)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleAllAppsEvent_flagDisabled_noInteractionWithTaskbar() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.allAppsKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(allAppsPendingIntent)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleAllAppsEvent_noPermission_noInteractionWithTaskbar() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.allAppsKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(allAppsPendingIntent)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsEvent_flagEnabled_showOverviewWithUndefinedType() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )
        verify(sysUIComponent.binder).onOverviewShown(triggeredFromAltTab = false)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsEvent_userSetupIncomplete_noOverviewEventInFake() {
        userSetupComplete = false
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsEvent_flagDisabled_noOverviewEventInFake() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )
        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsEvent_noPermission_noOverviewEventInFake() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )
        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherStartEvent_flagEnabled_showOverviewWithAltTabType() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_START)
                .build(),
            /* focusedToken= */ null,
        )
        verify(sysUIComponent.binder).onOverviewShown(triggeredFromAltTab = true)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherStartEvent_userSetupIncomplete_noOverviewEventInFake() {
        userSetupComplete = false
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_START)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherStartEvent_flagDisabled_noOverviewEventInFake() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_START)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherStartEvent_noPermission_noOverviewEventInFake() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_START)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_flagEnabled_hideOverviewWithAltTabType() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        verify(sysUIComponent.binder)
            .onOverviewHidden(triggeredFromAltTab = true, triggeredFromHomeKey = false)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_userSetupIncomplete_noOverviewEventInFake() {
        userSetupComplete = false
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_flagDisabled_noOverviewEventInFake() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_noPermission_noOverviewEventInFake() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(sysUIComponent.binder)
    }

    @Test
    fun handleHomeEvent_addHomeCommand() {
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
                .build(),
            /* focusedToken= */ null,
        )

        verify(overviewCommandHelper)
            .addCommand(OverviewCommandHelper.CommandType.HOME, TEST_DISPLAY_ID)
    }

    @Test
    fun handleHomeEvent_userSetupIncomplete_noInteractionWithOverviewCommandHelper() {
        userSetupComplete = false
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(overviewCommandHelper)
    }

    @Test
    fun handleHomeEvent_noPermission_noInteractionWithOverviewCommandHelper() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(overviewCommandHelper)
    }

    @Test
    fun handleHomeEvent_wrongEventType_noInteractionWithOverviewCommandHelper() {
        // Use a different key gesture type
        sysUIConnectionTracker.setActiveComponent(sysUIComponent)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        keyGestureEventsManager.overviewKeyGestureHelper.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(overviewCommandHelper)
    }

    private companion object {
        const val TEST_DISPLAY_ID = 6789
    }
}
