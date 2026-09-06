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

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.annotation.UiThreadTest
import com.android.internal.R
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.launcher3.BubbleTextView.RunningAppState
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.deviceprofile.DeviceProperties
import com.android.launcher3.deviceprofile.TaskbarConfiguration
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconShape
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarRecentAppsController.TaskState
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.ListenableStream
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SafeCloseable
import com.android.quickstep.RecentsModel
import com.android.quickstep.RecentsModel.RecentTasksChangedListener
import com.android.quickstep.TaskIconCache
import com.android.quickstep.TaskIconCache.GetTaskBitmapInfoCallback
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitTask
import com.android.quickstep.util.TaskVisualsChangeListener
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@UiThreadTest
@RunWith(LauncherMultivalentJUnit::class)
@EnableFlags(Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR)
class TaskbarRecentAppsControllerTest : TaskbarBaseTestCase() {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule
    val disableControllerForCertainTestsWatcher =
        object : TestWatcher() {
            override fun starting(description: Description) {
                // Update canShowRunningAndRecentAppsAtInit before setUp() is called for each test.
                canShowRunningAndRecentAppsAtInit =
                    description.methodName !in
                        listOf("canShowRunningAndRecentAppsAtInitIsFalse_getTasksNeverCalled")
            }
        }

    @Mock private lateinit var mockIconCache: TaskIconCache
    @Mock private lateinit var mockRecentsModel: RecentsModel
    @Mock private lateinit var mockTaskChangesListenable: ListenableStream<Void?>
    @Mock private lateinit var mockTaskChangesSafeClosable: SafeCloseable
    @Mock private lateinit var mockThemeManager: ThemeManager
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockResources: Resources
    @Mock private lateinit var mockDeviceProfile: DeviceProfile
    @Mock private lateinit var mockDeviceProperties: DeviceProperties
    @Mock private lateinit var mockDeviceTaskbarConfiguration: TaskbarConfiguration
    @Mock private lateinit var mockDesktopModeCompatPolicy: DesktopModeCompatPolicy

    private var taskListChangeId: Int = 1

    private lateinit var recentAppsController: TaskbarRecentAppsController
    private lateinit var myUserHandle: UserHandle
    private val USER_HANDLE_1 = UserHandle.of(1)
    private val USER_HANDLE_2 = UserHandle.of(2)
    private val iconShapeData = MutableListenableRef(IconShape.EMPTY)

    private var canShowRunningAndRecentAppsAtInit = true
    private var recentTasksChangedListener: RecentTasksChangedListener? = null
    private var recentTasksChangedCallback: ((Void?) -> Unit)? = null
    private var taskVisualsChangeListener: TaskVisualsChangeListener? = null

    val recentShownTasks: List<Task>
        get() = recentAppsController.shownTasks.flatMap { it.tasks }

    @Before
    fun setUp() {
        super.setup()
        myUserHandle = Process.myUserHandle()

        // Set desktop mode supported
        whenever(mockContext.getResources()).thenReturn(mockResources)
        whenever(mockResources.getBoolean(R.bool.config_isDesktopModeSupported)).thenReturn(true)
        whenever(taskbarActivityContext.deviceProfile).thenReturn(mockDeviceProfile)
        whenever(mockDeviceProfile.deviceProperties).thenReturn(mockDeviceProperties)
        whenever(mockDeviceProperties.taskbarConfiguration)
            .thenReturn(mockDeviceTaskbarConfiguration)
        whenever(mockDeviceTaskbarConfiguration.isTaskbarPresent).thenReturn(true)

        whenever(mockRecentsModel.iconCache).thenReturn(mockIconCache)

        val taskVisualsChangeListenerCaptor = argumentCaptor<TaskVisualsChangeListener>()
        whenever(
                mockRecentsModel.addThumbnailChangeListener(
                    taskVisualsChangeListenerCaptor.capture()
                )
            )
            .then { taskVisualsChangeListener = taskVisualsChangeListenerCaptor.lastValue }
        whenever(mockRecentsModel.removeThumbnailChangeListener(any())).then {
            taskVisualsChangeListener = null
        }

        whenever(mockIconCache.getBitmapInfoInBackground(any(), any(), any())).thenAnswer {
            it.getArgument<GetTaskBitmapInfoCallback>(2)
                .onBitmapInfoReceived(BITMAP_INFO_1, TASK_DESCRIPTION, TASK_TITLE)
            null
        }
        whenever(mockRecentsModel.unregisterRecentTasksChangedListener(any())).then {
            recentTasksChangedListener = null
            it
        }
        whenever(mockRecentsModel.tasksChanges).thenReturn(mockTaskChangesListenable)
        whenever(mockTaskChangesListenable.forEach(any(), any()))
            .thenReturn(mockTaskChangesSafeClosable)
        whenever(mockTaskChangesSafeClosable.close()).then {
            recentTasksChangedCallback = null
            it
        }
        whenever(mockThemeManager.iconShapeData).thenReturn(iconShapeData)
        whenever(taskbarDesktopModeController.isLauncherAnimationRunning).thenReturn(false)
        recentAppsController =
            TaskbarRecentAppsController(
                mockContext,
                mockRecentsModel,
                mockThemeManager,
                mockDesktopModeCompatPolicy,
            )
        recentAppsController.canShowRunningApps = canShowRunningAndRecentAppsAtInit
        recentAppsController.canShowRecentApps = canShowRunningAndRecentAppsAtInit

        // To ensure the initial getTasks() call is not seen as "loading" for the rest of the test,
        // execute its callback.
        doAnswer {
                val callback: Consumer<ArrayList<GroupTask>> = it.getArgument(1)
                callback.accept(arrayListOf())
                taskListChangeId
            }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentAppsController.init(taskbarControllers, emptyList())
        taskbarControllers.onPostInit()

        if (enableTaskbarUiThread()) {
            recentTasksChangedCallback =
                if (canShowRunningAndRecentAppsAtInit) {
                    val listenerCaptor = argumentCaptor<(Void?) -> Unit>()
                    verify(mockTaskChangesListenable)
                        .forEach(same(getTaskbarUiThread()), listenerCaptor.capture())
                    listenerCaptor.lastValue
                } else {
                    verify(mockTaskChangesListenable, never()).forEach(any(), any())
                    null
                }
        } else {
            recentTasksChangedListener =
                if (canShowRunningAndRecentAppsAtInit) {
                    val listenerCaptor = argumentCaptor<RecentTasksChangedListener>()
                    verify(mockRecentsModel)
                        .registerRecentTasksChangedListener(listenerCaptor.capture())
                    listenerCaptor.lastValue
                } else {
                    verify(mockRecentsModel, never()).registerRecentTasksChangedListener(any())
                    null
                }
        }

        // Make sure updateHotseatItemInfos() is called after commitRunningAppsToUI()
        whenever(taskbarViewController.commitRunningAppsToUI()).then {
            recentAppsController.updateHotseatItemInfos(
                recentAppsController.shownHotseatItems.toTypedArray()
            )
        }
    }

