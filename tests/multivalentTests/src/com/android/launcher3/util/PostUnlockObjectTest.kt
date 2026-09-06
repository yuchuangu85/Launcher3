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

package com.android.launcher3.util

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import java.util.concurrent.Executor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Unit tests for [PostUnlockObject] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PostUnlockObjectTest {

    @get:Rule(order = 1) val context = SandboxApplication()
    @get:Rule(order = 2) val mockUser = MockUsersRule(context)

    private val lazyObject: Lazy<Intent> = Lazy { Intent() }
    private val target: PostUnlockObject<Intent> by lazy {
        PostUnlockObject(lazyObject, LockedUserState.get(context))
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun getIfReady_is_null_when_locked() {
        assertThat(target.getIfReady()).isNull()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = true)
    fun getIfReady_is_non_null_when_unlocked() {
        assertThat(target.getIfReady()).isNotNull()
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = true)
    fun whenAvailable_called_immediately_if_unlocked() {
        val cleanup = mock<Runnable>()
        val executor = ListExecutor()
        var taskRun = false
        target.whenAvailable(executor) { intent ->
            assertThat(intent).isNotNull()
            taskRun = true
            cleanup
        }

        // Task immediately executed on executor
        assertThat(executor).hasSize(1)
        executor[0].run()
        assertThat(taskRun).isTrue()
        assertThat(executor).hasSize(1)

        // Closing the target, executes the cleanup
        target.close()
        assertThat(executor).hasSize(2)
        assertThat(executor[1]).isEqualTo(cleanup)
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun whenAvailable_called_after_user_is_unlocked() {
        val cleanup = mock<Runnable>()
        val executor = ListExecutor()
        var taskRun = false
        target.whenAvailable(executor) { intent ->
            assertThat(intent).isNotNull()
            taskRun = true
            cleanup
        }
        assertThat(executor).isEmpty()
        assertThat(taskRun).isFalse()

        LockedUserState.get(context)
            .userUnlockedReceiver
            .onReceive(context, Intent(Intent.ACTION_USER_UNLOCKED))
        // Task executed after user is unlocked
        assertThat(executor).hasSize(1)
        executor[0].run()
        assertThat(taskRun).isTrue()
        assertThat(executor).hasSize(1)

        // Closing the target, executes the cleanup
        target.close()
        assertThat(executor).hasSize(2)
        assertThat(executor[1]).isEqualTo(cleanup)
    }

    @Test
    @MockUser(userType = UserType.MAIN, isUserUnlocked = false)
    fun whenAvailable_not_called_if_closed_before_unlock() {
        val cleanup = mock<Runnable>()
        val executor = ListExecutor()
        var taskRun = false
        target.whenAvailable(executor) { intent ->
            assertThat(intent).isNotNull()
            taskRun = true
            cleanup
        }
        assertThat(executor).isEmpty()
        assertThat(taskRun).isFalse()

        target.close()
        LockedUserState.get(context)
            .userUnlockedReceiver
            .onReceive(context, Intent(Intent.ACTION_USER_UNLOCKED))

        // No task was executed
        assertThat(executor).isEmpty()
        assertThat(taskRun).isFalse()
    }

    private class ListExecutor : ArrayList<Runnable>(), Executor {

        override fun execute(r: Runnable) {
            add(r)
        }
    }
}
