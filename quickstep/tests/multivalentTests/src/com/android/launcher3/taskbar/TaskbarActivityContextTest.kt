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

package com.android.launcher3.taskbar

import android.app.ActivityOptions
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.rules.MockedRecentsModelHelper
import com.android.launcher3.taskbar.rules.SandboxParams
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.DESKTOP_TASKBAR
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext_ModifiedComponent
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SlideInRemoteTransition
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.tools.dagger.mutation.annotations.BindValue
import com.android.tools.dagger.mutation.annotations.MutatedComponent
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import org.junit.Assert
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@MutatedComponent(target = TaskbarWindowSandboxContext_ModifiedComponent::class)
class TaskbarActivityContextTest {

    private val mockRecentsModelHelper: MockedRecentsModelHelper = MockedRecentsModelHelper()
    private val fakeDesktopState: FakeDesktopState = FakeDesktopState()
    private lateinit var systemUiProxy: SystemUiProxy

    @BindValue val recentsModel: RecentsModel by mockRecentsModelHelper
    @BindValue val activityManagerWrapper: ActivityManagerWrapper = mock()
    @BindValue val desktopState: DesktopState = fakeDesktopState

    @get:Rule(order = 0)
    val context =
        TaskbarWindowSandboxContext.create(
            params =
                SandboxParams(builderBase = mutatedComponentBuilder()) {
                    systemUiProxy = it.systemUiProxy
                }
        )

