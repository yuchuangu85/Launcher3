/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.Rect
import android.os.Process
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.View
import android.view.View.GONE
import android.view.View.MeasureSpec
import android.view.View.VISIBLE
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget
import com.android.launcher3.Flags.FLAG_ENABLE_TASKBAR_DRAG_TO_REMOVE
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.model.data.TaskItemInfo.Companion.isSameItem
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.testing.FakeModelWriter
import com.android.launcher3.model.testing.WriterAction
import com.android.launcher3.popup.ArrowPopup.CLOSE_DURATION_U
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createTestWorkspaceItem
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext
import com.android.launcher3.taskbar.rules.MockedRecentsModelHelper
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext_ModifiedComponent
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.android.launcher3.views.Snackbar
import com.android.quickstep.RecentsModel
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.SingleTask
import com.android.systemui.shared.recents.model.Task
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@MutatedComponent(target = TaskbarWindowSandboxContext_ModifiedComponent::class)
class TaskbarViewDragDropControllerTest {
    private val TEST_APP = TaskbarViewTestUtil.createAppInfo(0)
    private val TEST_WORKSPACE_ITEM = TaskbarViewTestUtil.createHotseatWorkspaceItem(1)
    private val TEST_OPEN_ANIMATION_DURATION = 15L

    private val mockRecentsModelHelper: MockedRecentsModelHelper = MockedRecentsModelHelper()
    @BindValue val recentsModel: RecentsModel by mockRecentsModelHelper

    private val modelWriter = FakeModelWriter()
    @BindValue
    val launcherModel: LauncherModel = mock {
        on { getWriter(any(), any(), any()) } doReturn modelWriter
    }
    private val modelCallbacks: TaskbarModelCallbacks = mock {
        on { hotseatItems } doReturn IntSparseArrayMap()
    }

