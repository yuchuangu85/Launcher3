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

package com.google.android.torus.utils.system

import android.content.Context
import android.os.PowerManager
import android.os.PowerManager.THERMAL_STATUS_SEVERE
import android.util.Log

/** Interface definition for a callback to be invoked when the device's thermal status changes. */
interface ThermalStatusCallback {
    /**
     * Called when the device's thermal status has changed.
     *
     * @param newStatus The new thermal status. This will be one of the
     *   `PowerManager.THERMAL_STATUS_*` constants.
     */
    fun onThermalStatusChanged(newStatus: Int)
}

/**
 * Monitors the device's thermal status and provides callbacks when it changes. This class requires
 * API level 29 (Android Q) or higher to function.
 *
 * @param callback The callback to be invoked when the thermal status changes.
 */
class ThermalStatusMonitor(
    private val context: Context,
    private val callback: ThermalStatusCallback,
) : PowerManager.OnThermalStatusChangedListener {

    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager?
    }

    private var currentThermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    private var isListenerRegistered: Boolean = false

    /** If the listener is already registered, this method does nothing. */
    fun startMonitoring() {
        if (powerManager == null) {
            Log.w(TAG, "Failed to start monitoring. Power manager is null")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (!isListenerRegistered) {
                powerManager!!.addThermalStatusListener(this)
                Log.d(TAG, "Thermal status monitoring started.")
                currentThermalStatus = powerManager!!.currentThermalStatus
                callback.onThermalStatusChanged(currentThermalStatus)
                isListenerRegistered = true
            } else {
                Log.d(TAG, "Thermal status listener already registered.")
            }
        } else {
            Log.w(TAG, "Thermal API (API 29+) not available on this device. Monitoring skipped.")
        }
    }

    /** If the listener is not currently registered, this method does nothing. */
    fun stopMonitoring() {
        if (powerManager == null) {
            Log.w(TAG, "Failed to stop monitoring. Power manager is null")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (isListenerRegistered) {
                powerManager!!.removeThermalStatusListener(this)
                Log.d(TAG, "Thermal status monitoring stopped.")
                isListenerRegistered = false
            } else {
                Log.d(TAG, "This thermal status listener hasn't been registered.")
            }
        }
    }

    /**
     * Checks if the current thermal status is [PowerManager.THERMAL_STATUS_SEVERE] or higher.
     *
     * @return `true` if the current thermal status is severe or critical, `false` otherwise.
     */
    fun isSevereOrHigher(): Boolean {
        return currentThermalStatus >= THERMAL_STATUS_SEVERE
    }

    /**
     * Callback method invoked by the system when the thermal status changes. This method updates
     * the [currentThermalStatus] and notifies the registered [ThermalStatusCallback].
     */
    override fun onThermalStatusChanged(status: Int) {
        Log.d(TAG, "Thermal status changed from $currentThermalStatus to $status")
        currentThermalStatus = status
        callback.onThermalStatusChanged(status)
    }

    companion object {
        private const val TAG = "ThermalStatusMonitor"
    }
}
