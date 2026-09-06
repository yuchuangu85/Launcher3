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

package com.android.launcher3.taskbar.edu

/** Represents the configuration for a tooltips educational page. */
data class TooltipsEduPage(

    /** The title of the page. */
    val title: String,

    /** Whether the page can be skipped. */
    val canBeSkipped: Boolean,

    /** The action button text, or null if no button should be shown. */
    val actionButton: String? = null,

    /** The list of tooltips for the educational page. */
    val tooltips: List<TooltipInfo>,

    /** The location where the page should be shown. */
    val location: DisplayLocation,
) {

    /** The location where the page should be displayed. Interpreted by the presenter. */
    enum class DisplayLocation {

        /** The taskbar handle. */
        TASKBAR_HANDLE,

        /** The center of the taskbar. */
        TASKBAR_CENTER,

        /** The search divider. */
        SEARCH_DIVIDER,

        /** The search icon. */
        SEARCH_ICON,
    }
}