    @get:Rule(order = 0)
    val context =
        TaskbarWindowSandboxContext.create(
            params = SandboxParams(builderBase = mutatedComponentBuilder())
        )
    @get:Rule(order = 1) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 2) val setFlagsRule = SetFlagsRule()
    @get:Rule(order = 3)
    val taskbarUnitTestRule = TaskbarUnitTestRule(context, this::onControllersInitialized)

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

    private val activityContext
        get() = taskbarUnitTestRule.activityContext

    private val desktopVisibilityController: DesktopVisibilityController
        get() = DesktopVisibilityController.INSTANCE[context]

    private val overlayContext: TaskbarOverlayContext
        get() = activityContext.controllers.taskbarOverlayController.requestWindow()

    private val TaskbarOverlayContext.snackbar
        get() = AbstractFloatingView.getOpenView<Snackbar>(this, AbstractFloatingView.TYPE_SNACKBAR)

    private val Snackbar.actionView
        get() = findViewById<TextView>(R.id.action)

    private val taskbarViewController by taskbarUnitTestRule.delegate { it.taskbarViewController }
    private val taskbarDragController by taskbarUnitTestRule.delegate { it.taskbarDragController }
    private val recentAppsController by
        taskbarUnitTestRule.delegate { it.taskbarRecentAppsController }

    private val taskbarViewDragDropController by
        taskbarUnitTestRule.delegate { it.taskbarViewDragDropController }

    private val overflowIconRect = Rect(0, 0, 20, 20)

    @Before
    fun setup() {
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)
        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }
    }

    @Test
    fun pinned_onDropWithNewAppInfo_addOrMoveItemInDatabase() {
        val dragObject = createDragObject(TEST_APP)
        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        val action = modelWriter.actions.last() as WriterAction.AddItem
        assertThat(action.item.targetComponent).isEqualTo(TEST_APP.componentName)
        assertThat(action.item.user).isEqualTo(TEST_APP.user)
        assertThat(action.container).isEqualTo(CONTAINER_HOTSEAT)
    }

    @Test
    fun pinned_onDropWithExistingItem_addOrMoveItemInDatabase() {
        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(1, TEST_WORKSPACE_ITEM)
        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems

        val dragObject = createDragObject(TEST_WORKSPACE_ITEM)

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        val action = modelWriter.actions.last() as WriterAction.ModifyItem
        assertThat(action.item.targetComponent).isEqualTo(TEST_WORKSPACE_ITEM.targetComponent)
        assertThat(action.item.user).isEqualTo(TEST_WORKSPACE_ITEM.user)
    }

    @Test
    fun pinned_onDrop_reorderLeftToRight_shiftsItemsLeft() {
        // Setup: Items [A(0), B(1), C(2)]
        val itemA = createHotseatItem(0)
        val itemB = createHotseatItem(1)
        val itemC = createHotseatItem(2)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag A (Index 0) to Index 2 (Where C is)
        val dragObject = createDragObject(itemA)
        taskbarViewDragDropController.targetPinIndex = 2

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        assertThat(modelWriter.actions).hasSize(3)
        assertThat((modelWriter.actions[0] as WriterAction.ModifyItem).item).isEqualTo(itemC)
        assertThat((modelWriter.actions[1] as WriterAction.ModifyItem).item).isEqualTo(itemB)
        assertThat((modelWriter.actions[2] as WriterAction.ModifyItem).item).isEqualTo(itemA)
    }

    @Test
    fun pinned_onDrop_reorderLeftToRight_shiftStopsAtEmptyItems() {
        // Setup: Items [A(0), B(1), C(3), D(4)]
        val itemA = createHotseatItem(0)
        val itemB = createHotseatItem(1)
        val itemC = createHotseatItem(3)
        val itemD = createHotseatItem(4)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)
        hotseatInfos.append(3, itemD)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag A (Index 0) to Index 3 (Where D is)
        val dragObject = createDragObject(itemA)
        taskbarViewDragDropController.targetPinIndex = 3

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        assertThat(modelWriter.actions).hasSize(3)
        assertThat((modelWriter.actions[0] as WriterAction.ModifyItem).item).isEqualTo(itemD)
        assertThat((modelWriter.actions[1] as WriterAction.ModifyItem).item).isEqualTo(itemC)
        assertThat((modelWriter.actions[2] as WriterAction.ModifyItem).item).isEqualTo(itemA)
        assertThat(modelWriter.actions.map { (it as WriterAction.ModifyItem).item })
            .doesNotContain(itemB)
    }

    @Test
    fun pinned_onDrop_reorderRightToLeft_shiftsItemsRight() {
        // Setup: Items [A(0), B(1), C(2)]
        val itemA = createHotseatItem(0, 0)
        val itemB = createHotseatItem(1, 1)
        val itemC = createHotseatItem(2, 2)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag C (Index 2) to Index 0 (Where A is)
        val dragObject = createDragObject(itemC)
        taskbarViewDragDropController.targetPinIndex = 0

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        assertThat(modelWriter.actions).hasSize(3)
        assertThat((modelWriter.actions[0] as WriterAction.ModifyItem).item).isEqualTo(itemA)
        assertThat((modelWriter.actions[1] as WriterAction.ModifyItem).item).isEqualTo(itemB)
        assertThat((modelWriter.actions[2] as WriterAction.ModifyItem).item).isEqualTo(itemC)
    }

    @Test
    fun pinned_onDrop_reorderRightToLeft_shiftsStopAtEmptyItems() {
        // Setup: Items [A(0), B(1), C(3), D(4)]
        val itemA = createHotseatItem(0, 0)
        val itemB = createHotseatItem(1, 1)
        val itemC = createHotseatItem(3, 2)
        val itemD = createHotseatItem(4, 3)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(2, itemC)
        hotseatInfos.append(3, itemD)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag D (Index 3) to Index 0 (Where A is)
        val dragObject = createDragObject(itemD)
        taskbarViewDragDropController.targetPinIndex = 0

        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        assertThat(modelWriter.actions).hasSize(3)
        assertThat((modelWriter.actions[0] as WriterAction.ModifyItem).item).isEqualTo(itemA)
        assertThat((modelWriter.actions[1] as WriterAction.ModifyItem).item).isEqualTo(itemB)
        assertThat((modelWriter.actions[2] as WriterAction.ModifyItem).item).isEqualTo(itemD)
        assertThat(modelWriter.actions.map { (it as WriterAction.ModifyItem).item })
            .doesNotContain(itemC)
    }

    @Test
    fun pinned_onDrop_insertNewItem_shiftsItemsRight() {
        // Setup: Items [A(0), B(1)]
        val itemA = createHotseatItem(0)
        val itemB = createHotseatItem(1)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag New App to Index 0
        val dragObject = createDragObject(TEST_APP)
        taskbarViewDragDropController.targetPinIndex = 0
        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        assertThat(modelWriter.actions).hasSize(3)
        assertThat((modelWriter.actions[0] as WriterAction.ModifyItem).item).isEqualTo(itemA)
        assertThat((modelWriter.actions[1] as WriterAction.ModifyItem).item).isEqualTo(itemB)

        val action = modelWriter.actions[2] as WriterAction.AddItem
        assertThat(action.item.targetComponent).isEqualTo(TEST_APP.componentName)
        assertThat(action.container).isEqualTo(CONTAINER_HOTSEAT)
    }

    @Test
    fun pinned_onDrop_insertNewItem_shiftsItemsLeft() {
        // Setup: Items [A(1), B(2)]
        val itemA = createHotseatItem(1)
        val itemB = createHotseatItem(2)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag New App to Index 2
        val dragObject = createDragObject(TEST_APP)
        taskbarViewDragDropController.targetPinIndex = 2
        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        val modifiedItems = modelWriter.actions.take(2).map { (it as WriterAction.ModifyItem).item }
        assertThat(modifiedItems).containsExactly(itemA, itemB)

        val action = modelWriter.actions[2] as WriterAction.AddItem
        assertThat(action.item.targetComponent).isEqualTo(TEST_APP.componentName)
        assertThat(action.container).isEqualTo(CONTAINER_HOTSEAT)
    }

    @Test
    fun pinned_onDrop_reorderRightToLeft_shiftItemsLeftIfBlankAvailable() {
        // Setup: Items [A(0), B(1), C(4), D(5)]
        val itemA = createHotseatItem(0, 0)
        val itemB = createHotseatItem(1, 1)
        val itemC = createHotseatItem(4, 2)
        val itemD = createHotseatItem(5, 3)

        val hotseatInfos = IntSparseArrayMap<ItemInfo>()
        hotseatInfos.append(0, itemA)
        hotseatInfos.append(1, itemB)
        hotseatInfos.append(4, itemC)
        hotseatInfos.append(5, itemD)

        doReturn(hotseatInfos).whenever(modelCallbacks).hotseatItems
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)

        // Action: Drag D (Index 5) to Index 2 (Empty)
        val dragObject = createDragObject(itemD)
        taskbarViewDragDropController.targetPinIndex = 2
        taskbarViewDragDropController.taskbarPinningDropTarget.onDrop(dragObject, null)

        assertThat(modelWriter.actions).hasSize(1)
        val action = modelWriter.actions[0] as WriterAction.ModifyItem
        assertThat(action.item).isEqualTo(itemD)
        assertThat(action.container).isEqualTo(CONTAINER_HOTSEAT)
        assertThat(action.screenId).isEqualTo(3)

        val modifiedItems = modelWriter.actions.map { (it as WriterAction.ModifyItem).item }
        assertThat(modifiedItems).doesNotContain(itemC)
        assertThat(modifiedItems).doesNotContain(itemA)
        assertThat(modifiedItems).doesNotContain(itemB)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_TO_REMOVE)
    fun unpinned_onDragEnter_showsTooltip() {
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)
        val dragObject = createDragObjectWithView(TEST_WORKSPACE_ITEM)

        getOnTaskbarUiThread {
            taskbarViewDragDropController.unpinDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(TEST_OPEN_ANIMATION_DURATION)
        }

        val tooltip = taskbarViewDragDropController.tooltipController.activeTooltipView
        assertThat(tooltip).isNotNull()
        assertThat((tooltip as View).alpha).isEqualTo(1f)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_TO_REMOVE)
    fun unpinned_onDragOver_updatesTooltipPosition() {
        taskbarViewDragDropController.setUpCallbacks(modelCallbacks)
        val dragObject = createDragObjectWithView(TEST_WORKSPACE_ITEM)

        getOnTaskbarUiThread {
            taskbarViewDragDropController.unpinDropTarget.onDragEnter(dragObject)
        }
        val tooltip = taskbarViewDragDropController.tooltipController.activeTooltipView as View
        val initialX = tooltip.x

        dragObject.x = 100
        dragObject.y = 100

        getOnTaskbarUiThread {
            taskbarViewDragDropController.unpinDropTarget.onDragOver(dragObject)
        }

        assertThat(tooltip.x).isNotEqualTo(initialX)
    }

    private fun createHotseatItem(screenId: Int, id: Int = 0): ItemInfo {
        val item = TaskbarViewTestUtil.createHotseatWorkspaceItem(id)
        item.screenId = screenId
        return item
    }

    @Test
    fun onDragOver_pointOnOverflowIcon_openOverflowContainer() {
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)

        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()
    }

    @Test
    fun onDragOver_pointNotOnOverflowIcon_closeOverflowContainer() {
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()

        // Simulate dragging out of the overflow icon.
        dragObject.x = -100
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragOver(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            taskbarViewDragDropController.overflowContainerAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(CLOSE_DURATION_U.toLong())
        }
        assertThat(taskbarViewController.isOverflowContainerShowing).isFalse()
    }

    @Test
    fun onDragOver_dragEnterOverflow_cancelsCloseAlarm() {
        taskbarViewDragDropController.addOverflowDropTarget(
            taskbarDragController,
            mock<TaskbarViewDragDropController.PinnedAppsContainerDelegate>(),
        )
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()

        // Then simulate dragging into the overflow container.
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragExit(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            dragViewIntoOverflowContainer(dragObject)
        }

        // Verify the close alarm is cancelled and the overflow container is still opened.
        assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isFalse()
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()
    }

    @Test
    fun onDragOver_dragExitOverflow_closeOverflowContainer() {
        taskbarViewDragDropController.addOverflowDropTarget(
            taskbarDragController,
            mock<TaskbarViewDragDropController.PinnedAppsContainerDelegate>(),
        )
        val dragObject = createDragObject(TEST_APP)
        val overflowIcon = setUpPinnedOverflow()
        dragViewOntoOverflowIconToOpenContainer(dragObject, overflowIcon)
        assertThat(taskbarViewController.isOverflowContainerShowing).isTrue()

        // Simulate dragging into the overflow container and then dragging out from it.
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragExit(dragObject)
            dragViewIntoOverflowContainer(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending())
                .isFalse()
            requireNotNull(taskbarViewDragDropController.overflowPinningDropTarget)
                .onDragExit(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            taskbarViewDragDropController.overflowContainerAlarm.finishAlarm()
            animatorTestRule.advanceTimeBy(CLOSE_DURATION_U.toLong())
        }

        // Verify the close alarm is run and the overflow container is closed.
        assertThat(taskbarViewController.isOverflowContainerShowing).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_TO_REMOVE)
    fun unpinned_onDrop_undoClicked_abortsDelete() {
        val hotseatItems = createTestHotseatItemsWithDifferentPackages(2) as Array<ItemInfo>
        updateTaskbarHotseatItems(hotseatItems)
        whenever(modelCallbacks.commitRunningAppsToUI()).then {
            updateTaskbarHotseatItems(hotseatItems)
        }

        val draggedItem = hotseatItems[1]
        val dragView = getItemView(draggedItem)
        val dragObject = createDragObject(draggedItem)

        runOnTaskbarUiThreadSync {
            assertThat(dragView).isNotNull()
            taskbarViewDragDropController.onTaskbarItemViewDragStart(dragView!!)
            assertThat(dragView.visibility).isEqualTo(GONE)

            taskbarViewDragDropController.unpinDropTarget.onDrop(dragObject, null)
            taskbarViewDragDropController.onTaskbarItemViewDragEnd(dragView)
        }

        assertThat(modelWriter.actions).hasSize(0)

        assertThat(getItemView(draggedItem)).isNull()
        assertThat(getRunningTaskIconForItem(draggedItem)).isNull()

        // Undo deletion.
        val snackbar = getOnTaskbarUiThread { overlayContext.snackbar }
        assertThat(snackbar).isNotNull()
        runOnTaskbarUiThreadSync { snackbar!!.actionView.performClick() }
        assertThat(modelWriter.actions).hasSize(0)

        assertThat(getItemView(draggedItem)?.visibility).isEqualTo(VISIBLE)
        assertThat(getRunningTaskIconForItem(draggedItem)).isNull()

        updateTaskbarHotseatItems(hotseatItems)
        assertThat(getItemView(draggedItem)?.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_TO_REMOVE)
    fun unpinned_onDrop_onDismiss_commitsDelete() {
        val hotseatItems = createTestHotseatItemsWithDifferentPackages(2) as Array<ItemInfo>
        updateTaskbarHotseatItems(hotseatItems)
        whenever(modelCallbacks.commitRunningAppsToUI()).then {
            updateTaskbarHotseatItems(hotseatItems)
        }

        val draggedItem = hotseatItems[1]
        val dragView = getItemView(draggedItem)
        val dragObject = createDragObject(draggedItem)

        runOnTaskbarUiThreadSync {
            assertThat(dragView).isNotNull()
            taskbarViewDragDropController.onTaskbarItemViewDragStart(dragView!!)
            assertThat(dragView.visibility).isEqualTo(GONE)

            taskbarViewDragDropController.unpinDropTarget.onDrop(dragObject, null)
            taskbarViewDragDropController.onTaskbarItemViewDragEnd(dragView)
        }

        assertThat(modelWriter.actions).hasSize(0)

        assertThat(getItemView(draggedItem)).isNull()
        assertThat(getRunningTaskIconForItem(draggedItem)).isNull()

        val snackbar = getOnTaskbarUiThread { overlayContext.snackbar }
        assertThat(snackbar).isNotNull()

        runOnTaskbarUiThreadSync { snackbar!!.close(false) }

        assertThat(modelWriter.actions).hasSize(1)

        assertThat(modelWriter.actions).contains(WriterAction.DeleteItem(draggedItem))

        val closedSnackbar = getOnTaskbarUiThread { overlayContext.snackbar }
        assertThat(closedSnackbar).isNull()

        updateTaskbarHotseatItems(hotseatItems.sliceArray(0..0))

        assertThat(getItemView(draggedItem)).isNull()
        assertThat(getRunningTaskIconForItem(draggedItem)).isNull()

        updateTaskbarHotseatItems(hotseatItems)
        assertThat(getItemView(draggedItem)?.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_TO_REMOVE)
    fun unpinned_onDrop_itemWithRunningTasks_undoClicked_abortsDelete() {
        val hotseatItems: Array<ItemInfo> =
            createTestHotseatItemsWithDifferentPackages(2)
                .mapIndexed { index, it -> TaskItemInfo(index, it) }
                .toTypedArray()
        createDesktopTasksFromPackages(hotseatItems.mapNotNull { it.targetPackage })
        updateTaskbarHotseatItems(hotseatItems)
        whenever(modelCallbacks.commitRunningAppsToUI()).then {
            updateTaskbarHotseatItems(hotseatItems)
        }

        val draggedItem = hotseatItems[1]
        val dragView = getItemView(draggedItem)
        val dragObject = createDragObject(draggedItem)

        runOnTaskbarUiThreadSync {
            assertThat(dragView).isNotNull()
            taskbarViewDragDropController.onTaskbarItemViewDragStart(dragView!!)
            assertThat(dragView.visibility).isEqualTo(GONE)

            taskbarViewDragDropController.unpinDropTarget.onDrop(dragObject, null)
            taskbarViewDragDropController.onTaskbarItemViewDragEnd(dragView)
        }

        assertThat(modelWriter.actions).hasSize(0)

        // Verify that the taskbar is showing the recent task for drag view.
        assertThat(getRunningTaskIconForItem(draggedItem)?.visibility).isEqualTo(VISIBLE)

        // Verify that taskbar does not contain a pinned view for dragged item.
        assertThat(getItemView(draggedItem)).isNull()

        // Undo deletion.
        val snackbar = getOnTaskbarUiThread { overlayContext.snackbar }
        assertThat(snackbar).isNotNull()
        runOnTaskbarUiThreadSync { snackbar!!.actionView.performClick() }
        assertThat(modelWriter.actions).hasSize(0)

        // Verify that pinned item view is back in taskbar.
        assertThat(getItemView(draggedItem)?.visibility).isEqualTo(VISIBLE)

        // Recent task view is gone.
        assertThat(getRunningTaskIconForItem(draggedItem)).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_TO_REMOVE)
    fun unpinned_onDrop_itemWithRunningTasks_onDismiss_commitsDelete() {
        val hotseatItems: Array<ItemInfo> =
            createTestHotseatItemsWithDifferentPackages(2)
                .mapIndexed { index, it -> TaskItemInfo(index, it) }
                .toTypedArray()
        createDesktopTasksFromPackages(hotseatItems.mapNotNull { it.targetPackage })
        updateTaskbarHotseatItems(hotseatItems)
        whenever(modelCallbacks.commitRunningAppsToUI()).then {
            updateTaskbarHotseatItems(hotseatItems)
        }

        val draggedItem = hotseatItems[1]
        val dragView = getItemView(draggedItem)
        val dragObject = createDragObject(draggedItem)

        runOnTaskbarUiThreadSync {
            assertThat(dragView).isNotNull()
            taskbarViewDragDropController.onTaskbarItemViewDragStart(dragView!!)
            assertThat(dragView.visibility).isEqualTo(GONE)

            taskbarViewDragDropController.unpinDropTarget.onDrop(dragObject, null)
            taskbarViewDragDropController.onTaskbarItemViewDragEnd(dragView)
        }

        assertThat(modelWriter.actions).hasSize(0)

        // Verify that the taskbar is showing the recent task for drag view.
        assertThat(getRunningTaskIconForItem(draggedItem)?.visibility).isEqualTo(VISIBLE)

        // Verify that taskbar does not contain a pinned view for dragged item.
        assertThat(getItemView(draggedItem)).isNull()

        val snackbar = getOnTaskbarUiThread { overlayContext.snackbar }
        assertThat(snackbar).isNotNull()

        runOnTaskbarUiThreadSync { snackbar!!.close(false) }

        assertThat(modelWriter.actions).hasSize(1)
        assertThat(modelWriter.actions).contains(WriterAction.DeleteItem(draggedItem))

        val closedSnackbar = getOnTaskbarUiThread { overlayContext.snackbar }
        assertThat(closedSnackbar).isNull()

        updateTaskbarHotseatItems(hotseatItems.sliceArray(0..0))

        assertThat(getRunningTaskIconForItem(draggedItem)).isNotNull()
        assertThat(getItemView(draggedItem)).isNull()

        updateTaskbarHotseatItems(hotseatItems)
        assertThat(getItemView(draggedItem)?.visibility).isEqualTo(VISIBLE)
    }

    private fun createDragObject(info: ItemInfo): DropTarget.DragObject {
        val dragObject = DropTarget.DragObject(context)
        dragObject.dragInfo = info
        dragObject.dragView = mock<DragView>()
        return dragObject
    }

    private fun createDragObjectWithView(info: ItemInfo): DropTarget.DragObject {
        val dragObject = createDragObject(info)
        val mockDragView = mock<DragView>()

        whenever(mockDragView.measuredHeight).thenReturn(100)
        whenever(mockDragView.measuredWidth).thenReturn(100)
        whenever(mockDragView.dragRegion).thenReturn(Rect(0, 0, 100, 100))

        dragObject.dragView = mockDragView

        dragObject.x = 50
        dragObject.y = 50

        return dragObject
    }

    private fun setUpPinnedOverflow(): TaskbarOverflowView {
        val taskbarView = getOnTaskbarUiThread {
            val view = activityContext.dragLayer.findViewById<TaskbarView>(R.id.taskbar_view)
            view.updateItems(
                createHotseatItems(activityContext.deviceProfile.inv.numShownHotseatIcons + 2),
                emptyList(),
                emptyList(),
            )
            view
        }
        assertThat(taskbarView.taskbarPinnedOverflowView).isNotNull()
        val overflowIcon = getOnTaskbarUiThread {
            val overflowIcon = requireNotNull(taskbarView.taskbarPinnedOverflowView)
            overflowIcon.measure(
                MeasureSpec.makeMeasureSpec(overflowIconRect.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(overflowIconRect.height(), MeasureSpec.EXACTLY),
            )
            overflowIcon.layout(
                overflowIconRect.left,
                overflowIconRect.top,
                overflowIconRect.right,
                overflowIconRect.bottom,
            )
            overflowIcon
        }
        return overflowIcon
    }

    private fun mockDragViewToOverViewBounds(dragObject: DropTarget.DragObject, view: View) {
        val iconBounds = Rect()
        activityContext.dragLayer.getDescendantRectRelativeToSelf(view, iconBounds)
        whenever(dragObject.dragView.dragRegion)
            .thenReturn(Rect(0, 0, iconBounds.width(), iconBounds.height()))
        dragObject.x = iconBounds.left
        dragObject.y = iconBounds.top
        dragObject.xOffset = iconBounds.width() / 2
        dragObject.yOffset = iconBounds.height() / 2
    }

    private fun dragViewOntoOverflowIconToOpenContainer(
        dragObject: DropTarget.DragObject,
        overflowIcon: TaskbarOverflowView,
    ) {
        mockDragViewToOverViewBounds(dragObject, overflowIcon)

        // Simulate dragging on the overflow icon to open the container.
        runOnTaskbarUiThreadSync {
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragEnter(dragObject)
            taskbarViewDragDropController.taskbarPinningDropTarget.onDragOver(dragObject)
            assertThat(taskbarViewDragDropController.overflowContainerAlarm.alarmPending()).isTrue()
            taskbarViewDragDropController.overflowContainerAlarm.finishAlarm()
        }
    }

    private fun dragViewIntoOverflowContainer(dragObject: DropTarget.DragObject) {
        val overflowContainer =
            AbstractFloatingView.getOpenView<OverflownAppsContainerView<*>>(
                activityContext,
                AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
            )
        assertThat(overflowContainer).isNotNull()
        mockDragViewToOverViewBounds(dragObject, overflowContainer)
        requireNotNull(taskbarViewDragDropController.overflowPinningDropTarget)
            .onDragEnter(dragObject)
    }

    private fun createTestHotseatItemsWithDifferentPackages(count: Int): Array<WorkspaceItemInfo> {
        return Array(count) {
            createTestWorkspaceItem(
                it,
                "App $it",
                Intent().setComponent(ComponentName("com.test.app$it", "Test")),
                Process.myUserHandle(),
                LauncherSettings.Favorites.CONTAINER_ALL_APPS,
            )
        }
    }

    private fun createDesktopTasksFromPackages(desktopPackages: List<String>) {
        val defaultDisplayId = context.displayId
        val desktopTasks =
            desktopPackages.mapIndexed({ index, p ->
                Task(
                    Task.TaskKey(
                        index,
                        WindowConfiguration.WINDOWING_MODE_FREEFORM,
                        Intent().apply { `package` = p },
                        ComponentName(p, ""),
                        Process.myUserHandle().identifier,
                        2000,
                    )
                )
            })

        mockRecentsModelHelper.updateRecentTasks(
            listOf(DesktopTask(deskId = context.displayId, defaultDisplayId, desktopTasks))
        )

        runOnTaskbarUiThreadSync { mockRecentsModelHelper.resolvePendingTaskRequests() }
    }

    private fun updateTaskbarHotseatItems(hotseatItems: Array<ItemInfo>) {
        runOnTaskbarUiThreadSync {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.updateItems(
                recentAppsController.updateHotseatItemInfos(hotseatItems as Array<ItemInfo?>),
                recentAppsController.shownTasks,
                emptyList(),
            )
        }
    }

    private fun getItemView(item: ItemInfo): BubbleTextView? {
        return getOnTaskbarUiThread {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.iconViews.filterIsInstance<BubbleTextView>().firstOrNull() {
                item.isSameItem(it.tag as? ItemInfo)
            }
        }
    }

    private fun getRunningTaskIconForItem(item: ItemInfo): BubbleTextView? {
        return getOnTaskbarUiThread {
            val taskbarView: TaskbarView =
                taskbarUnitTestRule.activityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbarView.iconViews.filterIsInstance<BubbleTextView>().firstOrNull() {
                (it.tag as? SingleTask)?.containsPackage(item.targetPackage) == true
            }
        }
    }
}
