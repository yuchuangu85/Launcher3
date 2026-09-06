/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.logging

import android.os.SystemClock
import com.android.launcher3.Alarm
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_RECREATE_TASKBAR
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.Preconditions

/**
 * Tracks the latency recreating taskbar. If there are back to back latency window that is within
 * 5ms, [logStart1, logEnd1] - [<= 5ms gap] - [logStart2, logEnd2], this logger will merge the
 * window into a single one as [logStart1, logEnd2].
 */
class RecreateTaskbarLatencyLogger {

    private var startTime: Long = 0
    private val alarm = Alarm(getTaskbarUiThread().looper)

    /** Starts tracking the latency. */
    fun logStart() {
        Preconditions.assertTaskbarUiThread()

        if (alarm.alarmPending()) {
            // Cancel the pending log action to merge with the next window
            alarm.cancelAlarm()
        } else if (startTime == 0L) {
            startTime = SystemClock.elapsedRealtime()
        }
    }

    /** Ends tracking the latency and schedules the log after a short delay. */
    fun logEnd(statsLogManager: StatsLogManager) {
        Preconditions.assertTaskbarUiThread()
        if (startTime == 0L) return

        val endTime = SystemClock.elapsedRealtime()

        // Set the listener to log the total duration from the original startTime
        alarm.setOnAlarmListener {
            statsLogManager
                .latencyLogger()
                .withLatency(endTime - startTime)
                .log(LAUNCHER_LATENCY_RECREATE_TASKBAR)

            startTime = 0
        }

        // Cancels previous alarm and restarts the 5ms timer
        alarm.setAlarm(MERGE_DELAY_MS)
    }

    companion object {
        private const val MERGE_DELAY_MS = 5L
    }
}
