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

import androidx.annotation.VisibleForTesting
import com.android.launcher3.AsyncAnimatorPlaybackController
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.util.ListenableStream
import com.android.quickstep.views.RecentsViewContainer
import com.android.systemui.shared.statusbar.phone.BarTransitions
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import java.io.PrintWriter
import javax.annotation.concurrent.ThreadSafe

/** Expose threadsafe APIs of [TaskbarManagerImpl] to launcher. */
@ThreadSafe
interface TaskbarManager {

    fun createLauncherStartFromSuwAnim(duration: Int): AsyncAnimatorPlaybackController?

    fun shouldForceAllSetFallbackAnimation(): Boolean

    fun updateTaskbarsVisibility()

    fun setActivity(activity: StatefulActivity<*>)

    fun setRecentsViewContainer(recentsViewContainer: RecentsViewContainer)

    fun onSystemUiFlagsChanged(@SystemUiStateFlags systemUiStateFlags: Long, displayId: Int)

    fun onLongPressHomeEnabled(assistantLongPressEnabled: Boolean)

    fun setSetupUIVisible(isVisible: Boolean)

    fun setWallpaperVisible(displayId: Int, isVisible: Boolean)

    fun checkNavBarModes(displayId: Int)

    fun finishBarAnimations(displayId: Int)

    fun touchAutoDim(displayId: Int, reset: Boolean)

    fun transitionTo(displayId: Int, @BarTransitions.TransitionMode barMode: Int, animate: Boolean)

    fun appTransitionPending(pending: Boolean)

    fun onRotationProposal(rotation: Int, isValid: Boolean)

    fun disableNavBarElements(displayId: Int, state1: Int, state2: Int, animate: Boolean)

    fun onSystemBarAttributesChanged(displayId: Int, behavior: Int)

    fun onTransitionModeUpdated(barMode: Int, checkBarModes: Boolean)

    fun onNavButtonsDarkIntensityChanged(darkIntensity: Float)

    fun onNavigationBarLumaSamplingEnabled(displayId: Int, enable: Boolean)

    fun toggleTaskbarStash()

    fun getStashedHandleViewController(): StashedHandleViewControllerProxy?

    fun getPrimaryDisplayUiControllerStream(): ListenableStream<TaskbarUIController>

    fun dumpLogs(prefix: String, pw: PrintWriter)

    fun getTaskbarInteractor(displayId: Int): TaskbarInteractor?

    fun getTaskbarForDisplay(displayId: Int): TaskbarActivityContext?

    fun updateStashControllerLauncherStateFlag(displayId: Int, isVisible: Boolean)

    @VisibleForTesting fun <T : Any?> getFromImplSync(provider: (TaskbarManagerImpl) -> T): T

    @VisibleForTesting fun getCurrentActivityContext(): TaskbarActivityContext?

    @VisibleForTesting fun recreateTaskbars()

    @VisibleForTesting fun removeAllSystemUiBubbles()

    @VisibleForTesting fun unstashBubbleBarIfStashed()

    @VisibleForTesting fun limitMaxTaskbarIconsNum(maxIconLimitNum: Int)

    @VisibleForTesting fun getStashedTaskbarScale(): Float

    @VisibleForTesting fun removeAllBubbles()

    @VisibleForTesting fun unstashTaskbarIfStashed(): Boolean

    @VisibleForTesting fun enableBlockingTimeoutDuringTests(enableBlockingTimeout: Boolean)

    @VisibleForTesting fun isTransient(displayId: Int): Boolean

    @VisibleForTesting fun injectTestInsights()
}
