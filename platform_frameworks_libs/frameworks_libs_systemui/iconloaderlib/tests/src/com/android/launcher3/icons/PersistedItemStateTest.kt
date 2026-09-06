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

package com.android.launcher3.icons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.icons.PersistedItemState.Companion.toPersistedItemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistedItemStateTest {

    @Test
    fun copy_updates_existing_values() {
        val state = PersistedItemState().copy(locale = "l", sdk = "s", theme = "t", isCircle = "c")
        assertEquals("l;;s;;t;;c", state.toString())

        val state2 = state.copy(sdk = "dk")
        assertEquals("l;;dk;;t;;c", state2.toString())
    }

    @Test
    fun same_state_are_equal() {
        val state1 = PersistedItemState().copy(locale = "l", sdk = "s", theme = "t", isCircle = "c")

        assertEquals(
            PersistedItemState().copy(locale = "l", sdk = "s", theme = "t", isCircle = "c"),
            state1,
        )

        assertNotEquals(state1.copy(locale = "l1"), state1)
        assertEquals(state1.copy(locale = "l1").copy(locale = "l"), state1)
    }

    @Test
    fun additional_params_are_preserved_during_copy() {
        val state1 = PersistedItemState().withAdditionalValues("a1", "a2")
        assertEquals(";;;;;;;;a1;;a2", state1.toString())

        val state2 = state1.copy(sdk = "s")
        assertEquals(";;s;;;;;;a1;;a2", state2.toString())
    }

    @Test
    fun addition_params_are_matched_during_equals() {
        val state1 = PersistedItemState().copy(locale = "l")
        val state2 = state1.withAdditionalValues("a1", "a2")

        assertNotEquals(state1, state2)
        assertNotEquals(state1.withAdditionalValues("a2", "a2"), state2)
        assertNotEquals(state1.copy(locale = "l2").withAdditionalValues("a1", "a2"), state2)
        assertEquals(state1.withAdditionalValues("a1", "a2"), state2)
    }

    @Test
    fun toPersistedItemState_correctly_parses() {
        val state = PersistedItemState().copy(locale = "l", sdk = "s", theme = "t", isCircle = "c")
        assertEquals(state, state.toString().toPersistedItemState())
    }

    @Test
    fun toPersistedItemState_ignores_wrong_string() {
        assertNull("l;;s;;g".toPersistedItemState())
        assertNotNull("l;;s;;g;;g".toPersistedItemState())
    }
}
