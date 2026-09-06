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

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.ArrayMap
import android.util.ArraySet
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.DisplayCutout
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.DeviceType
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logging.FileLog
import com.android.launcher3.util.FlagDebugUtils.appendFlag
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy
import java.io.PrintWriter
import java.util.Collections
import java.util.StringJoiner
import kotlin.math.min

/** Cached information about a display used across launcher */
class LauncherDisplayInfo
@JvmOverloads
constructor(
    // Display context
    @JvmField val context: Context,
    wmProxy: WindowManagerProxy,
    isDesktopFormFactor: Boolean =
        Flags.enableScalabilityForDesktopExperience() &&
            context.resources.getBoolean(R.bool.desktop_form_factor),
    perDisplayBoundsCache: Map<CachedDisplayInfo, List<WindowBounds>> = ArrayMap(),
    defaultDensityDpi: Int = DisplayMetrics.DENSITY_DEVICE_STABLE,
    displayInfo: CachedDisplayInfo = wmProxy.getDisplayInfo(context),
    config: Configuration = context.resources.configuration,
) {
    // Cached property
    @JvmField val normalizedDisplayInfo: CachedDisplayInfo = displayInfo.normalize(wmProxy)
    @JvmField val rotation: Int = displayInfo.rotation
    @JvmField val currentSize: Point = displayInfo.size
    @JvmField val cutout: Rect = WindowManagerProxy.getSafeInsets(displayInfo.cutout)
    @JvmField val displayCutout: DisplayCutout = displayInfo.cutout

    // Configuration property
    @JvmField val fontScale: Float = config.fontScale
    val densityDpi: Int = config.densityDpi
    val stableDensityScaleFactor: Float =
        defaultDensityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT

    /** Getter for [.navigationMode] to allow mocking. */
    val navigationMode: NavigationMode = wmProxy.getNavigationMode(context)
    @JvmField
    val screenSizeDp: PortraitSize = PortraitSize.from(config.screenHeightDp, config.screenWidthDp)

    // WindowBounds
    @JvmField val realBounds: WindowBounds = wmProxy.getRealBounds(context, displayInfo)
    @JvmField val supportedBounds: MutableSet<WindowBounds> = ArraySet()
    val perDisplayBounds =
        ArrayMap<CachedDisplayInfo, List<WindowBounds>>().apply { putAll(perDisplayBoundsCache) }

    /** Returns whether the display is in desktop-first mode. */
    @JvmField val isInDesktopFirstMode: Boolean = wmProxy.isDisplayDesktopFirst(context)

    /** Returns whether the taskbar is forced to be pinned when home is visible. */
    private val mIsDesktopFormFactor: Boolean = isDesktopFormFactor

    /**
     * Returns whether the taskbar should be pinned, and showing desktop tasks, because the display
     * is a "freeform" display.
     */
    val showDesktopTaskbarForFreeformDisplay = wmProxy.showDesktopTaskbarForFreeformDisplay(context)

    @JvmField val isNightModeActive: Boolean = config.isNightModeActive

    // Used for testing
    init {

        var cachedValue = currentBounds
        if (cachedValue == null) {
            // Unexpected normalizedDisplayInfo is found, recreate the cache
            FileLog.e(
                TAG,
                "Unexpected normalizedDisplayInfo found, invalidating cache: $normalizedDisplayInfo",
            )
            FileLog.e(TAG, "(Invalid Cache) perDisplayBounds : $perDisplayBounds")

            perDisplayBounds.clear()
            perDisplayBounds.putAll(wmProxy.estimateInternalDisplayBounds(context))
            cachedValue = currentBounds
            if (cachedValue == null) {
                FileLog.e(
                    TAG,
                    "normalizedDisplayInfo not found in estimation: $normalizedDisplayInfo",
                )
                supportedBounds.add(realBounds)
            }
        }

        if (cachedValue != null) {
            // Verify that the real bounds are a match
            val expectedBounds = cachedValue[displayInfo.rotation]
            if (realBounds != expectedBounds) {
                val clone: MutableList<WindowBounds> = ArrayList(cachedValue)
                clone[displayInfo.rotation] = realBounds
                perDisplayBounds[normalizedDisplayInfo] = clone
            }
        }

        perDisplayBounds.values.forEach { supportedBounds.addAll(it) }
        if (DEBUG) {
            Log.d(TAG, "displayInfo: $displayInfo")
            Log.d(TAG, "realBounds: $realBounds")
            Log.d(TAG, "normalizedDisplayInfo: $normalizedDisplayInfo")
            Log.d(TAG, "perDisplayBounds: $perDisplayBounds")
        }
    }

    /**
     * Returns `true` if the bounds represent a tablet.
     *
     * External displays are always considered tablet.
     */
    fun isLargeScreen(bounds: WindowBounds): Boolean =
        mIsDesktopFormFactor ||
            smallestSizeDp(bounds) >= WindowManagerProxy.MIN_TABLET_WIDTH ||
            context.display.displayId != Display.DEFAULT_DISPLAY

    /** Returns smallest size in dp for given bounds. */
    fun smallestSizeDp(bounds: WindowBounds): Float =
        Utilities.dpiFromPx(
            min(bounds.bounds.width(), bounds.bounds.height()).toFloat(),
            densityDpi,
        )

    /** Returns all displays for the device */
    val allDisplays: Set<CachedDisplayInfo>
        get() = Collections.unmodifiableSet(perDisplayBounds.keys)

    /** Returns all [WindowBounds]s for the current display. */
    val currentBounds: List<WindowBounds>?
        get() = perDisplayBounds[normalizedDisplayInfo]

    @get:DeviceType
    val deviceType: Int
        get() {
            if (mIsDesktopFormFactor) {
                return InvariantDeviceProfile.TYPE_DESKTOP
            }

            var hasPhone = false
            var hasTablet = false

            supportedBounds.forEach { if (isLargeScreen(it)) hasTablet = true else hasPhone = true }
            return when {
                hasPhone && hasTablet -> InvariantDeviceProfile.TYPE_MULTI_DISPLAY
                hasTablet -> InvariantDeviceProfile.TYPE_TABLET
                else -> InvariantDeviceProfile.TYPE_PHONE
            }
        }

    fun diff(other: LauncherDisplayInfo): Int =
        DiffHelper(0)
            .comp(CHANGE_ACTIVE_SCREEN, this, other) { it.normalizedDisplayInfo }
            .comp(CHANGE_ROTATION, this, other) { it.rotation }
            .comp(CHANGE_DENSITY, this, other) { it.densityDpi }
            .comp(CHANGE_DENSITY, this, other) { it.fontScale }
            .comp(CHANGE_NAVIGATION_MODE, this, other) { it.navigationMode }
            .comp(CHANGE_SUPPORTED_BOUNDS, this, other) { it.perDisplayBounds }
            .comp(CHANGE_SUPPORTED_BOUNDS, this, other) { it.supportedBounds }
            .comp(CHANGE_SHOW_DESKTOP_FIRST_TASKBAR, this, other) {
                it.showDesktopTaskbarForFreeformDisplay
            }
            .comp(CHANGE_NIGHT_MODE, this, other) { it.isNightModeActive }
            .apply {
                if (DEBUG) Log.d(TAG, "handleInfoChange - change: ${getChangeFlagsString(change)}")
            }
            .change

    fun dump(pw: PrintWriter) {
        pw.println("  normalizedDisplayInfo=$normalizedDisplayInfo")
        pw.println("  rotation=$rotation")
        pw.println("  fontScale=$fontScale")
        pw.println("  densityDpi=$densityDpi")
        pw.println("  navigationMode=" + navigationMode.name)
        pw.println("  isInDesktopFirstMode=$isInDesktopFirstMode")
        pw.println("  showDesktopFirstTaskbar=$showDesktopTaskbarForFreeformDisplay")
        pw.println("  currentSize=$currentSize")
        perDisplayBounds.forEach { (key, value) -> pw.println("  perDisplayBounds - $key: $value") }
    }

    @JvmInline
    private value class DiffHelper(val change: Int) {

        inline fun <T : Any, V : Any?> comp(mask: Int, v1: T, v2: T, prop: (T) -> V) =
            if (prop(v1) != prop(v2)) DiffHelper(change or mask) else this
    }

    companion object {
        private const val TAG = "LauncherDisplayInfo"
        private const val DEBUG = false

        const val CHANGE_ACTIVE_SCREEN: Int = 1 shl 0
        const val CHANGE_ROTATION: Int = 1 shl 1
        const val CHANGE_DENSITY: Int = 1 shl 2
        const val CHANGE_SUPPORTED_BOUNDS: Int = 1 shl 3
        const val CHANGE_NAVIGATION_MODE: Int = 1 shl 4
        const val CHANGE_SHOW_DESKTOP_FIRST_TASKBAR: Int = 1 shl 5
        const val CHANGE_NIGHT_MODE: Int = 1 shl 6

        const val CHANGE_ALL: Int =
            (CHANGE_ACTIVE_SCREEN or
                CHANGE_ROTATION or
                CHANGE_DENSITY or
                CHANGE_SUPPORTED_BOUNDS or
                CHANGE_NAVIGATION_MODE or
                CHANGE_SHOW_DESKTOP_FIRST_TASKBAR or
                CHANGE_NIGHT_MODE)

        /**
         * Returns the given binary flags as a human-readable string.
         *
         * @see .CHANGE_ALL
         */
        @JvmStatic
        fun getChangeFlagsString(change: Int): String =
            StringJoiner("|")
                .apply {
                    appendFlag(change, CHANGE_ACTIVE_SCREEN, "CHANGE_ACTIVE_SCREEN")
                    appendFlag(change, CHANGE_ROTATION, "CHANGE_ROTATION")
                    appendFlag(change, CHANGE_DENSITY, "CHANGE_DENSITY")
                    appendFlag(change, CHANGE_SUPPORTED_BOUNDS, "CHANGE_SUPPORTED_BOUNDS")
                    appendFlag(change, CHANGE_NAVIGATION_MODE, "CHANGE_NAVIGATION_MODE")
                    appendFlag(
                        change,
                        CHANGE_SHOW_DESKTOP_FIRST_TASKBAR,
                        "CHANGE_SHOW_DESKTOP_FIRST_TASKBAR",
                    )
                    appendFlag(change, CHANGE_NIGHT_MODE, "CHANGE_NIGHT_MODE")
                }
                .toString()
    }
}
