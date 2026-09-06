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

package com.android.launcher3.remoteanimations

import android.app.TaskInfo
import android.graphics.Color
import android.view.IRemoteAnimationFinishedCallback
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.View
import android.window.WindowAnimationState
import androidx.core.graphics.ColorUtils
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimationRunner.AnimationResult
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.ViewEx.findInParentTree
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.ActivityTransitionAnimator.Callback
import com.android.systemui.animation.ActivityTransitionAnimator.Controller
import com.android.systemui.animation.ActivityTransitionAnimator.Controller.Companion.fromView
import com.android.systemui.animation.ActivityTransitionAnimator.LegacyAnimationDelegate
import com.android.systemui.animation.ActivityTransitionAnimator.Listener
import com.android.systemui.animation.DelegateTransitionAnimatorController
import com.android.systemui.animation.LaunchableView
import com.android.systemui.animation.RemoteAnimationDelegate

/** Remote animation runner to launch an app using System UI's animation library. */
class ContainerAnimationRunner(
    private val delegate: RemoteAnimationDelegate<IRemoteAnimationFinishedCallback>
) : RemoteAnimationFactory {

    override fun onAnimationStart(
        transit: Int,
        apps: Array<out RemoteAnimationTarget>?,
        wallpapers: Array<out RemoteAnimationTarget>?,
        nonApps: Array<out RemoteAnimationTarget>?,
        callback: AnimationResult?,
    ) {
        startAnimation(transit, apps, wallpapers, nonApps, callback)
    }

    fun startAnimation(
        transit: Int,
        apps: Array<out RemoteAnimationTarget>?,
        wallpapers: Array<out RemoteAnimationTarget>?,
        nonApps: Array<out RemoteAnimationTarget>?,
        result: IRemoteAnimationFinishedCallback?,
    ) {
        delegate.onAnimationStart(transit, apps, wallpapers, nonApps, result)
    }

    override fun onAnimationCancelled() {
        delegate.onAnimationCancelled()
    }

    companion object {

        @JvmStatic
        fun fromView(
            v: View,
            forLaunch: Boolean,
            launcher: Launcher,
            startingWindowListener: StartingWindowListener,
            onEndCallback: RunnableList,
            windowState: WindowAnimationState?,
        ): ContainerAnimationRunner? {
            // First the controller is created. This is used by the runner to animate the
            // origin/target view.

            return ContainerAnimationRunner(
                LegacyAnimationDelegate(
                    mainExecutor = Executors.MAIN_EXECUTOR,
                    controller = buildController(v, forLaunch, windowState) ?: return null,
                    callback =
                        object : Callback {
                            override fun getBackgroundColor(task: TaskInfo): Int {
                                val backgroundColor =
                                    if (startingWindowListener.backgroundColor == Color.TRANSPARENT)
                                        launcher.scrimView.backgroundColor
                                    else startingWindowListener.backgroundColor
                                return ColorUtils.setAlphaComponent(backgroundColor, 255)
                            }
                        },
                    listener =
                        object : Listener {
                            override fun onTransitionAnimationEnd(
                                transaction: SurfaceControl.Transaction
                            ) {
                                onEndCallback.executeAllAndDestroy()
                            }
                        },
                )
            )
        }

        /**
         * Constructs a [ActivityTransitionAnimator.Controller] that can be used by a
         * [ContainerAnimationRunner] to animate a view into an opening window or from a closing
         * one.
         */
        private fun buildController(
            v: View,
            isLaunching: Boolean,
            windowState: WindowAnimationState?,
        ): Controller? {
            val viewToUse =
                v.findInParentTree { it.background != null && it is LaunchableView } ?: return null

            // The CUJ is logged by the click handler, so we don't log it inside the animation
            // library. TODO: figure out return CUJ.
            val controllerDelegate = fromView(viewToUse, null /* cujType */) ?: return null

            // This wrapper allows us to override the default value, telling the controller that the
            // current window is below the animating window as well as information about the return
            // animation.
            return object : DelegateTransitionAnimatorController(controllerDelegate) {
                override val isLaunching = isLaunching

                override val isBelowAnimatingWindow = true

                override val windowAnimatorState = windowState
            }
        }
    }
}
