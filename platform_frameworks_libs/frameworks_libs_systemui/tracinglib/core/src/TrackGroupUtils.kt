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

package com.android.app.tracing

public object TrackGroupUtils {
    /**
     * Generates a track name in a way that perfetto can group tracks together.
     *
     * This leverages the "Create process workspace" perfetto plugin. This plugins parses all the
     * tracks that follow the "groupName##trackName" format, nesting "trackName" under "groupName".
     *
     * This allows to easily group tracks that are related under a single summary track (e.g. all
     * "shade" related tracks will appear together, under the "shade" track in the process
     * workspace).
     */
    @JvmStatic
    public fun trackGroup(groupName: String, trackName: String): String = "$groupName##$trackName"
}
