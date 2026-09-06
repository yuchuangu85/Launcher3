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

package com.android.quickstep.recents.domain.model

import android.graphics.Rect

// TODO(b/456480920) Expand to support unified layout states for both standalone Tasks and
// GroupTasks (split-screen pairs).
sealed interface TaskLayoutState {
    /**
     * Represents the unified layout data for a desktop task, containing its calculated positions
     * for both Fullscreen and Overview states.
     *
     * @property fullscreenPosition The position of the task in the default Fullscreen (desktop)
     *   state.
     * @property overviewPosition The position of the task in the organized Overview grid.
     */
    data class DesktopTaskLayoutState(
        val fullscreenPosition: TaskPosition,
        val overviewPosition: TaskPosition,
        val oldOverviewPosition: TaskPosition? = null,
    ) : TaskLayoutState
}

/**
 * Represents the layout data for a desktop task. It can either be a task that is rendered with
 * specific bounds, or a task that is considered hidden (e.g., completely obscured or couldn't fit
 * in a grid).
 */
sealed class TaskPosition {
    /** The ID of the task. */
    abstract val taskId: TaskId

    /**
     * Data for a desktop task that is rendered with calculated bounds.
     *
     * @param bounds The calculated bounds for the task.
     */
    data class Rendered(override val taskId: Int, val bounds: Rect) : TaskPosition()

    /**
     * Data for a desktop task that is not rendered (e.g., it's obscured or couldn't fit in a
     * layout).
     */
    data class Hidden(override val taskId: Int) : TaskPosition()
}
