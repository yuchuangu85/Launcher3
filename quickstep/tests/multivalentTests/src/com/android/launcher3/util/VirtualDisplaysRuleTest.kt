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

import android.content.Context
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.app.displaylib.DisplayDecorationListener
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class VirtualDisplaysRuleTest {

    @get:Rule val limitDeviceRule = LimitDevicesRule()

    private val virtualDisplaysRule = VirtualDisplaysRule()

    private val decorationListener =
        mock<DisplayDecorationListener> {
            virtualDisplaysRule.registerDisplayDecorationListener(mock)
        }

    @After
    fun cleanup() {
        virtualDisplaysRule.cleanup()
    }

    @Test
    fun testAdd_decorationChangeObserved() {
        val displayId = virtualDisplaysRule.add()
        verify(decorationListener, times(1)).onDisplayAddSystemDecorations(displayId)
    }

    @Test
    fun testRemove_decorationChangeObserved() {
        val displayId = virtualDisplaysRule.add()
        virtualDisplaysRule.remove(displayId)
        verify(decorationListener, times(1)).onDisplayRemoveSystemDecorations(displayId)
    }

    @Test
    fun testRemove_displayReleased() {
        val displayId = virtualDisplaysRule.add()
        val display = checkNotNull(virtualDisplaysRule[displayId])
        virtualDisplaysRule.remove(displayId)
        assertThat(display.token).isNull()
    }

    @Test
    fun testRemove_displayAlreadyRemoved_exceptionThrown() {
        val display = virtualDisplaysRule.add()
        virtualDisplaysRule.remove(display)

        assertThrows(IllegalArgumentException::class.java) { virtualDisplaysRule.remove(display) }
    }

    @Test
    fun get_displayAdded_notNull() {
        val display = virtualDisplaysRule[virtualDisplaysRule.add()]
        assertThat(display).isNotNull()
    }

    @Test
    fun get_displayRemoved_isNull() {
        val displayId = virtualDisplaysRule.add()
        virtualDisplaysRule.remove(displayId)
        assertThat(virtualDisplaysRule[displayId]).isNull()
    }

    @Test
    fun testTeardown_multipleDisplays_areReleased() {
        val display1 = checkNotNull(virtualDisplaysRule[virtualDisplaysRule.add()])
        val display2 = checkNotNull(virtualDisplaysRule[virtualDisplaysRule.add()])

        virtualDisplaysRule.cleanup()

        assertThat(display1.token).isNull()
        assertThat(display2.token).isNull()
    }

    @SkipOnDeviceless
    @Test
    fun virtualDisplays_shouldShowSystemDecors_isFalse() {
        val wm =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(WindowManager::class.java)!!
        val displayId = virtualDisplaysRule.add()
        assertThat(wm.shouldShowSystemDecors(displayId)).isFalse()
    }
}