    @get:Rule(order = 1) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 2) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val activityContext by taskbarUnitTestRule::activityContext
    private val mockRecentsViewInteractor: RecentsViewInteractor = mock()
    private lateinit var spiedActivityContext: Context
    private lateinit var launcherAppsService: LauncherApps

    @Before
    fun setUp() {
        activityContext.setUIController(
            object : TaskbarUIController() {
                override fun getRecentsViewInteractor(): RecentsViewInteractor {
                    return mockRecentsViewInteractor
                }
            }
        )
        spiedActivityContext =
            checkNotNull(
                    taskbarUnitTestRule.taskbarManager.getPerDisplayResourceForTest(
                        context.displayId
                    )
                )
                .windowContext
        launcherAppsService = context.base.spyServiceForChildren<LauncherApps>()
    }

    @Test
    @TaskbarMode(DESKTOP_TASKBAR)
    fun recentFullscreenAppTaskbarIconClicked_onFreeFormDisplay_resumesRecentTask() {
        whenever(activityManagerWrapper.startActivityFromRecents(any<TaskKey>(), any()))
            .thenReturn(true)
        val singleFullscreenTask = SingleTask(createTask("fakePackage1"))
        val workspaceItemInfo =
            WorkspaceItemInfo().apply { intent = singleFullscreenTask.task.key.baseIntent }
        val icon = View(context).apply { tag = workspaceItemInfo }
        mockRecentsModelHelper.updateRecentTasks(
            listOf(SingleTask(createTask("fakePackage2")), singleFullscreenTask)
        )

        runOnTaskbarUiThreadSync {
            activityContext.onTaskbarIconClicked(icon)
            mockRecentsModelHelper.resolvePendingTaskRequests()
        }

        val taskKeyCaptor = argumentCaptor<TaskKey>()
        val activityOptionsCaptor = argumentCaptor<ActivityOptions>()
        verify(activityManagerWrapper)
            .startActivityFromRecents(taskKeyCaptor.capture(), activityOptionsCaptor.capture())
        assertSame(singleFullscreenTask.task.key, taskKeyCaptor.lastValue)
        Assert.assertTrue(
            activityOptionsCaptor.lastValue.remoteTransition.remoteTransition
                is SlideInRemoteTransition
        )

        verify(systemUiProxy, never()).showDesktopApp(any(), any(), any())
        verify(systemUiProxy, never()).startLaunchIntentTransition(any(), any(), any())
        verify(spiedActivityContext, never()).startActivity(any<Intent>(), any<Bundle>())
        verify(launcherAppsService, never())
            .startMainActivity(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun recentFullscreenAppTaskbarIconClicked_normalDisplay_startsActivity() {
        val singleFullscreenTask = SingleTask(createTask())
        val workspaceItemInfo =
            WorkspaceItemInfo().apply { intent = singleFullscreenTask.task.key.baseIntent }
        val icon = View(context).apply { tag = workspaceItemInfo }
        mockRecentsModelHelper.updateRecentTasks(listOf(singleFullscreenTask))

        runOnTaskbarUiThreadSync {
            activityContext.onTaskbarIconClicked(icon)
            mockRecentsModelHelper.resolvePendingTaskRequests()
        }

        val intentCaptor = argumentCaptor<Intent>()
        verify(spiedActivityContext).startActivity(intentCaptor.capture(), any<Bundle>())
        assertSame(workspaceItemInfo.intent.component, intentCaptor.lastValue.component)

        verify(activityManagerWrapper, never()).startActivityFromRecents(any<TaskKey>(), any())
        verify(systemUiProxy, never()).showDesktopApp(any(), any(), any())
        verify(systemUiProxy, never()).startLaunchIntentTransition(any(), any(), any())
        verify(launcherAppsService, never())
            .startMainActivity(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @TaskbarMode(DESKTOP_TASKBAR)
    fun newTaskbarIconClicked_onFreeFormDisplay_startsDesktopLaunchIntentTransition() {
        val workspaceItemInfo =
            WorkspaceItemInfo().apply { intent = Intent().apply { `package` = "1" } }
        val icon = View(context).apply { tag = workspaceItemInfo }

        runOnTaskbarUiThreadSync {
            activityContext.onTaskbarIconClicked(icon)
            mockRecentsModelHelper.resolvePendingTaskRequests()
        }

        verify(systemUiProxy).startLaunchIntentTransition(any(), any(), any())

        verify(activityManagerWrapper, never()).startActivityFromRecents(any<TaskKey>(), any())
        verify(systemUiProxy, never()).showDesktopApp(any(), any(), any())
        verify(spiedActivityContext, never()).startActivity(any<Intent>(), any<Bundle>())
        verify(launcherAppsService, never())
            .startMainActivity(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @TaskbarMode(DESKTOP_TASKBAR)
    fun managedProfileTaskbarIconClicked_onFreeFormDisplay_startsActivity() {
        val workspaceItemInfo =
            WorkspaceItemInfo().apply {
                intent = Intent().apply { component = ComponentName("Package", "Class") }
                user = UserHandle(Process.myUserHandle().identifier + 1)
            }
        val icon = View(context).apply { tag = workspaceItemInfo }

        runOnTaskbarUiThreadSync {
            activityContext.onTaskbarIconClicked(icon)
            mockRecentsModelHelper.resolvePendingTaskRequests()
        }

        val componentNameCaptor = argumentCaptor<ComponentName>()
        verify(launcherAppsService)
            .startMainActivity(componentNameCaptor.capture(), anyOrNull(), anyOrNull(), anyOrNull())
        assertSame(workspaceItemInfo.intent.component, componentNameCaptor.lastValue)

        verify(activityManagerWrapper, never()).startActivityFromRecents(any<TaskKey>(), any())
        verify(systemUiProxy, never()).showDesktopApp(any(), any(), any())
        verify(systemUiProxy, never()).startLaunchIntentTransition(any(), any(), any())
        verify(spiedActivityContext, never()).startActivity(any<Intent>(), any<Bundle>())
    }

    @Test
    @TaskbarMode(DESKTOP_TASKBAR)
    fun recentFullscreenAppTaskbarIconClicked_onProjectedMode_resumesProjectedModeRecentTask() {
        fakeDesktopState.isProjected = true
        val singleFullscreenTask = SingleTask(createTask())
        val workspaceItemInfo =
            WorkspaceItemInfo().apply { intent = singleFullscreenTask.task.key.baseIntent }
        val icon = View(context).apply { tag = workspaceItemInfo }
        mockRecentsModelHelper.updateRecentTasks(listOf(singleFullscreenTask))

        runOnTaskbarUiThreadSync {
            activityContext.onTaskbarIconClicked(icon)
            mockRecentsModelHelper.resolvePendingTaskRequests()
        }

        verify(systemUiProxy).startLaunchIntentTransition(any(), any(), any())
        verify(activityManagerWrapper, never()).startActivityFromRecents(any<TaskKey>(), any())
    }

    private fun createTask(packageName: String = "fakePackage") =
        Task(
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent().apply { `package` = packageName },
                ComponentName("", ""),
                context.base.userId,
                /* lastActiveTime */ 2000,
                context.base.displayId,
                ComponentName(packageName, ""),
                /* numActivities= */ 1,
                /* isTopActivityNoDisplay= */ false,
                /* isActivityStackTransparent= */ false,
                /* topActivityType= */ ACTIVITY_TYPE_STANDARD,
                /* isTopActivityTransparent= */ false,
            )
        )
}
