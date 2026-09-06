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
package com.android.launcher3.display

import kotlin.math.max
import kotlin.math.min

/** Utility class to hold a size information in an orientation independent way */
data class PortraitSize private constructor(@JvmField val width: Int, @JvmField val height: Int) {

    companion object {
        @JvmStatic fun from(w: Int, h: Int): PortraitSize = PortraitSize(min(w, h), max(w, h))
    }
}
