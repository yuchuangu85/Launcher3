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

package com.google.android.torus.core.time

import android.os.SystemClock

/**
 * Class in charge of controlling the delta time and the elapsed time. This will help with a
 * common scenario in any Computer Graphics engine.
 */
class TimeController {
    companion object {
        private val MIN_THRESHOLD_OVERFLOW = Float.MAX_VALUE / 1E20f
        private const val MILLIS_TO_SECONDS = 1 / 1000f
    }

    /**
     * The elapsed time, since the last time it was reset, in seconds.
     */
    var elapsedTime = 0f
        private set

    /**
     * The delta time from the last frame, in milliseconds.
     */
    var deltaTimeMillis: Long = 0
        private set

    private var lastTimeMillis: Long = 0

    init {
        resetDeltaTime()
    }

    /**
     * Resets the delta time and last time since previous frame, and sets last time to
     * [currentTimeMillis] and increases the elapsed time.
     *
     * @property currentTimeMillis The last known frame time, in milliseconds.
     */
    fun resetDeltaTime(currentTimeMillis: Long = SystemClock.elapsedRealtime()) {
        lastTimeMillis = currentTimeMillis
        elapsedTime += deltaTimeMillis * MILLIS_TO_SECONDS
        deltaTimeMillis = 0
    }

    /**
     * Resets elapse time in case is bigger than the max value (to avoid overflow)
     */
    fun resetElapsedTimeIfNeeded() {
        if (elapsedTime > MIN_THRESHOLD_OVERFLOW) {
            elapsedTime = 0f
        }
    }

    /**
     * Calculates the delta time (in milliseconds) based on the current time
     * and the last saved time.
     *
     * @property currentTimeMillis The last known frame time, in milliseconds.
     */
    fun updateDeltaTime(currentTimeMillis: Long = SystemClock.elapsedRealtime()) {
        deltaTimeMillis = currentTimeMillis - lastTimeMillis
    }
}