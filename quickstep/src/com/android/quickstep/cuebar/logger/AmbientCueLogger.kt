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

package com.android.quickstep.cuebar.logger

/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Log
import com.android.internal.util.FrameworkStatsLog
import javax.inject.Inject

/**
 * Data object corresponding to a AmbientCueEventReported atom.
 *
 * Fields must be kept in sync with stats/atoms/ambientcue/amibent_cue_extension_atoms.proto.
 */
data class AmbientCueEventReported(
    var displayDurationMillis: Long = 0L,
    var fulfilledWithMaIntentMillis: Long = 0L,
    var fulfilledWithMrIntentMillis: Long = 0L,
    var loseFocusMillis: Long = 0L,
    var maCount: Int = 0,
    var mrCount: Int = 0,
    var fulfilledWithMaIntent: Boolean = false,
    var fulfilledWithMrIntent: Boolean = false,
    var clickedCloseButton: Boolean = false,
    var reachedTimeout: Boolean = false,
    var packageName: String = "",
)

/**
 * Interface for writing AmbientCueEventReported atoms to statsd log. Use AmbientCueLoggerImpl in
 * production.
 */
interface AmbientCueLogger {
    /**
     * Sets AmbientCue display events.
     *
     * @param maCount Number of ma actions generated and displayed.
     * @param mrCount Number of mr actions suggestions generated and displayed.
     */
    fun setAmbientCueDisplayStatus(maCount: Int, mrCount: Int)

    /**
     * Sets AmbientCue package name.
     *
     * @param packageName The package name of the target app.
     */
    fun setPackageName(packageName: String)

    /**
     * Sets AmbientCue lose focus time.
     *
     * @param loseFocusMillis The time in milliseconds that the cue bar lost focus.
     */
    fun setLoseFocusMillis()

    /** Sets fulfilled with ma intent events. */
    fun setFulfilledWithMaStatus()

    /** Sets fulfilled with mr intent events. */
    fun setFulfilledWithMrStatus()

    /** Sets clicked close button events. */
    fun setClickedCloseButtonStatus()

    /** Sets reached timeout events. */
    fun setReachedTimeoutStatus()

    /** Flushes a AmbientCueEventReported atom. */
    fun flushAmbientCueEventReported()

    /** Clears all saved status. */
    fun clear()
}

/** Implementation for logging UI events related to controls. */
class AmbientCueLoggerImpl @Inject constructor(private val packageManager: PackageManager) :
    AmbientCueLogger {
    private var report = AmbientCueEventReported()
    private var displayTimeMillis: Long = 0L

    /** {@see AmbientCueLogger#setAmbientCueDisplayStatus} */
    override fun setAmbientCueDisplayStatus(maCount: Int, mrCount: Int) {
        this.displayTimeMillis = System.currentTimeMillis()
        report.maCount = maCount
        report.mrCount = mrCount
    }

    /** {@see AmbientCueLogger#setPackageName} */
    override fun setPackageName(packageName: String) {
        report.packageName = packageName
    }

    /** {@see AmbientCueLogger#setLoseFocusMillis} */
    override fun setLoseFocusMillis() {
        // 3. USE standard System.currentTimeMillis()
        report.loseFocusMillis = System.currentTimeMillis()
    }

    /** {@see AmbientCueLogger#setFulfilledWithMaStatus} */
    override fun setFulfilledWithMaStatus() {
        report.fulfilledWithMaIntent = true
        report.fulfilledWithMaIntentMillis = System.currentTimeMillis() - displayTimeMillis
    }

    /** {@see AmbientCueLogger#setFulfilledWithMrStatus} */
    override fun setFulfilledWithMrStatus() {
        report.fulfilledWithMrIntent = true
        report.fulfilledWithMrIntentMillis = System.currentTimeMillis() - displayTimeMillis
    }

    override fun setClickedCloseButtonStatus() {
        report.clickedCloseButton = true
    }

    override fun setReachedTimeoutStatus() {
        report.reachedTimeout = true
    }

    /** {@see AmbientCueLogger#flushAmbientCueEventReported} */
    override fun flushAmbientCueEventReported() {
        var uid = 0
        try {
            uid = packageManager.getPackageUid(report.packageName, 0)
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "Package name not found: ${report.packageName}")
        }
        report.displayDurationMillis = System.currentTimeMillis() - displayTimeMillis
        FrameworkStatsLog.write(
            FrameworkStatsLog.AMBIENT_CUE_EVENT_REPORTED,
            report.displayDurationMillis,
            report.fulfilledWithMaIntentMillis,
            report.fulfilledWithMrIntentMillis,
            report.loseFocusMillis,
            report.maCount,
            report.mrCount,
            report.fulfilledWithMaIntent,
            report.fulfilledWithMrIntent,
            report.clickedCloseButton,
            report.reachedTimeout,
            uid,
        )
    }

    /** {@see AmbientCueLogger#clear} */
    override fun clear() {
        report = AmbientCueEventReported()
        displayTimeMillis = 0L
    }

    companion object {
        private const val TAG = "AmbientCueLogger"
    }
}
