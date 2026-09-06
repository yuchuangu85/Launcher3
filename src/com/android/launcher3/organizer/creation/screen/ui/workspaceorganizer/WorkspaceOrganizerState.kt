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

package com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer

import android.graphics.Bitmap
import java.lang.ref.WeakReference

/** View model used by the [OrganizerActivity] and its composables. */
data class WorkspaceOrganizerState(var selectedPage: Int = 0) {}

/** Holds the information needed to render the preview of the Workspace pages. */
data class WorkspacePage(
    val bitmap: Bitmap? = null,
    val screenId: Int,
    val lastGeneratedBitmap: WeakReference<Bitmap>? = null,
)
