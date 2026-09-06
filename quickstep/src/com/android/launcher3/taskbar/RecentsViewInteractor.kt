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

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.view.View
import android.window.RemoteTransition
import androidx.annotation.AnyThread
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ResolvedTargetInfo
import com.android.launcher3.util.AsyncView
import com.android.launcher3.util.Executors.IMMEDIATE_EXECUTOR
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.split.SplitSelectStateController
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SplitTask
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitScreenConstants
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.Predicate
import javax.annotation.concurrent.ThreadSafe

/**
 * Wraps [RecentsView] and allow taskbar to post action on mainExecutor since recents is rendered on
 * main thread.
 */
@ThreadSafe
class RecentsViewInteractor(private val recentsView: RecentsView<*, *>) {

    // We can use IMMEDIATE_EXECUTOR if enableTaskbarUiThread() is not turned on because caller
    // is already on main thread.
    private val mainExecutor: Executor =
        if (enableTaskbarUiThread()) MAIN_EXECUTOR else IMMEDIATE_EXECUTOR

    @AnyThread
    fun hasSameRecentsView(recentsView: RecentsView<*, *>) = this.recentsView === recentsView

    @AnyThread
    fun returnToDesktop(taskToRun: Runnable, callbackExecutor: Executor) {
        CompletableFuture.supplyAsync({ recentsView.returnToDesktop() }, mainExecutor)
            .thenApplyAsync(
                { runnableList ->
                    if (runnableList != null) {
                        runnableList.add { callbackExecutor.execute(taskToRun) }
                    } else {
                        callbackExecutor.execute(taskToRun)
                    }
                },
                mainExecutor,
            )
    }

    @AnyThread
    fun addSideTaskLaunchCallback(callback: RunnableList) {
        val wrapperRunnable = Runnable {
            getTaskbarUiThread().execute(callback::executeAllAndDestroy)
        }
        mainExecutor.execute { recentsView.addSideTaskLaunchCallback(wrapperRunnable) }
    }

    @AnyThread
    fun setTaskLaunchListener(taskLaunchListener: RecentsView.TaskLaunchListener?) {
        val wrapperListener =
            if (taskLaunchListener != null)
                RecentsView.TaskLaunchListener {
                    getTaskbarUiThread().execute { taskLaunchListener.onTaskLaunched() }
                }
            else null
        mainExecutor.execute { recentsView.setTaskLaunchListener(wrapperListener) }
    }

    @AnyThread
    fun setTaskLaunchCancelledRunnable(onTaskLaunchCancelledRunnable: Runnable?) {
        val wrapperRunnable =
            if (onTaskLaunchCancelledRunnable != null)
                Runnable { getTaskbarUiThread().execute(onTaskLaunchCancelledRunnable) }
            else null
        mainExecutor.execute { recentsView.setTaskLaunchCancelledRunnable(wrapperRunnable) }
    }

    @AnyThread
    fun getTaskViewByTaskId(taskId: Int): AsyncView<TaskView> {
        // TaskView should be found on main thread which renders it for recents.
        return AsyncView(mainExecutor) { recentsView.getTaskViewByTaskId(taskId) }
    }

    @AnyThread
    fun handleAppPairLaunchInApp(launchingIconView: AppPairIcon, itemInfos: List<ItemInfo>) {
        mainExecutor.execute {
            recentsView.splitSelectController
                ?.appPairsController
                ?.handleAppPairLaunchInApp(launchingIconView, itemInfos)
        }
    }

    @AnyThread
    fun launchSplitTask(splitTask: SplitTask, remoteTransition: RemoteTransition?) {
        mainExecutor.execute {
            val splitSelectStateController: SplitSelectStateController =
                recentsView.splitSelectController ?: return@execute

            splitSelectStateController.launchExistingSplitPair(
                null, /* launchingTaskView */
                splitTask.topLeftTask.key.id,
                splitTask.bottomRightTask.key.id,
                SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                /* callback= */ { success: Boolean? -> splitSelectStateController.resetState() },
                /* freezeTaskList= */ false,
                if (splitTask.splitBounds == null) SplitScreenConstants.SNAP_TO_2_50_50
                else splitTask.splitBounds.snapPosition,
                remoteTransition,
            )
        }
    }

    @AnyThread
    fun findLastActiveTasksAndRunCallback(
        filter: Predicate<GroupTask>,
        resolvedTargetInfos: List<ResolvedTargetInfo>?,
        findExactPairMatch: Boolean,
        callback: Consumer<Array<Task>>,
    ) {
        mainExecutor.execute {
            recentsView.splitSelectController?.findLastActiveTasksAndRunCallback(
                filter,
                resolvedTargetInfos,
                findExactPairMatch,
                callback,
            )
        }
    }

    @AnyThread
    fun initiateSplitSelect(taskContainer: SplitConfigurationOptions.SplitSelectSource) {
        mainExecutor.execute { recentsView.initiateSplitSelect(taskContainer) }
    }

    @AnyThread
    fun confirmSplitSelect(
        containerTaskView: TaskView?,
        task: Task?,
        drawable: Drawable?,
        secondView: View,
        thumbnail: Bitmap?,
        intent: Intent?,
        user: UserHandle?,
        itemInfo: ItemInfo?,
    ) {
        mainExecutor.execute {
            recentsView.confirmSplitSelect(
                containerTaskView,
                task,
                drawable,
                secondView,
                thumbnail,
                intent,
                user,
                itemInfo,
            )
        }
    }

    @AnyThread
    fun switchToScreenshot(onFinishRunnable: Runnable) {
        mainExecutor.execute { recentsView.switchToScreenshot(onFinishRunnable) }
    }

    @AnyThread
    fun finishRecentsAnimation(toHome: Boolean, shouldPip: Boolean, onFinishComplete: Runnable?) {
        mainExecutor.execute {
            recentsView.finishRecentsAnimation(toHome, shouldPip, onFinishComplete)
        }
    }

    @AnyThread
    fun launchAppPair(appPairIcon: AppPairIcon, cuj: Int) {
        mainExecutor.execute {
            recentsView.splitSelectController?.appPairsController?.launchAppPair(appPairIcon, cuj)
        }
    }

    @AnyThread fun isSplitSelectionActive() = recentsView.isSplitSelectionActive
}
