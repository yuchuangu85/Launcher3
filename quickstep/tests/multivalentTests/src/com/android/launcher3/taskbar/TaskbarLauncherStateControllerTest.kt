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

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Hotseat
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherInteractor
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherUiState
import com.android.launcher3.SplitScreenUiState
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.MutableListenableRef
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_AWAKE
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_WAKEFULNESS_TRANSITION
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskbarLauncherStateControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 3) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 4) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val bubbleControllers by taskbarUnitTestRule.delegate { it.bubbleControllers }
    private val taskbarStashController by taskbarUnitTestRule.delegate { it.taskbarStashController }

    private val bubbleBarViewController by lazy {
        bubbleControllers.orElseThrow().bubbleBarViewController
    }
    private val bubbleBarStashController by lazy {
        bubbleControllers.orElseThrow().bubbleStashController
    }
    private val taskbarLauncherStateController = TaskbarLauncherStateController()

    @Test
    @TaskbarMode(TRANSIENT)
    fun updateStateForSysuiFlags_singleTapPowerButton_stashTaskAndBubbleBarOnAnimationEnd() {
        initForWakeTransitionWithBubbles(SYSUI_STATE_AWAKE)

        runOnTaskbarUiThreadSync {
            bubbleBarStashController.showBubbleBar(expandBubbles = true)
            animatorTestRule.advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        }

        assertThat(bubbleBarStashController.isStashed).isFalse()
        assertThat(bubbleBarViewController.isExpanded).isTrue()

        runOnTaskbarUiThreadSync {
            // simulate the device going to sleep
            taskbarLauncherStateController.updateStateForSysuiFlags(
                SYSUI_STATE_WAKEFULNESS_TRANSITION and SYSUI_STATE_AWAKE.inv()
            )
            // Stash the taskbar.
            animatorTestRule.advanceTimeBy(taskbarStashController.stashDuration)
            // Stash the bubble bar.
            animatorTestRule.advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        }

        assertThat(bubbleBarStashController.isStashed).isTrue()
        assertThat(bubbleBarViewController.isExpanded).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun updateStateForSysuiFlags_doubleTapPowerButton_doesNotStashTaskAndBubbleBarOnAnimationEnd() {
        initForWakeTransitionWithBubbles(SYSUI_STATE_AWAKE)

        runOnTaskbarUiThreadSync {
            bubbleBarStashController.showBubbleBar(expandBubbles = true)
            animatorTestRule.advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        }

        assertThat(bubbleBarStashController.isStashed).isFalse()
        assertThat(bubbleBarViewController.isExpanded).isTrue()

        runOnTaskbarUiThreadSync {
            // simulate double tap
            taskbarLauncherStateController.updateStateForSysuiFlags(
                SYSUI_STATE_WAKEFULNESS_TRANSITION and SYSUI_STATE_AWAKE.inv()
            )
            taskbarLauncherStateController.updateStateForSysuiFlags(
                SYSUI_STATE_WAKEFULNESS_TRANSITION or SYSUI_STATE_AWAKE
            )
            animatorTestRule.advanceTimeBy(taskbarStashController.stashDuration)
        }

        assertThat(bubbleBarStashController.isStashed).isFalse()
        assertThat(bubbleBarViewController.isExpanded).isTrue()
    }

    /** Initializes the controller for a wake transition with a transit taskbar and bubbles. */
    private fun initForWakeTransitionWithBubbles(@SystemUiStateFlags sysUiStateFlags: Long) {
        val launcherStateManager =
            mock<StateManager<LauncherState, Launcher>> {
                on { state } doReturn LauncherState.NORMAL
            }
        val dp = taskbarUnitTestRule.activityContext.deviceProfile
        val mockedSplitScreenUiState =
            mock<SplitScreenUiState> { on { isSplitSelectActive } doReturn false }
        val mockedLauncherUiState =
            mock<LauncherUiState> {
                on { deviceProfileRef } doReturn MutableListenableRef(dp)
                on { splitScreenUiState } doReturn mockedSplitScreenUiState
                on { launcherState } doReturn LauncherState.NORMAL
                on { taskbarAlignmentChannelAlpha } doReturn 0f
            }
        val quickstepLauncher =
            mock<QuickstepLauncher> {
                on { deviceProfile } doReturn dp
                on { hotseat } doReturn mock<Hotseat>()
                on { stateManager } doReturn launcherStateManager
                on { launcherUiState } doReturn mockedLauncherUiState
            }
        val launcherInteractor =
            spy(LauncherInteractor(quickstepLauncher)) {
                doReturn(dp).whenever(mock).getDeviceProfile()
            }
        val controllers = taskbarUnitTestRule.activityContext.controllers
        runOnTaskbarUiThreadSync {
            taskbarLauncherStateController.init(
                controllers,
                launcherInteractor,
                mockedLauncherUiState,
                sysUiStateFlags,
                getTaskbarUiThread(),
            )
            taskbarStashController.toggleTaskbarStash() // Un-stashing the taskbar.
            bubbleBarViewController.setHiddenForBubbles(false) // Show the bubble bar.
        }
    }
}
