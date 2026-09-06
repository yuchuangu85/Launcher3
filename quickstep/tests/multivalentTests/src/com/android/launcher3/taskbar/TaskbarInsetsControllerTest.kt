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

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.LayoutInflater
import android.view.ViewTreeObserver
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.waitForIdleSync
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.DEFAULT_TOUCH_REGION
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.FULLSCREEN_TASKBAR_WINDOW
import com.android.launcher3.taskbar.TaskbarInsetsController.DebugTouchableRegion.Companion.ICONS_INVISIBLE
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP
import com.android.launcher3.taskbar.bubbles.BubbleBarBubble
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.launcher3.taskbar.bubbles.model.BubbleIcon
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE
import com.android.users.UserType
import com.android.wm.shell.Flags
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.BubbleInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskbarInsetsControllerTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 3) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 4) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val taskbarInsetsController by
        taskbarUnitTestRule.delegate { it.taskbarInsetsController }
    private val taskbarStashController by taskbarUnitTestRule.delegate { it.taskbarStashController }

    private val bubbleBarViewController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.get().bubbleBarViewController }
    private val bubbleStashController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.get().bubbleStashController }

    private val taskbarContext by taskbarUnitTestRule::activityContext

    private lateinit var bubbleView: BubbleView
    private lateinit var bubble: BubbleBarBubble
    private val mockController = mock<BubbleView.Controller>()

    @Test
    @TaskbarMode(TRANSIENT)
    fun imeShowing_taskbarWindowUntouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, false)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(ICONS_INVISIBLE)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isTrue()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun imeShowing_transientTaskbarUnstashed_taskbarWindowTouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, true)
            taskbarStashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(taskbarStashController.stashDuration)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(DEFAULT_TOUCH_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isFalse()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun imeShowing_transientTaskbarStashed_taskbarWindowUntouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, true)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(ICONS_INVISIBLE)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isTrue()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun windowFullscreen_entireTaskbarWindowTouchable() {
        runOnTaskbarUiThreadSync { taskbarContext.setTaskbarWindowFullscreen(true, 1) }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(FULLSCREEN_TASKBAR_WINDOW)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME)
        }
    }

    @Test
    fun windowFullscreen_imeShowing_entireTaskbarWindowTouchable() {
        runOnTaskbarUiThreadSync {
            taskbarContext.setTaskbarWindowFullscreen(true, 1)
            taskbarContext.updateSysuiStateFlags(SYSUI_STATE_IME_VISIBLE, false)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableReason)
                .isEqualTo(FULLSCREEN_TASKBAR_WINDOW)
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableInsets)
                .isEqualTo(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME)
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    @EnableFlags(Flags.FLAG_FIX_SWIPE_UP_NOTIFICATION_SHADE_WITH_BUBBLE_BAR)
    fun bubblesExpanded_transient_shadeExpanded_noInsets() {
        assumeTrue(taskbarContext.isGestureNav) // seems on robo tests that TaskbarMode doesn't work
        setupBubbles()
        runOnTaskbarUiThreadSync {
            taskbarStashController.updateAndAnimateTransientTaskbar(false, true)
            bubbleBarViewController.addBubble(bubble, false, true, null)
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleBarViewController.animateExpanded(true)
            taskbarContext.updateSysuiStateFlags(
                QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                false, /* fromInit */
            )
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarContext.isNotificationShadeExpanded).isTrue()
            assertThat(bubbleBarViewController.hasBubbles()).isTrue()
            assertThat(bubbleBarViewController.isBubbleBarVisible).isTrue()
            assertThat(bubbleBarViewController.isExpanded).isTrue()
            // When shade is expanded there shouldn't be any insets
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isTrue()
        }
    }

    @Test
    @TaskbarMode(PINNED)
    @EnableFlags(Flags.FLAG_FIX_SWIPE_UP_NOTIFICATION_SHADE_WITH_BUBBLE_BAR)
    fun bubblesExpanded_pinned_shadeExpanded_noInsets() {
        assumeTrue(taskbarContext.isGestureNav) // seems on robo tests that TaskbarMode doesn't work
        setupBubbles()
        runOnTaskbarUiThreadSync {
            taskbarStashController.updateAndAnimateTransientTaskbar(false, true)
            bubbleBarViewController.addBubble(bubble, false, true, null)
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleBarViewController.animateExpanded(true)
            taskbarContext.updateSysuiStateFlags(
                QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                false, /* fromInit */
            )
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarContext.isNotificationShadeExpanded).isTrue()
            assertThat(bubbleBarViewController.hasBubbles()).isTrue()
            assertThat(bubbleBarViewController.isBubbleBarVisible).isTrue()
            assertThat(bubbleBarViewController.isExpanded).isTrue()
            // When shade is expanded there shouldn't be any insets
            assertThat(taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.isEmpty)
                .isTrue()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_SWIPE_UP_NOTIFICATION_SHADE_WITH_BUBBLE_BAR)
    fun bubblesExpanded_shadeCollapsed_addsInsets() {
        setupBubbles()
        runOnTaskbarUiThreadSync {
            taskbarStashController.updateAndAnimateTransientTaskbar(false, true)
            bubbleBarViewController.addBubble(bubble, false, true, null)
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleBarViewController.animateExpanded(true)
            taskbarContext.updateSysuiStateFlags(
                QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                false, /* fromInit */
            )
            taskbarContext.updateSysuiStateFlags(0, true)
        }
        runOnTaskbarUiThreadSync {
            assertThat(taskbarContext.isNotificationShadeExpanded).isFalse()
            assertThat(bubbleBarViewController.hasBubbles()).isTrue()
            assertThat(bubbleBarViewController.isBubbleBarVisible).isTrue()
            assertThat(bubbleBarViewController.isExpanded).isTrue()
            assertThat(bubbleStashController.isBubbleBarVisible()).isTrue()
            assertThat(
                    taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.contains(
                        bubbleBarViewController.bubbleBarBounds
                    )
                )
                .isTrue()
        }
    }

    @Test
    fun bubblesCollapsed_addsInsets() {
        setupBubbles()
        runOnTaskbarUiThreadSync {
            taskbarStashController.updateAndAnimateTransientTaskbar(false, true)
            bubbleBarViewController.addBubble(bubble, false, true, null)
            bubbleBarViewController.setHiddenForBubbles(false)
        }
        runOnTaskbarUiThreadSync {
            assertThat(bubbleBarViewController.hasBubbles()).isTrue()
            assertThat(bubbleBarViewController.isBubbleBarVisible).isTrue()
            assertThat(bubbleBarViewController.isExpanded).isFalse()
            assertThat(bubbleBarViewController.isAnimatingNewBubble).isFalse()
            assertThat(
                    taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.contains(
                        bubbleBarViewController.bubbleBarBounds
                    )
                )
                .isTrue()
        }
    }

    @Test
    fun bubblesAnimatingNewBubble_addsInsets() {
        setupBubbles()
        runOnTaskbarUiThreadSync {
            taskbarStashController.updateAndAnimateTransientTaskbar(true, true)
            bubbleBarViewController.addBubble(bubble, false, false, null)
            bubbleBarViewController.setHiddenForBubbles(false)
        }
        // start the animating bubble animation
        getTaskbarUiThread().waitForIdleSync()
        assertThat(bubbleBarViewController.hasBubbles()).isTrue()
        assertThat(bubbleBarViewController.isBubbleBarVisible).isTrue()
        assertThat(bubbleBarViewController.isExpanded).isFalse()
        assertThat(bubbleBarViewController.isAnimatingNewBubble).isTrue()
        runOnTaskbarUiThreadSync {
            assertThat(
                    taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.contains(
                        bubbleBarViewController.bubbleBarBounds
                    )
                )
                .isTrue()
        }
    }

    @Test
    fun bubblesVisible_addsInsets() {
        setupBubbles()
        runOnTaskbarUiThreadSync {
            taskbarStashController.updateAndAnimateTransientTaskbar(true, true)
            bubbleBarViewController.addBubble(bubble, false, true, null)
            bubbleBarViewController.setHiddenForBubbles(false)
        }
        // start the animating bubble animation
        getTaskbarUiThread().waitForIdleSync()
        assertThat(bubbleBarViewController.hasBubbles()).isTrue()
        assertThat(bubbleBarViewController.isBubbleBarAndContainerVisible).isTrue()
        assertThat(bubbleBarViewController.isAnimatingNewBubble).isFalse()
        runOnTaskbarUiThreadSync {
            assertThat(
                    taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.contains(
                        bubbleBarViewController.bubbleBarBounds
                    )
                )
                .isTrue()
        }
    }

    @EnableFlags(Flags.FLAG_FIX_BUBBLE_INSETS_WHEN_INVISIBLE)
    @Test
    fun bubblesNotVisibleOnHome_noInsets() {
        setupBubbles()
        runOnTaskbarUiThreadSync {
            taskbarStashController.updateStateForFlag(FLAG_IN_APP.toLong(), false)
            bubbleStashController.launcherState = BubbleStashController.BubbleLauncherState.HOME
            bubbleBarViewController.addBubble(bubble, false, true, null)
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleBarViewController.setHiddenForSysui(true)
        }
        getTaskbarUiThread().waitForIdleSync()
        assertThat(bubbleBarViewController.hasBubbles()).isTrue()
        assertThat(bubbleBarViewController.isBubbleBarAndContainerVisible).isFalse()
        assertThat(bubbleBarViewController.isAnimatingNewBubble).isFalse()
        assertThat(bubbleStashController.isBubblesShowingOnHome).isTrue()
        runOnTaskbarUiThreadSync {
            assertThat(
                    taskbarInsetsController.debugTouchableRegion.lastSetTouchableBounds.contains(
                        bubbleBarViewController.bubbleBarBounds
                    )
                )
                .isFalse()
        }
    }

    private fun setupBubbles() {
        setupBubbleViews()
        bubbleView.setController(mockController)
        whenever(mockController.bubbleBarLocation).thenReturn(BubbleBarLocation.RIGHT)
    }

    private fun setupBubbleViews() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)
            val bitmap = ColorDrawable(Color.WHITE).toBitmap(width = 20, height = 20)
            val bubbleInfo =
                BubbleInfo(
                    "key",
                    0,
                    null,
                    null,
                    0,
                    context.packageName,
                    null,
                    null,
                    false,
                    null,
                    false,
                    false,
                    UserType.MAIN,
                )
            bubbleView = inflater.inflate(R.layout.bubblebar_item_view, null, false) as BubbleView
            bubble =
                BubbleBarBubble(
                    bubbleInfo,
                    bubbleView,
                    BitmapInfo.of(bitmap, Color.WHITE),
                    BubbleIcon.Custom(bitmap),
                    Color.WHITE,
                    "",
                    null,
                )
            bubbleView.setBubble(bubble)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
