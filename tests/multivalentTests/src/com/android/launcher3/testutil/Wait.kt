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
package com.android.launcher3.testutil

import android.os.SystemClock
import android.util.Log
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.util.TestUtil
import org.junit.Assert

/** A utility class for waiting for a condition to be true. */
object Wait {
    private const val DEFAULT_SLEEP_MS: Long = 200

    @JvmStatic
    fun atMost(message: String, condition: Condition) =
        atMost(message, TestUtil.DEFAULT_UI_TIMEOUT, condition)

    @JvmStatic
    fun atMost(message: String, timeout: Long, condition: Condition) {
        val startTime = SystemClock.uptimeMillis()
        val endTime = startTime + timeout
        Log.d("Wait", "atMost: $startTime - $endTime")
        while (SystemClock.uptimeMillis() < endTime) {
            if (condition.isTrue()) return
            RoboApiWrapper.yieldToMainLooper()
            SystemClock.sleep(DEFAULT_SLEEP_MS)
        }

        // Check once more before returning false.
        if (condition.isTrue()) return
        Log.d("Wait", "atMost: timed out: " + SystemClock.uptimeMillis())
        Assert.fail(message)
    }

    /** Interface representing a generic condition */
    fun interface Condition {

        @Throws(Throwable::class) fun isTrue(): Boolean
    }
}
