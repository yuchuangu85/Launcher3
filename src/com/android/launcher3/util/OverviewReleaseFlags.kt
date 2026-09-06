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

package com.android.launcher3.util

import com.android.launcher3.Flags

object OverviewReleaseFlags {

    @JvmStatic fun enableOverviewPagination() = Flags.enableOverviewPagination()

    @JvmStatic fun enablePredictiveBackInOverview() = Flags.enablePredictiveBackInOverview()

    @JvmStatic
    fun enableSimultaneousOverviewTriggerOnExtendedDesktop() =
        Flags.enableSimultaneousOverviewTriggerOnExtendedDesktop()

    @JvmStatic
    fun enableOverviewDesktopTileWallpaperBackground() =
        Flags.enableOverviewDesktopTileWallpaperBackground()

    @JvmStatic fun enableLaterIsLockedCheck() = Flags.enableLaterIsLockedCheck()

    @JvmStatic fun enableOverviewSelectTextView() = Flags.enableOverviewSelectTextView()

    @JvmStatic fun enableTasksDragAndDropInOverview() = Flags.enableTasksDragAndDropInOverview()

    @JvmStatic fun enableRecentsWindowBlur() = Flags.enableRecentsWindowBlur()

    @JvmStatic fun enableLauncherOverviewInWindow() = Flags.enableLauncherOverviewInWindow()

    @JvmStatic fun enableFallbackOverviewInWindow() = Flags.enableFallbackOverviewInWindow()

    @JvmStatic fun enableSaveActionInOverviewShare() = Flags.enableSaveActionInOverviewShare()
}
