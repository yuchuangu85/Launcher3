/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.personalcontext.ace.common

object PackedIntUtils {

    /**
     * Packs the [value] parameter into the upper 16 bits of the receiver (`this`). The receiver's
     * existing lower 16 bits are preserved.
     */
    fun Int.packValue(value: Int): Int {
        val maskedLower = this and 0xFFFF
        val shiftedUpper = value shl 16
        return shiftedUpper or maskedLower
    }

    /** Extracts the signed 16-bit value from the upper 16 bits of the receiver (`this`). */
    fun Int.unpackValue(): Int {
        return this shr 16
    }

    /** Extracts the original Int receiver. */
    fun Int.unpackOriginal(): Int {
        return this.toShort().toInt()
    }
}
