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

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.WindowInsets
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING_IN_DESKTOP_MODE
import com.android.launcher3.QuickstepTransitionManager.PINNED_TASKBAR_TRANSITION_DURATION
import com.android.launcher3.R
import com.android.launcher3.desktop.DesktopStateProvider.getDesktopState
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.StashedHandleViewController.ALPHA_INDEX_STASHED
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_BUBBLES
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_EDU_OPEN
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_GROWTH_NUDGE_OPEN
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.asProperty
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_OVERVIEW
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_DEVICE_LOCKED
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IME
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_APP_AUTO
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_OVERVIEW_FOR_TRANSLUCENT_APP
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_SMALL_SCREEN
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_SYSUI
import com.android.launcher3.taskbar.TaskbarStashController.FLAG_TASKBAR_HIDDEN
import com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION
import com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION_FOR_IME
import com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION_WITHOUT_ICON_ALIGNMENT
import com.android.launcher3.taskbar.TaskbarStashController.TRANSIENT_TASKBAR_STASH_ALPHA_DURATION
import com.android.launcher3.taskbar.TaskbarStashController.TRANSIENT_TASKBAR_STASH_DURATION
import com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_STASH
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.THREE_BUTTONS
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.UserSetupMode
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.reset
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
class TaskbarStashControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 2) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 4) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 5) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val stashController by taskbarUnitTestRule.delegate { it.taskbarStashController }
    private val viewController by taskbarUnitTestRule.delegate { it.taskbarViewController }
    private val stashedHandleViewController by
        taskbarUnitTestRule.delegate { it.stashedHandleViewController }
    private val dragLayerController by
        taskbarUnitTestRule.delegate { it.taskbarDragLayerController }
    private val autohideSuspendController by
        taskbarUnitTestRule.delegate { it.taskbarAutohideSuspendController }
    private val bubbleBarViewController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.orElseThrow().bubbleBarViewController }
    private val bubbleStashController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.orElseThrow().bubbleStashController }

    private val desktopVisibilityController: DesktopVisibilityController
        get() = DesktopVisibilityController.INSTANCE[context]

    private val activityContext by taskbarUnitTestRule::activityContext
    private lateinit var windowManagerSpy: WindowManager

    @Before
    fun setUp() {
        windowManagerSpy =
            checkNotNull(
                taskbarUnitTestRule.taskbarManager
                    .getPerDisplayResourceForTest(context.displayId)
                    ?.windowContext
                    ?.getSystemService(WindowManager::class.java)
            )
    }

    @After fun cancelTimeoutIfExists() = stashController.cancelTimeoutIfExists()

    @Test
    @TaskbarMode(TRANSIENT)
    fun testInit_transientMode_stashedInApp() {
        assertThat(stashController.isStashedInApp).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testInit_pinnedMode_unstashedInApp() {
        assertThat(stashController.isStashedInApp).isFalse()
    }

    @Test
    @UserSetupMode
    @TaskbarMode(PINNED)
    fun testInit_userSetupWithPinnedMode_stashedInApp() {
        assertThat(stashController.isStashedInApp).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSetSetupUiVisible_true_stashedInApp() {
        runOnTaskbarUiThreadSync { stashController.setSetupUIVisible(true) }
        assertThat(stashController.isStashedInApp).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSetSetupUiVisible_false_unstashedInApp() {
        runOnTaskbarUiThreadSync { stashController.setSetupUIVisible(false) }
        assertThat(stashController.isStashedInApp).isFalse()
    }

    private fun testRecreateAsTransient_timeoutStarted() {
        var isPinned by TASKBAR_PINNING.asProperty(context)
        isPinned = true
        activityContext.controllers.sharedState?.taskbarWasPinned = true

        isPinned = false
        if (enableTaskbarUiThread()) {
            getInstrumentation().runOnMainSync {
                getTaskbarUiThread().execute {
                    assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()
                }
            }
        } else {
            assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testSupportsVisualStashing_transientMode_supported() {
        assertThat(stashController.supportsVisualStashing()).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSupportsVisualStashing_pinnedMode_supported() {
        assertThat(stashController.supportsVisualStashing()).isTrue()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testSupportsVisualStashing_threeButtonsMode_unsupported() {
        assertThat(stashController.supportsVisualStashing()).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetStashDuration_transientMode() {
        if (!activityContext.controllers.taskbarRecentAppsController.canShowRecentApps) {
            assertThat(stashController.stashDuration).isEqualTo(TRANSIENT_TASKBAR_STASH_DURATION)
        } else {
            assertThat(stashController.stashDuration)
                .isEqualTo(TASKBAR_STASH_DURATION_WITHOUT_ICON_ALIGNMENT)
        }
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetStashDuration_pinnedMode() {
        assertThat(stashController.stashDuration).isEqualTo(PINNED_TASKBAR_TRANSITION_DURATION)
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testGetStashDuration_inThreeButtonMode() {
        assertThat(stashController.stashDuration).isEqualTo(PINNED_TASKBAR_TRANSITION_DURATION)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsStashed_pinnedInApp_isUnstashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsStashed_transientInApp_isStashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsStashed_transientNotInApp_isUnstashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    fun testIsStashed_stashedInLauncherState_isStashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsStashed_transientInOverview_isUnstashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_OVERVIEW, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsStashed_pinnedInOverviewWithIme_isStashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_OVERVIEW, true)
            stashController.updateStateForFlag(FLAG_STASHED_IME, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsStashed_inOverviewForTranslucentApp_isStashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_OVERVIEW, true)
            stashController.updateStateForFlag(FLAG_STASHED_IN_OVERVIEW_FOR_TRANSLUCENT_APP, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsStashed_pinnedTaskbarWithPinnedApp_isStashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.updateStateForFlag(FLAG_STASHED_SYSUI, true) // App pinned.
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    fun testIsInStashedLauncherState_flagUnset_false() {
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, false)
        assertThat(stashController.isInStashedLauncherState).isFalse()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testIsInStashedLauncherState_flagSetInThreeButtonsMode_false() {
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, true)
        assertThat(stashController.isInStashedLauncherState).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsInStashedLauncherState_flagSetInPinnedMode_true() {
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, true)
        assertThat(stashController.isInStashedLauncherState).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsTaskbarVisibleAndNotStashing_pinnedButNotVisible_false() {
        runOnTaskbarUiThreadSync {
            viewController.taskbarIconAlpha.get(ALPHA_INDEX_STASH).value = 0f
        }
        assertThat(stashController.isTaskbarVisibleAndNotStashing).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testIsTaskbarVisibleAndNotStashing_visibleButStashed_false() {
        runOnTaskbarUiThreadSync {
            viewController.taskbarIconAlpha.get(ALPHA_INDEX_STASH).value = 1f
        }
        assertThat(stashController.isTaskbarVisibleAndNotStashing).isFalse()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testIsTaskbarVisibleAndNotStashing_pinnedAndVisible_true() {
        runOnTaskbarUiThreadSync {
            viewController.taskbarIconAlpha.get(ALPHA_INDEX_STASH).value = 1f
        }
        assertThat(stashController.isTaskbarVisibleAndNotStashing).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetTouchableHeight_isStashed_stashedHeight() {
        assertThat(stashController.touchableHeight).isEqualTo(stashController.stashedHeight)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetTouchableHeight_unstashedTransientMode_heightAndBottomMargin() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, false)
            stashController.applyState(0)
        }

        val expectedHeight =
            activityContext.deviceProfile.taskbarProfile.run { height + bottomMargin }
        assertThat(stashController.touchableHeight).isEqualTo(expectedHeight)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetTouchableHeight_pinnedMode_taskbarHeight() {
        assertThat(stashController.touchableHeight)
            .isEqualTo(activityContext.deviceProfile.taskbarProfile.height)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetContentHeightToReportToApps_transientMode_stashedHeight() {
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(stashController.stashedHeight)
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testGetContentHeightToReportToApps_threeButtonsMode_taskbarHeight() {
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(activityContext.deviceProfile.taskbarProfile.height)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetContentHeightToReportToApps_pinnedMode_taskbarHeight() {
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(activityContext.deviceProfile.taskbarProfile.height)
    }

    @Test
    @TaskbarMode(PINNED)
    @UserSetupMode
    fun testGetContentHeightToReportToApps_pinnedInSetupMode_setupWizardInsets() {
        stashController.mNavbarHiddenOverrideForTest = false
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(context.resources.getDimensionPixelSize(R.dimen.taskbar_suw_insets))
        stashController.mNavbarHiddenOverrideForTest = null
    }

    @Test
    @UserSetupMode
    fun testGetContentHeightToReportToApps_inExpressiveTheme_setupWizardInsets() {
        stashController.mNavbarHiddenOverrideForTest = true
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(stashController.stashedHeight)
        stashController.mNavbarHiddenOverrideForTest = null
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetContentHeightToReportToApps_pinnedModeButFolded_stashedHeight() {
        runOnTaskbarUiThreadSync {
            stashedHandleViewController.stashedHandleAlpha.get(ALPHA_INDEX_STASHED).value = 1f
            stashController.updateStateForFlag(FLAG_STASHED_SMALL_SCREEN, true)
        }
        assertThat(stashController.contentHeightToReportToApps)
            .isEqualTo(stashController.stashedHeight)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetContentHeightToReportToApps_homeDisabledWhenFolded_zeroHeight() {
        runOnTaskbarUiThreadSync {
            stashedHandleViewController.stashedHandleAlpha.get(ALPHA_INDEX_STASHED).value = 1f
            stashedHandleViewController.setIsHomeButtonDisabled(true)
            stashController.updateStateForFlag(FLAG_STASHED_SMALL_SCREEN, true)
        }
        assertThat(stashController.contentHeightToReportToApps).isEqualTo(0)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetTappableHeightToReportToApps_transientMode_zeroHeight() {
        assertThat(stashController.tappableHeightToReportToApps).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testGetTappableHeightToReportToApps_pinnedMode_taskbarHeight() {
        assertThat(stashController.tappableHeightToReportToApps)
            .isEqualTo(activityContext.deviceProfile.taskbarProfile.height)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_unstashTaskbar_updatesState() {
        runOnTaskbarUiThreadSync { stashController.updateAndAnimateTransientTaskbar(false) }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_runUnstashAnimation_startsTaskbarTimeout() {
        runOnTaskbarUiThreadSync {
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testUpdateTaskbarTimeout_unPinnedTaskbarInDesktopMode_startsTaskbarTimeout() {
        LauncherPrefs.get(context).put(TASKBAR_PINNING_IN_DESKTOP_MODE, false)
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        stashController.updateTaskbarTimeout(false)
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testUpdateTaskbarTimeout_pinnedTaskbarInDesktopMode_shouldNotStartsTaskbarTimeout() {
        LauncherPrefs.get(context).put(TASKBAR_PINNING_IN_DESKTOP_MODE, true)
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        stashController.updateTaskbarTimeout(false)
        assertThat(stashController.timeoutAlarm.alarmPending()).isFalse()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun shouldAllowTaskbarToAutoStash_ThreeButtonNavTaskbar() {
        assertThat(stashController.shouldAllowTaskbarToAutoStash()).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun shouldAllowTaskbarToAutoStash_transientTaskbar() {
        assertThat(stashController.shouldAllowTaskbarToAutoStash()).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun toggleTaskbarStash_autoStashedDesktopModeTaskbar() {
        LauncherPrefs.get(context).put(TASKBAR_PINNING_IN_DESKTOP_MODE, false)
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)

        runOnTaskbarUiThreadSync { stashController.toggleTaskbarStash() }

        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_finishTaskbarTimeout_taskbarStashes() {
        runOnTaskbarUiThreadSync {
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()

        runOnTaskbarUiThreadSync {
            stashController.timeoutAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_autoHideSuspendedForEdu_remainsUnstashed() {
        runOnTaskbarUiThreadSync {
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }

        runOnTaskbarUiThreadSync {
            autohideSuspendController.updateFlag(FLAG_AUTOHIDE_SUSPEND_EDU_OPEN, true)
            stashController.updateAndAnimateTransientTaskbar(true)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_unstashTaskbarWithBubbles_bubbleBarUnstashes() {
        runOnTaskbarUiThreadSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.stashBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(false, true)
        }
        assertThat(bubbleStashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_unstashTaskbarWithoutBubbles_bubbleBarStashed() {
        runOnTaskbarUiThreadSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.stashBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(false, false)
        }
        assertThat(bubbleStashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_stashTaskbarWithBubbles_bubbleBarStashes() {
        runOnTaskbarUiThreadSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.showBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(true, true)
        }
        assertThat(bubbleStashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbarInSplitCreation_stashTaskbarWithBubbles_bubbleBarStashes() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_TASKBAR_HIDDEN, false)
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, true)
            stashController.applyState(0)
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.showBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(true, true)
        }
        assertThat(bubbleStashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_stashTaskbarWithoutBubbles_bubbleBarUnstashed() {
        runOnTaskbarUiThreadSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleStashController.showBubbleBarImmediate()
            stashController.updateAndAnimateTransientTaskbar(true, false)
        }
        assertThat(bubbleStashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUpdateAndAnimateTransientTaskbar_bubbleBarExpandedBeforeTimeout_expandedAfterwards() {
        runOnTaskbarUiThreadSync {
            bubbleBarViewController.setHiddenForBubbles(false)
            bubbleBarViewController.animateExpanded(true)
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashController.timeoutAlarm.alarmPending()).isTrue()

        runOnTaskbarUiThreadSync {
            stashController.timeoutAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(bubbleBarViewController.isExpanded).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testToggleTaskbarStash_pinnedMode_doesNothing() {
        runOnTaskbarUiThreadSync { stashController.toggleTaskbarStash() }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testToggleTaskbarStash_transientMode_unstashesTaskbar() {
        runOnTaskbarUiThreadSync { stashController.toggleTaskbarStash() }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testToggleTaskbarStash_twiceInTransientMode_stashesTaskbar() {
        runOnTaskbarUiThreadSync {
            stashController.toggleTaskbarStash()
            stashController.toggleTaskbarStash()
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testToggleTaskbarStash_notInAppWithTransientMode_doesNothing() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.applyState(0)
            stashController.toggleTaskbarStash()
        }
        assertThat(stashController.isStashed).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testAnimateTransientTaskbar_bubblesShownInOverview_stashesTaskbar() {
        // Start in Overview. Should unstash Taskbar.
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_STASHED_IN_APP_AUTO, false)
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_IN_OVERVIEW, true)
            stashController.applyState(0)
        }
        assertThat(stashController.isStashed).isFalse()

        // Expand bubbles. Should stash Taskbar.
        runOnTaskbarUiThreadSync {
            stashController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, false)
            animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION)
        }
        assertThat(stashController.isStashed).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testAnimatePinnedTaskbar_imeShown_replacesIconsWithHandle() {
        try {
            activityContext.setImeDockedOverrideForTest(true)
            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, false)
                animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
            }
            assertThat(viewController.areIconsVisible()).isFalse()
            assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()
        } finally {
            activityContext.setImeDockedOverrideForTest(null)
        }
    }

    @Test
    @TaskbarMode(PINNED)
    fun testAnimatePinnedTaskbar_imeHidden_replacesHandleWithIcons() {
        try {
            activityContext.setImeDockedOverrideForTest(true)
            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, true)
                animatorTestRule.advanceTimeBy(0)
            }

            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(0, true)
                animatorTestRule.advanceTimeBy(0)
            }
            assertThat(stashedHandleViewController.isStashedHandleVisible).isFalse()
            assertThat(viewController.areIconsVisible()).isTrue()
        } finally {
            activityContext.setImeDockedOverrideForTest(null)
        }
    }

    @Test
    @TaskbarMode(PINNED)
    fun testAnimatePinnedTaskbar_imeHidden_verifyAnimationDuration() {
        try {
            activityContext.setImeDockedOverrideForTest(true)
            // Start with IME shown.
            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, true)
                animatorTestRule.advanceTimeBy(0)
            }

            // Hide IME with animation.
            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(0, false)
                // Fast forward without start delay.
                animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
            }
            // Icons should not be visible yet due to start delay.
            assertThat(viewController.areIconsVisible()).isFalse()

            // Advance by start delay retroactively. Animation should complete.
            runOnTaskbarUiThreadSync {
                animatorTestRule.advanceTimeBy(stashController.taskbarStashStartDelayForIme)
            }
            assertThat(viewController.areIconsVisible()).isTrue()
        } finally {
            activityContext.setImeDockedOverrideForTest(null)
        }
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testAnimateThreeButtonsTaskbar_imeShown_hidesIconsAndBg() {
        try {
            activityContext.setImeDockedOverrideForTest(true)
            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, false)
                animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
            }
            assertThat(stashController.isStashed).isTrue()
            assertThat(viewController.areIconsVisible()).isFalse()
            assertThat(dragLayerController.imeBgTaskbar.value).isEqualTo(0)
        } finally {
            activityContext.setImeDockedOverrideForTest(null)
        }
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testAnimateThreeButtonsTaskbar_imeHidden_showsIconsAndBg() {
        try {
            activityContext.setImeDockedOverrideForTest(true)
            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, false)
                animatorTestRule.advanceTimeBy(TASKBAR_STASH_DURATION_FOR_IME)
            }

            runOnTaskbarUiThreadSync {
                stashController.updateStateForSysuiFlags(0, false)
                animatorTestRule.advanceTimeBy(
                    TASKBAR_STASH_DURATION_FOR_IME + stashController.taskbarStashStartDelayForIme
                )
            }
            assertThat(viewController.areIconsVisible()).isTrue()
            assertThat(dragLayerController.imeBgTaskbar.value).isEqualTo(1)
        } finally {
            activityContext.setImeDockedOverrideForTest(null)
        }
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testThreeButtonsTaskbarOnHome_homeShownBehindDesktop_showsIconsAndBg() {
        val desktopState = activityContext.getDesktopState()
        desktopState.convertToSpy()
        doReturn(true).whenever(desktopState).shouldShowHomeBehindDesktop
        LauncherPrefs.get(context).put(TASKBAR_PINNING_IN_DESKTOP_MODE, false)

        taskbarUnitTestRule.recreateTaskbar()

        getInstrumentation().runOnMainSync {}
        assertThat(stashController.isStashed).isFalse()
        assertThat(viewController.areIconsVisible()).isTrue()
        assertThat(dragLayerController.imeBgTaskbar.value).isEqualTo(1)

        reset(desktopState)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testSysuiStateImeShowingInApp_imeNotDocked_notStashedForIme() {
        try {
            activityContext.setImeDockedOverrideForTest(false)
            runOnTaskbarUiThreadSync {
                stashController.updateStateForFlag(FLAG_IN_APP, true)
                stashController.updateStateForSysuiFlags(SYSUI_STATE_IME_VISIBLE, true)
            }

            assertThat(stashController.isStashed).isFalse()
        } finally {
            activityContext.setImeDockedOverrideForTest(null)
        }
    }

    @Test
    @TaskbarMode(PINNED)
    fun testUnlockTransition_pinnedMode_fadesOutHandle() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, true)
            stashController.applyState(0)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()

        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, false)
            stashController.applyState()
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testUnlockTransition_transientMode_fadesOutHandleEarly() {
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, false)
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, true)
            stashController.applyState(0)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()

        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_STASHED_DEVICE_LOCKED, false)
            stashController.applyState()
            // Time it takes for just the handle to hide (full stash animation is longer).
            animatorTestRule.advanceTimeBy(TRANSIENT_TASKBAR_STASH_ALPHA_DURATION)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun unstashTaskbar_inApp_navBarForciblyShown() {
        val wmLayoutParamsCaptor = argumentCaptor<WindowManager.LayoutParams>()
        runOnTaskbarUiThreadSync {
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isFalse()
        assertThat(stashController.isStashedInApp).isFalse()

        verify(windowManagerSpy, atLeastOnce())
            .updateViewLayout(any(), wmLayoutParamsCaptor.capture())
        assertThat(isNavBarForciblyShown(wmLayoutParamsCaptor.lastValue.forciblyShownTypes))
            .isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun stashTaskbar_inApp_withBubbleBarExpanded_navBarForciblyShown() {
        val wmLayoutParamsCaptor = argumentCaptor<WindowManager.LayoutParams>()
        runOnTaskbarUiThreadSync {
            // unstash taskbar in an app
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
            stashController.updateAndAnimateTransientTaskbar(false)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)

            // suspend auto hide due to bubble bar and stash taskbar
            autohideSuspendController.updateFlag(FLAG_AUTOHIDE_SUSPEND_BUBBLES, true)
            stashController.updateAndAnimateTransientTaskbar(
                /* stash= */ true,
                /* shouldBubblesFollow= */ false,
            )
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()
        assertThat(stashController.isStashedInApp).isTrue()

        // verify the nav bar window should be forcibly shown
        verify(windowManagerSpy, atLeastOnce())
            .updateViewLayout(any(), wmLayoutParamsCaptor.capture())
        assertThat(isNavBarForciblyShown(wmLayoutParamsCaptor.lastValue.forciblyShownTypes))
            .isTrue()

        // unsuspend auto hide and verify that the nav bar window is no longer forcibly shown
        runOnTaskbarUiThreadSync {
            autohideSuspendController.updateFlag(FLAG_AUTOHIDE_SUSPEND_BUBBLES, false)
        }
        verify(windowManagerSpy, atLeastOnce())
            .updateViewLayout(any(), wmLayoutParamsCaptor.capture())
        assertThat(isNavBarForciblyShown(wmLayoutParamsCaptor.lastValue.forciblyShownTypes))
            .isFalse()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun stashTaskbar_taskbarAutohideSuspended_withForceShow_navBarForciblyShown() {
        val wmLayoutParamsCaptor = argumentCaptor<WindowManager.LayoutParams>()
        runOnTaskbarUiThreadSync {
            // stash taskbar in an app
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
            stashController.updateAndAnimateTransientTaskbar(true)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()
        assertThat(stashController.isStashedInApp).isTrue()

        // verify the nav bar window is not forcibly shown
        verify(windowManagerSpy, atLeastOnce())
            .updateViewLayout(any(), wmLayoutParamsCaptor.capture())
        assertThat(isNavBarForciblyShown(wmLayoutParamsCaptor.lastValue.forciblyShownTypes))
            .isFalse()

        // suspend auto hide for bubbles and verify that the nav bar window is forcibly shown
        runOnTaskbarUiThreadSync {
            autohideSuspendController.updateFlag(FLAG_AUTOHIDE_SUSPEND_BUBBLES, true)
        }
        verify(windowManagerSpy, atLeastOnce())
            .updateViewLayout(any(), wmLayoutParamsCaptor.capture())
        assertThat(isNavBarForciblyShown(wmLayoutParamsCaptor.lastValue.forciblyShownTypes))
            .isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun stashTaskbar_taskbarAutohideSuspended_withoutForceShow_navBarNotForciblyShown() {
        val wmLayoutParamsCaptor = argumentCaptor<WindowManager.LayoutParams>()
        runOnTaskbarUiThreadSync {
            // stash taskbar in an app
            stashController.updateStateForFlag(FLAG_IN_APP, true)
            stashController.applyState(0)
            stashController.updateAndAnimateTransientTaskbar(true)
            animatorTestRule.advanceTimeBy(stashController.stashDuration)
        }
        assertThat(stashedHandleViewController.isStashedHandleVisible).isTrue()
        assertThat(stashController.isStashedInApp).isTrue()

        // verify the nav bar window is not forcibly shown
        verify(windowManagerSpy, atLeastOnce())
            .updateViewLayout(any(), wmLayoutParamsCaptor.capture())
        assertThat(isNavBarForciblyShown(wmLayoutParamsCaptor.lastValue.forciblyShownTypes))
            .isFalse()

        // suspend auto hide in a way that does not force show taskbar and verify that the nav bar
        // window is not forcibly shown
        runOnTaskbarUiThreadSync {
            autohideSuspendController.updateFlag(FLAG_AUTOHIDE_SUSPEND_GROWTH_NUDGE_OPEN, true)
        }
        verify(windowManagerSpy, atLeastOnce())
            .updateViewLayout(any(), wmLayoutParamsCaptor.capture())
        assertThat(isNavBarForciblyShown(wmLayoutParamsCaptor.lastValue.forciblyShownTypes))
            .isFalse()
    }

    private fun isNavBarForciblyShown(forciblyShownTypes: Int): Boolean =
        (forciblyShownTypes and WindowInsets.Type.navigationBars()) != 0
}

private fun TaskbarStashController.updateStateForFlag(flag: Int, value: Boolean) {
    updateStateForFlag(flag.toLong(), value)
}
