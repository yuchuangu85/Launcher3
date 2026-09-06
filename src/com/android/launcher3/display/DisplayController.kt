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
import android.view.Display
import androidx.annotation.AnyThread
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.ListenableDiffAwareRef
import com.android.launcher3.util.NavigationMode
import java.io.PrintWriter

/** Utility class to cache properties of default display to avoid a system RPC on every call. */
interface DisplayController {

    @get:AnyThread
    val listenable: ListenableDiffAwareRef<LauncherDisplayInfo, Int>?
        get() = getListenable(Display.DEFAULT_DISPLAY)

    @get:AnyThread
    val info: LauncherDisplayInfo
        get() = requireNotNull(getInfoForDisplay(Display.DEFAULT_DISPLAY))

    @AnyThread
    fun getListenable(displayId: Int): ListenableDiffAwareRef<LauncherDisplayInfo, Int>? =
        getPerDisplayInfoById(displayId)?.info

    @AnyThread
    fun getInfoForDisplay(displayId: Int): LauncherDisplayInfo? =
        getPerDisplayInfoById(displayId)?.info?.value

    @AnyThread
    fun notifyConfigChange(displayId: Int) = getPerDisplayInfoById(displayId)?.notifyConfigChange()

    @AnyThread fun getPerDisplayInfoById(displayId: Int): DisplayInfoContainer?

    /** Dumps the current state information */
    fun dump(pw: PrintWriter)

    companion object {
        @JvmField val INSTANCE = DaggerSingletonObject { it.displayController }

        /** Returns the current navigation mode */
        @JvmStatic
        fun getNavigationMode(context: Context): NavigationMode = getInfo(context).navigationMode

        /**
         * Gets the info for whatever display the context is associated with or the default display
         * if it is not associated with a display.
         */
        @JvmStatic
        fun getInfo(context: Context): LauncherDisplayInfo {
            val appComponent = context.appComponent
            val controller = appComponent.displayController
            val displayId = appComponent.wmProxy.getDisplay(context)?.displayId
            return displayId?.let { controller.getInfoForDisplay(it) } ?: controller.info
        }
    }
}
