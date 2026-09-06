/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/** Unit tests for [LockedUserState] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class LockedUserStateTest {

    @get:Rule(order = 1) val context = SandboxApplication()
    @get:Rule(order = 2) val mockUser = MockUsersRule(context)

    @After
    fun tearDown() {
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) {}
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = true)
    fun runOnUserUnlocked_runs_action_immediately_if_already_unlocked() {
        val action: Runnable = mock()
        LockedUserState.get(context).runOnUserUnlocked(action = action)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        verify(action).run()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun runOnUserUnlocked_waits_to_run_action_until_user_is_unlocked() {
        val action: Runnable = mock()
        LockedUserState.get(context).runOnUserUnlocked(action = action)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        verifyNoMoreInteractions(action)

        LockedUserState.get(context)
            .userUnlockedReceiver
            .onReceive(context, Intent(Intent.ACTION_USER_UNLOCKED))
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        verify(action).run()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = true)
    fun isUserUnlocked_returns_true_when_user_is_unlocked() {
        assertThat(LockedUserState.get(context).isUserUnlocked).isTrue()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun isUserUnlocked_returns_false_when_user_is_locked() {
        assertThat(LockedUserState.get(context).isUserUnlocked).isFalse()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = true)
    fun taskExecutorOnProvidedExecutor_when_user_already_unlocked() {
        val action: Runnable = mock()
        val taskQueue = mutableListOf<Runnable>()
        LockedUserState.get(context).runOnUserUnlocked(executor = taskQueue::add, action = action)

        assertThat(taskQueue).containsExactly(action)
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun taskExecutorOnProvidedExecutor_after_user_is_unlocked() {
        val action: Runnable = mock()
        val taskQueue = mutableListOf<Runnable>()
        LockedUserState.get(context).runOnUserUnlocked(executor = taskQueue::add, action = action)

        assertThat(taskQueue).isEmpty()

        LockedUserState.get(context)
            .userUnlockedReceiver
            .onReceive(context, Intent(Intent.ACTION_USER_UNLOCKED))
        assertThat(taskQueue).containsExactly(action)
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun removed_task_does_not_execute() {
        val action1: Runnable = mock()
        val action2: Runnable = mock()
        val taskQueue = mutableListOf<Runnable>()
        LockedUserState.get(context).runOnUserUnlocked(executor = taskQueue::add, action = action1)
        LockedUserState.get(context).runOnUserUnlocked(executor = taskQueue::add, action = action2)
        assertThat(taskQueue).isEmpty()

        LockedUserState.get(context).removeOnUserUnlockedRunnable(action1)
        LockedUserState.get(context)
            .userUnlockedReceiver
            .onReceive(context, Intent(Intent.ACTION_USER_UNLOCKED))
        assertThat(taskQueue).containsExactly(action2)
    }
}
