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
 * limitations under the License
 */

package com.android.launcher3.taskbar.navbutton

import android.content.res.Configuration
import android.content.res.Resources
import android.platform.test.flag.junit.SetFlagsRule
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import com.android.launcher3.taskbar.navbutton.AbstractNavButtonLayoutter.Companion.NAVBAR_KEY_ORDER_URI
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

open class NavButtonLayoutterTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()

    val configuration = mock<Configuration>()
    val resources = mock<Resources> { on { configuration } doReturn configuration }
    val navButtonContainer = mock<LinearLayout> { on { context } doReturn context }
    val endContextualContainer = mock<ViewGroup> { on { context } doReturn context }
    val startContextualContainer = mock<ViewGroup> { on { context } doReturn context }
    val imeSwitcher = mock<ImageView>()
    val a11yButton = mock<ImageView>()
    val moreOptionsButton = mock<ImageView>()
    val space = mock<Space>()
    val backButton = mock<ImageView>()
    val homeButton = mock<ImageView>()
    val recentsButton = mock<ImageView>()

    fun configureButtonOrder(flipSetting: Boolean, rtlLocale: Boolean) {
        context.settingsCacheSandbox[NAVBAR_KEY_ORDER_URI] = if (flipSetting) 1 else 0
        whenever(configuration.layoutDirection)
            .thenReturn(if (rtlLocale) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
    }

    fun assertButtonOrder(isFlipOrderExpected: Boolean) {
        if (isFlipOrderExpected) {
            assertButtonOrder(recentsButton, homeButton, backButton)
        } else {
            assertButtonOrder(backButton, homeButton, recentsButton)
        }
    }

    fun assertButtonOrder(vararg buttons: ImageView) {
        val captor = argumentCaptor<ImageView>()
        verify(navButtonContainer, times(3)).addView(captor.capture())

        assertThat(captor.allValues).containsExactlyElementsIn(buttons).inOrder()
    }
}
