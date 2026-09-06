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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ListenableRefsTest {

    private val source1 = MutableListenableRef("A")
    private val source2 = MutableListenableRef(1)

    // Direct Executor for testing
    private val directExecutor = Executor { command -> command.run() }

    private var lastCombinedValue: String? = null
    private val observer = { value: String -> lastCombinedValue = value }

    private var closeable: SafeCloseable? = null

    private lateinit var combinedRef: ListenableRef<String>

    @Before
    fun setup() {
        // Reset sources to initial state for each test
        source1.dispatchValue("A")
        source2.dispatchValue(1)

        // Combine sources by concatenating their string representations
        combinedRef = ListenableRefs.combine(source1, source2) { s, n -> s + n }
        // Attach listener
        closeable = combinedRef.forEach(directExecutor, observer)
    }

    @After
    fun tearDown() {
        closeable?.close()
    }

    @Test
    fun combine_initialValue() {
        assertThat(combinedRef.value).isEqualTo("A1")
        assertThat(lastCombinedValue).isEqualTo("A1")
    }

    @Test
    fun combine_source1Update_updatesCombinedValue() {
        source1.dispatchValue("B")

        assertThat(combinedRef.value).isEqualTo("B1")
        assertThat(lastCombinedValue).isEqualTo("B1")
    }

    @Test
    fun combine_source2Update_updatesCombinedValue() {
        source2.dispatchValue(2)

        assertThat(combinedRef.value).isEqualTo("A2")
        assertThat(lastCombinedValue).isEqualTo("A2")
    }

    @Test
    fun combine_bothSourcesUpdate_updatesCombinedValue() {
        source1.dispatchValue("C")
        source2.dispatchValue(3)

        assertThat(combinedRef.value).isEqualTo("C3")
        assertThat(lastCombinedValue).isEqualTo("C3")
    }

    @Test
    fun combine_multipleUpdates_usesLatestValues() {
        val queuingExecutor = mutableListOf<Runnable>()
        closeable?.close()
        var observed: String? = null
        // Create a new combinedRef for this test to use the queuingExecutor
        val newCombined = ListenableRefs.combine(source1, source2) { s, n -> s + n }
        // Use a queuing executor to control execution
        closeable = newCombined.forEach(queuingExecutor::add) { observed = it }
        // Drain the queue to process initial setup calls.
        while (queuingExecutor.isNotEmpty()) {
            queuingExecutor.removeAt(0).run()
        }
        assertThat(observed).isEqualTo("A1")
        assertThat(queuingExecutor).isEmpty()

        // Dispatch multiple changes before running any more queued items
        source1.dispatchValue("X")
        source2.dispatchValue(9)
        source1.dispatchValue("Y")

        // Each dispatch on the sources schedules a call to updater.schedule()
        assertThat(queuingExecutor).hasSize(3)
        // Run all queued updates
        while (queuingExecutor.isNotEmpty()) {
            queuingExecutor.removeAt(0).run()
        }
        assertThat(newCombined.value).isEqualTo("Y9")
        assertThat(observed).isEqualTo("Y9")
    }

    @Test
    fun combine_listenerRemoved_stopsUpdates() {
        source1.dispatchValue("B")
        assertThat(lastCombinedValue).isEqualTo("B1")

        closeable?.close()
        lastCombinedValue = "ListenerClosed" // Mark that the listener shouldn't update this

        source1.dispatchValue("C")
        source2.dispatchValue(2)

        // lastCombinedValue should not change because the listener is closed
        assertThat(lastCombinedValue).isEqualTo("ListenerClosed")
        // combinedRef's value should update because it's an on-demand getter.
        assertThat(combinedRef.value).isEqualTo("C2")
    }
}
