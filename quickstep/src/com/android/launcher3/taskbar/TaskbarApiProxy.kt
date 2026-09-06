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

import androidx.annotation.AnyThread
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.AutohideSuspendFlag
import com.android.launcher3.util.Executors.getTaskbarUiThread

/**
 * Expose API of [TaskbarActivityContext] to TaskbarUnstashInputConsumer and BubbleBarInputConsumer
 * to ensure touches for taskbar are handled on taskbar UI thread.
 */
class TaskbarApiProxy(private val taskbarActivityContext: TaskbarActivityContext) {

    private val transitionCallback =
        if (isTransient()) taskbarActivityContext.translationCallbacks else null

    @AnyThread fun getTaskbarUiState(): TaskbarUiState = taskbarActivityContext.taskbarUiState

    @AnyThread
    fun isTransient(): Boolean = taskbarActivityContext.taskbarFeatureEvaluator.isTransient

    @AnyThread
    fun shouldAllowTaskbarToAutoStash(): Boolean =
        taskbarActivityContext.shouldAllowTaskbarToAutoStash()

    /** Called only once during a gesture. Safe to post Runnable to taskbar's ui thread. */
    @AnyThread
    fun playTaskbarBackgroundAlphaAnimation() {
        getTaskbarUiThread().execute {
            taskbarActivityContext.playTaskbarBackgroundAlphaAnimation()
        }
    }

    /**
     * Called on ACTION_DOWN, ACTION_UP and ACTION_CANCEL. Safe to post Runnable to taskbar's ui
     * thread.
     */
    @AnyThread
    fun setAutohideSuspendFlag(@AutohideSuspendFlag flag: Int, newValue: Boolean) {
        getTaskbarUiThread().execute {
            taskbarActivityContext.setAutohideSuspendFlag(flag, newValue)
        }
    }

    @AnyThread
    fun startTaskbarUnstashHint(isHovered: Boolean) {
        getTaskbarUiThread().execute { taskbarActivityContext.startTaskbarUnstashHint(isHovered) }
    }

    /** Called once when ACTION_MOVE reach certain threshold. */
    @AnyThread
    fun onSwipeToUnstashTaskbar(delayTaskbarBackground: Boolean) {
        getTaskbarUiThread().execute {
            taskbarActivityContext.onSwipeToUnstashTaskbar(delayTaskbarBackground)
        }
    }

    /** Called on ACTION_DOWN. */
    @AnyThread
    fun onTransitionActionDown() {
        if (transitionCallback == null) return
        getTaskbarUiThread().execute { transitionCallback.onActionDown() }
    }

    /** Called on every ACTION_MOVE. */
    @AnyThread
    fun onTransitionActionMove(dy: Float) {
        if (transitionCallback == null) return
        getTaskbarUiThread().execute { transitionCallback.onActionMove(dy) }
    }

    /** Called on ACTION_UP and ACTION_CANCEL */
    @AnyThread
    fun onTransitionActionEnd() {
        if (transitionCallback == null) return
        getTaskbarUiThread().execute { transitionCallback.onActionEnd() }
    }
}
