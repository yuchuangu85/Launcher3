/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.view.WindowInsets
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import com.android.app.animation.Interpolators
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.Utilities.shouldReduceWorkspaceBlurUsage
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.display.DisplayController
import com.android.launcher3.util.WindowBlurState
import com.android.launcher3.widgetpicker.WidgetPickerActivity
import com.android.launcher3.widgetpicker.WidgetPickerConfig
import com.android.launcher3.widgetpicker.WidgetPickerProgressHandler
import com.android.systemui.animation.back.FlingOnBackAnimationCallback
import java.util.regex.Pattern

/** An Activity that can host Launcher's widget picker for additional surfaces. */
open class QuickstepWidgetPickerActivity : WidgetPickerActivity(), WidgetPickerProgressHandler {
    private var wallpaperManager: WallpaperManager? = null
    private var isBlurEnabled = false
    private var isWallpaperZoomEnabled = false
    private var blurRadius: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        wallpaperManager = getSystemService(WallpaperManager::class.java)
        isBlurEnabled =
            !shouldReduceWorkspaceBlurUsage(this) && WindowBlurState.getInstance(this).value
        isWallpaperZoomEnabled = !shouldReduceWorkspaceBlurUsage(this)
        blurRadius = resources.getDimensionPixelSize(R.dimen.max_depth_blur_radius_enhanced)

        widgetPickerConfig = parseIntentExtras()
        super.onCreate(savedInstanceState)

