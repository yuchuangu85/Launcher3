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

package com.android.systemui.biometrics.shared.model

import android.hardware.biometrics.fingerprint.location.PhysicalSensorLocation

/** Peripheral fingerprint sensor locations. Represents [PhysicalSensorLocation]. */
enum class PeripheralFingerprintSensorLocation {
    UNKNOWN,
    KEYBOARD_BOTTOM_LEFT,
    KEYBOARD_BOTTOM_RIGHT,
    KEYBOARD_TOP_RIGHT,
    RIGHT_SIDE,
    LEFT_SIDE,
    LEFT_OF_POWER_BUTTON_TOP_RIGHT,
    POWER_BUTTON_TOP_RIGHT_KEY;

    fun isUnknown(): Boolean = this == UNKNOWN
}

/** Convert [this] to corresponding [PeripheralFingerprintSensorLocation] */
fun Byte.toPeripheralFingerprintSensorLocation(): PeripheralFingerprintSensorLocation =
    when (this) {
        PhysicalSensorLocation.UNKNOWN -> PeripheralFingerprintSensorLocation.UNKNOWN
        PhysicalSensorLocation.KEYBOARD_BOTTOM_LEFT ->
            PeripheralFingerprintSensorLocation.KEYBOARD_BOTTOM_LEFT
        PhysicalSensorLocation.KEYBOARD_BOTTOM_RIGHT ->
            PeripheralFingerprintSensorLocation.KEYBOARD_BOTTOM_RIGHT
        PhysicalSensorLocation.KEYBOARD_TOP_RIGHT ->
            PeripheralFingerprintSensorLocation.KEYBOARD_TOP_RIGHT
        PhysicalSensorLocation.RIGHT_SIDE -> PeripheralFingerprintSensorLocation.RIGHT_SIDE
        PhysicalSensorLocation.LEFT_SIDE -> PeripheralFingerprintSensorLocation.LEFT_SIDE
        PhysicalSensorLocation.LEFT_OF_POWER_BUTTON_TOP_RIGHT ->
            PeripheralFingerprintSensorLocation.LEFT_OF_POWER_BUTTON_TOP_RIGHT
        PhysicalSensorLocation.POWER_BUTTON_TOP_RIGHT_KEY ->
            PeripheralFingerprintSensorLocation.POWER_BUTTON_TOP_RIGHT_KEY
        else -> throw IllegalArgumentException("Invalid PhysicalSensorLocation value: $this")
    }
