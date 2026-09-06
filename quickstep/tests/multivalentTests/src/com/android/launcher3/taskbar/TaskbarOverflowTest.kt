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

import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_EXIT
import android.view.ViewTreeObserver
import android.window.RemoteTransition
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.waitForIdleSync
import com.android.launcher3.taskbar.TaskbarIconType.ALL_APPS
import com.android.launcher3.taskbar.TaskbarIconType.DIVIDER
import com.android.launcher3.taskbar.TaskbarIconType.HOTSEAT
import com.android.launcher3.taskbar.TaskbarIconType.OVERFLOW
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.rules.MockedRecentsModelHelper
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext_ModifiedComponent
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.ModelTestExtensions.preloadModelData
import com.android.launcher3.util.Preconditions.assertNotNull
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SingleTask.Companion.createTaskItemInfo
import com.android.quickstep.util.SlideInRemoteTransition
import com.android.systemui.shared.recents.model.Task
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS
import com.android.window.flags.Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU
import com.android.window.flags.Flags.FLAG_ENABLE_TASKBAR_OVERFLOW
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.desktopmode.IDesktopTaskListener
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@EnableFlags(
    FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    FLAG_ENABLE_BUBBLE_BAR,
    FLAG_ENABLE_TASKBAR_OVERFLOW,
)
@DisableFlags(FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR)
@MutatedComponent(target = TaskbarWindowSandboxContext_ModifiedComponent::class)
class TaskbarOverflowTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    private val mockRecentsModelHelper: MockedRecentsModelHelper = MockedRecentsModelHelper()
    @BindValue val recentsModel: RecentsModel by mockRecentsModelHelper

    private var systemUiProxySpy: SystemUiProxy? = null

    @get:Rule(order = 1)
    val context =
        TaskbarWindowSandboxContext.create(
            params =
                SandboxParams(builderBase = mutatedComponentBuilder()) {
                    systemUiProxySpy = it.systemUiProxy
                }
        )

    @get:Rule(order = 3) val taskbarModeRule = TaskbarModeRule(context)

    @get:Rule(order = 4)
    val desktopModeRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                whenever(desktopVisibilityController.isInDesktopMode(context.displayId))
                    .thenReturn(true)
                base?.evaluate()
            }
        }
    }

    @get:Rule(order = 5) val animatorTestRule = TaskbarAnimatorTestRule(this)

    @get:Rule(order = 6)
    val taskbarUnitTestRule = TaskbarUnitTestRule(context, this::onControllersInitialized)

    private val taskbarViewController by taskbarUnitTestRule.delegate { it.taskbarViewController }
    private val recentAppsController by
        taskbarUnitTestRule.delegate { it.taskbarRecentAppsController }
    private val bubbleBarViewController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.orElseThrow().bubbleBarViewController }
    private val bubbleStashController by
        taskbarUnitTestRule.delegate { it.bubbleControllers.orElseThrow().bubbleStashController }
    private val keyboardQuickSwitchController by
        taskbarUnitTestRule.delegate { it.keyboardQuickSwitchController }

    private val desktopVisibilityController: DesktopVisibilityController
        get() = DesktopVisibilityController.INSTANCE[context]

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private var currentControllerInitCallback: () -> Unit = {}
        set(value) {
            runOnTaskbarUiThreadSync { value.invoke() }
            field = value
        }

    private fun onControllersInitialized() {
        runOnTaskbarUiThreadSync {
            if (!recentAppsController.canShowRunningApps) {
                recentAppsController.onDestroy()
                recentAppsController.canShowRunningApps = true
                recentAppsController.init(
                    taskbarUnitTestRule.activityContext.controllers,
                    emptyList(),
                )
            }

            currentControllerInitCallback.invoke()
        }
    }

    @Before
    fun ensureRunningAppsShowing() {
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }
    }

    @After
    fun resetForcedMaxIconCount() {
        runOnTaskbarUiThreadSync { taskbarViewController.limitMaxTaskbarIconsNum(-1) }
        RoboApiWrapper.waitForLooperSync(Looper.getMainLooper())
    }

    @Test
    @TaskbarMode(PINNED)
    fun testTaskbarWithMaxNumIcons_pinned() {
        addRunningAppsAndVerifyOverflowState(0)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testTaskbarWithMaxNumIcons_transient() {
        addRunningAppsAndVerifyOverflowState(0)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOverflownTaskbar_pinned() {
        addRunningAppsAndVerifyOverflowState(5)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testOverflownTaskbar_transient() {
        addRunningAppsAndVerifyOverflowState(5)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin).isAtLeast(navButtonEndSpacing)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOverflownTaskbarWithNoSpaceForRecentApps_pinned() {
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)

        val numberOfHotseatApps =
            taskbarUnitTestRule.activityContext.deviceProfile.hotseatProfile.numShownIcons
        val forcedMaxIconCount = numberOfHotseatApps + 2

        runOnTaskbarUiThreadSync {
            taskbarViewController.limitMaxTaskbarIconsNum(forcedMaxIconCount)
        }

        // Create two "recent" desktop tasks, and then add enough hotseat items so the taskbar
        // reaches max number of items with hotseat item icons, all apps and divider icons only.
        // I.e. so all desktop tasks are in taskbar overflow.
        createDesktopTask(2)
        runOnTaskbarUiThreadSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                createHotseatItems(forcedMaxIconCount - initialIconCount),
                recentAppsController.shownTasks,
                emptyList(),
            )
        }

        // Verify that taskbar overflow view is shown.
        assertThat(taskbarOverflowIconIndex).isEqualTo(currentNumberOfTaskbarIcons - 1)
        assertThat(overflowItems).containsExactlyElementsIn(0..1)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOverflownTaskbarWithNoSpaceForRecentApps_singleRecent_pinned() {
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)

        val numberOfHotseatApps =
            taskbarUnitTestRule.activityContext.deviceProfile.hotseatProfile.numShownIcons
        val forcedMaxIconCount = numberOfHotseatApps + 2

        runOnTaskbarUiThreadSync {
            taskbarViewController.limitMaxTaskbarIconsNum(forcedMaxIconCount)
        }

        // Create a "recent" desktop task, and then add enough hotseat items so the taskbar
        // reaches max number of items with hotseat item icons, all apps and divider icons only.
        // I.e. so the single desktop tasks is in taskbar overflow.
        createDesktopTask(1)
        runOnTaskbarUiThreadSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            val hotseatItems = createHotseatItems(forcedMaxIconCount - initialIconCount)

            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
                emptyList(),
            )
        }

        // Verify that recent task is shown (eventhough it exceeds max taskbar icons), and that
        // the taskbar overflow view is not added for the single recent app.
        assertThat(taskbarOverflowIconIndex).isEqualTo(-1)
        assertThat(runningAppIconIndex(0)).isEqualTo(currentNumberOfTaskbarIcons - 1)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testBubbleBarReducesTaskbarMaxNumIcons_pinned() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testTaskbarWithPinAppsOverflow_pinned() {
        val numHotseatIcons = taskbarContext.deviceProfile.inv.numShownHotseatIcons

        val taskbarView = getOnTaskbarUiThread {
            val view = taskbarContext.dragLayer.findViewById<TaskbarView>(R.id.taskbar_view)
            view.updateItems(createHotseatItems(numHotseatIcons + 2), emptyList(), emptyList())
            view
        }

        TaskbarViewTestUtil.assertThat(taskbarView)
            .hasIconTypes(ALL_APPS, DIVIDER, *HOTSEAT * (numHotseatIcons - 1), OVERFLOW)
        // Add one to the index to account for the divider.
        assertThat(taskbarOverflowIconIndex).isEqualTo(numHotseatIcons + 1)
        verifyOverflowIconTooltip("Other apps")
        assertThat(overflowItems)
            .containsExactlyElementsIn(numHotseatIcons - 1..numHotseatIcons + 1)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testBubbleBarReducesTaskbarMaxNumIcons_transient() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin)
            .isAtLeast(
                navButtonEndSpacing +
                    bubbleBarViewController.collapsedWidthWithMaxVisibleBubbles.toInt()
            )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testBubbleBarReducesTaskbarMaxNumIcons_transientBubbleInitiallyStashed() {
        var initialMaxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(initialMaxNumIconViews).isGreaterThan(0)
        currentControllerInitCallback = {
            bubbleStashController.stashBubbleBarImmediate()
            bubbleBarViewController.setHiddenForBubbles(false)
        }

        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)
        assertThat(maxNumIconViews).isLessThan(initialMaxNumIconViews)

        assertThat(taskbarIconsCentered).isTrue()
        assertThat(taskbarEndMargin)
            .isAtLeast(
                navButtonEndSpacing +
                    bubbleBarViewController.collapsedWidthWithMaxVisibleBubbles.toInt()
            )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testStashingBubbleBarMaintainsMaxNumIcons_transient() {
        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val maxNumIconViews = addRunningAppsAndVerifyOverflowState(2)

        runOnTaskbarUiThreadSync { bubbleStashController.stashBubbleBarImmediate() }
        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))
    }

    @Test
    @TaskbarMode(PINNED)
    fun testHidingBubbleBarIncreasesMaxNumIcons_pinned() {
        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val initialMaxNumIconViews = addRunningAppsAndVerifyOverflowState(5)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(true) }
        runOnTaskbarUiThreadSync { animatorTestRule.advanceTimeBy(150) }

        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(initialMaxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testHidingBubbleBarIncreasesMaxNumIcons_transient() {
        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(false) }

        val initialNumIcons = currentNumberOfTaskbarIcons
        val initialMaxNumIconViews = addRunningAppsAndVerifyOverflowState(5)

        currentControllerInitCallback = { bubbleBarViewController.setHiddenForBubbles(true) }
        runOnTaskbarUiThreadSync { animatorTestRule.advanceTimeBy(150) }

        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(initialMaxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialNumIcons.coerceAtLeast(2))

        assertThat(taskbarIconsCentered).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testPressingOverflowButtonOpensKeyboardQuickSwitch() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createDesktopTask(createdTasks)

        assertThat(taskbarOverflowIconIndex).isEqualTo(initialIconCount)
        verifyOverflowIconTooltip("Other recent apps")

        tapOverflowIcon()
        // Keyboard quick switch view is shown only after list of recent task is asynchronously
        // retrieved from the recents model.
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }

        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.isShownFromTaskbar })
            .isTrue()
        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.shownTaskIds() })
            .containsExactlyElementsIn(0..targetOverflowSize)
        verifyOverflowIconTooltip(null)
        verifyTaskbarOverlayInsetsTouchability(
            ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME
        )

        tapOverflowIcon()
        assertThat(keyboardQuickSwitchController.isShown).isFalse()
        verifyOverflowIconTooltip("Other recent apps")
        verifyTaskbarOverlayInsetsTouchability(
            ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
        )
    }

    @Test
    @TaskbarMode(PINNED)
    fun testKeyboardQuickSwitchLaunchesTaskAsDesktopApp() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createDesktopTask(createdTasks)

        assertThat(taskbarOverflowIconIndex).isEqualTo(initialIconCount)

        tapOverflowIcon()
        // Keyboard quick switch view is shown only after list of recent task is asynchronously
        // retrieved from the recents model.
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }

        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.isShownFromTaskbar })
            .isTrue()
        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.shownTaskIds() })
            .containsExactlyElementsIn(0..targetOverflowSize)

        runOnTaskbarUiThreadSync { keyboardQuickSwitchController.launchFocusedTask() }

        // `keyboardQuickSwitchController.launchFocusedTask()` will post a task to activate target
        // desk to `UI_HELPER_EXECUTOR`. Flush the executor to make sure the task runs before
        // verifying mocks.
        UI_HELPER_EXECUTOR.waitForIdleSync()

        val deskIdCaptor = argumentCaptor<Int>()
        val taskIdCaptor = argumentCaptor<Int>()
        val transitionCaptor = argumentCaptor<RemoteTransition>()
        val transitionSource = argumentCaptor<DesktopModeTransitionSource>()
        verify(systemUiProxySpy)
            ?.activateDesk(
                deskIdCaptor.capture(),
                transitionCaptor.capture(),
                taskIdCaptor.capture(),
                transitionSource.capture(),
            )
        assertThat(deskIdCaptor.firstValue).isEqualTo(0)
        assertThat(taskIdCaptor.firstValue).isEqualTo(0)
        assertThat(transitionCaptor.firstValue.remoteTransition)
            .isInstanceOf(SlideInRemoteTransition::class.java)
        assertThat(transitionSource.firstValue)
            .isEqualTo(DesktopModeTransitionSource.KEYBOARD_SHORTCUT)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testHotseatItemTasksNotShownInRecents() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        val hotseatItems = createHotseatItems(1)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createDesktopTaskWithTasksFromPackages(
            listOf("fake") +
                listOf(hotseatItems[0]?.targetPackage ?: "") +
                List(createdTasks - 2) { "fake" }
        )

        runOnTaskbarUiThreadSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
                emptyList(),
            )
        }

        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex).isEqualTo(initialIconCount + hotseatItems.size)
        assertThat(overflowItems)
            .containsExactlyElementsIn(listOf(0) + (2..targetOverflowSize + 1).toList())
    }

    @Test
    @TaskbarMode(PINNED)
    fun testHotseatItemTasksNotShownInKQS() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        val hotseatItems = createHotseatItems(1)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createDesktopTaskWithTasksFromPackages(
            listOf("fake") +
                listOf(hotseatItems[0]?.targetPackage ?: "") +
                List(createdTasks - 2) { "fake" }
        )

        runOnTaskbarUiThreadSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
                emptyList(),
            )
        }

        tapOverflowIcon()
        // Keyboard quick switch view is shown only after list of recent task is asynchronously
        // retrieved from the recents model.
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }

        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.isShownFromTaskbar })
            .isTrue()
        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.shownTaskIds() })
            .containsExactlyElementsIn(listOf(0) + (2..targetOverflowSize + 1).toList())
    }

    @Test
    @TaskbarMode(PINNED)
    fun testLimitMaxTaskbarIcons() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        createDesktopTask(5)

        runOnTaskbarUiThreadSync { taskbarUnitTestRule.activityContext.limitMaxTaskbarIconsNum(4) }
        assertThat(maxNumberOfTaskbarIcons).isAtMost(4)
        assertThat(currentNumberOfTaskbarIcons).isAtMost(4)

        runOnTaskbarUiThreadSync { taskbarUnitTestRule.activityContext.limitMaxTaskbarIconsNum(-1) }
        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isGreaterThan(4)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testFullscreenTasksNotShownInKQS() {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        val hotseatItems = createHotseatItems(1)

        val targetOverflowSize = 5
        val createdTasks = maxNumIconViews - initialIconCount + targetOverflowSize
        createFullscreenAndDesktopTasksFromPackages(
            listOf("fakeFullscreen"),
            listOf("fake") +
                listOf(hotseatItems[0]?.targetPackage ?: "") +
                List(createdTasks - 2) { "fake" },
        )

        runOnTaskbarUiThreadSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
                emptyList(),
            )
        }

        tapOverflowIcon()
        // Keyboard quick switch view is shown only after list of recent task is asynchronously
        // retrieved from the recents model.
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }

        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.isShownFromTaskbar })
            .isTrue()
        // Taskbar is in overflow by `targetOverflowSize`, so overflow UI should have
        // `targetOverflowSize + 1` items, to account for a spot in taskbar taken by the overflow
        // icon. Task IDs for running desktop apps start at 1 - 0 is used for fullscreen task.
        assertThat(getOnTaskbarUiThread { keyboardQuickSwitchController.shownTaskIds() })
            .containsExactlyElementsIn(listOf(1) + (3..targetOverflowSize + 2).toList())
    }

    @Test
    @TaskbarMode(PINNED)
    @EnableFlags(FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU)
    fun pinToTaskbarShortcut_unpinPinnedItem() {
        // Create two tasks and two pinned items.
        createDesktopTask(2)
        val hotseatItems = createHotseatItems(2)
        var shortcut: SystemShortcut<*>? = null
        var hotseatIcon: BubbleTextView? = null
        runOnTaskbarUiThreadSync {
            val taskbarView = setUpTaskbarAndModelCallback(hotseatItems)
            hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            shortcut =
                taskbarContext.controllers.taskbarPopupController.createPinShortcut(
                    taskbarContext,
                    hotseatIcon!!.tag as ItemInfo,
                    hotseatIcon,
                ) as SystemShortcut<*>
        }
        assertNotNull(shortcut)
        runOnTaskbarUiThreadSync { shortcut?.onClick(hotseatIcon) }

        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        runOnTaskbarUiThreadSync {}

        // After unpinning the first item, only the second app is left.
        assertThat(getHotseatItems().map { info -> info.title }).isEqualTo(listOf("Test App 1"))
        // The unpinned app doesn't have a task so the shown tasks won't change.
        assertThat(recentAppsController.shownTasks.map { it.tasks[0].key.id })
            .isEqualTo(listOf(0, 1))
    }

    @Test
    @TaskbarMode(PINNED)
    @EnableFlags(FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU)
    fun pinToTaskbarShortcut_unpinPinnedItemWithTask() {
        // Create two hotseat items with a task for both of them respectively.
        var hotseatItems =
            createHotseatItems(2).mapIndexed { idx, item -> TaskItemInfo(idx, item) }.toTypedArray()
        createDesktopTaskWithTasksFromPackages(hotseatItems.mapNotNull { it.targetPackage })
        var shortcut: SystemShortcut<*>? = null
        var hotseatIcon: BubbleTextView? = null
        runOnTaskbarUiThreadSync {
            val taskbarView = setUpTaskbarAndModelCallback(hotseatItems.map { it }.toTypedArray())
            hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            shortcut =
                taskbarContext.controllers.taskbarPopupController.createPinShortcut(
                    taskbarContext,
                    hotseatIcon!!.tag as ItemInfo,
                    hotseatIcon,
                ) as SystemShortcut<*>
        }
        // Before unpinning the app, both of the apps should be pinned and no shown task available.
        assertThat(getHotseatItems().map { info -> info.title })
            .isEqualTo(listOf("Test App 0", "Test App 1"))
        assertThat(recentAppsController.shownTasks.map { it.tasks[0].key.id })
            .isEqualTo(emptyList<Int>())
        assertNotNull(shortcut)
        runOnTaskbarUiThreadSync { shortcut?.onClick(hotseatIcon) }

        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        runOnTaskbarUiThreadSync {}

        // After unpinning the app, app 0 is removed and its task is shown as a recent task.
        assertThat(getHotseatItems().map { info -> info.title }).isEqualTo(listOf("Test App 1"))
        assertThat(recentAppsController.shownTasks.map { it.tasks[0].key.id }).isEqualTo(listOf(0))
    }

    @Test
    @TaskbarMode(PINNED)
    @EnableFlags(FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU)
    fun pinToTaskbarShortcut_pinRecentTask() {
        // Create two tasks and two pinned items.
        createDesktopTask(2)
        val hotseatItems = createHotseatItems(2)

        var shortcut: SystemShortcut<*>? = null
        var recentTaskIcon: BubbleTextView? = null
        runOnTaskbarUiThreadSync {
            val taskbarView = setUpTaskbarAndModelCallback(hotseatItems)
            // Get the first recent task icon
            recentTaskIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is GroupTask
                }
            val recentTaskInfo =
                createTaskItemInfo(
                    recentTaskIcon!!.tag as SingleTask,
                    WorkspaceItemInfo().apply {
                        title = "Test App 2"
                        intent = Intent().apply { `package` = "fake" }
                    },
                )
            shortcut =
                taskbarContext.controllers.taskbarPopupController.createPinShortcut(
                    taskbarContext,
                    recentTaskInfo,
                    recentTaskIcon,
                ) as SystemShortcut<*>
        }
        assertNotNull(shortcut)
        runOnTaskbarUiThreadSync { shortcut?.onClick(recentTaskIcon) }

        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {}
        runOnTaskbarUiThreadSync {}

        // After pinning the recent task, it should be included in the hotseat items.
        assertThat(getHotseatItems().map { info -> info.title })
            .isEqualTo(listOf("Test App 0", "Test App 1", "Test App 2"))
        // As the task is pinned, the shown tasks should remove it from the list
        assertThat(recentAppsController.shownTasks.map { it.tasks[0].key.id }).isEqualTo(listOf(1))
    }

    @Test
    @TaskbarMode(PINNED)
    fun recentTaskIconHasClosePopupOption() {
        // Create two tasks and two pinned items.
        createDesktopTask(2)
        val hotseatItems = createHotseatItems(2)

        var shortcut: SystemShortcut<*>? = null
        runOnTaskbarUiThreadSync {
            val taskbarView = setUpTaskbarAndModelCallback(hotseatItems)
            // Get the first recent task icon
            val recentTaskIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is GroupTask
                }
            assertNotNull(recentTaskIcon)

            val recentTaskInfo =
                createTaskItemInfo(
                    recentTaskIcon!!.tag as SingleTask,
                    WorkspaceItemInfo().apply {
                        title = "Test App 2"
                        intent = Intent().apply { `package` = "fake" }
                    },
                )
            shortcut =
                taskbarContext.controllers.taskbarPopupController
                    .createCloseAppTaskbarShortcutFactory()
                    ?.getShortcut(taskbarContext, recentTaskInfo, recentTaskIcon!!)
        }
        assertThat(shortcut).isNotNull()
    }

    @Test
    @TaskbarMode(PINNED)
    fun pinnedAppIconWithDesktopTaskHasClosePopupOption() {
        // Create two hotseat items with a task for both of them respectively.
        var hotseatItems =
            createHotseatItems(2).mapIndexed { idx, item -> TaskItemInfo(idx, item) }.toTypedArray()
        createDesktopTaskWithTasksFromPackages(hotseatItems.mapNotNull { it.targetPackage })

        var shortcut: SystemShortcut<*>? = null
        runOnTaskbarUiThreadSync {
            val taskbarView = setUpTaskbarAndModelCallback(hotseatItems.map { it }.toTypedArray())
            val hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            assertNotNull(hotseatIcon)
            shortcut =
                taskbarContext.controllers.taskbarPopupController
                    .createCloseAppTaskbarShortcutFactory()
                    ?.getShortcut(taskbarContext, hotseatIcon!!.tag as ItemInfo, hotseatIcon!!)
        }
        assertThat(shortcut).isNotNull()
    }

    @Test
    @TaskbarMode(PINNED)
    fun pinnedAppIconWithFullscreenTaskDoesntHaveClosePopupOption() {
        // Create two hotseat items with a task for both of them respectively.
        var hotseatItems =
            createHotseatItems(2).mapIndexed { idx, item -> TaskItemInfo(idx, item) }.toTypedArray()
        createFullscreenAndDesktopTasksFromPackages(
            hotseatItems.mapNotNull { it.targetPackage },
            emptyList(),
        )

        var shortcut: SystemShortcut<*>? = null
        runOnTaskbarUiThreadSync {
            val taskbarView = setUpTaskbarAndModelCallback(hotseatItems.map { it }.toTypedArray())
            val hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            assertNotNull(hotseatIcon)
            shortcut =
                taskbarContext.controllers.taskbarPopupController
                    .createCloseAppTaskbarShortcutFactory()
                    ?.getShortcut(taskbarContext, hotseatIcon!!.tag as ItemInfo, hotseatIcon!!)
        }
        assertThat(shortcut).isNull()
    }

    @Test
    @TaskbarMode(PINNED)
    fun pinnedAppIconWithoutDesktopTaskDoesNotHaveClosePopupOption() {
        // Create two hotseat items with a task for both of them respectively.
        var hotseatItems = createHotseatItems(2)

        var shortcut: SystemShortcut<*>? = null
        runOnTaskbarUiThreadSync {
            val taskbarView = setUpTaskbarAndModelCallback(hotseatItems.map { it }.toTypedArray())
            val hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            assertNotNull(hotseatIcon)
            shortcut =
                taskbarContext.controllers.taskbarPopupController
                    .createCloseAppTaskbarShortcutFactory()
                    ?.getShortcut(taskbarContext, hotseatIcon!!.tag as ItemInfo, hotseatIcon!!)
        }
        assertThat(shortcut).isNull()
    }

    private fun setUpTaskbarAndModelCallback(hotseatItems: Array<WorkspaceItemInfo>): TaskbarView {
        context.preloadModelData(*hotseatItems)
        val taskbarView: TaskbarView =
            taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
        taskbarView.updateItems(hotseatItems, recentAppsController.shownTasks, emptyList())

        context.appComponent.testableModelState.homeRepo.workspaceState.forEach(
            getTaskbarUiThread()
        ) {
            recentAppsController?.updateHotseatItemInfos(getHotseatItems().toTypedArray())
        }
        return taskbarView
    }

    private fun createDesktopTask(tasksToAdd: Int) {
        createDesktopTaskWithTasksFromPackages((0..<tasksToAdd).map { "fake" })
    }

    private fun createDesktopTaskWithTasksFromPackages(packages: List<String>) {
        createFullscreenAndDesktopTasksFromPackages(emptyList(), packages)
    }

    private fun createFullscreenAndDesktopTasksFromPackages(
        fullscreenPackages: List<String>,
        desktopPackages: List<String>,
    ) {
        val defaultDisplayId = context.displayId
        val tasks: List<GroupTask> =
            fullscreenPackages.mapIndexed({ index, p ->
                SingleTask(
                    Task(
                        Task.TaskKey(
                            index,
                            WindowConfiguration.WINDOWING_MODE_FULLSCREEN,
                            Intent().apply { `package` = p },
                            ComponentName(p, ""),
                            Process.myUserHandle().identifier,
                            2000,
                        )
                    )
                )
            })

        val desktopTasks =
            desktopPackages.mapIndexed({ index, p ->
                Task(
                    Task.TaskKey(
                        index + fullscreenPackages.size,
                        WindowConfiguration.WINDOWING_MODE_FREEFORM,
                        Intent().apply { `package` = p },
                        ComponentName(p, ""),
                        Process.myUserHandle().identifier,
                        2000,
                    )
                )
            })

        mockRecentsModelHelper.updateRecentTasks(
            tasks + listOf(DesktopTask(deskId = 0, defaultDisplayId, desktopTasks))
        )
        for (task in 1..desktopTasks.size) {
            systemUiProxySpy?.desktopTaskListeners?.listeners?.forEach {
                IDesktopTaskListener.Stub.asInterface(it)
                    .onTasksVisibilityChanged(defaultDisplayId, task)
            }
        }
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }
    }

    private val navButtonEndSpacing: Int
        get() {
            return taskbarUnitTestRule.activityContext.resources.getDimensionPixelSize(
                taskbarUnitTestRule.activityContext.deviceProfile.inv.inlineNavButtonsEndSpacing
            )
        }

    private val taskbarOverflowIconIndex: Int
        get() {
            return getOnTaskbarUiThread {
                taskbarViewController.iconViews.indexOfFirst { it is TaskbarOverflowView }
            }
        }

    private val maxNumberOfTaskbarIcons: Int
        get() = getOnTaskbarUiThread { taskbarViewController.maxNumIconViews }

    private val currentNumberOfTaskbarIcons: Int
        get() = getOnTaskbarUiThread { taskbarViewController.iconViews.size }

    private val taskbarIconsCentered: Boolean
        get() {
            return getOnTaskbarUiThread {
                val iconLayoutBounds =
                    taskbarViewController.transientTaskbarIconLayoutBoundsInParent
                val availableWidth =
                    taskbarUnitTestRule.activityContext.deviceProfile.deviceProperties.widthPx
                iconLayoutBounds.left - (availableWidth - iconLayoutBounds.right) < 2
            }
        }

    private val taskbarEndMargin: Int
        get() {
            return getOnTaskbarUiThread {
                taskbarUnitTestRule.activityContext.deviceProfile.deviceProperties.widthPx -
                    taskbarViewController.transientTaskbarIconLayoutBoundsInParent.right
            }
        }

    private val overflowItems: List<Int>
        get() {
            return getOnTaskbarUiThread {
                val overflowIcon =
                    taskbarViewController.iconViews.firstOrNull { it is TaskbarOverflowView }

                if (overflowIcon is TaskbarOverflowView) {
                    overflowIcon.itemIds
                } else {
                    emptyList()
                }
            }
        }

    private fun runningAppIconIndex(taskId: Int): Int {
        return getOnTaskbarUiThread {
            taskbarViewController.iconViews.indexOfFirst {
                it is BubbleTextView &&
                    it.tag is SingleTask &&
                    (it.tag as SingleTask)?.task?.key?.id == taskId
            }
        }
    }

    private fun tapOverflowIcon() {
        runOnTaskbarUiThreadSync {
            val overflowIcon =
                taskbarViewController.iconViews.firstOrNull { it is TaskbarOverflowView }
            assertThat(overflowIcon?.callOnClick()).isTrue()
        }
    }

    /**
     * Verifies that when hovering over the overflow icon, the tooltip popup is shown with the
     * [expectedText], or verifies that the tooltip is not shown if [expectedText] is null.
     */
    private fun verifyOverflowIconTooltip(expectedText: String?) {
        val overflowIcon = getOnTaskbarUiThread {
            taskbarViewController.iconViews
                .filterIsInstance<TaskbarOverflowView>()
                .firstOrNull()
                ?.also {
                    it.dispatchGenericMotionEvent(
                        MotionEvent.obtain(0, 0, ACTION_HOVER_ENTER, 0f, 0f, 0)
                    )
                }
        }

        val isPopupOpen =
            AbstractFloatingView.hasOpenView(
                taskbarContext,
                AbstractFloatingView.TYPE_ON_BOARD_POPUP,
            )

        if (expectedText == null) {
            assertThat(isPopupOpen).isFalse()
        } else {
            assertThat(isPopupOpen).isTrue()
            val actualText = getOnTaskbarUiThread { overflowIcon?.textForTooltipPopup }
            assertThat(actualText).isEqualTo(expectedText)
        }

        runOnTaskbarUiThreadSync {
            overflowIcon?.dispatchGenericMotionEvent(
                MotionEvent.obtain(0, 0, ACTION_HOVER_EXIT, 0f, 0f, 0)
            )
        }
    }

    fun verifyTaskbarOverlayInsetsTouchability(expectedTouchableInsets: Int) {
        val overlayController by taskbarUnitTestRule.delegate { it.taskbarOverlayController }
        val insetsInfo = ViewTreeObserver.InternalInsetsInfo()
        runOnTaskbarUiThreadSync { overlayController.updateInsetsTouchability(insetsInfo) }
        assertThat(insetsInfo)
            .isEqualTo(
                ViewTreeObserver.InternalInsetsInfo().also {
                    it.setTouchableInsets(expectedTouchableInsets)
                }
            )
    }

    /**
     * Adds enough running apps for taskbar to enter overflow of `targetOverflowSize`, and verifies
     * * max number of icons in the taskbar remains unchanged
     * * number of icons in the taskbar is at most max number of icons
     * * whether the taskbar overflow icon is shown, and its position in taskbar.
     *
     * Returns max number of icons.
     */
    private fun addRunningAppsAndVerifyOverflowState(targetOverflowSize: Int): Int {
        val maxNumIconViews = maxNumberOfTaskbarIcons
        assertThat(maxNumIconViews).isGreaterThan(0)
        // Assume there are at least all apps and divider icon, as they would appear once running
        // apps are added, even if not present initially.
        val initialIconCount = currentNumberOfTaskbarIcons.coerceAtLeast(2)
        assertThat(initialIconCount).isLessThan(maxNumIconViews)

        createDesktopTask(maxNumIconViews - initialIconCount + targetOverflowSize)

        assertThat(maxNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(currentNumberOfTaskbarIcons).isEqualTo(maxNumIconViews)
        assertThat(taskbarOverflowIconIndex)
            .isEqualTo(if (targetOverflowSize > 0) initialIconCount else -1)
        if (targetOverflowSize > 0) {
            assertThat(overflowItems).containsExactlyElementsIn(0..targetOverflowSize)
        }
        return maxNumIconViews
    }

    private fun getHotseatItems() =
        context.appComponent.testableModelState.homeRepo.workspaceState.value
            .filter { it.container == CONTAINER_HOTSEAT }
            .filterIsInstance<WorkspaceItemInfo>()
}
