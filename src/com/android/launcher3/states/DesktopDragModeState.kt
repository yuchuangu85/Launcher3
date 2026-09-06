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

package com.android.launcher3.states

import com.android.launcher3.LauncherState
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.ActivityContext

/** Definition for Desktop Drag Mode state used for desktop */
class DesktopDragModeState(id: Int) :
    LauncherState(id, StatsLogManager.LAUNCHER_STATE_HOME, STATE_FLAGS) {

    companion object {

        private val STATE_FLAGS =
            (FLAG_MULTI_PAGE or
                FLAG_WORKSPACE_INACCESSIBLE or
                FLAG_DISABLE_RESTORE_EXCEPT_UI_MODE_CHANGE or
                FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED or
                FLAG_WORKSPACE_HAS_BACKGROUNDS or
                FLAG_WORKSPACE_ICONS_BEING_DRAGGED)
    }

    override fun getTransitionDuration(context: ActivityContext, isToState: Boolean) = 150
}
