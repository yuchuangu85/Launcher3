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
package com.android.launcher3.keyboard

import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets.Type
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.keyboard.KeyboardStateManager.KeyboardState.HIDE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_KEYBOARD_CLOSED
import com.android.launcher3.util.Executors
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject

/** Class to maintain keyboard states. */
@ActivityContextSingleton
class KeyboardStateManager @Inject constructor(private val activityContext: ActivityContext) {

    /** Returns keyboard's height in pixels when shown. imeShownHeight>=imeHeightPx always. */
    var imeShownHeight: Int =
        activityContext.asContext().resources.getDimensionPixelSize(R.dimen.default_ime_height)
        private set

    /** Returns time when keyboard state was updated. */
    var lastUpdatedTime: Long = 0
        private set

    /** Returns current keyboard state. */
    var keyboardState: KeyboardState = HIDE
        set(value) {
            field = value
            lastUpdatedTime = SystemClock.elapsedRealtime()
        }

    /**
     * Indicate if the latest All Apps session was started from a11y action (rather than a direct
     * user interaction)
     */
    var launchedFromA11y: Boolean = false

    /**
     * Returns keyboard's current height in pixels. Updating it also updates [imeShownHeight] when
     * keyboard is shown
     */
    var imeHeight: Int = 0
        set(value) {
            field = value
            if (value > 0) imeShownHeight = value
        }

    /** Hides the keyboard if it is visible */
    fun hideKeyboard() {
        Log.d(TAG, "hideKeyboard")
        val root: View = activityContext.getDragLayer() ?: return

        // Hide keyboard with WindowInsetsController if could. In case hideSoftInputFromWindow may
        // get ignored by input connection being finished when the screen is off.
        //
        // In addition, inside IMF, the keyboards are closed asynchronously that launcher no longer
        // need to post to the message queue.
        val wic = root.windowInsetsController
        val insets = root.rootWindowInsets
        val isImeShown = insets != null && insets.isVisible(Type.ime())

        Log.d(TAG, "isImeShown: $isImeShown")

        if (wic == null) {
            Log.d(TAG, "hideKeyboard: WIC IS NULL")
        } else {
            // Only hide the keyboard if it is actually showing.
            if (isImeShown) {
                // this method cannot be called cross threads
                Log.d(TAG, "hideKeyboard: calling wic.hide() because isImeShown is true")
                wic.hide(Type.ime())
                activityContext.statsLogManager.logger().log(LAUNCHER_ALLAPPS_KEYBOARD_CLOSED)
            }

            // If the WindowInsetsController is not null, we end here regardless of whether we hid
            // the keyboard or not.
            return
        }

        val imm = root.context.getSystemService(InputMethodManager::class.java)
        val token = root.windowToken
        Log.d(TAG, "InputMethodManager: $imm token: $token")
        if (imm != null && token != null) {
            Log.d(TAG, "EXECUTING BECAUSE IMM AND TOKEN IS NOT NULL")
            Executors.UI_HELPER_EXECUTOR.execute {
                if (imm.hideSoftInputFromWindow(token, 0)) {
                    Log.d(TAG, "imm.hideSoftInputFromWindow() is true and should be closed")
                    // log keyboard close event only when keyboard is actually closed
                    Executors.MAIN_EXECUTOR.execute {
                        activityContext.statsLogManager
                            .logger()
                            .log(LAUNCHER_ALLAPPS_KEYBOARD_CLOSED)
                    }
                } else {
                    Log.d(TAG, "imm.hideSoftInputFromWindow() is false")
                }
            }
        }
    }

    /**
     * Returns if the software keyboard (including input toolbar) is hidden. Hardware keyboards do
     * not display on screen by default.
     */
    fun isSoftwareKeyboardHidden(): Boolean {
        if (
            Configuration.KEYBOARD_QWERTY ==
                activityContext.asContext().resources.configuration.keyboard
        ) {
            return true
        } else {
            val dragLayer: View = activityContext.getDragLayer()
            val insets = dragLayer.rootWindowInsets ?: return false
            val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, dragLayer)
            return !insetsCompat.isVisible(WindowInsetsCompat.Type.ime())
        }
    }

    enum class KeyboardState {
        SHOW,
        HIDE,
    }

    companion object {
        private const val TAG = "KeyboardStateManager"
    }
}
