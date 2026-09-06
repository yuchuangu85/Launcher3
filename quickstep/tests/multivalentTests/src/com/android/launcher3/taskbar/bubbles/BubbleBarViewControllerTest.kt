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
import androidx.test.filters.SmallTest
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.Flags.FLAG_FIX_BUBBLE_NOTIFICATION_SHOWING_IN_LOCK_SCREEN
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_BUBBLE_BAR, FLAG_FIX_BUBBLE_NOTIFICATION_SHOWING_IN_LOCK_SCREEN)
class BubbleBarViewControllerTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val controller by
        taskbarUnitTestRule.delegate { it.bubbleControllers.orElseThrow().bubbleBarViewController }

    @Test
    fun setHiddenForSysui_true_hidesContainer() {
        runOnTaskbarUiThreadSync {
            controller.setHiddenForBubbles(false)
            controller.setHiddenForSysui(true)
        }

        assertThat(controller.isBubbleBarVisible).isFalse()
        assertThat(controller.isBubbleBarContainerVisible).isFalse()
    }

    @Test
    fun setHiddenForSysui_false_showsContainer() {
        runOnTaskbarUiThreadSync {
            controller.setHiddenForBubbles(false)
            controller.setHiddenForSysui(true)

            controller.setHiddenForSysui(false)
        }

        assertThat(controller.isBubbleBarVisible).isTrue()
        assertThat(controller.isBubbleBarContainerVisible).isTrue()
    }

    @Test
    fun setHiddenForBubbles_true_showsContainer() {
        runOnTaskbarUiThreadSync {
            controller.setHiddenForBubbles(false)
            controller.setHiddenForBubbles(true)

            // Advance time to allow the dismiss animation to complete
            animatorTestRule.advanceTimeBy(
                BubbleBarViewController.TASKBAR_FADE_OUT_DURATION_MS + 100L
            )
        }

        assertThat(controller.isBubbleBarVisible).isFalse()
        assertThat(controller.isBubbleBarContainerVisible).isTrue()
    }

    @Test
    fun setHiddenForStashed_true_hidesBubbleBar() {
        runOnTaskbarUiThreadSync {
            controller.setHiddenForBubbles(false)
            controller.setHiddenForSysui(false)

            controller.setHiddenForStashed(true)
            // Advance time to allow the dismiss animation to complete
            animatorTestRule.advanceTimeBy(
                BubbleBarViewController.TASKBAR_FADE_OUT_DURATION_MS + 100L
            )
        }

        assertThat(controller.isBubbleBarVisible).isFalse()
        assertThat(controller.isBubbleBarContainerVisible).isTrue()
    }

    @Test
    fun setHiddenForBubbles_false_showsContainer() {
        runOnTaskbarUiThreadSync {
            // GIVEN the bubble bar is hidden because there are no bubbles
            controller.setHiddenForBubbles(false)
            controller.setHiddenForBubbles(true)
            animatorTestRule.advanceTimeBy(
                BubbleBarViewController.TASKBAR_FADE_OUT_DURATION_MS + 100L
            )

            // WHEN bubbles are added (i.e. it's no longer hidden for no bubbles)
            controller.setHiddenForBubbles(false)
        }

        // THEN the bubble bar and container are visible
        assertThat(controller.isBubbleBarVisible).isTrue()
        assertThat(controller.isBubbleBarContainerVisible).isTrue()
    }

    @Test
    fun setHiddenForStashed_false_showsBubbleBar() {
        runOnTaskbarUiThreadSync {
            controller.setHiddenForBubbles(false)
            controller.setHiddenForSysui(false)

            // GIVEN the bubble bar is hidden for being stashed
            controller.setHiddenForStashed(true)
            animatorTestRule.advanceTimeBy(
                BubbleBarViewController.TASKBAR_FADE_OUT_DURATION_MS + 100L
            )

            // WHEN it's no longer hidden for being stashed
            controller.setHiddenForStashed(false)
        }

        // THEN the bubble bar is visible
        assertThat(controller.isBubbleBarVisible).isTrue()
        assertThat(controller.isBubbleBarContainerVisible).isTrue()
    }

    @Test
    fun setHiddenForSysuiAndBubbles_unhidingForSysui_showsContainer() {
        runOnTaskbarUiThreadSync {
            // GIVEN bubble bar is hidden for sysui and for no bubbles
            controller.setHiddenForBubbles(false) // to make sure next call is not a no-op
            controller.setHiddenForSysui(true)
            controller.setHiddenForBubbles(true)
            animatorTestRule.advanceTimeBy(
                BubbleBarViewController.TASKBAR_FADE_OUT_DURATION_MS + 100L
            )

            // WHEN we un-hide for sysui
            controller.setHiddenForSysui(false)
        }

        // THEN it remains hidden because there are no bubbles
        assertThat(controller.isBubbleBarVisible).isFalse()
        assertThat(controller.isBubbleBarContainerVisible).isTrue()
    }

    @Test
    fun setHiddenForSysuiAndBubbles_unhidingForBubbles_keepsContainerHidden() {
        runOnTaskbarUiThreadSync {
            // GIVEN bubble bar is hidden for sysui and for no bubbles
            controller.setHiddenForBubbles(false) // to make sure next call is not a no-op
            controller.setHiddenForSysui(true)
            controller.setHiddenForBubbles(true)
            animatorTestRule.advanceTimeBy(
                BubbleBarViewController.TASKBAR_FADE_OUT_DURATION_MS + 100L
            )

            // WHEN we un-hide for no bubbles
            controller.setHiddenForBubbles(false)
        }

        // THEN it remains hidden because of sysui
        assertThat(controller.isBubbleBarVisible).isFalse()
        assertThat(controller.isBubbleBarContainerVisible).isFalse()
    }

    @Test
    fun setHiddenForStashedAndSysui_unhidingForStashed_keepsContainerHidden() {
        runOnTaskbarUiThreadSync {
            controller.setHiddenForBubbles(false)
            controller.setHiddenForSysui(false)

            // GIVEN bubble bar is hidden for being stashed and for sysui
            controller.setHiddenForStashed(true)
            controller.setHiddenForSysui(true)
            animatorTestRule.advanceTimeBy(
                BubbleBarViewController.TASKBAR_FADE_OUT_DURATION_MS + 100L
            )

            // WHEN it's no longer hidden for being stashed
            controller.setHiddenForStashed(false)
        }

        // THEN it remains hidden because of sysui
        assertThat(controller.isBubbleBarVisible).isFalse()
        assertThat(controller.isBubbleBarContainerVisible).isFalse()
    }
}
