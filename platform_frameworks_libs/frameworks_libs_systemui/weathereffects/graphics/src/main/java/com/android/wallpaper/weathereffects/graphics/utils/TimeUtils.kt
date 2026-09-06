/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wallpaper.weathereffects.graphics.utils

/** Contains functions related to time. */
object TimeUtils {
    private const val MILLIS_TO_SECONDS = 1 / 1_000f
    private const val NANOS_TO_SECONDS = 1 / 1_000_000_000f

    /** Converts milliseconds to decimal seconds. */
    fun millisToSeconds(millis: Long): Float = millis * MILLIS_TO_SECONDS

    /** Converts nanoseconds to decimal seconds. */
    fun nanosToSeconds(nanos: Long): Float = nanos * NANOS_TO_SECONDS
}
