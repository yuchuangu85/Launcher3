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

package com.google.android.torus.utils.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager


/**
 * Listens to rotation changes of the current display (read from the context for Android 11+)
 * or the orientation of default display (For version <= Android 10).
 */
class DisplayOrientationController(
    context: Context,
    private val listener: DisplayOrientationListener? = null
) {
    /**
     * The orientation of the screen. we have two types:
     * [DisplayOrientation.NATURAL_ORIENTATION]: The default orientation of the device
     * (for a phone it is portrait).
     *
     * [DisplayOrientation.ALTERNATE_ORIENTATION]: When we rotate the device ± 90º from the
     * default orientation (for a phone it is landscape).
     */
    enum class DisplayOrientation { NATURAL_ORIENTATION, ALTERNATE_ORIENTATION }

    var rotation = Surface.ROTATION_0
        private set
    var orientation = DisplayOrientation.NATURAL_ORIENTATION
        private set
    private val displayChangeListener: DisplayManager.DisplayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                if (displayId == display.displayId) updateRotationAndOrientation()
            }

            override fun onDisplayRemoved(displayId: Int) {
                if (displayId == display.displayId) updateRotationAndOrientation()
            }

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == display.displayId) updateRotationAndOrientation()
            }

        }
    private val displayManager: DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val display: Display

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Only available for Android 11 (SDK 30); before that we can only know the default display.
        display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        }

        updateRotationAndOrientation(false)
    }

    /** Starts listening for display rotation/orientation changes. */
    fun start() {
        updateRotationAndOrientation()
        displayManager.registerDisplayListener(displayChangeListener, null)
    }

    /**
     * Requests an update on rotation and orientation. If there are changes,
     * [DisplayOrientationListener.onDisplayOrientationChanged] will be called.
     */
    fun update() = updateRotationAndOrientation()

    /** Stops listening for display rotation/orientation changes. */
    fun stop() = displayManager.unregisterDisplayListener(displayChangeListener)

    private fun updateRotationAndOrientation(sendEvent: Boolean = true) {
        val rotationTmp = display.rotation

        if (rotation != rotationTmp) {

            rotation = rotationTmp
            orientation = when (rotation) {
                Surface.ROTATION_90 -> DisplayOrientation.ALTERNATE_ORIENTATION
                Surface.ROTATION_270 -> DisplayOrientation.ALTERNATE_ORIENTATION
                else -> DisplayOrientation.NATURAL_ORIENTATION
            }

            if (sendEvent) listener?.onDisplayOrientationChanged(orientation, rotation)
        }
    }

    /** Interface to listen to display orientation changes. */
    interface DisplayOrientationListener {
        /**
         * Called when orientation has changed for the [Display] that [DisplayOrientationController]
         * is currently tracking.
         *
         * @param orientation the new [DisplayOrientationController.DisplayOrientation] orientation.
         * @param rotation the new rotation (it can be [Surface.ROTATION_0], [Surface.ROTATION_90],
         * [Surface.ROTATION_180] or [Surface.ROTATION_270]).
         */
        fun onDisplayOrientationChanged(orientation: DisplayOrientation, rotation: Int)
    }
}
