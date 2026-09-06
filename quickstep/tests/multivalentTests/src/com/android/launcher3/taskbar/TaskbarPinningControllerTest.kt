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

package com.android.launcher3.taskbar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_PINNING_POPUP
import com.android.launcher3.R
import com.android.launcher3.popup.ArrowPopup.OPEN_DURATION_U
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.customization.TaskbarDividerContainer
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskbarPinningControllerTest {
    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(context)
    @get:Rule(order = 2) val animatorTestRule = TaskbarAnimatorTestRule(this)

    private val pinningController by taskbarUnitTestRule.delegate { it.taskbarPinningController }

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private lateinit var taskbarView: TaskbarView
    private lateinit var dividerIcon: TaskbarDividerContainer

    @Before
    fun setup() {
        taskbarContext.controllers.uiController.init(taskbarContext.controllers)
        runOnTaskbarUiThreadSync {
            taskbarView = taskbarContext.dragLayer.findViewById(R.id.taskbar_view)
        }

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
            dividerIcon = requireNotNull(taskbarView.taskbarDividerViewContainer)
        }
    }

    @Test
    fun showPinningView() {
        assertThat(hasPinningPopUp).isFalse()
        runOnTaskbarUiThreadSync { pinningController.showPinningView(dividerIcon) }
        runOnTaskbarUiThreadSync {
            // Animation has started. Advance to end of animation.
            animatorTestRule.advanceTimeBy(OPEN_DURATION_U.toLong())
        }
        assertThat(hasPinningPopUp).isTrue()
    }

    private val hasPinningPopUp: Boolean
        get() {
            return AbstractFloatingView.hasOpenView(taskbarContext, TYPE_TASKBAR_PINNING_POPUP)
        }
}
