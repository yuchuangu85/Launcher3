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

import android.animation.AnimatorSet
import android.util.SparseArray
import android.view.View
import android.view.ViewRootImpl
import androidx.annotation.AnyThread
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.LauncherState
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.AsyncView
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.TaskbarAsyncAnimator
import com.android.quickstep.GestureState
import com.android.quickstep.RecentsAnimationCallbacks
import com.android.quickstep.ViewUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future
import javax.annotation.concurrent.ThreadSafe

/**
 * Expose [TaskbarUIController] APIs to launcher, gesture nav and recents to be called on taskbar's
 * per-window UI thread.
 */
@ThreadSafe
class TaskbarInteractor(private val taskbarUIController: TaskbarUIController) {

    /** SparseArray of all pinned apps on the taskbar. */
    @get:AnyThread
    val pinnedApps: SparseArray<ItemInfo>
        get() = taskbarUIController.allPinnedApps

    @get:AnyThread
    val supportsPinnedAppsOverflow
        get() = TaskbarPopupController.canPinAppsOverflow()

    @AnyThread
    fun setUserIsNotGoingHome(isNotGoingHome: Boolean) {
        getTaskbarUiThread().execute { taskbarUIController.setUserIsNotGoingHome(isNotGoingHome) }
    }

    @AnyThread
    fun hideOverlayWindow() {
        getTaskbarUiThread().execute { taskbarUIController.hideOverlayWindow() }
    }

    @AnyThread
    fun startTranslationSpring() {
        getTaskbarUiThread().execute { taskbarUIController.startTranslationSpring() }
    }

    @AnyThread
    fun onExpandPip() {
        getTaskbarUiThread().execute { taskbarUIController.onExpandPip() }
    }

    @AnyThread
    fun onLauncherVisibilityChanged(visible: Boolean) {
        getTaskbarUiThread().execute { taskbarUIController.onLauncherVisibilityChanged(visible) }
    }

    @AnyThread
    fun onStateTransitionCompletedAfterSwipeToHome(finalState: LauncherState) {
        getTaskbarUiThread().execute {
            taskbarUIController.onStateTransitionCompletedAfterSwipeToHome(finalState)
        }
    }

    @AnyThread
    fun notifyRenderer(reason: String) {
        getTaskbarUiThread().execute {
            val rootViewImpl: ViewRootImpl = taskbarUIController.rootView.viewRootImpl
            rootViewImpl.notifyRendererOfExpensiveFrame()
            rootViewImpl.notifyRendererForGpuLoadUp(reason)
        }
    }

    @AnyThread
    fun onTaskbarInAppDisplayProgressUpdate(progress: Float, flag: Int) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute {
                taskbarUIController.onTaskbarInAppDisplayProgressUpdate(progress, flag)
            }
        }
    }

    @AnyThread
    fun setShouldDelayLauncherStateAnim(shouldDelayLauncherStateAnim: Boolean) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute {
                taskbarUIController.setShouldDelayLauncherStateAnim(shouldDelayLauncherStateAnim)
            }
        }
    }

    @AnyThread
    fun showEduOnAppLaunch() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute { taskbarUIController.showEduOnAppLaunch() }
        }
    }

    @AnyThread
    fun openQuickSwitchView() {
        getTaskbarUiThread().execute { taskbarUIController.openQuickSwitchView() }
    }

    @AnyThread
    fun refreshResumedState() {
        getTaskbarUiThread().execute { taskbarUIController.refreshResumedState() }
    }

    @AnyThread
    fun setSkipLauncherVisibilityChange(skip: Boolean) {
        getTaskbarUiThread().execute { taskbarUIController.setSkipLauncherVisibilityChange(skip) }
    }

    @AnyThread
    fun onLauncherResume() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute { taskbarUIController.onLauncherResume() }
        }
    }

    @AnyThread
    fun onLauncherPause() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute { taskbarUIController.onLauncherPause() }
        }
    }

    @AnyThread
    fun onLauncherStop() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute { taskbarUIController.onLauncherStop() }
        }
    }

    @AnyThread
    fun onNavigateHome() {
        getTaskbarUiThread().execute { taskbarUIController.onNavigateHome() }
    }

    @AnyThread
    fun setIgnoreInAppFlagForSync(enabled: Boolean) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute { taskbarUIController.setIgnoreInAppFlagForSync(enabled) }
        }
    }

    /**
     * Create taskbar stash animation. If enableTaskbarUiThread() is off, this animation is played
     * on main thread so we can added it as child animation to [appLaunchAnimationSet] which is also
     * played on main thread.
     *
     * If enableTaskbarUiThread() is on, this animation will be played on taskbar's ui thread and we
     * will return a thread safe [TaskbarAsyncAnimator] to Launcher, who will later need to manually
     * start it with app launch animation.
     */
    @AnyThread
    fun createAnimToApp(appLaunchAnimationSet: AnimatorSet): TaskbarAsyncAnimator? {
        if (taskbarUIController is LauncherTaskbarUIController) {
            if (enableTaskbarUiThread()) {
                return TaskbarAsyncAnimator(getTaskbarUiThread(), MAIN_EXECUTOR) {
                    taskbarUIController.createAnimToApp()
                }
            } else {
                appLaunchAnimationSet.play(taskbarUIController.createAnimToApp())
            }
        }
        return null
    }

    @AnyThread
    fun updateTaskbarLauncherStateGoingHome() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            getTaskbarUiThread().execute {
                taskbarUIController.updateTaskbarLauncherStateGoingHome()
            }
        }
    }

    /**
     * This API both launches focused tasks and returns focused task ids, so it cannot be converted
     * to a one-way API where caller can just fire and forget.
     *
     * We decided to return a [Future] so that caller can call [Future.get] to wait for (and be
     * blocked by) taskbar thread to finish the task due to 2 reasons:
     * 1. caller is keyboard switch handling which is a low use cases
     * 2. caller can be moved off main thread in the future, so blocking a bg thread is less a
     *    performance issue.
     */
    @AnyThread
    fun launchFocusedTask(): Future<Set<Int>?> =
        CompletableFuture.supplyAsync(
            { taskbarUIController.launchFocusedTask() },
            getTaskbarUiThread(),
        )

    @AnyThread
    fun postOnRootViewDraw(callback: Runnable, callbackExecutor: Executor): Boolean {
        val rootView = taskbarUIController.rootView
        return if (rootView != null) {
            getTaskbarUiThread().execute {
                ViewUtils.postFrameDrawn(rootView) { callbackExecutor.execute(callback) }
            }
            true
        } else {
            false
        }
    }

    @AnyThread
    fun getMaxPinnableCount() = taskbarUIController.taskbarSpecsEvaluator.maxPinnableCount

    @AnyThread
    fun findMatchingAsyncView(v: View): AsyncView<View> {
        return AsyncView(getTaskbarUiThread()) { taskbarUIController.findMatchingView(v) }
    }

    @AnyThread
    fun getParallelAnimationToGestureEndTarget(
        endTarget: GestureState.GestureEndTarget,
        duration: Long,
        callbacks: RecentsAnimationCallbacks,
    ) = taskbarUIController.getParallelAnimationToGestureEndTarget(endTarget, duration, callbacks)

    @AnyThread
    fun shouldAllowTaskbarToAutoStash() = taskbarUIController.shouldAllowTaskbarToAutoStash()
}