    // See the TestWatcher rule at the top which sets canShowRunningAndRecentAppsAtInit = false.
    @Test
    fun canShowRunningAndRecentAppsAtInitIsFalse_getTasksNeverCalled() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = listOf(createTask(1, RUNNING_APP_PACKAGE_1)),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(mockRecentsModel, never()).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun canShowRunningAndRecentAppsIsFalseAfterInit_getTasksOnlyCalledInInit() {
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentAppsController.canShowRunningApps = false
        recentAppsController.canShowRecentApps = false
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = listOf(createTask(1, RUNNING_APP_PACKAGE_1)),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        // Verify that getTasks() was not called again after the init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun recentTasksChanged_duringGetTasksLoading_dontCallGetTasks() {
        assumeTrue("Only run this test if enableTaskbarUiThread() is on", enableTaskbarUiThread())
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedCallback?.invoke(null)
        waitForTaskbarUiThreadSync()
        // By not invoking the callback passed to getTasks() we here emulate getTasks() loading.

        recentTasksChangedCallback?.invoke(null)
        waitForTaskbarUiThreadSync()

        // getTasks() is only called two times overall (init + once more).
        verify(mockRecentsModel, times(2)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun recentTasksChanged_duringGetTasksLoading_dontCallGetTasks_disableFlags_taskbarUiThread() {
        assumeFalse("Only run this test if enableTaskbarUiThread() is off", enableTaskbarUiThread())
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), any<Consumer<List<GroupTask>>>())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
        // By not invoking the callback passed to getTasks() we here emulate getTasks() loading.

        recentTasksChangedListener?.onRecentTasksChanged()

        // getTasks() is only called two times overall (init + once more).
        verify(mockRecentsModel, times(2)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun recentTasksChanged_duringGetTasksLoading_getTasksCalledWhenLoadingDone() {
        assumeTrue("Only run this test if enableTaskbarUiThread() is on", enableTaskbarUiThread())
        val callbackCaptor = argumentCaptor<Consumer<List<GroupTask>>>()
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), callbackCaptor.capture())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedCallback?.invoke(null)
        waitForTaskbarUiThreadSync()
        // By not invoking the callback passed to getTasks() we here emulate getTasks() loading.

        recentTasksChangedCallback?.invoke(null)
        waitForTaskbarUiThreadSync()
        callbackCaptor.lastValue.accept(emptyList())
        waitForTaskbarUiThreadSync()

        // getTasks() is called again now that the first getTasks() call finished.
        verify(mockRecentsModel, times(3)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun recentTasksChanged_duringGetTasksLoading_getTasksCalledWhenLoadingDone_legacy() {
        assumeFalse("Only run this test if enableTaskbarUiThread() is off", enableTaskbarUiThread())
        val callbackCaptor = argumentCaptor<Consumer<List<GroupTask>>>()
        // getTasks() should have been called once from init().
        verify(mockRecentsModel, times(1)).getTasks(any(), callbackCaptor.capture())
        // Override the mock answer for getTasks() so it doesn't call the callback immediately.
        doAnswer { taskListChangeId }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        recentTasksChangedListener?.onRecentTasksChanged()
        // By not invoking the callback passed to getTasks() we here emulate getTasks() loading.

        recentTasksChangedListener?.onRecentTasksChanged()
        callbackCaptor.lastValue.accept(emptyList())

        // getTasks() is called again now that the first getTasks() call finished.
        verify(mockRecentsModel, times(3)).getTasks(any(), any<Consumer<List<GroupTask>>>())
    }

    @Test
    fun getTaskbarItemState_nullItemInfo_returnsNotRunning() {
        setInDesktopMode(true)
        val taskState = recentAppsController.getTaskbarItemState(/* itemInfo= */ null)
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getTaskbarItemState_noItemPackage_returnsNotRunning() {
        setInDesktopMode(true)
        val taskState = recentAppsController.getTaskbarItemState(ItemInfo())
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getTaskbarItemState_noMatchingTasks_returnsNotRunning() {
        setInDesktopMode(true)
        val taskState = recentAppsController.getTaskbarItemState(createItemInfo("package"))
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getTaskbarItemState_matchingVisibleTask_returnsVisible() {
        setInDesktopMode(true)
        val visibleTask =
            PerDisplayRunningApps(
                listOf(createTask(id = 1, "visiblePackage", isVisible = true)),
                DEFAULT_DISPLAY,
            )
        updateRecentTasks(runningTasks = listOf(visibleTask), recentTaskPackages = emptyList())

        val taskState = recentAppsController.getTaskbarItemState(createItemInfo("visiblePackage"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.RUNNING, taskId = 1))
    }

    @Test
    fun getTaskbarItemState_matchingVisibleTaskOnSecondaryDisplay_returnsVisible() {
        setInDesktopMode(true)
        val visibleTask1 =
            PerDisplayRunningApps(
                listOf(createTask(id = 1, "visiblePackage1", isVisible = false)),
                DEFAULT_DISPLAY,
            )
        val visibleTask2 =
            PerDisplayRunningApps(
                listOf(createTask(id = 2, "visiblePackage2", isVisible = true)),
                DEFAULT_DISPLAY + 1,
            )
        updateRecentTasks(
            runningTasks = listOf(visibleTask1, visibleTask2),
            recentTaskPackages = emptyList(),
        )

        val taskState = recentAppsController.getTaskbarItemState(createItemInfo("visiblePackage2"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.RUNNING, taskId = 2))
    }

    @Test
    fun getTaskbarItemState_matchingMinimizedTask_returnsMinimized() {
        setInDesktopMode(true)
        val minimizedTask =
            PerDisplayRunningApps(
                listOf(
                    createTask(id = 1, "minimizedPackage", isVisible = false, isMinimized = true)
                ),
                DEFAULT_DISPLAY,
            )
        updateRecentTasks(runningTasks = listOf(minimizedTask), recentTaskPackages = emptyList())

        val taskState = recentAppsController.getTaskbarItemState(createItemInfo("minimizedPackage"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.MINIMIZED, taskId = 1))
    }

    @Test
    fun getTaskbarItemState_matchingMinimizedTaskOnSecondaryDisplay_returnsVisible() {
        setInDesktopMode(true)
        val visibleTask1 =
            PerDisplayRunningApps(
                listOf(createTask(id = 1, "visiblePackage1", isVisible = false)),
                DEFAULT_DISPLAY,
            )
        val visibleTask2 =
            PerDisplayRunningApps(
                listOf(
                    createTask(id = 2, "visiblePackage2", isVisible = false, isMinimized = true)
                ),
                DEFAULT_DISPLAY + 1,
            )
        updateRecentTasks(
            runningTasks = listOf(visibleTask1, visibleTask2),
            recentTaskPackages = emptyList(),
        )

        val taskState = recentAppsController.getTaskbarItemState(createItemInfo("visiblePackage2"))

        assertThat(taskState).isEqualTo(TaskState(RunningAppState.MINIMIZED, taskId = 2))
    }

    @Test
    fun getTaskbarItemState_matchingMinimizedAndRunningTask_returnsVisible() {
        setInDesktopMode(true)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(
                        listOf(
                            createTask(id = 1, "package", isVisible = false, isMinimized = true),
                            createTask(id = 2, "package", isVisible = true),
                        ),
                        DEFAULT_DISPLAY,
                    )
                ),
            recentTaskPackages = emptyList(),
        )

        val taskState = recentAppsController.getTaskbarItemState(createItemInfo("package"))
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.RUNNING, taskId = 2))
    }

    @Test
    fun getTaskbarItemState_noMatchingUserId_returnsNotRunning() {
        setInDesktopMode(true)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(
                        listOf(
                            createTask(id = 1, "package", isVisible = false, USER_HANDLE_1),
                            createTask(id = 2, "package", isVisible = true, USER_HANDLE_1),
                        ),
                        DEFAULT_DISPLAY,
                    )
                ),
            recentTaskPackages = emptyList(),
        )

