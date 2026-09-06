/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.RetryingExecutor.Companion.BACKOFF_DELAYS_MS
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RetryingExecutorTest {

    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock lateinit var task: (Int) -> Boolean

    private lateinit var handler: Handler
    private val postedMessages = mutableListOf<Pair<Message, Long>>()

    private lateinit var executor: RetryingExecutor

    @Before
    fun setup() {
        handler = spy(Handler(Looper.getMainLooper()))
        doAnswer {
                postedMessages.add(it.getArgument<Message>(0) to it.getArgument(1))
                true
            }
            .whenever(handler)
            .sendMessageDelayed(any(), any())
        executor = RetryingExecutor(handler)
    }

    @Test
    fun executes_calls_actual_task() {
        doReturn(true).whenever(task).invoke(any())
        executor.execute(task)
        verify(task, times(1)).invoke(0)
    }

    @Test
    fun execute_keeps_retrying() {
        doReturn(false).whenever(task).invoke(any())
        executor.execute(task)
        verify(task, times(1)).invoke(0)

        // Verify scheduled task to add again
        for (i in 1..BACKOFF_DELAYS_MS.size) {
            assertThat(postedMessages).hasSize(i)

            val msg = postedMessages[i - 1]
            assertThat(msg.second).isEqualTo(BACKOFF_DELAYS_MS[i - 1])
            msg.first.callback.run()
            verify(task, times(i + 1)).invoke(any())
        }
    }

    @Test
    fun cancel_cancels_pending_task() {
        doReturn(false).whenever(task).invoke(any())

        executor.execute(task)
        verify(task, times(1)).invoke(0)

        executor.cancel()
        verify(handler, atLeastOnce()).removeCallbacksAndMessages(any())
    }
}
