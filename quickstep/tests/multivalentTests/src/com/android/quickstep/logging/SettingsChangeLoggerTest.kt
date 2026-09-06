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

package com.android.quickstep.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.logging.InstanceId
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALL_APPS_SUGGESTIONS_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DOT_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_DISABLED
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.TestStabilityRule
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability
import com.android.launcher3.util.rule.TestStabilityRule.LOCAL
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.capture
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@MutatedComponent(target = LauncherAppComponent::class, installModules = [FakePrefsModule::class])
class SettingsChangeLoggerTest {

    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val context = SandboxApplication()
    @get:Rule val testStabilityRule = TestStabilityRule()

    private val mInstanceId = InstanceId.fakeInstanceId(1)

    @BindValue
    @Mock(answer = Answers.RETURNS_SELF)
    lateinit var mMockLogger: StatsLogManager.StatsLogger

    @Captor private lateinit var mEventCaptor: ArgumentCaptor<StatsLogManager.EventEnum>

    @Before
    fun setUp() {
        context.initDaggerComponent(mutatedComponentBuilder())

        // To match the default value of THEMED_ICONS
        ThemeManager.INSTANCE.get(context).isMonoThemeEnabled = false
    }

    @Test
    fun loggingPrefs_correctDefaultValue() {
        val systemUnderTest = SettingsChangeLogger.INSTANCE.get(context)

        assertThat(systemUnderTest.loggingPrefs[ADD_ICON_PREFERENCE_KEY]!!.defaultValue).isTrue()
        assertThat(systemUnderTest.loggingPrefs[OVERVIEW_SUGGESTED_ACTIONS]!!.defaultValue).isTrue()
        assertThat(systemUnderTest.loggingPrefs[KEY_ENABLE_MINUS_ONE]!!.defaultValue).isTrue()
    }

    @Test
    @DesktopStability(flavors = LOCAL, bug = 486280752)
    fun logSnapshot_defaultValue() {
        SettingsChangeLogger.INSTANCE.get(context).apply {
            // Wait for all the states in SettingsChangeLogger to get initialized
            TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
            logSnapshot(mInstanceId)
        }

        verify(mMockLogger, atLeastOnce()).log(capture(mEventCaptor))
        val capturedEvents = mEventCaptor.allValues
        assertThat(capturedEvents.isNotEmpty()).isTrue()
        verifyDefaultEvent(capturedEvents)
    }

    private fun verifyDefaultEvent(capturedEvents: MutableList<StatsLogManager.EventEnum>) {
        assertThat(capturedEvents.any { it.id == LAUNCHER_NOTIFICATION_DOT_ENABLED.id }).isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_THEMED_ICON_DISABLED.id }).isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_ALL_APPS_SUGGESTIONS_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_GOOGLE_APP_SWIPE_LEFT_ENABLED }).isTrue()
    }

    companion object {
        private const val KEY_ENABLE_MINUS_ONE = "pref_enable_minus_one"
        private const val OVERVIEW_SUGGESTED_ACTIONS = "pref_overview_action_suggestions"

        private const val LAUNCHER_GOOGLE_APP_SWIPE_LEFT_ENABLED = 617
    }
}
