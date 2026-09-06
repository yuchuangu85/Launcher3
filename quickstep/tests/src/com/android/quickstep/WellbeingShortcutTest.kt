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
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.views.ActivityContext
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
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Test for [WellbeingShortcut] */
class WellbeingShortcutTest {

    private val context: Context = spy(InstrumentationRegistry.getInstrumentation().targetContext)

    class RecentsViewContainerContextWrapper(base: Context) :
        ContextWrapper(base), RecentsViewContainer by mock() {

        private val statsLogManager: StatsLogManager = mock()

        override fun getStatsLogManager(): StatsLogManager = statsLogManager

        override fun startActivitySafely(v: View, intent: Intent, item: ItemInfo?): RunnableList? =
            null
    }

    private val launcher: RecentsViewContainerContextWrapper =
        spy(RecentsViewContainerContextWrapper(context))
    private val taskOverlayFactory: TaskOverlayFactory =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val recentsView: LauncherRecentsView = mock()
    private val orientedState: RecentsOrientedState =
        mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val taskView: TaskView = createTaskViewMock()

    @Before
    fun setUp() {
        whenever(launcher.getOverviewPanel<LauncherRecentsView>()).thenReturn(recentsView)
        whenever(orientedState.orientationHandler).thenReturn(LandscapePagedViewHandler())
        taskView.viewModel = mock()
        taskView.coroutineScope = mock()
        taskView.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    @Test
    fun wellbeingShortcut_onClick_callsAction() {
        val innerAction = mock<SystemShortcut<ActivityContext>>()
        val task = createTask()
        taskView.bind(SingleTask(task), orientedState, taskOverlayFactory)
        val taskContainer = createTaskContainer(task)

        val wellbeingShortcut = WellbeingShortcut(launcher, taskContainer, innerAction)

        wellbeingShortcut.onClick(taskView)

        // Capture the first runnable passed to switchToScreenshot
        val screenshotRunnableCaptor = argumentCaptor<Runnable>()
        verify(recentsView).switchToScreenshot(screenshotRunnableCaptor.capture())
        screenshotRunnableCaptor.firstValue.run()

        // Capture the runnable passed to finishRecentsAnimation
        val animationRunnableCaptor = argumentCaptor<Runnable>()
        verify(recentsView)
            .finishRecentsAnimation(
                /* toHome = */ eq(true),
                /* shouldPip = */ eq(false),
                /* onFinishComplete = */ animationRunnableCaptor.capture(),
            )
        animationRunnableCaptor.firstValue.run()

        // Verify inner action onClick is finally called
        verify(innerAction).onClick(taskView)
    }

    @Test
    fun wellbeingShortcut_inheritsPropertiesFromAction() {
        val innerAction = mock<SystemShortcut<ActivityContext>>()
        whenever(innerAction.iconResId).thenReturn(R.drawable.hourglass_24px)
        whenever(innerAction.labelResId).thenReturn(R.string.pause_app_label)

        val task = createTask()
        taskView.bind(SingleTask(task), orientedState, taskOverlayFactory)
        val taskContainer = createTaskContainer(task)
        val wellbeingShortcut = WellbeingShortcut(launcher, taskContainer, innerAction)

        assertThat(wellbeingShortcut.iconResId).isEqualTo(R.drawable.hourglass_24px)
        assertThat(wellbeingShortcut.labelResId).isEqualTo(R.string.pause_app_label)
    }

    @Test
    fun factory_getShortcuts_returnsNull_whenInnerFactoryReturnsNull() {
        val wellbeingShortcutFactory = mock<SystemShortcut.Factory<ActivityContext>>()
        whenever(wellbeingShortcutFactory.getShortcut(any(), any(), any())).thenReturn(null)
        val factory = WellbeingShortcut.Factory(wellbeingShortcutFactory)

        // Mock TaskViewItemInfo to return null targetComponent
        val mockItemInfo = mock<TaskViewItemInfo>()
        whenever(mockItemInfo.targetComponent).thenReturn(null)

        val mockTaskContainer = mock<TaskContainer>()
        whenever(mockTaskContainer.itemInfo).thenReturn(mockItemInfo)
        whenever(mockTaskContainer.taskView).thenReturn(taskView)

        val shortcuts = factory.getShortcuts(launcher, mockTaskContainer)

        assertThat(shortcuts).isNull()
    }

    @Test
    fun factory_getShortcuts_returnsWrappedShortcut_whenInnerFactorySucceeds() {
        val wellbeingShortcutFactory = mock<SystemShortcut.Factory<ActivityContext>>()
        val factory = WellbeingShortcut.Factory(wellbeingShortcutFactory)
        val mockTaskContainer = mock<TaskContainer>()
        whenever(mockTaskContainer.itemInfo).thenReturn(mock())
        whenever(mockTaskContainer.taskView).thenReturn(taskView)

        val mockShortcut = mock<SystemShortcut<ActivityContext>>()
        whenever(wellbeingShortcutFactory.getShortcut(eq(launcher), any(), eq(taskView)))
            .thenReturn(mockShortcut)

        val shortcuts = factory.getShortcuts(launcher, mockTaskContainer)

        assertThat(shortcuts).isNotNull()
        assertThat(shortcuts).hasSize(1)
        assertThat(shortcuts?.first()).isInstanceOf(WellbeingShortcut::class.java)
    }

    private fun createTask(componentName: ComponentName? = ComponentName("pkg", "cls")) =
        Task(
            TaskKey(
                /* id = */ 1,
                /* windowingMode = */ 0,
                /* intent = */ Intent(),
                /* sourceComponent = */ componentName,
                /* userId = */ 0,
                /* lastActiveTime = */ 2000,
                /* displayId = */ 0,
                /* baseActivity = */ componentName,
                /* numActivities = */ 1,
                /* isTopActivityNoDisplay = */ false,
                /* isActivityStackTransparent = */ false,
                /* topActivityType = */ ACTIVITY_TYPE_STANDARD,
                /* isTopActivityTransparent = */ false,
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

    private fun createTaskViewMock(): TaskView {
        val taskView: TaskView = mock()
        whenever(taskView.type).thenReturn(TaskViewType.SINGLE)
        whenever(taskView.context).thenReturn(context)
        return taskView
    }
}
