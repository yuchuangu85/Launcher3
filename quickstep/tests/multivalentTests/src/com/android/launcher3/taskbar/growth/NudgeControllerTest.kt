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

package com.android.launcher3.taskbar.growth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NudgeControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 1) val taskbarModeRule = TaskbarModeRule(context)

    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val nudgeController by taskbarUnitTestRule.delegate { it.nudgeController }

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val wasInTestHarness = Utilities.isRunningInTestHarness()

    @Before
    fun disableRunningInTestHarnessForTests() {
        Utilities.disableRunningInTestHarnessForTests()
    }

    @After
    fun maybeEnableRunningInTestHarnessForTests() {
        if (wasInTestHarness) {
            Utilities.enableRunningInTestHarnessForTests()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testShow_doesShowNudge() {
        runOnTaskbarUiThreadSync { showNudge() } // Then show it
        assertThat(nudgeController.isNudgeOpen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testHide_whenNudgeIsOpen_shouldCloseNudge() {
        assertThat(nudgeController.isNudgeOpen).isFalse()
        runOnTaskbarUiThreadSync { showNudge() }
        assertThat(nudgeController.isNudgeOpen).isTrue()
        runOnTaskbarUiThreadSync { nudgeController.hide() }
        assertThat(nudgeController.isNudgeOpen).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testShow_whenTaskbarIsTransient_shouldNotShowNudge() {
        assertThat(nudgeController.isNudgeOpen).isFalse()
        runOnTaskbarUiThreadSync { nudgeController.init(taskbarContext.controllers) }
        assertThat(nudgeController.isNudgeOpen).isFalse()
    }

    private fun showNudge() {
        val nudgePayload =
            NudgePayload(
                titleText = "Nudge title",
                bodyText = "Nudge body text.",
                image = Image.ResourceId(R.drawable.ic_apps),
                primaryButton =
                    ButtonPayload(
                        label = "Get perk",
                        actions = listOf(Action.Dismiss(), Action.OpenUrl("https://www.google.com")),
                    ),
                secondaryButton =
                    ButtonPayload(label = "Dismiss", actions = listOf(Action.Dismiss())),
            )
        runOnTaskbarUiThreadSync { nudgeController.maybeShow(nudgePayload) }
    }
}