        val taskState =
            recentAppsController.getTaskbarItemState(createItemInfo("package", USER_HANDLE_2))
        assertThat(taskState).isEqualTo(TaskState(RunningAppState.NOT_RUNNING))
    }

    @Test
    fun getRunningAppState_taskNotRunningOrMinimized_returnsNotRunning() {
        setInDesktopMode(true)
        updateRecentTasks(runningTasks = emptyList(), recentTaskPackages = emptyList())

        assertThat(recentAppsController.getRunningAppState(taskId = 1))
            .isEqualTo(RunningAppState.NOT_RUNNING)
    }

    @Test
    fun getRunningAppState_taskNotVisible_returnsMinimized() {
        setInDesktopMode(true)
        val task1 =
            createTask(
                id = 1,
                packageName = RUNNING_APP_PACKAGE_1,
                isVisible = false,
                isMinimized = true,
            )
        val task2 = createTask(id = 2, packageName = RUNNING_APP_PACKAGE_1, isVisible = true)
        updateRecentTasks(
            runningTasks = listOf(PerDisplayRunningApps(listOf(task1, task2), DEFAULT_DISPLAY)),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentAppsController.getRunningAppState(taskId = 1))
            .isEqualTo(RunningAppState.MINIMIZED)
    }

    @Test
    fun getRunningAppState_taskNotVisible_returnsMinimizedForSecondaryDisplay() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, packageName = RUNNING_APP_PACKAGE_1, isVisible = false)
        val task2 = createTask(id = 2, packageName = RUNNING_APP_PACKAGE_1, isVisible = true)
        val task3 =
            createTask(
                id = 3,
                packageName = RUNNING_APP_PACKAGE_3,
                isVisible = false,
                isMinimized = true,
            )
        val task4 = createTask(id = 3, packageName = RUNNING_APP_PACKAGE_3, isVisible = false)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(listOf(task1, task2), DEFAULT_DISPLAY),
                    PerDisplayRunningApps(listOf(task3, task4), DEFAULT_DISPLAY + 1),
                ),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentAppsController.getRunningAppState(taskId = 3))
            .isEqualTo(RunningAppState.MINIMIZED)
    }

    @Test
    fun getRunningAppState_taskVisible_returnsRunningForSecondaryDisplay() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, packageName = RUNNING_APP_PACKAGE_1, isVisible = false)
        val task2 = createTask(id = 2, packageName = RUNNING_APP_PACKAGE_1, isVisible = true)
        val task3 = createTask(id = 3, packageName = RUNNING_APP_PACKAGE_3, isVisible = true)
        updateRecentTasks(
            runningTasks =
                listOf(
                    PerDisplayRunningApps(listOf(task1, task2), DEFAULT_DISPLAY),
                    PerDisplayRunningApps(listOf(task3), DEFAULT_DISPLAY + 1),
                ),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentAppsController.getRunningAppState(taskId = 3))
            .isEqualTo(RunningAppState.RUNNING)
    }

    @Test
    fun isReplacingPredictions_inDesktopMode_canShowRunningApps_returnsTrue() {
        // In desktop mode, if we can show running apps, we should replace predictions.
        setInDesktopMode(true)
        recentAppsController.canShowRunningApps = true
        recentAppsController.canShowRecentApps = false

        assertThat(recentAppsController.isReplacingPredictions).isTrue()
    }

    @Test
    fun isReplacingPredictions_inDesktopMode_cannotShowRunningApps_returnsFalse() {
        // In desktop mode, if we can't show running apps, we should not replace predictions.
        setInDesktopMode(true)
        recentAppsController.canShowRunningApps = false
        recentAppsController.canShowRecentApps = true

        assertThat(recentAppsController.isReplacingPredictions).isFalse()
    }

    @Test
    fun isReplacingPredictions_notInDesktopMode_canShowRecentApps_returnsTrue() {
        // Outside of desktop mode, if we can show recent apps, we should replace predictions.
        setInDesktopMode(false)
        recentAppsController.canShowRunningApps = false
        recentAppsController.canShowRecentApps = true

        assertThat(recentAppsController.isReplacingPredictions).isTrue()
    }

    @Test
    fun isReplacingPredictions_notInDesktopMode_cannotShowRecentApps_returnsFalse() {
        // Outside of desktop mode, if we can't show recent apps, we should not replace predictions.
        setInDesktopMode(false)
        recentAppsController.canShowRunningApps = true
        recentAppsController.canShowRecentApps = false

        assertThat(recentAppsController.isReplacingPredictions).isFalse()
    }

    @Test
    fun updateHotseatItemInfos_cantShowRunning_inDesktopMode_returnsAllHotseatItems() {
        recentAppsController.canShowRunningApps = false
        setInDesktopMode(true)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = hotseatPackages,
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(hotseatPackages)
    }

    @Test
    fun updateHotseatItemInfos_cantShowRecent_notInDesktopMode_returnsAllHotseatItems() {
        recentAppsController.canShowRecentApps = false
        setInDesktopMode(false)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = hotseatPackages,
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(hotseatPackages)
    }

    @Test
    fun updateHotseatItemInfos_canShowRunning_inDesktopMode_returnsNonPredictedHotseatItems() {
        recentAppsController.canShowRunningApps = true
        setInDesktopMode(true)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        val expectedPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun updateHotseatItemInfos_inDesktopMode_hotseatPackageHasRunningTask_hotseatItemLinksToTask() {
        setInDesktopMode(true)

        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks = listOf(createTask(id = 1, HOTSEAT_PACKAGE_1)),
                recentTaskPackages = emptyList(),
            )

        assertThat(newHotseatItems).hasLength(2)
        assertThat(newHotseatItems[0]).isInstanceOf(TaskItemInfo::class.java)
        assertThat(newHotseatItems[1]).isNotInstanceOf(TaskItemInfo::class.java)
        val hotseatItem1 = newHotseatItems[0] as TaskItemInfo
        assertThat(hotseatItem1.taskId).isEqualTo(1)
    }

    /**
     * Tests that in desktop mode, when two tasks have the same package name and one is in the
     * hotseat, only the hotseat item represents the app, and no duplicate is shown in recent apps.
     */
    @Test
    fun updateHotseatItemInfos_inDesktopMode_twoRunningTasksSamePackage_onlyHotseatCoversTask() {
        setInDesktopMode(true)

        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks =
                    listOf(
                        createTask(id = 1, HOTSEAT_PACKAGE_1),
                        createTask(id = 2, HOTSEAT_PACKAGE_1),
                    ),
                recentTaskPackages = emptyList(),
            )

        // The task is in Hotseat Items
        assertThat(newHotseatItems).hasLength(2)
        assertThat(newHotseatItems[0]).isInstanceOf(TaskItemInfo::class.java)
        assertThat(newHotseatItems[1]).isNotInstanceOf(TaskItemInfo::class.java)
        val hotseatItem1 = newHotseatItems[0] as TaskItemInfo
        assertThat(hotseatItem1.targetPackage).isEqualTo(HOTSEAT_PACKAGE_1)

        // The other task of the same package is not in recentShownTasks
        assertThat(recentShownTasks).isEmpty()
    }

    @Test
    fun updateHotseatItemInfos_canShowRecent_notInDesktopMode_returnsNonPredictedHotseatItems() {
        recentAppsController.canShowRecentApps = true
        setInDesktopMode(false)
        val newHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        val expectedPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        assertThat(newHotseatItems.map { it?.targetPackage })
            .containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun updateHotseatItemInfos_filterOutItemsMarkedForDeletion() {
        recentAppsController.canShowRunningApps = true
        setInDesktopMode(true)

        val initialHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(initialHotseatItems.size).isEqualTo(2)

        assertThat(recentAppsController.setItemMarkedForDeletion(initialHotseatItems[1]!!, true))
            .isTrue()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems)
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(1)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isFalse()

        assertThat(recentAppsController.setItemMarkedForDeletion(initialHotseatItems[1]!!, false))
            .isTrue()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems)
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(2)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isTrue()
    }

    @Test
    fun updateHotseatItemInfos_itemMarkedForDeletionReaddedAfterDeletion() {
        recentAppsController.canShowRunningApps = true
        setInDesktopMode(true)

        val initialHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks = emptyList(),
                recentTaskPackages = emptyList(),
            )
        assertThat(initialHotseatItems.size).isEqualTo(2)

        assertThat(recentAppsController.setItemMarkedForDeletion(initialHotseatItems[1]!!, true))
            .isTrue()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems)
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(1)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isFalse()

        assertThat(recentAppsController.setItemMarkedForDeletion(initialHotseatItems[1]!!, false))
            .isTrue()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems.sliceArray(0..0))
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(1)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isFalse()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems)
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(2)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isTrue()

        assertThat(recentAppsController.setItemMarkedForDeletion(initialHotseatItems[1]!!, false))
            .isFalse()
    }

    @Test
    fun updateHotseatItemInfos_itemWithRunningTaskMarkedForDeletion() {
        setInDesktopMode(true)

        val initialHotseatItems =
            prepareHotseatAndRunningAndRecentApps(
                hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
                runningTasks =
                    listOf(
                        createTask(id = 1, HOTSEAT_PACKAGE_1),
                        createTask(id = 2, HOTSEAT_PACKAGE_2),
                    ),
                recentTaskPackages = emptyList(),
            )

        assertThat(initialHotseatItems.size).isEqualTo(2)

        assertThat(recentAppsController.setItemMarkedForDeletion(initialHotseatItems[1]!!, true))
            .isTrue()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems)
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(1)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isFalse()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems.sliceArray(0..0))
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(1)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isFalse()

        recentAppsController.updateHotseatItemInfos(initialHotseatItems)
        assertThat(recentAppsController.shownHotseatItems.size).isEqualTo(2)
        assertThat(recentAppsController.shownHotseatItems.contains(initialHotseatItems[1]!!))
            .isTrue()

        assertThat(recentAppsController.setItemMarkedForDeletion(initialHotseatItems[1]!!, false))
            .isFalse()
    }

    @Test
    fun onRecentTasksChanged_cantShowRunning_inDesktopMode_shownTasks_returnsEmptyList() {
        recentAppsController.canShowRunningApps = false
        setInDesktopMode(true)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
            runningTasks =
                listOf(
                    createTask(id = 1, RUNNING_APP_PACKAGE_1),
                    createTask(id = 2, RUNNING_APP_PACKAGE_2),
                ),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_cantShowRecent_notInDesktopMode_shownTasks_returnsEmptyList() {
        recentAppsController.canShowRecentApps = false
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2, PREDICTED_PACKAGE_1),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_noRecentTasks_shownTasks_returnsEmptyList() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks =
                listOf(
                    createTask(id = 1, RUNNING_APP_PACKAGE_1),
                    createTask(id = 2, RUNNING_APP_PACKAGE_2),
                ),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_noRunningApps_shownTasks_returnsEmptyList() {
        setInDesktopMode(true)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.shownTasks).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_onlyDesktopTasksConsideredRunning() {
        setInDesktopMode(true)
        val desktopTask = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val singleTask = SingleTask(createTask(id = 2, RECENT_PACKAGE_1))
        val splitTask =
            SplitTask(
                createTask(id = 3, "split1"),
                createTask(id = 4, "split2"),
                SplitBounds(Rect(), Rect(), 3, 4, SplitScreenConstants.SNAP_TO_2_50_50)
            )

        val allTasks =
            arrayListOf(
                DesktopTask(0, DEFAULT_DISPLAY, arrayListOf(desktopTask)),
                singleTask,
                splitTask
            )

        doAnswer {
                val callback: Consumer<ArrayList<GroupTask>> = it.getArgument(1)
                callback.accept(allTasks)
                taskListChangeId
            }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())

        if (enableTaskbarUiThread()) {
            recentTasksChangedCallback?.invoke(null)
            waitForTaskbarUiThreadSync()
        } else {
            recentTasksChangedListener?.onRecentTasksChanged()
        }

        // Only the task from DesktopTask should be in runningTaskIds
        assertThat(recentAppsController.runningTaskIds).containsExactly(1)
        // And only that task should be shown
        assertThat(recentShownTasks).hasSize(1)
        assertThat(recentShownTasks[0].key.id).isEqualTo(1)
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_shownTasks_returnsRunningTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks).containsExactlyElementsIn(listOf(task1, task2))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_getRunningApps_returnsEmptySet() {
        setInDesktopMode(false)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).isEmpty()
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_getRunningApps_returnsAllTaskbarRunningTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_getRunningApps_includesHotseat() {
        setInDesktopMode(true)
        val runningTasks =
            listOf(
                createTask(id = 1, HOTSEAT_PACKAGE_1),
                createTask(id = 2, RUNNING_APP_PACKAGE_1),
                createTask(id = 3, RUNNING_APP_PACKAGE_2),
            )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2),
            runningTasks = runningTasks,
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2, 3))
        assertThat(recentAppsController.minimizedTaskIds).isEmpty()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_allAppsRunningAndInvisibleAppsMinimized() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3Minimized =
            createTask(id = 3, RUNNING_APP_PACKAGE_3, isVisible = false, isMinimized = true)
        val runningTasks = listOf(task1, task2, task3Minimized)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactly(1, 2, 3)
        assertThat(recentAppsController.minimizedTaskIds).containsExactly(3)
    }

    @Test
    fun minimizedTaskIds_multipleDesktopsEnabled_returnsMinimizedTasks() {
        setInDesktopMode(true)

        val task1Minimized =
            createTask(id = 1, RUNNING_APP_PACKAGE_1, isMinimized = true, isVisible = false)
        val task2Visible =
            createTask(id = 2, RUNNING_APP_PACKAGE_2, isMinimized = false, isVisible = false)
        val task3Minimized =
            createTask(id = 3, RUNNING_APP_PACKAGE_3, isMinimized = true, isVisible = false)
        val runningTasks = listOf(task1Minimized, task2Visible, task3Minimized)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.minimizedTaskIds).containsExactly(1, 3)
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_samePackage_differentTasks_severalRunningTasks() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentShownTasks).isEqualTo(listOf(task1, task2))
    }

    /**
     * Tests that when multiple instances of the same app are running in desktop mode and the app is
     * not in the hotseat, only one instance is shown in the recent apps section.
     */
    @Test
    fun onRecentTasksChanged_inDesktopMode_multiInstance_noHotseat_shownTasksHasOneInstance() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_1)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )

        // Assert that recentShownTasks contains only one instance of the app
        assertThat(recentShownTasks).hasSize(1)
        assertThat(recentShownTasks[0].key.packageName).isEqualTo(RUNNING_APP_PACKAGE_1)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3, RECENT_PACKAGE_1),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_1).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_addTask_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3 = createTask(id = 3, RUNNING_APP_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1, task3),
            recentTaskPackages = emptyList(),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        val expectedOrder =
            listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2, RUNNING_APP_PACKAGE_3)
        assertThat(shownPackages).isEqualTo(expectedOrder)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_addTask_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_3, RECENT_PACKAGE_2),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3, RECENT_PACKAGE_1),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_1).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_removeTask_shownTasks_maintainsOrder() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task3 = createTask(id = 3, RUNNING_APP_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2, task3),
            recentTaskPackages = emptyList(),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task2, task1),
            recentTaskPackages = emptyList(),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).isEqualTo(listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_removeTask_shownTasks_maintainsRecency() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Most recent packages, minus the currently running one (RECENT_PACKAGE_3).
        assertThat(shownPackages).isEqualTo(listOf(RECENT_PACKAGE_2))
    }

    @Test
    fun onRecentTasksChanged_enterDesktopMode_shownTasks_onlyIncludesRunningTasks_enableFlags_taskbarUiThread() {
        assumeTrue("Only run this test if enableTaskbarUiThread() is on", enableTaskbarUiThread())
        setInDesktopMode(false)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )

        setInDesktopMode(true)
        recentTasksChangedCallback!!.invoke(null)
        waitForTaskbarUiThreadSync()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).containsExactly(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
    }

    @Test
    fun onRecentTasksChanged_enterDesktopMode_shownTasks_onlyIncludesRunningTasks_disableFlags_taskbarUiThread() {
        assumeFalse("Only run this test if enableTaskbarUiThread() is off", enableTaskbarUiThread())
        setInDesktopMode(false)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )

        setInDesktopMode(true)
        recentTasksChangedListener!!.onRecentTasksChanged()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).containsExactly(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
    }

    @Test
    fun onRecentTasksChanged_exitDesktopMode_shownTasks_onlyIncludesRecentTasks_enableFlag_taskbarUiThread() {
        assumeTrue("Only run this test if enableTaskbarUiThread() is on", enableTaskbarUiThread())
        setInDesktopMode(true)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )
        setInDesktopMode(false)
        recentTasksChangedCallback!!.invoke(null)
        waitForTaskbarUiThreadSync()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Don't expect RECENT_PACKAGE_3 because it is currently running.
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_exitDesktopMode_shownTasks_onlyIncludesRecentTasks_disableFlag_taskbarUiThread() {
        assumeFalse("Only run this test if enableTaskbarUiThread() is off", enableTaskbarUiThread())
        setInDesktopMode(true)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = recentTaskPackages,
        )
        setInDesktopMode(false)
        recentTasksChangedListener!!.onRecentTasksChanged()
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // Don't expect RECENT_PACKAGE_3 because it is currently running.
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentTasks_shownTasks_returnsRecentTasks() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        // RECENT_PACKAGE_3 is the top task (visible to user) so should be excluded.
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentAndRunningTasks_shownTasks_returnsRecentTaskAndDesktopTile() {
        setInDesktopMode(false)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val shownPackages = recentAppsController.shownTasks.map { it.packageNames }
        // Only 2 recent tasks shown: Desktop Tile + 1 Recent Task
        val desktopTilePackages = listOf(RUNNING_APP_PACKAGE_1, RUNNING_APP_PACKAGE_2)
        val recentTaskPackages = listOf(RECENT_PACKAGE_1)
        val expectedPackages = listOf(desktopTilePackages, recentTaskPackages)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasRecentAndSplitTasks_shownTasks_returnsRecentTaskAndPair() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_SPLIT_PACKAGES_1, RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val shownPackages = recentAppsController.shownTasks.map { it.packageNames }
        // Only 2 recent tasks shown: Pair + 1 Recent Task
        val pairPackages = RECENT_SPLIT_PACKAGES_1.split("_")
        val recentTaskPackages = listOf(RECENT_PACKAGE_1)
        val expectedPackages = listOf(pairPackages, recentTaskPackages)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasMatchingAppPairAndSplitTask_dedupesSplitTask() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(RECENT_SPLIT_PACKAGES_1),
            runningTasks = emptyList(),
            recentTaskPackages =
                listOf(
                    RECENT_SPLIT_PACKAGES_1,
                    RECENT_PACKAGE_1,
                    RECENT_PACKAGE_2,
                    RECENT_PACKAGE_3,
                ),
        )

        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasReversedAppPairAndSplitTask_dedupesSplitTask() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(RECENT_SPLIT_PACKAGES_1),
            runningTasks = emptyList(),
            recentTaskPackages =
                listOf(
                    RECENT_SPLIT_PACKAGES_1_REVERSED,
                    RECENT_PACKAGE_1,
                    RECENT_PACKAGE_2,
                    RECENT_PACKAGE_3,
                ),
        )

        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        val expectedPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_hasDifferentAppPairAndSplitTask_includesSplitTask() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = listOf(RECENT_SPLIT_PACKAGES_1),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_SPLIT_PACKAGES_2, RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )

        val shownPackages = recentAppsController.shownTasks.map { it.packageNames }
        val pairPackages = RECENT_SPLIT_PACKAGES_2.split("_")
        val recentTaskPackages = listOf(RECENT_PACKAGE_1)
        val expectedPackages = listOf(pairPackages, recentTaskPackages)
        assertThat(shownPackages).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_noActualChangeToRecents_commitRunningAppsToUI_notCalled() {
        setInDesktopMode(false)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
        // Call onRecentTasksChanged() again with the same tasks, verify it's a no-op.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_noActualChangeToRunning_commitRunningAppsToUI_notCalled() {
        setInDesktopMode(true)
        val runningTask1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val runningTask2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
        // Call onRecentTasksChanged() again with the same tasks, verify it's a no-op.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(runningTask1, runningTask2),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_onlyMinimizedChanges_commitRunningAppsToUI_isCalled() {
        setInDesktopMode(true)
        val task1Minimized =
            createTask(id = 1, RUNNING_APP_PACKAGE_1, isVisible = false, isMinimized = true)
        val task2Visible = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        val task2Minimized =
            createTask(id = 2, RUNNING_APP_PACKAGE_2, isVisible = false, isMinimized = true)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1Minimized, task2Visible),
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()

        // Call onRecentTasksChanged() again with a new minimized app, verify we update UI.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1Minimized, task2Minimized),
            recentTaskPackages = emptyList(),
        )

        verify(taskbarViewController, times(2)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_hotseatAppStartsRunning_commitRunningAppsToUI_isCalled() {
        setInDesktopMode(true)
        val hotseatPackages = listOf(HOTSEAT_PACKAGE_1, HOTSEAT_PACKAGE_2)
        val originalTasks = listOf(createTask(id = 1, RUNNING_APP_PACKAGE_1))
        val newTasks =
            listOf(createTask(id = 1, RUNNING_APP_PACKAGE_1), createTask(id = 2, HOTSEAT_PACKAGE_1))
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = hotseatPackages,
            runningTasks = originalTasks,
            recentTaskPackages = emptyList(),
        )
        verify(taskbarViewController, times(1)).commitRunningAppsToUI()

        // Call onRecentTasksChanged() again with a new running app, verify we update UI.
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = hotseatPackages,
            runningTasks = newTasks,
            recentTaskPackages = emptyList(),
        )

        verify(taskbarViewController, times(2)).commitRunningAppsToUI()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_sameHotseatPackage_differentUser_isInShownTasks() {
        setInDesktopMode(true)
        val hotseatPackageUser = PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_2)
        val hotseatPackageUsers = listOf(hotseatPackageUser)
        val runningTask = createTask(id = 1, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val runningTasks = listOf(PerDisplayRunningApps(listOf(runningTask), DEFAULT_DISPLAY))
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks).contains(runningTask)
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_multipleDesktops() {
        setInDesktopMode(true)
        val hotseatPackageUsers = listOf(PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_1))
        val defaultDisplayRunningTask =
            createTask(id = 1, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val secondaryDisplayRunningTask =
            createTask(id = 2, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val runningTasks =
            listOf(
                PerDisplayRunningApps(listOf(defaultDisplayRunningTask), DEFAULT_DISPLAY),
                PerDisplayRunningApps(listOf(secondaryDisplayRunningTask), DEFAULT_DISPLAY + 1),
            )
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks).containsExactly(secondaryDisplayRunningTask)
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_multipleDesktops_appsNotInHotseat() {
        setInDesktopMode(true)
        val hotseatPackageUsers = listOf(PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_1))
        val defaultDisplayRunningTask =
            createTask(id = 1, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val secondaryDisplayRunningTask =
            createTask(id = 2, RUNNING_APP_PACKAGE_2, localUserHandle = USER_HANDLE_1)
        val runningTasks =
            listOf(
                PerDisplayRunningApps(listOf(defaultDisplayRunningTask), DEFAULT_DISPLAY),
                PerDisplayRunningApps(listOf(secondaryDisplayRunningTask), DEFAULT_DISPLAY + 1),
            )
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )
        assertThat(recentShownTasks)
            .containsExactly(defaultDisplayRunningTask, secondaryDisplayRunningTask)
        assertThat(recentAppsController.runningTaskIds).containsExactlyElementsIn(listOf(1, 2))
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_multipleDesktops_multipleAppInstances() {
        setInDesktopMode(true)
        val hotseatPackageUsers = listOf(PackageUser(HOTSEAT_PACKAGE_1, USER_HANDLE_1))
        val defaultDisplayRunningTask1 =
            createTask(id = 1, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val defaultDisplayRunningTask2 =
            createTask(id = 2, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)

        val secondaryDisplayRunningTask1 =
            createTask(id = 3, RUNNING_APP_PACKAGE_1, localUserHandle = USER_HANDLE_1)
        val secondaryDisplayRunningTask2 =
            createTask(id = 4, HOTSEAT_PACKAGE_1, localUserHandle = USER_HANDLE_1)

        val runningTasks =
            listOf(
                PerDisplayRunningApps(
                    listOf(defaultDisplayRunningTask1, defaultDisplayRunningTask2),
                    DEFAULT_DISPLAY,
                ),
                PerDisplayRunningApps(
                    listOf(secondaryDisplayRunningTask1, secondaryDisplayRunningTask2),
                    DEFAULT_DISPLAY + 1,
                ),
            )
        prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers = hotseatPackageUsers,
            runningTasks = runningTasks,
            recentTaskPackages = emptyList(),
        )

        assertThat(recentShownTasks).hasSize(1)
        assertThat(recentShownTasks)
            .containsAnyOf(defaultDisplayRunningTask2, secondaryDisplayRunningTask1)

        assertThat(recentAppsController.runningTaskIds)
            .containsExactlyElementsIn(listOf(1, 2, 3, 4))
    }

    @Test
    fun onRecentTasksChanged_notInDesktopMode_addTask_existingTaskInstanceReused() {
        setInDesktopMode(false)

        // Initial task.
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_3),
        )
        val task1 = recentAppsController.shownTasks.first().tasks.first()

        // New task.
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        val task2 = recentAppsController.shownTasks.first().tasks.first()
        assertThat(task1).isSameInstanceAs(task2)
    }

    @Test
    fun hasSingleTask_noTargetPackage_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        assertThat(recentAppsController.getSingleTask(ItemInfo())).isNull()
    }

    @Test
    fun hasSingleTask_noRecentTasks_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = emptyList(),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_1)
        assertThat(recentAppsController.getSingleTask(itemInfo)).isNull()
    }

    @Test
    fun hasSingleTask_noMatchingSingleTask_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_2)
        assertThat(recentAppsController.getSingleTask(itemInfo)).isNull()
    }

    @Test
    fun hasSingleTask_matchingSingleTask_returnsTrue() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_1)
        assertThat(recentAppsController.getSingleTask(itemInfo)).isNotNull()
    }

    @Test
    fun hasSingleTask_matchingSingleTaskDifferentUser_returnsFalse() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        // RECENT_PACKAGE_1 is created with myUserHandle in createRecentTasksFromPackageNames
        val itemInfo = createItemInfo(RECENT_PACKAGE_1, USER_HANDLE_1)
        assertThat(recentAppsController.getSingleTask(itemInfo)).isNull()
    }

    @Test
    fun getNonDesktopTask_nullItemInfo_returnsNull() {
        // No setup needed, just call with null
        assertThat(recentAppsController.getNonDesktopTask(null)).isNull()
    }

    @Test
    fun getNonDesktopTask_itemInfoWithNoPackage_returnsNull() {
        // No setup needed, just call with empty ItemInfo
        assertThat(recentAppsController.getNonDesktopTask(ItemInfo())).isNull()
    }

    @Test
    fun getNonDesktopTask_noRecentTasks_returnsNull() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = emptyList(),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_1)
        assertThat(recentAppsController.getNonDesktopTask(itemInfo)).isNull()
    }

    @Test
    fun getNonDesktopTask_onlyDesktopTasks_returnsNull() {
        val desktopTask = createTask(id = 1, RECENT_PACKAGE_1)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(desktopTask),
            recentTaskPackages = emptyList(),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_1)
        assertThat(recentAppsController.getNonDesktopTask(itemInfo)).isNull()
    }

    @Test
    fun getNonDesktopTask_matchingSingleTask_returnsTask() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_1)
        val task = recentAppsController.getNonDesktopTask(itemInfo)
        assertThat(task).isNotNull()
        assertThat(task!!.key.packageName).isEqualTo(RECENT_PACKAGE_1)
    }

    @Test
    fun getNonDesktopTask_matchingSplitTask_returnsTask() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_SPLIT_PACKAGES_1, RECENT_PACKAGE_1),
        )
        // RECENT_SPLIT_PACKAGES_1 is "split1_split2"
        val itemInfo = createItemInfo("split1")
        val task = recentAppsController.getNonDesktopTask(itemInfo)
        assertThat(task).isNotNull()
        assertThat(task!!.key.packageName).isEqualTo("split1")
    }

    @Test
    fun getNonDesktopTask_noMatchingTask_returnsNull() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        val itemInfo = createItemInfo(RECENT_PACKAGE_2)
        assertThat(recentAppsController.getNonDesktopTask(itemInfo)).isNull()
    }

    @Test
    fun getNonDesktopTask_matchingPackageDifferentUser_returnsNull() {
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1),
        )
        // RECENT_PACKAGE_1 is created with myUserHandle
        val itemInfo = createItemInfo(RECENT_PACKAGE_1, USER_HANDLE_1)
        assertThat(recentAppsController.getNonDesktopTask(itemInfo)).isNull()
    }

    @Test
    fun getRunningTaskWithId_taskExists_returnsTask() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        val task2 = createTask(id = 2, RUNNING_APP_PACKAGE_2)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1, task2),
            recentTaskPackages = emptyList(),
        )

        val result = recentAppsController.getRunningTaskWithId(2)

        assertThat(result).isSameInstanceAs(task2)
    }

    @Test
    fun getRunningTaskWithId_taskDoesNotExist_returnsNull() {
        setInDesktopMode(true)
        val task1 = createTask(id = 1, RUNNING_APP_PACKAGE_1)
        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(task1),
            recentTaskPackages = emptyList(),
        )

        val result = recentAppsController.getRunningTaskWithId(99)

        assertThat(result).isNull()
    }

    @Test
    fun onRecentTasksChanged_inDesktopMode_transparentTask_isFilteredOut() {
        setInDesktopMode(true)
        val transparentTask = createTask(id = 1, "transparentPackage")
        transparentTask.key.numActivities = 1
        transparentTask.key.isActivityStackTransparent = true
        transparentTask.key.windowingMode = WINDOWING_MODE_FULLSCREEN
        val regularTask = createTask(id = 2, RUNNING_APP_PACKAGE_1)
        whenever(
                mockDesktopModeCompatPolicy.isTransparentOverlay(
                    transparentTask.key.isActivityStackTransparent,
                    transparentTask.key.numActivities,
                    transparentTask.key.windowingMode,
                )
            )
            .thenReturn(true)

        prepareHotseatAndRunningAndRecentApps(
            hotseatPackages = emptyList(),
            runningTasks = listOf(transparentTask, regularTask),
            recentTaskPackages = emptyList(),
        )

        assertThat(recentAppsController.runningTaskIds).containsExactly(2)
        val shownPackages = recentAppsController.shownTasks.flatMap { it.packageNames }
        assertThat(shownPackages).containsExactly(RUNNING_APP_PACKAGE_1)
    }

    @Test
    fun fetchIcons_addTask_onlyUpdatesNewTask() {
        setInDesktopMode(false)

        // Initial task.
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_3),
        )
        waitForTaskbarUiThreadSync()
        val task1 = recentAppsController.shownTasks.first().tasks.first()
        verify(taskbarViewController, times(1)).onTaskUpdated(eq(task1), any())

        // New task.
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )
        waitForTaskbarUiThreadSync()
        val task2 = recentAppsController.shownTasks.last().tasks.first()
        verify(taskbarViewController, times(1)).onTaskUpdated(eq(task2), any())
        // Not updated again.
        verify(taskbarViewController, times(1)).onTaskUpdated(eq(task1), any())
    }

    @Test
    fun fetchIcons_addTask_infoChangedForExistingTask_updatesInfoForExistingTask() {
        setInDesktopMode(false)

        // Initial task.
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_3),
        )
        val task1 = recentAppsController.shownTasks.first().tasks.first()

        // Update info for task.
        whenever(mockIconCache.getBitmapInfoInBackground(eq(task1), any(), any())).thenAnswer {
            it.getArgument<GetTaskBitmapInfoCallback>(2)
                .onBitmapInfoReceived(BITMAP_INFO_2, TASK_DESCRIPTION, TASK_TITLE)
            null
        }

        // New task.
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2, RECENT_PACKAGE_3),
        )

        // Updated twice in total.
        waitForTaskbarUiThreadSync()
        verify(taskbarViewController, times(2)).onTaskUpdated(eq(task1), any())
    }

    @Test
    fun themeChanged_forceUpdatesExistingTaskIcon() {
        setInDesktopMode(false)
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_3),
        )
        val task = recentAppsController.shownTasks.first().tasks.first()

        val themeChangeListenerCaptor = argumentCaptor<ThemeManager.ThemeChangeListener>()
        verify(mockThemeManager).addChangeListener(themeChangeListenerCaptor.capture())
        themeChangeListenerCaptor.lastValue.onThemeChanged()

        waitForTaskbarUiThreadSync()
        // Called second time due to theme change.
        verify(taskbarViewController, times(2)).onTaskUpdated(eq(task), any())
    }

    @Test
    fun iconShapeChanged_forceUpdatesExistingTaskIcon() {
        setInDesktopMode(false)
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_3),
        )
        val task = recentAppsController.shownTasks.first().tasks.first()

        iconShapeData.dispatchValue(
            IconShape(
                100,
                AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null)
                    .apply { setBounds(0, 0, 50, 100) }
                    .iconMask,
                Bitmap.createBitmap(1, 1, Config.ARGB_8888).apply { eraseColor(Color.BLACK) },
            )
        )

        waitForTaskbarUiThreadSync()
        // Called second time due to icon shape change.
        verify(taskbarViewController, times(2)).onTaskUpdated(eq(task), any())
    }

    @Test
    fun onTaskIconChanged_updatesExistingTaskIcon() {
        setInDesktopMode(false)
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        waitForTaskbarUiThreadSync()
        val task = recentAppsController.shownTasks.first().tasks.first()
        verify(taskbarViewController, times(1)).onTaskUpdated(eq(task), any())

        taskVisualsChangeListener?.onTaskIconChanged(
            task.key.packageName,
            UserHandle.of(task.key.userId),
        )
        waitForTaskbarUiThreadSync()
        verify(taskbarViewController, times(2)).onTaskUpdated(eq(task), any())
    }

    @Test
    fun onTaskIconChanged_differentUser_ignoresIconUpdate() {
        setInDesktopMode(false)
        updateRecentTasks(
            runningTasks = emptyList(),
            recentTaskPackages = listOf(RECENT_PACKAGE_1, RECENT_PACKAGE_2),
        )
        waitForTaskbarUiThreadSync()
        val task = recentAppsController.shownTasks.first().tasks.first()
        verify(taskbarViewController, times(1)).onTaskUpdated(eq(task), any())

        // Trigger icon change for different user.
        taskVisualsChangeListener?.onTaskIconChanged(
            task.key.packageName,
            UserHandle.of(task.key.userId + 1),
        )
        waitForTaskbarUiThreadSync()
        // Icon not updated for actual user.
        verify(taskbarViewController, times(1)).onTaskUpdated(eq(task), any())
    }

    private fun prepareHotseatAndRunningAndRecentApps(
        hotseatPackages: List<String>,
        runningTasks: List<Task>,
        recentTaskPackages: List<String>,
    ): Array<ItemInfo?> {
        val hotseatPackageUsers = hotseatPackages.map { PackageUser(it, myUserHandle) }
        return prepareHotseatAndRunningAndRecentAppsInternal(
            hotseatPackageUsers,
            listOf(PerDisplayRunningApps(runningTasks, DEFAULT_DISPLAY)),
            recentTaskPackages,
        )
    }

    private fun prepareHotseatAndRunningAndRecentAppsInternal(
        hotseatPackageUsers: List<PackageUser>,
        runningTasks: List<PerDisplayRunningApps>,
        recentTaskPackages: List<String>,
    ): Array<ItemInfo?> {
        val hotseatItems = createHotseatItemsFromPackageUsers(hotseatPackageUsers)
        recentAppsController.updateHotseatItemInfos(hotseatItems.toTypedArray())
        updateRecentTasks(runningTasks, recentTaskPackages)
        return recentAppsController.shownHotseatItems.toTypedArray()
    }

    private fun updateRecentTasks(
        runningTasks: List<PerDisplayRunningApps>,
        recentTaskPackages: List<String>,
    ) {
        val recentTasks = createRecentTasksFromPackageNames(recentTaskPackages)
        val allTasks =
            ArrayList<GroupTask>().apply {
                runningTasks.forEach {
                    add(DesktopTask(deskId = 0, it.displayId, ArrayList(it.tasks)))
                }
                addAll(recentTasks)
            }
        doAnswer {
                val callback: Consumer<ArrayList<GroupTask>> = it.getArgument(1)
                callback.accept(allTasks)
                taskListChangeId
            }
            .whenever(mockRecentsModel)
            .getTasks(any(), any<Consumer<List<GroupTask>>>())
        if (enableTaskbarUiThread()) {
            recentTasksChangedCallback?.invoke(null)
            waitForTaskbarUiThreadSync()
        } else {
            recentTasksChangedListener?.onRecentTasksChanged()
        }
    }

    private fun createHotseatItemsFromPackageUsers(
        packageUsers: List<PackageUser>
    ): List<ItemInfo> {
        return packageUsers.map {
            val userHandle = it.userHandle
            if (it.packageName.startsWith("split")) {
                AppPairInfo(
                    it.packageName.split("_").map {
                        createTestAppInfo(packageName = it, userHandle = userHandle)
                            .makeWorkspaceItem(taskbarActivityContext)
                    }
                )
            } else {
                createTestAppInfo(packageName = it.packageName, userHandle = userHandle)
                    .apply {
                        container =
                            if (it.packageName.startsWith("predicted")) {
                                CONTAINER_HOTSEAT_PREDICTION
                            } else {
                                CONTAINER_HOTSEAT
                            }
                    }
                    .makeWorkspaceItem(taskbarActivityContext)
            }
        }
    }

    private fun createTestAppInfo(
        packageName: String = "testPackageName",
        className: String = "testClassName",
        userHandle: UserHandle,
    ) = AppInfo(ComponentName(packageName, className), className /* title */, userHandle, Intent())

    private fun createRecentTasksFromPackageNames(packageNames: List<String>): List<GroupTask> {
        return packageNames.map { packageName ->
            if (packageName.startsWith("split")) {
                val splitPackages = packageName.split("_")
                SplitTask(
                    createTask(100, splitPackages[0]),
                    createTask(101, splitPackages[1]),
                    SplitBounds(
                        /* leftTopBounds = */ Rect(),
                        /* rightBottomBounds = */ Rect(),
                        /* leftTopTaskId = */ 1,
                        /* rightBottomTaskId = */ 2,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50,
                    ),
                )
            } else {
                // Use the number at the end of the test packageName as the id.
                val id = 1000 + packageName[packageName.length - 1].code
                SingleTask(createTask(id, packageName))
            }
        }
    }

    private fun createTask(
        id: Int,
        packageName: String,
        isVisible: Boolean = true,
        localUserHandle: UserHandle? = null,
        isMinimized: Boolean = false,
    ): Task {
        return Task(
                Task.TaskKey(
                    id,
                    WINDOWING_MODE_FREEFORM,
                    Intent().apply { `package` = packageName },
                    ComponentName(packageName, "TestActivity"),
                    localUserHandle?.identifier ?: myUserHandle.identifier,
                    0,
                )
            )
            .apply {
                this.isVisible = isVisible
                this.isMinimized = isMinimized
            }
    }

    private fun setInDesktopMode(inDesktopMode: Boolean) {
        whenever(taskbarControllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar())
            .thenReturn(inDesktopMode)
        whenever(taskbarControllers.taskbarDesktopModeController.isInDesktopMode(DEFAULT_DISPLAY))
            .thenReturn(inDesktopMode)
    }

    private fun createItemInfo(
        packageName: String,
        userHandle: UserHandle = myUserHandle,
    ): ItemInfo {
        val appInfo = AppInfo()
        appInfo.intent = Intent().setComponent(ComponentName(packageName, "className"))
        appInfo.user = userHandle
        return WorkspaceItemInfo(appInfo)
    }

    private val GroupTask.packageNames: List<String>
        get() = tasks.map { task -> task.key.packageName }

    private fun waitForTaskbarUiThreadSync() {
        if (enableTaskbarUiThread()) {
            getTaskbarUiThread().submit {}.get()
        }
    }

    private companion object {
        const val HOTSEAT_PACKAGE_1 = "hotseat1"
        const val HOTSEAT_PACKAGE_2 = "hotseat2"
        const val PREDICTED_PACKAGE_1 = "predicted1"
        const val RUNNING_APP_PACKAGE_1 = "running1"
        const val RUNNING_APP_PACKAGE_2 = "running2"
        const val RUNNING_APP_PACKAGE_3 = "running3"
        const val RECENT_PACKAGE_1 = "recent1"
        const val RECENT_PACKAGE_2 = "recent2"
        const val RECENT_PACKAGE_3 = "recent3"
        const val RECENT_SPLIT_PACKAGES_1 = "split1_split2"
        const val RECENT_SPLIT_PACKAGES_1_REVERSED = "split1_split2"
        const val RECENT_SPLIT_PACKAGES_2 = "split3_split4"

        const val TASK_TITLE = "title"
        const val TASK_DESCRIPTION = "description"

        val BITMAP_INFO_1 = BitmapInfo.fromBitmap(Bitmap.createBitmap(100, 100, Config.ARGB_8888))
        val BITMAP_INFO_2 = BitmapInfo.fromBitmap(Bitmap.createBitmap(200, 200, Config.ARGB_8888))
    }

    data class PackageUser(val packageName: String, val userHandle: UserHandle)

    data class PerDisplayRunningApps(val tasks: List<Task>, val displayId: Int)
}
