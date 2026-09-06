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
package com.android.launcher3.display

import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.DisplayMetrics
import com.android.launcher3.Flags
import com.android.launcher3.display.PortraitSize.Companion.from
import com.android.launcher3.util.MutableDiffAwareRef
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy

/** Class to manage LauncherDisplayInfo for a single display */
class DisplayInfoContainer(
    val displayId: Int,
    val windowContext: Context,
    private val wmProxy: WindowManagerProxy,
) : ComponentCallbacks {

    private val isDesktopFormFactor =
        Flags.enableScalabilityForDesktopExperience() &&
            windowContext.packageManager.hasSystemFeature(PackageManager.FEATURE_PC)

    private val _info: MutableDiffAwareRef<LauncherDisplayInfo, Int> =
        MutableDiffAwareRef(getNewInfo())
    val info = _info.asListenable()

    init {
        windowContext.registerComponentCallbacks(this)
    }

    override fun onConfigurationChanged(config: Configuration) {
        val info: LauncherDisplayInfo = _info.value
        if (
            config.densityDpi != info.densityDpi ||
                config.fontScale != info.fontScale ||
                (info.screenSizeDp != from(config.screenHeightDp, config.screenWidthDp)) ||
                windowContext.display.rotation != info.rotation ||
                (wmProxy.showDesktopTaskbarForFreeformDisplay(windowContext) !=
                    info.showDesktopTaskbarForFreeformDisplay) ||
                config.isNightModeActive != info.isNightModeActive
        ) {
            notifyConfigChange()
        }
    }

    fun notifyConfigChange() {
        val oldInfo = _info.value
        var newInfo = getNewInfo(oldInfo.perDisplayBounds)

        if (
            newInfo.densityDpi != oldInfo.densityDpi ||
                newInfo.fontScale != oldInfo.fontScale ||
                newInfo.navigationMode != oldInfo.navigationMode
        ) {
            // Bounds cache may not be valid anymore, recreate without cache
            newInfo = getNewInfo()
        }
        val flags = oldInfo.diff(newInfo)
        if (flags != 0) _info.dispatchValue(newInfo, flags)
    }

    private fun getNewInfo(
        perDisplayBounds: Map<CachedDisplayInfo, List<WindowBounds>> =
            wmProxy.estimateInternalDisplayBounds(windowContext)
    ) =
        LauncherDisplayInfo(
            windowContext,
            wmProxy,
            isDesktopFormFactor,
            perDisplayBounds,
            DisplayMetrics.DENSITY_DEVICE_STABLE,
        )

    override fun onLowMemory() {}

    fun cleanup() {
        windowContext.unregisterComponentCallbacks(this)
    }
}
