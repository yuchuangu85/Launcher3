/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
import android.window.IRemoteTransition
import android.window.RemoteTransition
import android.window.TransitionFilter
import android.window.TransitionInfo
import com.android.internal.jank.Cuj
import com.android.launcher3.desktop.DesktopAppLaunchTransition.AppLaunchType
import com.android.launcher3.display.DisplayController
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.quickstep.SystemUiProxy
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus

/** Manages transitions related to app launches in Desktop Mode. */
class DesktopAppLaunchTransitionManager(
    private val context: Context,
    private val systemUiProxy: SystemUiProxy,
    private val displayController: DisplayController,
) {
    private var remoteWindowLimitUnminimizeTransition: RemoteTransition? = null

    /**
     * Register a [RemoteTransition] supporting Desktop app launches, and window limit
     * minimizations.
     */
    fun registerTransitions() {
        if (!shouldRegisterTransitions()) {
            return
        }
        remoteWindowLimitUnminimizeTransition =
            RemoteTransition(
                DesktopAppLaunchTransition(
                    context,
                    displayController,
                    AppLaunchType.UNMINIMIZE,
                    Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_INTENT,
                    MAIN_EXECUTOR,
                ),
                null /* appThread */,
                "DesktopWindowLimitUnminimize",
                buildAppLaunchFilter(),
            )
        systemUiProxy.registerRemoteTransition(remoteWindowLimitUnminimizeTransition)
    }

    /**
     * Unregister the [RemoteTransition] supporting Desktop app launches and window limit
     * minimizations.
     */
    fun unregisterTransitions() {
        if (!shouldRegisterTransitions()) {
            return
        }
        systemUiProxy.unregisterRemoteTransition(remoteWindowLimitUnminimizeTransition)
        remoteWindowLimitUnminimizeTransition = null
    }

    private fun shouldRegisterTransitions(): Boolean =
        DesktopModeStatus.canEnterDesktopMode(context)

    companion object {
        /** Returns whether the given transition is a Desktop app launch. */
        @JvmStatic
        fun isDesktopAppLaunch(context: Context, info: TransitionInfo): Boolean =
            DesktopModeStatus.canEnterDesktopMode(context) &&
                Flags.desktopHomescreenIconsApplaunchTransitions() &&
                (DesktopAppLaunchTransition.getDesktopLaunchChange(info) != null)

        /** Returns an [IRemoteTransition] to animate a Desktop app launch. */
        @JvmStatic
        fun createDesktopAppLaunchRemoteTransition(
            context: Context,
            launchType: AppLaunchType,
            cujType: Int,
            onEndCallback: Runnable? = null,
        ): IRemoteTransition =
            DesktopAppLaunchTransition(
                context,
                DisplayController.INSTANCE.get(context),
                launchType,
                cujType,
                MAIN_EXECUTOR,
                onEndCallback = onEndCallback,
            )

        private fun buildAppLaunchFilter(): TransitionFilter {
            val openRequirement =
                TransitionFilter.Requirement().apply {
                    mActivityType = ACTIVITY_TYPE_STANDARD
                    mWindowingMode = WINDOWING_MODE_FREEFORM
                    mModes = DesktopAppLaunchTransition.LAUNCH_CHANGE_MODES
                    mMustBeTask = true
                }

            val requirements =
                if (Flags.crossDisplayTransitionV2()) {
                    val notCrossDisplayRequirement =
                        TransitionFilter.Requirement().apply {
                            mNot = true
                            mIsCrossDisplayMove = true
                        }
                    arrayOf(openRequirement, notCrossDisplayRequirement)
                } else {
                    arrayOf(openRequirement)
                }

            return TransitionFilter().apply {
                mTypeSet = DesktopAppLaunchTransition.LAUNCH_CHANGE_MODES
                mRequirements = requirements
            }
        }
    }
}
