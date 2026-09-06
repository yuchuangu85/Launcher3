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

import androidx.test.filters.SmallTest
import com.android.launcher3.taskbar.TaskbarActivityContext
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class AbstractNavButtonLayoutterTest(private val order: AbstractButtonOrder) :
    NavButtonLayoutterTest() {

    @Test
    fun addThreeButtons_expectedOrder() {
        val layoutter = TestLayoutter()

        configureButtonOrder(order.flipSetting, order.rtlLocale)
        layoutter.addThreeButtons()

        assertButtonOrder(order.isFlipOrderExpected)
    }

    inner class TestLayoutter :
        AbstractNavButtonLayoutter(
            resources,
            navButtonContainer,
            endContextualContainer,
            startContextualContainer,
            imeSwitcher,
            a11yButton,
            moreOptionsButton,
            space,
            backButton,
            homeButton,
            recentsButton,
        ) {

        override fun layoutButtons(
            context: TaskbarActivityContext,
            isA11yButtonPersistent: Boolean,
        ) {}
    }

    companion object {
        enum class AbstractButtonOrder(
            val flipSetting: Boolean,
            val rtlLocale: Boolean,
            val isFlipOrderExpected: Boolean,
        ) {
            NO_FLIP_LTR(flipSetting = false, rtlLocale = false, isFlipOrderExpected = false),
            NO_FLIP_RTL(flipSetting = false, rtlLocale = true, isFlipOrderExpected = false),
            FLIP_LTR(flipSetting = true, rtlLocale = false, isFlipOrderExpected = true),
            FLIP_RTL(flipSetting = true, rtlLocale = true, isFlipOrderExpected = true),
        }

        @JvmStatic
        @Parameters
        fun testParameters(): List<AbstractButtonOrder> = AbstractButtonOrder.entries
    }
}
