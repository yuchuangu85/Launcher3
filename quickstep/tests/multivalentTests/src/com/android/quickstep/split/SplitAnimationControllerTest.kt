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

package com.android.quickstep.split

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.SurfaceControl.Transaction
import android.view.View
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.split.SplitAnimationController.SplitLaunchKind
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.IconAppChipView
import com.android.quickstep.views.TaskContainer
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.recents.model.Task
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SplitAnimationControllerTest {

    private val taskId = 9
    private val taskId2 = 10

    private val mockSplitSelectStateController: SplitSelectStateController = mock()
    // TaskView
    private val mockTaskView: TaskView = mock()
    private val mockSnapshotView: TaskThumbnailView = mock()
    private val mockBitmap: Bitmap = mock()
    private val mockIconView: IconAppChipView = mock()
    private val mockTaskViewDrawable: Drawable = mock()
    // GroupedTaskView
    private val mockGroupedTaskView: GroupedTaskView = mock()
    private val mockTask: Task = mock()
    private val mockTaskKey: Task.TaskKey = mock()
    private val mockTaskContainer: TaskContainer = mock()
    // AppPairIcon
    private val mockAppPairIcon: AppPairIcon = mock()
    private val mockContextThemeWrapper: ContextThemeWrapper = mock()
    private val mockTaskbarActivityContext: TaskbarActivityContext = mock()
    private val mockPackageManager: PackageManager = mock()

    // AppPairInfo
    private val mockAppPairInfo: AppPairInfo = mock()
    private val mockWorkspaceItemInfo1: WorkspaceItemInfo = mock()
    private val mockWorkspaceItemInfo2: WorkspaceItemInfo = mock()

    // SplitSelectSource
    private val splitSelectSource: SplitConfigurationOptions.SplitSelectSource = mock()
    private val mockSplitSourceDrawable: Drawable = mock()
    private val mockSplitSourceView: View = mock()
    private val mockItemInfo: ItemInfo = mock()

    private val stateManager: StateManager<*, *> = mock()
    private val depthController: DepthController<*, *> = mock()
    private val transitionInfo: TransitionInfo = mock()
    private val transaction: Transaction = mock()

    private lateinit var splitAnimationController: SplitAnimationController

    @Before
    fun setup() {
        whenever(mockTaskContainer.snapshotView).thenReturn(mockSnapshotView)
        whenever(mockTaskContainer.thumbnail).thenReturn(mockBitmap)
        whenever(mockTaskContainer.iconView).thenReturn(mockIconView)
        whenever(mockTaskContainer.task).thenReturn(mockTask)
        whenever(mockIconView.getDrawable()).thenReturn(mockTaskViewDrawable)
        whenever(mockTaskView.taskContainers).thenReturn(List(1) { mockTaskContainer })
        whenever(mockTaskView.firstTaskContainer).thenReturn(mockTaskContainer)

        whenever(splitSelectSource.drawable).thenReturn(mockSplitSourceDrawable)
        whenever(splitSelectSource.view).thenReturn(mockSplitSourceView)
        whenever(splitSelectSource.itemInfo).thenReturn(mockItemInfo)

        whenever(mockAppPairIcon.context).thenReturn(mockContextThemeWrapper)
        whenever(mockContextThemeWrapper.packageManager).thenReturn(mockPackageManager)
        whenever(mockAppPairIcon.info).thenReturn(mockAppPairInfo)
        whenever(mockAppPairInfo.getFirstApp()).thenReturn(mockWorkspaceItemInfo1)
        whenever(mockAppPairInfo.getSecondApp()).thenReturn(mockWorkspaceItemInfo2)

        splitAnimationController = SplitAnimationController(mockSplitSelectStateController)
    }

    @Test
    fun getFallbackAppInfoForUnknownFullscreen_app2Suspended_returnsApp2() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        whenever(mockPackageManager.isPackageSuspended("pkg1")).thenReturn(false)
        whenever(mockPackageManager.isPackageSuspended("pkg2")).thenReturn(true)

        val result =
            splitAnimationController.getFallbackAppInfoForUnknownFullscreen(mockAppPairIcon)
        assertEquals(mockWorkspaceItemInfo2, result)
    }

    @Test
    fun getFallbackAppInfoForUnknownFullscreen_app1Suspended_returnsApp1() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        whenever(mockPackageManager.isPackageSuspended("pkg1")).thenReturn(true)
        whenever(mockPackageManager.isPackageSuspended("pkg2")).thenReturn(false)

        val result =
            splitAnimationController.getFallbackAppInfoForUnknownFullscreen(mockAppPairIcon)
        assertEquals(mockWorkspaceItemInfo1, result)
    }

    @Test
    fun getFallbackAppInfoForUnknownFullscreen_neitherSuspended_returnsApp1() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        whenever(mockPackageManager.isPackageSuspended("pkg1")).thenReturn(false)
        whenever(mockPackageManager.isPackageSuspended("pkg2")).thenReturn(false)

        val result =
            splitAnimationController.getFallbackAppInfoForUnknownFullscreen(mockAppPairIcon)
        assertEquals(mockWorkspaceItemInfo1, result)
    }

    @Test
    fun getFallbackAppInfoForUnknownFullscreen_packageNotFound_returnsApp1() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        whenever(mockPackageManager.isPackageSuspended("pkg1"))
            .thenThrow(IllegalArgumentException())
        whenever(mockPackageManager.isPackageSuspended("pkg2"))
            .thenThrow(IllegalArgumentException())

        val result =
            splitAnimationController.getFallbackAppInfoForUnknownFullscreen(mockAppPairIcon)
        assertEquals(mockWorkspaceItemInfo1, result)
    }

    @Test
    fun getFirstAnimInitViews_nullAppChip_useSplitSourceIcon() {
        // Hit fullscreen task dismissal state
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        // Missing taskView icon
        whenever(mockIconView.getDrawable()).thenReturn(null)

        val splitAnimInitProps: SplitAnimationController.Companion.SplitAnimInitProps? =
            splitAnimationController.getFirstAnimInitViews({ mockTaskView }, { splitSelectSource })

        assertEquals(
            "Did not fallback to use splitSource icon drawable",
            mockSplitSourceDrawable,
            splitAnimInitProps!!.iconDrawable,
        )
    }

    @Test
    fun getFirstAnimInitViews_validAppChip_useAppChip() {
        // Hit fullscreen task dismissal state
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        val splitAnimInitProps: SplitAnimationController.Companion.SplitAnimInitProps? =
            splitAnimationController.getFirstAnimInitViews({ mockTaskView }, { splitSelectSource })

        assertEquals(
            "Did not use taskView icon drawable",
            mockTaskViewDrawable,
            splitAnimInitProps!!.iconDrawable,
        )
    }

    @Test
    fun getFirstAnimInitViews_validTaskViewNullSplitSource_useAppChip() {
        // Hit fullscreen task dismissal state
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        // Set split source to null
        whenever(splitSelectSource.drawable).thenReturn(null)

        val splitAnimInitProps: SplitAnimationController.Companion.SplitAnimInitProps? =
            splitAnimationController.getFirstAnimInitViews({ mockTaskView }, { splitSelectSource })

        assertEquals(
            "Did not use taskView icon drawable",
            mockTaskViewDrawable,
            splitAnimInitProps!!.iconDrawable,
        )
    }

    @Test
    fun getFirstAnimInitViews_nullTaskViewValidSplitSource_noTaskDismissal() {
        // Hit initiating split from home
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(false)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        val splitAnimInitProps: SplitAnimationController.Companion.SplitAnimInitProps? =
            splitAnimationController.getFirstAnimInitViews({ mockTaskView }, { splitSelectSource })

        assertEquals(
            "Did not use splitSource icon drawable",
            mockSplitSourceDrawable,
            splitAnimInitProps!!.iconDrawable,
        )
    }

    @Test
    fun getFirstAnimInitViews_nullTaskViewValidSplitSource_groupedTaskView() {
        // Hit groupedTaskView dismissal
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(true)

        // Remove icon view from GroupedTaskView
        whenever(mockIconView.getDrawable()).thenReturn(null)

        whenever(mockTaskContainer.task).thenReturn(mockTask)
        whenever(mockTaskContainer.iconView).thenReturn(mockIconView)
        whenever(mockTask.getKey()).thenReturn(mockTaskKey)
        whenever(mockTaskKey.getId()).thenReturn(taskId)
        whenever(mockSplitSelectStateController.initialTaskId).thenReturn(taskId)
        whenever(mockGroupedTaskView.taskContainers).thenReturn(List(1) { mockTaskContainer })
        val splitAnimInitProps: SplitAnimationController.Companion.SplitAnimInitProps? =
            splitAnimationController.getFirstAnimInitViews(
                { mockGroupedTaskView },
                { splitSelectSource },
            )

        assertEquals(
            "Did not use splitSource icon drawable",
            mockSplitSourceDrawable,
            splitAnimInitProps!!.iconDrawable,
        )
    }

    @Test
    fun playsAppropriateSplitLaunchAnimation_playsLegacyLaunchCorrectly() {
        val spySplitAnimationController = spy(splitAnimationController)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeRecentsSplitLaunchAnimatorLegacy(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )

        spySplitAnimationController.playSplitLaunchAnimation(
            mockGroupedTaskView,
            null /* launchingIconView */,
            taskId,
            taskId2,
            arrayOf() /* apps */,
            arrayOf() /* wallpapers */,
            arrayOf() /* nonApps */,
            stateManager,
            depthController,
            null /* info */,
            null /* t */,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController)
            .composeRecentsSplitLaunchAnimatorLegacy(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
    }

    @Test
    fun playsAppropriateSplitLaunchAnimation_playsRecentsLaunchCorrectly() {
        val spySplitAnimationController = spy(splitAnimationController)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeRecentsSplitLaunchAnimator(any(), any(), any(), any(), any(), any())

        spySplitAnimationController.playSplitLaunchAnimation(
            mockGroupedTaskView,
            null /* launchingIconView */,
            taskId,
            taskId2,
            null /* apps */,
            null /* wallpapers */,
            null /* nonApps */,
            stateManager,
            depthController,
            transitionInfo,
            transaction,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController)
            .composeRecentsSplitLaunchAnimator(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun playsAppropriateSplitLaunchAnimation_playsIconLaunchCorrectly() {
        val spySplitAnimationController = spy(splitAnimationController)
        whenever(mockAppPairIcon.context).thenReturn(mockContextThemeWrapper)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeIconSplitLaunchAnimator(any(), any(), any(), any(), any())
        doReturn(SplitAnimationController.SplitLaunchKind.SPLIT)
            .whenever(spySplitAnimationController)
            .getSplitLaunchKind(any(), any())

        spySplitAnimationController.playSplitLaunchAnimation(
            null /* launchingTaskView */,
            mockAppPairIcon,
            taskId,
            taskId2,
            null /* apps */,
            null /* wallpapers */,
            null /* nonApps */,
            stateManager,
            depthController,
            transitionInfo,
            transaction,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController)
            .composeIconSplitLaunchAnimator(any(), any(), any(), any(), any())
    }

    @Test
    fun playsAppropriatePartialSplitLaunchAnimation_playsIconLaunchCorrectly() {
        val spySplitAnimationController = spy(splitAnimationController)
        whenever(mockAppPairIcon.context).thenReturn(mockContextThemeWrapper)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeIconSplitLaunchAnimator(any(), any(), any(), any(), any())
        doReturn(SplitAnimationController.SplitLaunchKind.PARTIAL_SPLIT)
            .whenever(spySplitAnimationController)
            .getSplitLaunchKind(any(), any())

        spySplitAnimationController.playSplitLaunchAnimation(
            null /* launchingTaskView */,
            mockAppPairIcon,
            taskId,
            taskId2,
            null /* apps */,
            null /* wallpapers */,
            null /* nonApps */,
            stateManager,
            depthController,
            transitionInfo,
            transaction,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController)
            .composeIconSplitLaunchAnimator(any(), any(), any(), any(), any())
    }

    @Test
    fun playsAppropriateSplitLaunchAnimation_playsIconFullscreenLaunchCorrectly() {
        val spySplitAnimationController = spy(splitAnimationController)
        whenever(mockAppPairIcon.context).thenReturn(mockContextThemeWrapper)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeFullscreenIconSplitLaunchAnimator(any(), any(), any(), any(), any())
        doReturn(SplitAnimationController.SplitLaunchKind.FULLSCREEN_FIRST)
            .whenever(spySplitAnimationController)
            .getSplitLaunchKind(any(), any())

        spySplitAnimationController.playSplitLaunchAnimation(
            null /* launchingTaskView */,
            mockAppPairIcon,
            taskId,
            taskId2,
            null /* apps */,
            null /* wallpapers */,
            null /* nonApps */,
            stateManager,
            depthController,
            transitionInfo,
            transaction,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController)
            .composeFullscreenIconSplitLaunchAnimator(
                any(),
                any(),
                any(),
                any(),
                eq(SplitAnimationController.SplitLaunchKind.FULLSCREEN_FIRST),
            )
    }

    @Test
    fun playsAppropriateSplitLaunchAnimation_playsIconLaunchFromTaskbarCMultiWindow() {
        val spySplitAnimationController = spy(splitAnimationController)
        whenever(mockAppPairIcon.context).thenReturn(mockTaskbarActivityContext)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeScaleUpLaunchAnimation(any(), any(), any())
        doReturn(SplitAnimationController.SplitLaunchKind.SPLIT)
            .whenever(spySplitAnimationController)
            .getSplitLaunchKind(any(), any())
        spySplitAnimationController.playSplitLaunchAnimation(
            null /* launchingTaskView */,
            mockAppPairIcon,
            taskId,
            taskId2,
            null /* apps */,
            null /* wallpapers */,
            null /* nonApps */,
            stateManager,
            depthController,
            transitionInfo,
            transaction,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController).composeScaleUpLaunchAnimation(any(), any(), any())
    }

    @Test
    fun playsAppropriateSplitLaunchAnimation_playsIconLaunchFromTaskbarFullscreen() {
        val spySplitAnimationController = spy(splitAnimationController)
        whenever(mockAppPairIcon.context).thenReturn(mockTaskbarActivityContext)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeScaleUpLaunchAnimation(any(), any(), any())
        doReturn(SplitAnimationController.SplitLaunchKind.FULLSCREEN_FIRST)
            .whenever(spySplitAnimationController)
            .getSplitLaunchKind(any(), any())
        spySplitAnimationController.playSplitLaunchAnimation(
            null /* launchingTaskView */,
            mockAppPairIcon,
            taskId,
            taskId2,
            null /* apps */,
            null /* wallpapers */,
            null /* nonApps */,
            stateManager,
            depthController,
            transitionInfo,
            transaction,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController).composeScaleUpLaunchAnimation(any(), any(), any())
    }

    @Test
    fun playsAppropriateSplitLaunchAnimation_playsFadeInLaunchCorrectly() {
        val spySplitAnimationController = spy(splitAnimationController)
        doNothing()
            .whenever(spySplitAnimationController)
            .composeFadeInSplitLaunchAnimator(any(), any(), any(), any(), any(), any())

        spySplitAnimationController.playSplitLaunchAnimation(
            null /* launchingTaskView */,
            null /* launchingIconView */,
            taskId,
            taskId2,
            null /* apps */,
            null /* wallpapers */,
            null /* nonApps */,
            stateManager,
            depthController,
            transitionInfo,
            transaction,
            {} /* finishCallback */,
            1f, /* cornerRadius */
        )

        verify(spySplitAnimationController)
            .composeFadeInSplitLaunchAnimator(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun getSplitLaunchKind_bothAppsOpen_returnsSplit() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0)
        transitionInfo.addChange(createChange("pkg1", WINDOWING_MODE_MULTI_WINDOW))
        transitionInfo.addChange(createChange("pkg2", WINDOWING_MODE_MULTI_WINDOW))

        val result = splitAnimationController.getSplitLaunchKind(mockAppPairIcon, transitionInfo)
        assertEquals(SplitLaunchKind.SPLIT, result)
    }

    @Test
    fun getSplitLaunchKind_firstAppFullscreen_returnsFullscreenFirst() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0)
        transitionInfo.addChange(createChange("pkg1", WINDOWING_MODE_FULLSCREEN))

        val result = splitAnimationController.getSplitLaunchKind(mockAppPairIcon, transitionInfo)
        assertEquals(SplitLaunchKind.FULLSCREEN_FIRST, result)
    }

    @Test
    fun getSplitLaunchKind_secondAppFullscreen_returnsFullscreenSecond() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0)
        transitionInfo.addChange(createChange("pkg2", WINDOWING_MODE_FULLSCREEN))

        val result = splitAnimationController.getSplitLaunchKind(mockAppPairIcon, transitionInfo)
        assertEquals(SplitLaunchKind.FULLSCREEN_SECOND, result)
    }

    @Test
    fun getSplitLaunchKind_unknownAppFullscreen_returnsFullscreenPaused() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0)
        transitionInfo.addChange(createChange("pkg3", WINDOWING_MODE_FULLSCREEN))

        val result = splitAnimationController.getSplitLaunchKind(mockAppPairIcon, transitionInfo)
        assertEquals(SplitLaunchKind.FULLSCREEN_PAUSED, result)
    }

    @Test
    fun getSplitLaunchKind_firstAppOnly_returnsPartialSplit() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0)
        transitionInfo.addChange(createChange("pkg1", WINDOWING_MODE_MULTI_WINDOW))

        val result = splitAnimationController.getSplitLaunchKind(mockAppPairIcon, transitionInfo)
        assertEquals(SplitLaunchKind.PARTIAL_SPLIT, result)
    }

    @Test
    fun getSplitLaunchKind_secondAppOnly_returnsPartialSplit() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0)
        transitionInfo.addChange(createChange("pkg2", WINDOWING_MODE_MULTI_WINDOW))

        val result = splitAnimationController.getSplitLaunchKind(mockAppPairIcon, transitionInfo)
        assertEquals(SplitLaunchKind.PARTIAL_SPLIT, result)
    }

    @Test
    fun getSplitLaunchKind_mixedFullscreenAndPartial_returnsPartialSplit() {
        val intent1 = Intent().setComponent(ComponentName("pkg1", "cls1"))
        val intent2 = Intent().setComponent(ComponentName("pkg2", "cls2"))
        mockWorkspaceItemInfo1.intent = intent1
        mockWorkspaceItemInfo2.intent = intent2

        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0)
        // Task 1: App 1 (not fullscreen)
        transitionInfo.addChange(createChange("pkg1", WINDOWING_MODE_MULTI_WINDOW))
        // Task 2: Unknown app (fullscreen)
        transitionInfo.addChange(createChange("pkg3", WINDOWING_MODE_FULLSCREEN))

        val result = splitAnimationController.getSplitLaunchKind(mockAppPairIcon, transitionInfo)
        assertEquals(SplitLaunchKind.PARTIAL_SPLIT, result)
    }

    private fun createChange(packageName: String, windowingMode: Int): TransitionInfo.Change {
        val taskInfo = RunningTaskInfo()
        taskInfo.baseIntent = Intent().setComponent(ComponentName(packageName, "cls"))
        taskInfo.configuration.windowConfiguration.windowingMode = windowingMode

        val change = TransitionInfo.Change(null, mock())
        change.mode = TRANSIT_OPEN
        change.taskInfo = taskInfo
        return change
    }
}
