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

package com.android.quickstep

import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer

/**
 * A wrapper system shortcut for Digital Wellbeing actions for a overview. this allows us to finish
 * animation and switch to screenshot before the action is executed.
 */
class WellbeingShortcut
@VisibleForTesting
constructor(
    container: RecentsViewContainer,
    taskContainer: TaskContainer,
    private val action: SystemShortcut<ActivityContext>,
) :
    SystemShortcut<RecentsViewContainer>(
        action.iconResId,
        action.labelResId,
        container,
        taskContainer.itemInfo,
        taskContainer.taskView,
    ) {

    override fun onClick(view: View) {
        val recentsView = mTarget.getOverviewPanel<RecentsView<*, *>>()
        recentsView.switchToScreenshot {
            recentsView.finishRecentsAnimation(/* toHome= */ true, /* shouldPip= */ false) {
                action.onClick(view)
            }
        }
    }

    /** Factory class for creating [WellbeingShortcut] instances. */
    class Factory(private val shortcutFactory: SystemShortcut.Factory<ActivityContext>) :
        TaskShortcutFactory {
        override fun getShortcuts(
            container: RecentsViewContainer,
            taskContainer: TaskContainer,
        ): List<WellbeingShortcut>? {
            return shortcutFactory
                .getShortcut(container, taskContainer.itemInfo, taskContainer.taskView)
                ?.let { wellbeingShortcut ->
                    listOf(WellbeingShortcut(container, taskContainer, wellbeingShortcut))
                }
        }
    }
}
