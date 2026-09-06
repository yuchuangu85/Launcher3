/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.TaskbarBackgroundRenderer.Companion.MAX_ROUNDNESS
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskbarDesktopModeControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val taskbarDesktopModeController by
        taskbarUnitTestRule.delegate { it.taskbarDesktopModeController }
    private val taskbarCornerRoundness by taskbarUnitTestRule.delegate { it.taskbarCornerRoundness }

    @Test
    fun whenTaskbarRequiresCornerRoundness_shouldReturnDefaultCornerRoundness() {
        assertThat(taskbarDesktopModeController.getTaskbarCornerRoundness(true))
            .isEqualTo(MAX_ROUNDNESS)
    }

    @Test
    fun whenTaskbarRequiresCornerRoundness_shouldReturnZeroAsCornerRoundness() {
        assertThat(taskbarDesktopModeController.getTaskbarCornerRoundness(false)).isEqualTo(0f)
    }

    @Test
    fun onTaskbarCornerRoundingUpdate_taskbarRoundingRequired_cornerAnimationRoundingStarts() {
        runOnTaskbarUiThreadSync {
            taskbarDesktopModeController.onTaskbarCornerRoundingUpdate(true, context.base.displayId)
        }

        assertTrue(taskbarCornerRoundness.isAnimatingToValue(MAX_ROUNDNESS))
    }

    @Test
    fun onTaskbarCornerRoundingUpdate_taskbarRoundingRequired_differentDisplay_noRounding() {
        runOnTaskbarUiThreadSync {
            taskbarDesktopModeController.onTaskbarCornerRoundingUpdate(
                true,
                Display.INVALID_DISPLAY,
            )
        }

        assertFalse(taskbarCornerRoundness.isAnimating)
    }
}
