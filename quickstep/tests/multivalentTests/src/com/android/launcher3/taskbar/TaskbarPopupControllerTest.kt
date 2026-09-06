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

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.SparseArray
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR
import com.android.launcher3.Flags.FLAG_ENABLE_TASKBAR_UI_THREAD
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PinToTaskbarShortcut
import com.android.launcher3.popup.SystemShortcut.BubbleShortcut
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatFolderItem
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatWorkspaceItem
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecents
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createTestWorkspaceItem
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.ModelTestExtensions.preloadModelData
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.android.quickstep.util.SingleTask
import com.android.systemui.shared.recents.model.Task
import com.android.window.flags.Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.shared.bubbles.FakeBubbleFeatureConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskbarPopupControllerTest {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val popupController by taskbarUnitTestRule.delegate { it.taskbarPopupController }

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val desktopVisibilityController: DesktopVisibilityController
        get() = DesktopVisibilityController.INSTANCE[context]

    private lateinit var taskbarView: TaskbarView
    private lateinit var hotseatIcon: BubbleTextView
    private lateinit var recentTaskIcon: BubbleTextView
    private lateinit var folderIcon: FolderIcon

    @Before
    fun setup() {
        taskbarContext.controllers.uiController.init(taskbarContext.controllers)
        runOnTaskbarUiThreadSync {
            taskbarView = taskbarContext.dragLayer.findViewById(R.id.taskbar_view)
        }

        val hotseatItems =
            arrayOf(
                createHotseatWorkspaceItem(),
                createHotseatFolderItem().apply {
                    container = LauncherSettings.Favorites.CONTAINER_HOTSEAT
                },
            )
        if (LauncherModel.useModelRepositoryBinding()) {
            context.preloadModelData(*hotseatItems)
        } else {
            popupController.setApps(
                hotseatItems
                    .filterIsInstance<WorkspaceItemInfo>()
                    .map { item ->
                        AppInfo(item.targetComponent, item.title, item.user, item.intent)
                    }
                    .toTypedArray()
            )
        }
        popupController.taskbarInfoList = SparseArray()
        val recentItems = createRecents(2)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(hotseatItems, recentItems, emptyList())
            hotseatIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            recentTaskIcon =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is SingleTask
                }
            folderIcon = taskbarView.iconViews.filterIsInstance<FolderIcon>().first()
        }
    }

    @Test
    fun showForIcon_hotseatItem() {
        assertThat(hasPopupMenu()).isFalse()
        runOnTaskbarUiThreadSync { popupController.show(hotseatIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU)
    fun showForIcon_recentTask() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        assertThat(hasPopupMenu()).isFalse()
        runOnTaskbarUiThreadSync { popupController.show(recentTaskIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU)
    fun showForIcon_folderItem() {
        assertThat(hasPopupMenu()).isFalse()
        runOnTaskbarUiThreadSync { popupController.show(folderIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    fun showForIcon_recentTask_notInDesktopMode() {
        // Verifies popup menu is shown for recent tasks even when not in desktop mode.
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(false)
        assertThat(hasPopupMenu()).isFalse()
        runOnTaskbarUiThreadSync { popupController.show(recentTaskIcon) }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    fun showForIcon_recentTask_mismatchedComponent() {
        // Create a task with a component name that doesn't match any in AllAppsStore,
        // but has the same package name as one of the apps.
        val originalTask = (recentTaskIcon.tag as SingleTask).task
        val mismatchedComponent = ComponentName(originalTask.key.packageName, "MismatchedActivity")
        val mismatchedTask =
            Task().apply {
                key =
                    Task.TaskKey(
                        123,
                        originalTask.key.windowingMode,
                        originalTask.key.baseIntent.cloneFilter().setComponent(mismatchedComponent),
                        mismatchedComponent,
                        originalTask.key.userId,
                        originalTask.key.lastActiveTime,
                    )
            }
        val mismatchedSingleTask = SingleTask(mismatchedTask)

        runOnTaskbarUiThreadSync {
            recentTaskIcon.tag = mismatchedSingleTask
            assertThat(hasPopupMenu()).isFalse()
            popupController.show(recentTaskIcon)
        }
        assertThat(hasPopupMenu()).isTrue()
    }

    @Test
    fun showForIconUsingA11yAction_hotseatItem() {
        assertThat(hasPopupMenu()).isFalse()
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)

        runOnTaskbarUiThreadSync {
            hotseatIcon.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null)
        }
        assertThat(hasPopupMenu()).isTrue()
        assertThat(hasTaskbarDragView()).isFalse()

        closePopupMenu()
        assertThat(hasTaskbarDragView()).isFalse()
    }

    @Test
    fun showForIconUsingA11yAction_recentTask() {
        assertThat(hasPopupMenu()).isFalse()
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)

        runOnTaskbarUiThreadSync {
            recentTaskIcon.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null)
        }
        assertThat(hasPopupMenu()).isTrue()
        assertThat(hasTaskbarDragView()).isFalse()

        closePopupMenu()
        assertThat(hasTaskbarDragView()).isFalse()
    }

    @Test
    fun showForIconUsingA11yAction_recentTask_notInDesktopMode() {
        // Verifies popup menu is shown for recent tasks via a11y action even when not in desktop
        // mode.
        assertThat(hasPopupMenu()).isFalse()
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(false)

        runOnTaskbarUiThreadSync {
            recentTaskIcon.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null)
        }
        assertThat(hasPopupMenu()).isTrue()
        assertThat(hasTaskbarDragView()).isFalse()

        closePopupMenu()
        assertThat(hasTaskbarDragView()).isFalse()
    }

    @Test
    fun createPinShortcut_appAlreadyPinned_returnsUnpinShortcut() {
        val hotseatItems = SparseArray<ItemInfo>()
        val appUser = android.os.Process.myUserHandle()
        val appAIntent = Intent().setComponent(ComponentName("com.example.app", "AppAActivity"))

        val itemFromAllApps =
            createTestWorkspaceItem(
                0,
                "AppA",
                appAIntent,
                appUser,
                LauncherSettings.Favorites.CONTAINER_ALL_APPS,
            )

        val pinnedItemInHotseat =
            createTestWorkspaceItem(
                1,
                "AppA",
                appAIntent,
                appUser,
                LauncherSettings.Favorites.CONTAINER_HOTSEAT,
            )

        hotseatItems.put(0, pinnedItemInHotseat)
        popupController.taskbarInfoList = hotseatItems
        val allAppsAppIcon = Mockito.mock(BubbleTextView::class.java)

        val shortcut =
            popupController.createPinShortcut(taskbarContext, itemFromAllApps, allAppsAppIcon)
        Assert.assertNotNull("Shortcut should not be null", shortcut)
        Assert.assertTrue(
            "Shortcut should be PinToTaskbarShortcut",
            shortcut is PinToTaskbarShortcut<*>,
        )
        Assert.assertFalse((shortcut as PinToTaskbarShortcut<*>).isPin)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_UI_THREAD)
    fun createPinShortcut_appAlreadyPinned_withUiThreadEnabled_returnsUnpinShortcut() {
        val hotseatItems = SparseArray<ItemInfo>()
        val appUser = android.os.Process.myUserHandle()
        val appAIntent = Intent().setComponent(ComponentName("com.example.app", "AppAActivity"))

        val itemFromAllApps =
            createTestWorkspaceItem(
                0,
                "AppA",
                appAIntent,
                appUser,
                LauncherSettings.Favorites.CONTAINER_ALL_APPS,
            )

        val pinnedItemInHotseat =
            createTestWorkspaceItem(
                1,
                "AppA",
                appAIntent,
                appUser,
                LauncherSettings.Favorites.CONTAINER_HOTSEAT,
            )

        hotseatItems.put(0, pinnedItemInHotseat)
        popupController.taskbarInfoList = hotseatItems
        val allAppsAppIcon = Mockito.mock(BubbleTextView::class.java)

        val shortcut =
            popupController.createPinShortcut(taskbarContext, itemFromAllApps, allAppsAppIcon)
        Assert.assertNotNull("Shortcut should not be null", shortcut)
        Assert.assertTrue(
            "Shortcut should be PinToTaskbarShortcut",
            shortcut is PinToTaskbarShortcut<*>,
        )
        Assert.assertFalse((shortcut as PinToTaskbarShortcut<*>).isPin)
    }

    @Test
    fun createPinShortcut_folderAlreadyPinned_returnsUnpinShortcut() {
        val hotseatItems = SparseArray<ItemInfo>()
        val pinnedFolder =
            createHotseatFolderItem().apply {
                container = LauncherSettings.Favorites.CONTAINER_HOTSEAT
            }

        hotseatItems.put(0, pinnedFolder)
        popupController.taskbarInfoList = hotseatItems
        val folderIcon = Mockito.mock(FolderIcon::class.java)

        val shortcut = popupController.createPinShortcut(taskbarContext, pinnedFolder, folderIcon)
        Assert.assertNotNull("Shortcut should not be null", shortcut)
        Assert.assertTrue(
            "Shortcut should be PinToTaskbarShortcut",
            shortcut is PinToTaskbarShortcut<*>,
        )
        Assert.assertFalse((shortcut as PinToTaskbarShortcut<*>).isPin)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_UI_THREAD)
    fun setTaskbarInfoList_withUiThreadEnabled_clonesList() {
        // Verifies that setTaskbarInfoList clones the input list when UI thread is enabled.
        val originalList = SparseArray<ItemInfo>()
        val item = createHotseatWorkspaceItem()
        originalList.put(0, item)

        popupController.taskbarInfoList = originalList

        // Modify the original list after setting it.
        originalList.remove(0)

        // The internal list should not be affected.
        val internalList = popupController.taskbarInfoList
        assertThat(internalList.size()).isEqualTo(1)
        assertThat(internalList.get(0)).isEqualTo(item)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_TASKBAR_UI_THREAD)
    fun setTaskbarInfoList_withUiThreadDisabled_doesNotCloneList() {
        // Verifies that setTaskbarInfoList uses the same list reference when UI thread is disabled.
        val originalList = SparseArray<ItemInfo>()
        val item = createHotseatWorkspaceItem()
        originalList.put(0, item)

        popupController.taskbarInfoList = originalList

        // Modify the original list after setting it.
        originalList.remove(0)

        // The internal list should be affected.
        val internalList = popupController.taskbarInfoList
        assertThat(internalList.size()).isEqualTo(0)
    }

    @Test
    fun getTaskbarInfoList_returnsClonedList() {
        // Verifies that getTaskbarInfoList always returns a defensive copy.
        val originalList = SparseArray<ItemInfo>()
        val item = createHotseatWorkspaceItem()
        originalList.put(0, item)
        popupController.taskbarInfoList = originalList

        val retrievedList1 = popupController.taskbarInfoList
        val retrievedList2 = popupController.taskbarInfoList

        // Verify it's a clone and we get a new instance each time.
        assertThat(retrievedList1).isNotSameInstanceAs(retrievedList2)

        // Modify the retrieved list.
        retrievedList1.remove(0)

        // Verify the internal list is not modified by getting it again.
        val internalList = popupController.taskbarInfoList
        assertThat(internalList.size()).isEqualTo(1)
        assertThat(internalList.get(0)).isEqualTo(item)
    }

    private fun hasTaskbarDragView(): Boolean {
        return getOnTaskbarUiThread {
            val dragView: DragView? =
                taskbarContext.dragLayer.findViewByPredicate { it is DragView }
            dragView != null
        }
    }

    private fun hasPopupMenu(): Boolean {
        return getOnTaskbarUiThread {
            AbstractFloatingView.hasOpenView(taskbarContext, AbstractFloatingView.TYPE_ACTION_POPUP)
        }
    }

    private fun closePopupMenu() {
        runOnTaskbarUiThreadSync {
            val popup: AbstractFloatingView =
                AbstractFloatingView.getOpenView(
                    taskbarContext,
                    AbstractFloatingView.TYPE_ACTION_POPUP,
                )
            popup?.close(false)
        }
    }

    @Test
    fun createPinShortcut_forAllAppsPredictedApp_returnsShortcut() {
        val item = ItemInfo()
        item.container = LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION
        val shortcut =
            popupController.createPinShortcut(
                taskbarContext,
                item,
                Mockito.mock(BubbleTextView::class.java),
            )
        Assert.assertNotNull(
            "Pin shortcut should be available for predicted All Apps items",
            shortcut,
        )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun getShortcuts_bubblesEnabled() {
        val itemInfo = hotseatIcon.getTag() as ItemInfo
        val systemShortcuts =
            popupController
                .getSystemShortcuts()
                .map { it.getShortcut(taskbarContext, itemInfo, hotseatIcon) }
                .toList()

        val hasBubble = systemShortcuts.any { it is BubbleShortcut }
        assertThat(hasBubble).isTrue()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun getShortcuts_bubblesDisabled() {
        val itemInfo = hotseatIcon.getTag() as ItemInfo
        val systemShortcuts =
            popupController
                .getSystemShortcuts()
                .map { it.getShortcut(taskbarContext, itemInfo, hotseatIcon) }
                .toList()

        val hasBubble = systemShortcuts.any { it is BubbleShortcut }
        assertThat(hasBubble).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun getShortcuts_bubblesNotSupported() {
        val bubbleFeatureConfig = FakeBubbleFeatureConfig()
        bubbleFeatureConfig.areAppBubblesSupported = false
        taskbarContext.overrideBubbleFeatureConfigForTests(bubbleFeatureConfig)
        val itemInfo = hotseatIcon.getTag() as ItemInfo
        val systemShortcuts =
            popupController
                .getSystemShortcuts()
                .map { it.getShortcut(taskbarContext, itemInfo, hotseatIcon) }
                .toList()

        val hasBubble = systemShortcuts.any { it is BubbleShortcut }
        assertThat(hasBubble).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR)
    fun getShortcuts_recentTask_multiInstanceSupported_showsMultiInstanceOptions() {
        val originalTask = (recentTaskIcon.tag as SingleTask).task
        val componentKey =
            ComponentKey(originalTask.key.component, UserHandle.of(originalTask.key.userId))

        // Enable multi-instance
        val appInfo = popupController.getApp(componentKey)
        assertThat(appInfo).isNotNull()
        appInfo!!.runtimeStatusFlags =
            appInfo.runtimeStatusFlags or ItemInfoWithIcon.FLAG_SUPPORTS_MULTI_INSTANCE

        val workspaceItemInfo = appInfo.makeWorkspaceItem(context)
        val taskItemInfo =
            SingleTask.createTaskItemInfo(recentTaskIcon.tag as SingleTask, workspaceItemInfo)

        val shortcut =
            popupController
                .createNewWindowShortcutFactory()
                .getShortcut(taskbarContext, taskItemInfo, recentTaskIcon)

        assertThat(shortcut).isNotNull()
        assertThat(shortcut is NewWindowTaskbarShortcut<*>).isTrue()

        val manageShortcut =
            popupController
                .createManageWindowsShortcutFactory()
                .getShortcut(taskbarContext, taskItemInfo, recentTaskIcon)
        assertThat(manageShortcut).isNotNull()
        assertThat(manageShortcut is ManageWindowsTaskbarShortcut<*>).isTrue()
    }
}
