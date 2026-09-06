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

package com.google.android.torus.math

import kotlin.math.max
import kotlin.math.min

/**
 * Numeric operations and constants not included in the main Math class.
 */
object MathUtils {
    const val DEG_TO_RAD = Math.PI / 180.0
    const val RAD_TO_DEG = 1 / DEG_TO_RAD
    const val TAU = Math.PI * 2.0

    /**
     * Maps a value from a range to a different one (with the option to clamp it to the new range).
     *
     * @param value The value to map from a value range to a different one.
     * @param inMin The minimum value of the original range.
     * @param inMax The maximum value of the original range.
     * @param outMin The minimum value of the new range.
     * @param outMax The maximum value of the new range.
     * @param clamp If you want to clamp the mapped value to the new range, set to true; otherwise
     * set to false (by default is set to true).
     *
     * @return The [value] mapped to the new range.
     */
    @JvmStatic
    @JvmOverloads
    fun map(
        value: Double,
        inMin: Double,
        inMax: Double,
        outMin: Double,
        outMax: Double,
        clamp: Boolean = true
    ): Double {
        if (clamp) {
            if (value < inMin) {
                return outMin
            }
            if (value > inMax) {
                return outMax
            }
        }
        return (value - inMin) / (inMax - inMin) * (outMax - outMin) + outMin
    }

    /**
     * Maps a value from a range to a different one (with the option to clamp it to the new range).
     *
     * @param value The value to map from a value range to a different one.
     * @param inMin The minimum value of the original range.
     * @param inMax The maximum value of the original range.
     * @param outMin The minimum value of the new range.
     * @param outMax The maximum value of the new range.
     * @param clamp If you want to clamp the mapped value to the new range, set to true; otherwise
     * set to false (by default is set to true).
     *
     * @return The [value] mapped to the new range.
     */
    @JvmStatic
    @JvmOverloads
        fun map(
        value: Float,
        inMin: Float,
        inMax: Float,
        outMin: Float,
        outMax: Float,
        clamp: Boolean = true
    ): Float {
        if (clamp) {
            if (value < inMin) {
                return outMin
            }
            if (value > inMax) {
                return outMax
            }
        }
        return (value - inMin) / (inMax - inMin) * (outMax - outMin) + outMin
    }

    /**
     * Linear interpolation between two values.
     *
     * @param start The first value.
     * @param end The second value.
     * @param amount Decides the how we mix the interpolated values; when is 0, it returns the init
     * value. When is 1, it returns the end value. For any value in between it returns the linearly
     * interpolated value between [init] and [end]. If [amount] is smaller than 0 or bigger than 1
     * and [clamp] is false, it continues returning values based on the line created using
     * [init] and [end]; otherwise the value is clamped to [init] and [end].
     * @param clamp If you want to clamp the mapped value to the new range, set to true; otherwise
     * set to false (by default is set to true).
     *
     * @return The interpolated value.
     */
    @JvmStatic
    @JvmOverloads
    fun lerp(start: Double, end: Double, amount: Double, clamp: Boolean = true): Double {
        val amountClamped = if (clamp) {
            clamp(amount, 0.0, 1.0)
        } else {
            amount
        }
        return (end - start) * amountClamped + start
    }

    /**
     * Linear interpolation between two values.
     *
     * @param init The first value.
     * @param end The second value.
     * @param amount Decides the how we mix the interpolated values; when is 0, it returns the init
     * value. When is 1, it returns the end value. For any value in between it returns the linearly
     * interpolated value between [init] and [end]. If [amount] is smaller than 0 or bigger than 1
     * and [clamp] is false, it continues returning values based on the line created using
     * [init] and [end]; otherwise the value is clamped to [init] and [end].
     * @param clamp If you want to clamp the mapped value to the new range, set to true; otherwise
     * set to false (by default is set to true).
     *
     * @return The interpolated value.
     */
    @JvmStatic
    @JvmOverloads
    fun lerp(init: Float, end: Float, amount: Float, clamp: Boolean = true): Float {
        val amountClamped = if (clamp) {
            clamp(amount, 0.0f, 1.0f)
        } else {
            amount
        }
        return (end - init) * amountClamped + init
    }

    /**
     * Secures that a value is not smaller or bigger than the given range.
     *
     * @param value The input value.
     * @param min The min value of the range. If [value] is smaller than this value, it is fixed to
     * [min] value.
     * @param max The max value of the range. If [value] is bigger than this value, it is fixed to
     * [min] value.
     *
     * @return the value that is secured in the [[min], [max]] range.
     */
    @JvmStatic
    fun clamp(value: Double, min: Double, max: Double): Double {
        return max(min(value, max), min)
    }

    /**
     * Secures that a value is not smaller or bigger than the given range.
     *
     * @param value The input value.
     * @param min The min value of the range. If [value] is smaller than this value, it is fixed to
     * [min] value.
     * @param max The max value of the range. If [value] is bigger than this value, it is fixed to
     * [min] value.
     *
     * @return the value that is secured in the [[min], [max]] range.
     */
    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float): Float {
        return max(min(value, max), min)
    }
}
