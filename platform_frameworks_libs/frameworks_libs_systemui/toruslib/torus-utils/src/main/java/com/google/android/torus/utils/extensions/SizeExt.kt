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

package com.google.android.torus.utils.extensions

import android.util.Size
import android.util.SizeF

/**
 * Extends [Size] to return the aspect ratio (ratio between the width and height). This ratio is
 * returned as the value resulting of the operation width / height. If width or height have invalid
 * values (smaller or equal to 0), -1 is returned.
 *
 * @return the [Float] representing the aspect ratio, or -1 if width or height have invalid values
 * (smaller or equal to 0).
 */
fun Size.getAspectRatio(): Float {
    return if (height <= 0 || width <= 0) {
        -1f
    } else {
        width / height.toFloat()
    }
}

/**
 * Extends [Size] to return the aspect ratio (ratio between the width and height). This ratio is
 * returned as the value resulting of the operation width / height. If width or height have invalid
 * values (smaller or equal to 0), -1 is returned.
 *
 * @return the [Float] representing the aspect ratio, or -1 if width or height have invalid values
 * (smaller or equal to 0).
 */
fun SizeF.getAspectRatio(): Float {
    return if (height <= 0 || width <= 0) {
        -1f
    } else {
        width / height
    }
}