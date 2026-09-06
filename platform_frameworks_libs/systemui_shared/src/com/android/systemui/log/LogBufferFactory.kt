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

package com.android.systemui.log

/** Allows creating new independent [LogBuffer] */
public interface LogBufferFactory {
    public fun create(
        /**
         * Identifier for the [LogBuffer], it will be the title of its section in the dump.
         *
         * @see LogBuffer
         */
        name: String,
        /**
         * Maximum size of log messages that will be kept in the buffer.
         *
         * @see LogBuffer
         */
        maxSize: Int,
        /** Whether log messages are included in system traces */
        systrace: Boolean = true,
        /** Whether log messages are outputted to logcat. */
        alwaysLogToLogcat: Boolean = false,
        /** Track name used for logging messages in the system trace. */
        systraceTrackName: String = LogBuffer.DEFAULT_LOGBUFFER_TRACK_NAME,
    ): LogBuffer

    /**
     * Retrieves an existing [LogBuffer] associated with the given [name], or creates and returns a
     * new one if it doesn't exist. The new instance is configured with the provided parameters.
     *
     * This function ensures that only one instance of [LogBuffer] exists per unique name.
     *
     * For the parameters' description, see [create]
     *
     * @return The existing or newly created [LogBuffer] instance.
     */
    public fun getOrCreate(
        name: String,
        maxSize: Int,
        systrace: Boolean = true,
        alwaysLogToLogcat: Boolean = false,
        systraceTrackName: String = LogBuffer.DEFAULT_LOGBUFFER_TRACK_NAME,
    ): LogBuffer
}
