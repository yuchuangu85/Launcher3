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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Unit tests for [ThreadSafeRunnableList] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class ThreadSafeRunnableListTest {

    private val actions = Array(5) { mock<Runnable>() }
    private val executor1 = ListExecutor()
    private val executor2 = ListExecutor()

    private val list = ThreadSafeRunnableList()

    @Test
    fun addTask_adds_on_multiple_executors() {
        list.addTask(executor1, actions[0])
        list.addTask(executor2, actions[1])
        list.addTask(executor2, actions[2])
        list.addTask(executor1, actions[3])
        list.addTask(executor1, actions[4])

        assertThat(executor1).isEmpty()
        assertThat(executor2).isEmpty()

        list.complete()
        assertThat(executor1).containsExactly(actions[0], actions[3], actions[4]).inOrder()
        assertThat(executor2).containsExactly(actions[1], actions[2]).inOrder()
    }

    @Test
    fun removeTask() {
        list.addTask(executor1, actions[0])
        list.addTask(executor2, actions[1])
        list.addTask(executor2, actions[2])
        list.addTask(executor1, actions[3])
        list.addTask(executor1, actions[4])
        list.removeTask(actions[3])

        list.complete()
        assertThat(executor1).containsExactly(actions[0], actions[4]).inOrder()
        assertThat(executor2).containsExactly(actions[1], actions[2]).inOrder()
    }

    @Test
    fun multiple_complete_is_no_op() {
        list.addTask(executor1, actions[0])
        list.addTask(executor1, actions[1])
        list.addTask(executor1, actions[2])

        list.complete()
        assertThat(executor1).containsExactly(actions[0], actions[1], actions[2]).inOrder()

        list.complete()
        assertThat(executor1).containsExactly(actions[0], actions[1], actions[2]).inOrder()
    }

    @Test
    fun addTask_after_complete() {
        list.addTask(executor1, actions[0])
        assertThat(executor1).isEmpty()
        list.complete()

        assertThat(executor1).containsExactly(actions[0]).inOrder()

        list.addTask(executor1, actions[1])
        assertThat(executor1).containsExactly(actions[0], actions[1]).inOrder()
    }

    private class ListExecutor : ArrayList<Runnable>(), Executor {

        override fun execute(r: Runnable) {
            add(r)
        }
    }
}