        if (widgetPickerConfig.uiSurface == LOCKSCREEN_WIDGETS_HUB_UI_SURFACE) {
            window
                ?.decorView
                ?.windowInsetsController
                ?.hide(WindowInsets.Type.navigationBars() + WindowInsets.Type.statusBars())
        }
    }

    override fun onProgress(progress: Float) {
        rootView.windowToken?.let { token ->
            if (isWallpaperZoomEnabled) {
                wallpaperManager?.setWallpaperZoomOut(token, progress)
            }

            if (isBlurEnabled) {
                updateBlurBackground(progress)
            }
        }
    }

    private fun updateBlurBackground(progress: Float) {
        window?.decorView?.viewRootImpl?.let {
            if (rootView.background == null) {
                val bgDrawable: BackgroundBlurDrawable = it.createBackgroundBlurDrawable()
                rootView.background = bgDrawable
            }
            (rootView.background as BackgroundBlurDrawable).apply {
                setBlurRadius(
                    (Interpolators.clampToProgress(
                            progress,
                            /*lowerBound=*/ MIN_BACKGROUND_BLUR_FRACTION,
                            /*upperBound=*/ MAX_BACKGROUND_BLUR_FRACTION,
                        ) * blurRadius)
                        .toInt()
                )
            }
        }
    }

    override fun registerBackDispatcher() {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            BackAnimationCallback(),
        )
    }

    private fun parseIntentExtras(): WidgetPickerConfig {
        val title = intent.getStringExtra(EXTRA_PICKER_TITLE)
        val description = intent.getStringExtra(EXTRA_PICKER_DESCRIPTION)

        // Defaults to '0' to indicate that there isn't a category filter.
        // Negative value indicates it's an exclusion filter (e.g. NOT_KEYGUARD_CATEGORY.inv())
        // Positive value indicates it's inclusion filter (e.g. HOME_SCREEN or KEYGUARD)
        // Note: A filter can either be inclusion or exclusion filter; not both.
        val inclusionFilter = intent.getIntExtra(AppWidgetManager.EXTRA_CATEGORY_FILTER, 0)
        if (inclusionFilter < 0) {
            Log.w(TAG, "Invalid EXTRA_CATEGORY_FILTER: $inclusionFilter")
        }
        val exclusionFilter = intent.getIntExtra(EXTRA_CATEGORY_EXCLUSION_FILTER, 0)
        if (exclusionFilter > 0) {
            Log.w(TAG, "Invalid EXTRA_CATEGORY_EXCLUSION_FILTER: $exclusionFilter")
        }

        val uiSurfaceParam = intent.getStringExtra(EXTRA_UI_SURFACE)
        var uiSurface = WidgetPickerConfig.HOMESCREEN_WIDGETS_UI_SURFACE
        if (uiSurfaceParam != null && UI_SURFACE_PATTERN.matcher(uiSurfaceParam).matches()) {
            uiSurface = uiSurfaceParam
        }

        var filteredUsers = listOf<UserHandle>()
        val filteredUserIds = intent.getIntegerArrayListExtra(EXTRA_USER_ID_FILTER)
        if (filteredUserIds != null) {
            filteredUsers = filteredUserIds.stream().map { UserHandle.of(it) }.toList()
        }

        val deviceProfile = LauncherComponentProvider.get(this).idp.getDeviceProfile(this)

        return WidgetPickerConfig(
            uiSurface = uiSurface,
            title = title,
            description = description,
            categoryInclusionFilter = inclusionFilter,
            categoryExclusionFilter = exclusionFilter,
            filteredUsers = filteredUsers,
            enableSwipeUpToDismiss =
                deviceProfile.deviceProperties.deviceConfiguration.isGestureMode,
            isDesktopFormFactor = Utilities.shouldEnableMouseInteractionChanges(applicationContext),
            enableCursorDrivenWorkflows =
                Utilities.shouldEnableCursorDrivenWorkflows(applicationContext),
        )
    }

    override fun onResume() {
        super.onResume()
        updateServiceState(true)
    }

    override fun onPause() {
        super.onPause()
        updateServiceState(false)
    }

    private fun updateServiceState(isEnabled: Boolean) {
        if (DisplayController.getInfo(this).showDesktopTaskbarForFreeformDisplay) {
            // Avoid blocking gestures when taskbar is always shown. Gestures should still allow
            // user to return home in this case.
            return
        }
        appComponent.recentsAnimationDeviceStateRepository[displayId]?.setGestureBlockingTaskId(
            if (isEnabled) taskId else -1
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        updateServiceState(false)
    }

    /** Animation callback for different predictive back animation states for the widget picker. */
    private class BackAnimationCallback : FlingOnBackAnimationCallback() {
        var mActiveOnBackAnimationCallback: OnBackAnimationCallback? = null

        override fun onBackStartedCompat(backEvent: BackEvent) {
            mActiveOnBackAnimationCallback?.onBackCancelled()
        }

        override fun onBackInvokedCompat() {
            mActiveOnBackAnimationCallback?.onBackInvoked()
            mActiveOnBackAnimationCallback = null
        }

        override fun onBackProgressedCompat(backEvent: BackEvent) {
            mActiveOnBackAnimationCallback?.onBackProgressed(backEvent)
        }

        override fun onBackCancelledCompat() {
            mActiveOnBackAnimationCallback?.onBackCancelled()
            mActiveOnBackAnimationCallback = null
        }
    }

    companion object {
        private const val TAG = "WidgetPickerActivity"

        // Unlike the AppWidgetManager.EXTRA_CATEGORY_FILTER, this filter removes certain
        // categories.
        // Filter is ignore if it is not a negative value.
        // Example usage: WIDGET_CATEGORY_HOME_SCREEN.inv() and WIDGET_CATEGORY_NOT_KEYGUARD.inv()
        private const val EXTRA_CATEGORY_EXCLUSION_FILTER = "category_exclusion_filter"

        /**
         * Intent extra for the string representing the title displayed within the picker header.
         */
        private const val EXTRA_PICKER_TITLE = "picker_title"

        /**
         * Intent extra for the string representing the description displayed within the picker
         * header.
         */
        private const val EXTRA_PICKER_DESCRIPTION = "picker_description"

        /**
         * A unique identifier of the surface hosting the widgets;
         *
         * "widgets" is reserved for home screen surface.
         *
         * "widgets_hub" is reserved for lockscreen hub surface.
         */
        private const val EXTRA_UI_SURFACE = "ui_surface"
        private const val LOCKSCREEN_WIDGETS_HUB_UI_SURFACE = "widgets_hub"
        private val UI_SURFACE_PATTERN: Pattern = Pattern.compile("^(widgets|widgets_hub)$")

        /** User ids that should be filtered out of the widget lists created by this activity. */
        private const val EXTRA_USER_ID_FILTER = "filtered_user_ids"

        private const val MIN_BACKGROUND_BLUR_FRACTION = 0f // At closed state, no blur
        private const val MAX_BACKGROUND_BLUR_FRACTION = 0.3f // blur capped at 30%
    }
}
