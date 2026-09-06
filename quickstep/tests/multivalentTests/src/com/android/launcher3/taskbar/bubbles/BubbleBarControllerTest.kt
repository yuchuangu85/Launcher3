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

package com.android.launcher3.taskbar.bubbles

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.Flags.FLAG_FIX_BUBBLE_BAR_STASHING_WITH_HARDWARE_KEYBOARD
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
class BubbleBarControllerTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val bubbleBarController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.orElseThrow().bubbleBarController }
    private val bubbleBarViewController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.orElseThrow().bubbleBarViewController }

    private val activityContext by taskbarUnitTestRule::activityContext

    @Test
    fun testUpdateStateForSysuiFlags_imeVisibleAndDocked_doesStash() {
        activityContext.setImeDockedOverrideForTest(true)

        runOnTaskbarUiThreadSync {
            bubbleBarController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE)
        }

        assertThat(bubbleBarViewController.isHiddenForSysui).isTrue()
    }

    @Test
    @EnableFlags(FLAG_FIX_BUBBLE_BAR_STASHING_WITH_HARDWARE_KEYBOARD)
    fun testUpdateStateForSysuiFlags_imeVisibleAndNotDocked_withFlagEnabled_doesNotStash() {
        activityContext.setImeDockedOverrideForTest(false)

        runOnTaskbarUiThreadSync {
            bubbleBarController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE)
        }

        assertThat(bubbleBarViewController.isHiddenForSysui).isFalse()
    }

    @Test
    fun testUpdateStateForSysuiFlags_imeHidden_doesNotStash() {
        activityContext.setImeDockedOverrideForTest(false)

        runOnTaskbarUiThreadSync { bubbleBarController.updateStateForSysuiFlags(0) }

        assertThat(bubbleBarViewController.isHiddenForSysui).isFalse()
    }

    @Test
    @EnableFlags(FLAG_FIX_BUBBLE_BAR_STASHING_WITH_HARDWARE_KEYBOARD)
    fun testOnImeInsetChanged_imeDocked_doesStash() {
        activityContext.setImeDockedOverrideForTest(true)

        runOnTaskbarUiThreadSync { bubbleBarController.onImeInsetChanged() }

        assertThat(bubbleBarViewController.isHiddenForSysui).isTrue()
    }

    @Test
    @EnableFlags(FLAG_FIX_BUBBLE_BAR_STASHING_WITH_HARDWARE_KEYBOARD)
    fun testOnImeInsetChanged_imeNotDocked_doesNotStash() {
        activityContext.setImeDockedOverrideForTest(false)

        runOnTaskbarUiThreadSync { bubbleBarController.onImeInsetChanged() }

        assertThat(bubbleBarViewController.isHiddenForSysui).isFalse()
    }
}
