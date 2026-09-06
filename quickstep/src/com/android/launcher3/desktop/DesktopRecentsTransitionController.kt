/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.desktop

import android.app.IApplicationThread
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransition
import android.window.RemoteTransitionStub
import android.window.TransitionInfo
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.views.DesktopTaskView
import com.android.quickstep.views.TaskContainer
import com.android.quickstep.views.TaskView
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import java.util.function.Consumer

/** Manage recents related operations with desktop tasks */
class DesktopRecentsTransitionController(
    private val stateManager: StateManager<*, *>,
    private val systemUiProxy: SystemUiProxy,
    private val appThread: IApplicationThread,
    private val depthController: DepthController<*, *>?,
) {
    private var desktopLaunchRunner: RemoteDesktopLaunchTransitionRunner? = null

    fun isDesktopLaunchOngoing() = desktopLaunchRunner != null

    /**
     * Launch desktop tasks from recents view and activate the new freeform task with id
     * [taskIdToReorderToFront] if it's provided and already on the given desk.
     */
    fun launchDesktopFromRecents(
        desktopTaskView: DesktopTaskView,
        animated: Boolean,
        taskIdToReorderToFront: Int? = null,
        callback: Consumer<Boolean>? = null,
    ) {
        if (desktopLaunchRunner != null) {
            Log.d(TAG, "launchDesktopFromRecents - runner already exists: $desktopLaunchRunner")
            callback?.accept(false)
            return
        }
        val animRunner =
            RemoteDesktopLaunchTransitionRunner(
                    desktopTaskView,
                    animated,
                    stateManager,
                    depthController,
                ) { result ->
                    this.desktopLaunchRunner = null
                    callback?.accept(result)
                }
                .also { this.desktopLaunchRunner = it }
        val transition = RemoteTransition(animRunner, appThread, "RecentsToDesktop")
        systemUiProxy.activateDesk(
            desktopTaskView.deskId,
            transition,
            taskIdToReorderToFront,
            DesktopModeTransitionSource.RECENTS,
        )
    }

    /** Launch desktop tasks from recents view */
    fun moveToDesktop(
        taskContainer: TaskContainer,
        transitionSource: DesktopModeTransitionSource,
        successCallback: Runnable,
    ) {
        systemUiProxy.moveToDesktop(
            taskContainer.task.key.id,
            transitionSource,
            /* transition = */ null,
            successCallback,
        )
    }

    /** Move task to external display from recents view */
    fun moveToExternalDisplay(taskId: Int, transitionSource: DesktopModeTransitionSource) {
        systemUiProxy.moveToExternalDisplay(taskId, transitionSource)
    }

    private class RemoteDesktopLaunchTransitionRunner(
        private val taskView: TaskView,
        private val animated: Boolean,
        private val stateManager: StateManager<*, *>,
        private val depthController: DepthController<*, *>?,
        private val successCallback: Consumer<Boolean>?,
    ) : RemoteTransitionStub() {

        override fun onTransitionConsumed(transition: IBinder?, aborted: Boolean) {
            Log.d(TAG, "onTransitionConsumed - aborted: $aborted - $this")
            if (aborted) {
                // This transition can be consumed in the empty desk case when there are no windows
                // to animate, which means the launcher won't animate to a NORMAL state. However in
                // this case we still want to animate launcher back from OVERVIEW to NORMAL state.
                MAIN_EXECUTOR.execute {
                    stateManager.moveToRestState()
                    successCallback?.accept(true)
                }
            } else {
                successCallback?.accept(true)
            }
        }

        override fun startAnimation(
            token: IBinder,
            info: TransitionInfo,
            t: SurfaceControl.Transaction,
            finishCallback: IRemoteTransitionFinishedCallback,
        ) {
            Log.d(TAG, "startAnimation - $this")
            val errorHandlingFinishCallback = Runnable {
                try {
                    finishCallback.onTransitionFinished(null /* wct */, null /* sct */)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to call finish callback for desktop recents animation", e)
                }
            }

            if (Flags.enableDesktopWindowingPersistence()) {
                handleAnimationAfterReboot(info)
            }
            MAIN_EXECUTOR.execute {
                val animator =
                    TaskViewUtils.composeRecentsDesktopLaunchAnimator(
                        taskView,
                        stateManager,
                        depthController,
                        info,
                        t,
                    ) {
                        Log.d(TAG, "finishedAnimation - $this")
                        errorHandlingFinishCallback.run()
                        successCallback?.accept(true)
                    }
                if (!animated) {
                    animator.setDuration(0)
                }
                animator.start()
            }
        }

        /**
         * Upon reboot the start bounds of a task is set to fullscreen with the recents transition.
         * Check this case and set the start bounds to the end bounds so that the window doesn't
         * jump from start bounds to end bounds during the animation. Tasks in desktop cannot
         * normally have top bound as 0 due to status bar so this is a good indicator to identify
         * reboot case.
         */
        private fun handleAnimationAfterReboot(info: TransitionInfo) {
            info.changes.forEach { change ->
                if (
                    change.mode == TRANSIT_TO_FRONT &&
                        change.taskInfo?.isFreeform == true &&
                        change.startAbsBounds.top == 0 &&
                        change.startAbsBounds.left == 0
                ) {
                    change.setStartAbsBounds(change.endAbsBounds)
                }
            }
        }
    }

    companion object {
        const val TAG = "DesktopRecentsTransitionController"
    }
}
