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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.Flags.FLAG_FIX_TASKBAR_SCRIM_VIEW_ON_HOME
import com.android.wm.shell.shared.bubbles.BubbleConstants.BUBBLE_BAR_EXPANDED_SCRIM_ALPHA
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskbarScrimViewControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1)
    val context =
        TaskbarWindowSandboxContext.create(
            params =
                SandboxParams {
                    doAnswer { backPressed = true }
                        .whenever(it.systemUiProxy)
                        .onBackEvent(anyOrNull(), any())
                }
        )

    @get:Rule(order = 2) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 3) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 4) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val scrimViewController by
        taskbarUnitTestRule.delegate { it.taskbarScrimViewController }

    // Default animation duration.
    private val animationDuration: Long
        get() = context.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()

    private var backPressed = false

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibleChanged_onlyTaskbarVisible_noScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            scrimViewController.updateStateForSysuiFlags(0, true)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarVisibleWithBubblesExpanded_showsScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            animatorTestRule.advanceTimeBy(animationDuration)
        }

        assertThat(scrimViewController.scrimAlpha).isEqualTo(BUBBLE_BAR_EXPANDED_SCRIM_ALPHA)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_BUBBLE_BAR, FLAG_FIX_TASKBAR_SCRIM_VIEW_ON_HOME)
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarHiddenDuringScrim_hidesScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(BUBBLE_BAR_EXPANDED_SCRIM_ALPHA)

        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(GONE)
            animatorTestRule.advanceTimeBy(animationDuration)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @DisableFlags(FLAG_FIX_TASKBAR_SCRIM_VIEW_ON_HOME)
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarOnHomeHiddenDuringScrim_hidesScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            taskbarUnitTestRule.activityContext.bubbleControllers!!
                .bubbleStashController
                .launcherState = BubbleStashController.BubbleLauncherState.HOME
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(BUBBLE_BAR_EXPANDED_SCRIM_ALPHA)

        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(GONE)
            animatorTestRule.advanceTimeBy(animationDuration)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR, FLAG_FIX_TASKBAR_SCRIM_VIEW_ON_HOME)
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarHiddenDuringScrimFixFlagOn_hidesScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(BUBBLE_BAR_EXPANDED_SCRIM_ALPHA)

        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(GONE)
            animatorTestRule.advanceTimeBy(animationDuration)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR, FLAG_FIX_TASKBAR_SCRIM_VIEW_ON_HOME)
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarOnHomeWithBubblesExpanded_noScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            taskbarUnitTestRule.activityContext.bubbleControllers!!
                .bubbleStashController
                .launcherState = BubbleStashController.BubbleLauncherState.HOME
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_notificationsOverPinnedTaskbarAndBubbles_noScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.updateStateForSysuiFlags(
                SYSUI_STATE_BUBBLES_EXPANDED or SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                true,
            )
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarWithBubbleMenu_darkerScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            scrimViewController.updateStateForSysuiFlags(
                SYSUI_STATE_BUBBLES_EXPANDED or SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED,
                true,
            )
        }
        assertThat(scrimViewController.scrimAlpha).isGreaterThan(BUBBLE_BAR_EXPANDED_SCRIM_ALPHA)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChangedMultipleTimes_pinnedTaskbarWithBubbleMenu_darkerScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            // show scrim immediately
            scrimViewController.updateStateForSysuiFlags(
                SYSUI_STATE_BUBBLES_EXPANDED or SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED,
                true,
            )
            // hide scrim with animation
            scrimViewController.updateStateForSysuiFlags(0, false)
            // show scrim immediately again
            scrimViewController.updateStateForSysuiFlags(
                SYSUI_STATE_BUBBLES_EXPANDED or SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED,
                true,
            )
            // wait for animation completion
            animatorTestRule.advanceTimeBy(animationDuration)
        }
        assertThat(scrimViewController.scrimAlpha).isGreaterThan(BUBBLE_BAR_EXPANDED_SCRIM_ALPHA)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testOnTaskbarVisibilityChanged_stashedTaskbarWithBubbles_noScrim() {
        runOnTaskbarUiThreadSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnClick_scrimShown_performsSystemBack() {
        runOnTaskbarUiThreadSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimView.isClickable).isTrue()

        runOnTaskbarUiThreadSync { scrimViewController.scrimView.performClick() }
        assertThat(backPressed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testOnClick_scrimHidden_notClickable() {
        runOnTaskbarUiThreadSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimView.isClickable).isFalse()
    }
}
