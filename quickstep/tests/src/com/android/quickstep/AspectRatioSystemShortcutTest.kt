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

package com.android.quickstep

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.Display.DEFAULT_DISPLAY
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.deviceprofile.DeviceProperties
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.logging.StatsLogManager.StatsLogger
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SandboxContext
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskViewTestDIHelpers.mockRecentsModel
import com.android.quickstep.orientation.LandscapePagedViewHandler
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.util.SingleTask
import com.android.quickstep.views.IconAppChipView
import com.android.quickstep.views.LauncherRecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Test for [AspectRatioSystemShortcut] */
class AspectRatioSystemShortcutTest {

    /** Spy on a concrete Context so we can reference real View, Layout, and Display properties. */
    private val context: Context = spy(InstrumentationRegistry.getInstrumentation().targetContext)
    private val applicationContext = SandboxContext(context)

    /**
     * RecentsViewContainer and its super-interface ActivityContext contain methods to convert
     * themselves to a Context at runtime, and static methods to convert a Context back to
     * themselves by traversing ContextWrapper layers.
     *
     * Thus there is an undocumented assumption that a RecentsViewContainer always extends Context.
     * We need to mock all of the RecentsViewContainer methods but leave the Context-under-test
     * intact.
     *
     * The simplest way is to extend ContextWrapper and delegate the RecentsViewContainer interface
     * to a mock.
     */
    class RecentsViewContainerContextWrapper(base: Context) :
        ContextWrapper(base), RecentsViewContainer by mock() {

        private val statsLogManager: StatsLogManager = mock()

        override fun getStatsLogManager(): StatsLogManager = statsLogManager

        override fun startActivitySafely(v: View, intent: Intent, item: ItemInfo?): RunnableList? =
            null
    }

    /**
     * This <RecentsViewContainer & Context> is implicitly required in many parts of Launcher that
     * require a Context. See RecentsViewContainerContextWrapper.
     */
    private val launcher: RecentsViewContainerContextWrapper =
        spy(RecentsViewContainerContextWrapper(context))

    private val recentsView: LauncherRecentsView = mock()
    private val abstractFloatingViewHelper: AbstractFloatingViewHelper = mock()
    private val taskOverlayFactory: TaskOverlayFactory =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val factory = AspectRatioSystemShortcut.Factory(abstractFloatingViewHelper)
    private val statsLogger = mock<StatsLogger>()
    private val orientedState: RecentsOrientedState =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private lateinit var taskView: TaskView

    @Before
    fun setUp() {
        whenever(launcher.getOverviewPanel<LauncherRecentsView>()).thenReturn(recentsView)

        val statsLogManager = launcher.getStatsLogManager()
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
        whenever(statsLogger.withItemInfo(any())).thenReturn(statsLogger)

        whenever(orientedState.orientationHandler).thenReturn(LandscapePagedViewHandler())

        applicationContext.initDaggerComponent(
            DaggerTaskViewTestComponent.builder().bindRecentsModel(mockRecentsModel())
        )
        taskView =
            LayoutInflater.from(context).cloneInContext(launcher).inflate(R.layout.task, null)
                as TaskView
        taskView.viewModel = mock()
        taskView.setLayoutParams(ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    @Test
    fun createShortcut_nonLargeScreen_notCreated() {
        val task = createTask()
        val taskContainer = createTaskContainer(task)

        setIsLargeScreen(false)
        taskView.bind(SingleTask(task), orientedState, taskOverlayFactory)

        assertThat(factory.getShortcuts(launcher, taskContainer)).isNull()
    }

    @Test
    fun createShortcut_largeScreen_created_andOpensSettings() {
        val task = createTask()
        val taskContainer = spy(createTaskContainer(task))
        val taskViewItemInfo = mock<TaskViewItemInfo>()
        doReturn(taskViewItemInfo).whenever(taskContainer).itemInfo

        setIsLargeScreen(true)
        taskView.bind(SingleTask(task), orientedState, taskOverlayFactory)

        val singleShortcut = factory.getShortcuts(launcher, taskContainer)!!.single()
        singleShortcut.onClick(taskView)

        // 1) Panel should be closed
        val allTypesExceptRebindSafe =
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv()
        verify(abstractFloatingViewHelper).closeOpenViews(launcher, true, allTypesExceptRebindSafe)

        // 2) Compat mode settings activity should be launched
        val intentCaptor = argumentCaptor<Intent>()
        verify(launcher)
            .startActivitySafely(any<View>(), intentCaptor.capture(), eq(taskViewItemInfo))
        val intent = intentCaptor.firstValue
        assertThat(intent.action).isEqualTo(Settings.ACTION_MANAGE_USER_ASPECT_RATIO_SETTINGS)

        // 3) Shortcut tap event should be reported
        verify(statsLogger).withItemInfo(taskViewItemInfo)
        verify(statsLogger).log(LauncherEvent.LAUNCHER_ASPECT_RATIO_SETTINGS_SYSTEM_SHORTCUT_TAP)
    }

    /**
     * Overrides the [DeviceProperties.isLargeScreen] property to simulate different screen sizes.
     */
    private fun setIsLargeScreen(isLargeScreen: Boolean) {
        val deviceProperties = mock<DeviceProperties>()
        whenever(deviceProperties.isLargeScreen).thenReturn(isLargeScreen)

        val deviceProfile = mock<DeviceProfile>()
        whenever(deviceProfile.deviceProperties).thenReturn(deviceProperties)

        whenever(launcher.getDeviceProfile()).thenReturn(deviceProfile)
    }

    /** Create a (very) fake task for testing. */
    private fun createTask() =
        Task(
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                ComponentName("", ""),
                /* userId */ 0,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                ComponentName("", ""),
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ false,
                /* topActivityType */ ACTIVITY_TYPE_STANDARD,
                /* isTopActivityTransparent */ false,
            )
        )

    /** Create TaskContainer out of a given Task and fill in the rest with mocks. */
    private fun createTaskContainer(task: Task) =
        TaskContainer(
            taskView,
            task,
            mock<TaskContentView>(),
            mock<TaskThumbnailView>(),
            mock<IconAppChipView>(),
            mock<TransformingTouchDelegate>(),
            SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
            digitalWellBeingToast = null,
            taskOverlayFactory,
        )
}
