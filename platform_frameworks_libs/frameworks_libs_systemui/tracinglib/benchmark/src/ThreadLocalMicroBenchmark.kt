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
package com.android.app.tracing.benchmark

import android.os.Trace
import android.perftests.utils.PerfStatusReporter
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.EnsureDeviceSettingsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ThreadLocalMicroBenchmark {

    @get:Rule val setFlagsRule = SetFlagsRule()

    @get:Rule val ensureDeviceSettingsRule = EnsureDeviceSettingsRule()

    @get:Rule val perfStatusReporter = PerfStatusReporter()

    @Before
    fun before() {
        Assert.assertTrue(Trace.isEnabled())
    }

    @After
    fun after() {
        Assert.assertTrue(Trace.isEnabled())
    }

    @Test
    fun testIntegerIncrement() {
        val state = perfStatusReporter.benchmarkState
        val count: ThreadLocal<Int> = ThreadLocal()
        count.set(0)
        while (state.keepRunning()) {
            count.set(count.get()!! + 1)
        }
    }

    @Test
    fun testAtomicIntegerIncrement() {
        val state = perfStatusReporter.benchmarkState
        val count: ThreadLocal<AtomicInteger> = ThreadLocal()
        count.set(AtomicInteger(0))
        while (state.keepRunning()) {
            count.get()!!.getAndIncrement()
        }
    }

    @Test
    fun testIntArrayIncrement() {
        val state = perfStatusReporter.benchmarkState
        val count: ThreadLocal<Array<Int>> = ThreadLocal()
        count.set(arrayOf(0))
        while (state.keepRunning()) {
            val arr = count.get()!!
            arr[0]++
        }
    }

    @Test
    fun testMutableIntIncrement() {
        val state = perfStatusReporter.benchmarkState
        class MutableInt(var value: Int)
        val count: ThreadLocal<MutableInt> = ThreadLocal()
        count.set(MutableInt(0))
        while (state.keepRunning()) {
            count.get()!!.value++
        }
    }
}
