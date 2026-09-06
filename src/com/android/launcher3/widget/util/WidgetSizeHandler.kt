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

package com.android.launcher3.widget.util

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.SizeF
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.graphics.theme.ThemePreference
import com.android.launcher3.util.Executors
import java.util.concurrent.Executor
import javax.inject.Inject

/** Helper class for handling widget updates */
open class WidgetSizeHandler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val idp: InvariantDeviceProfile,
    private val themePreference: ThemePreference,
) {

    /**
     * Updates the widget size range if it is not currently the same. This makes two binder calls,
     * one for getting the existing options, [AppWidgetManager.getAppWidgetOptions] and if it
     * doesn't match the expected value, another call to update it,
     * [AppWidgetManager.updateAppWidgetOptions].
     *
     * Note that updating the options is a costly call as it wakes up the provider process and
     * causes a full widget update, hence two binder calls are preferable over unnecessarily
     * updating the widget options.
     */
    @JvmOverloads
    open fun updateSizeRangesAsync(
        widgetId: Int,
        spanX: Int,
        spanY: Int,
        executor: Executor = Executors.UI_HELPER_EXECUTOR,
    ) {
        updateSizeRangesAsyncInternal(widgetId, executor) { getWidgetSizeOptions(spanX, spanY) }
    }

    @JvmOverloads
    open fun updateHotseatQsbSizeRangesAsync(
        widgetId: Int,
        executor: Executor = Executors.UI_HELPER_EXECUTOR,
    ) {
        updateSizeRangesAsyncInternal(widgetId, executor) {
            getHotseatQsbSizeOptions().apply {
                putBoolean(
                    MONO_THEME_ENABLED,
                    ThemePreference.MONO_THEME_VALUE == themePreference.value,
                )
                // Used to disable the preview when qsb is masked in extreme battery saver mode.
                putBoolean(USE_DISABLED_PREVIEW_WHEN_MASKED, true)
            }
        }
    }

    private inline fun updateSizeRangesAsyncInternal(
        widgetId: Int,
        executor: Executor = Executors.UI_HELPER_EXECUTOR,
        crossinline widgetSizeOptionsProvider: () -> Bundle,
    ) {
        if (widgetId <= 0) return
        executor.execute {
            val widgetManager = AppWidgetManager.getInstance(context)
            if (widgetManager.getAppWidgetInfo(widgetId) == null) {
                return@execute
            }
            val sizeOptions = widgetSizeOptionsProvider.invoke()
            val widgetOptions = widgetManager.getAppWidgetOptions(widgetId)
            if (
                (sizeOptions.getWidgetSizeList() != widgetOptions.getWidgetSizeList()) ||
                    (sizeOptions.getBoolean(MONO_THEME_ENABLED) !=
                        widgetOptions.getBoolean(MONO_THEME_ENABLED)) ||
                    (sizeOptions.getBoolean(USE_DISABLED_PREVIEW_WHEN_MASKED) !=
                        widgetOptions.getBoolean(USE_DISABLED_PREVIEW_WHEN_MASKED))
            )
                widgetManager.updateAppWidgetOptions(widgetId, sizeOptions)
        }
    }

    private inline fun getWidgetSizeOptionsInternal(
        widgetSizeFromProfile: (DeviceProfile) -> SizeF
    ): Bundle {
        val paddedSizes =
            idp.supportedProfiles.mapTo(ArrayList()) { widgetSizeFromProfile.invoke(it) }
        val rect = getMinMaxSizes(paddedSizes)
        val options = Bundle()
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, rect.left)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, rect.top)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, rect.right)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, rect.bottom)
        options.putParcelableArrayList(OPTION_APPWIDGET_SIZES, paddedSizes)
        return options
    }

    /** Returns the bundle to be used as the default options for a widget with provided size. */
    fun getWidgetSizeOptions(spanX: Int, spanY: Int): Bundle {
        return getWidgetSizeOptionsInternal { profile ->
            val density = context.resources.displayMetrics.density
            val widgetSizePx = WidgetSizes.getWidgetSizePx(profile, spanX, spanY)
            SizeF(widgetSizePx.width / density, widgetSizePx.height / density)
        }
    }

    /** Returns the bundle to be used as the default options for a QSB widget in hotseat. */
    private fun getHotseatQsbSizeOptions(): Bundle {
        return getWidgetSizeOptionsInternal { profile ->
            val density = context.resources.displayMetrics.density
            val widgetSizePx = WidgetSizes.getWidgetSizePx(profile, idp.numColumns, 1)
            SizeF(widgetSizePx.width / density, profile.getHotseatProfile().qsbHeight / density)
        }
    }

    companion object {
        const val MONO_THEME_ENABLED = "monoThemeEnabled"
        const val USE_DISABLED_PREVIEW_WHEN_MASKED = "useDisabledPreviewWhenMasked"

        fun Bundle.getWidgetSizeList() = getParcelableArrayList<SizeF>(OPTION_APPWIDGET_SIZES)

        /**
         * Returns the min and max widths and heights given a list of sizes, in dp.
         *
         * @param sizes List of sizes to get the min/max from.
         * @return A rectangle with the left (resp. top) is used for the min width (resp. height)
         *   and the right (resp. bottom) for the max. The returned rectangle is set with 0s if the
         *   list is empty.
         */
        private fun getMinMaxSizes(sizes: List<SizeF>): Rect {
            if (sizes.isEmpty()) return Rect()

            val first = sizes[0]
            val result =
                Rect(
                    first.width.toInt(),
                    first.height.toInt(),
                    first.width.toInt(),
                    first.height.toInt(),
                )
            for (i in 1..<sizes.size) {
                result.union(sizes[i].width.toInt(), sizes[i].height.toInt())
            }
            return result
        }

        /**
         * Updates a widget with size, [spanX], [spanY]
         *
         * On Android S+, it also updates the given widget with a list of sizes derived from
         * [spanX], [spanY] in all supported device profiles.
         */
        @JvmStatic
        fun AppWidgetHostView.updateSizeRanges(spanX: Int, spanY: Int) =
            context.appComponent.widgetSizeHandler.updateSizeRangesAsync(appWidgetId, spanX, spanY)
    }
}
