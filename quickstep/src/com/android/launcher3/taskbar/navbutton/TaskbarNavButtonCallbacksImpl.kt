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

package com.android.launcher3.taskbar.navbutton

import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks
import com.android.launcher3.util.PostUnlockObject
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.OverviewCommandHelper.CommandType.HIDE_ALT_TAB
import com.android.quickstep.OverviewCommandHelper.CommandType.HOME
import com.android.quickstep.OverviewCommandHelper.CommandType.TOGGLE
import javax.inject.Inject
import javax.inject.Provider

class TaskbarNavButtonCallbacksImpl
@Inject
constructor(
    commandHelperProvider: PostUnlockObject<OverviewCommandHelper>,
    private val taskbarManagerProvider: Provider<TaskbarManager>,
) : TaskbarNavButtonCallbacks {

    private val commandHelper: OverviewCommandHelper? by commandHelperProvider

    override fun onNavigateHome(displayId: Int) {
        commandHelper?.addCommand(HOME, displayId)
        val taskbarManager = taskbarManagerProvider.get()
        val taskbarInteractor = taskbarManager?.getTaskbarInteractor(displayId)
        taskbarInteractor?.onNavigateHome()
    }

    override fun onToggleOverview(displayId: Int) {
        commandHelper?.addCommand(TOGGLE, displayId)
    }

    override fun onHideOverview(displayId: Int) {
        commandHelper?.addCommand(HIDE_ALT_TAB, displayId)
    }
}
