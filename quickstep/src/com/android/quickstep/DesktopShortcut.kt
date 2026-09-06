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

package com.android.quickstep

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.android.internal.jank.Cuj
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.DesktopState
import javax.inject.Inject

/** A menu item, "Desktop", that allows the user to bring the current app into Desktop Windowing. */
class DesktopShortcut
private constructor(
    container: RecentsViewContainer,
    private val taskContainer: TaskContainer,
    abstractFloatingViewHelper: AbstractFloatingViewHelper,
    private val taskUtils: TaskUtils,
) :
    SystemShortcut<RecentsViewContainer>(
        R.drawable.ic_desktop,
        R.string.recent_task_option_desktop,
        container,
        taskContainer.itemInfo,
        taskContainer.taskView,
        abstractFloatingViewHelper,
    ) {
    init {
        mAccessibilityActionId =
            if (taskContainer.stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
                R.id.action_desktop_bottom_right
            } else {
                R.id.action_desktop_top_left
            }
    }

    override fun onClick(view: View) {
        InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_DESKTOP_MODE_ENTER_FROM_OVERVIEW_MENU)
        dismissTaskMenuView()
        val recentsView = mTarget.getOverviewPanel<RecentsView<*, *>>()
        recentsView.moveTaskToDesktop(
            taskContainer,
            DesktopModeTransitionSource.OVERVIEW_TASK_MENU,
        ) {
            InteractionJankMonitorWrapper.end(Cuj.CUJ_DESKTOP_MODE_ENTER_FROM_OVERVIEW_MENU)
            mTarget.statsLogManager
                .logger()
                .withItemInfo(taskContainer.itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DESKTOP_TAP)
        }
    }

    override fun createAccessibilityAction(
        context: Context
    ): AccessibilityNodeInfo.AccessibilityAction =
        if (taskContainer.taskView.containsMultipleTasks()) {
            AccessibilityNodeInfo.AccessibilityAction(
                mAccessibilityActionId,
                context.getString(
                    R.string.recent_split_task_option_desktop,
                    taskUtils.getTitle(context, taskContainer.task),
                ),
            )
        } else {
            super.createAccessibilityAction(context)
        }

    class Factory
    @Inject
    constructor(
        private val abstractFloatingViewHelper: AbstractFloatingViewHelper,
        private val desktopState: DesktopState,
        private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
        private val taskUtils: TaskUtils,
    ) : TaskShortcutFactory {
        override fun getShortcuts(
            container: RecentsViewContainer,
            taskContainer: TaskContainer,
        ): List<DesktopShortcut>? {
            val context = container.asContext()
            val taskKey = taskContainer.task.key
            return when {
                !desktopState.isDesktopModeSupportedOnDisplay(context.display) -> null
                desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
                    taskKey.baseActivity?.packageName,
                    taskKey.baseActivity?.className,
                    taskKey.numActivities,
                    taskKey.isTopActivityNoDisplay,
                    taskKey.isActivityStackTransparent,
                    taskKey.topActivityType,
                    context.userId,
                ) -> null
                !taskContainer.task.isDockable -> null
                else ->
                    listOf(
                        DesktopShortcut(
                            container,
                            taskContainer,
                            abstractFloatingViewHelper,
                            taskUtils,
                        )
                    )
            }
        }

        override fun showForGroupedTask() = true
    }
}
