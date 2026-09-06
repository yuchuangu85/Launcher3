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

import java.text.DecimalFormat
import java.util.*
import kotlin.math.hypot

/**
 * An immutable two-dimensional vector.
 */
class Vector2 @JvmOverloads constructor(val x: Float, val y: Float) {
    companion object {
        val ZERO = Vector2(0f, 0f)
        val Y_AXIS = Vector2(0f, 1f)
        val NEG_Y_AXIS = Vector2(0f, -1f)
        val X_AXIS = Vector2(1f, 0f)
        val NEG_X_AXIS = Vector2(-1f, 0f)
        private val FORMAT = DecimalFormat("##.###")

        /**
         * Linear interpolation between two vectors. The interpolated value will be always inside
         * the interval [[vectorStart], [vectorEnd]].
         *
         * @param vectorStart The first point that defines the linear interpolant.
         * @param vectorEnd The second point that defines the linear interpolant.
         * @param amount Value used to interpolate between [vectorStart] and [vectorEnd]. When
         * [amount] is zero, the return value is [vectorStart]; when [amount] is 1, the return value
         * is [vectorEnd].
         *
         * @return interpolated value.
         */
        @JvmStatic
        fun lerp(vectorStart: Vector2, vectorEnd: Vector2, amount: Float): Vector2 {
            return Vector2(
                MathUtils.lerp(vectorStart.x, vectorEnd.x, amount),
                MathUtils.lerp(vectorStart.y, vectorEnd.y, amount)
            )
        }
    }

    constructor() : this(0f)
    constructor(value: Float) : this(value, value)
    constructor(vector: Vector2) : this(vector.x, vector.y)

    fun plus(x: Float, y: Float): Vector2 {
        return Vector2(this.x + x, this.y + y)
    }

    fun minus(x: Float, y: Float): Vector2 {
        return Vector2(this.x - x, this.y - y)
    }

    fun multiplyBy(value: Float): Vector2 {
        return multiplyBy(value, value)
    }

    fun multiplyBy(vector: Vector2): Vector2 {
        return multiplyBy(vector.x, vector.y)
    }

    fun multiplyBy(x: Float, y: Float): Vector2 {
        return Vector2(this.x * x, this.y * y)
    }

    /**
     * Returns a new [Vector2] instance with a normalize length (length = 1), and same direction
     * as the current vector.
     *
     * @return the new [Vector2] instance with a normalized length and same direction as current
     * vector.
     */
    fun toNormalized(): Vector2 {
        val length = length()
        return Vector2(x / length, y / length)
    }

    /**
     * Performs the algebraic dot product operation.
     *
     * @param vector The second vector of the dot product.
     *
     * @return the result of the dot product of current vector and [vector].
     */
    fun dot(vector: Vector2): Float {
        return dot(vector.x, vector.y)
    }

    /**
     * Performs the algebraic dot product operation.
     *
     * @param x The first component of a two-dimensional vector.
     * @param y The second component of a two-dimensional vector.
     *
     * @return the result of the dot product of current vector and ([x], [y]).
     */
    fun dot(x: Float, y: Float): Float {
        return this.x * x + this.y * y
    }

    /**
     * Returns the distance between the current 2D point defined by current vector and [vector].
     *
     * @param vector The second point.
     *
     * @return the distance between current vector and [vector].
     */
    fun distanceTo(vector: Vector2): Float {
        return distanceTo(vector.x, vector.y)
    }

    /**
     * Returns the distance between the current 2D point defined by current vector
     * and ([x], [y]).
     *
     * @param x The first component of a two-dimensional vector.
     * @param y The second component of a two-dimensional vector.
     *
     * @return the distance between the current vector and ([x], [y]).
     */
    fun distanceTo(x: Float, y: Float): Float {
        return hypot(x - this.x, y - this.y)
    }

    /**
     * Returns the length of the current vector (which is the distance from origin (0, 0) to the
     * position defined by the current vector).
     *
     * @return The length of the current vector.
     */
    fun length(): Float {
        return hypot(x, y)
    }

    /**
     * Returns a new vector with same direction as the current vector and a [newLength] length.
     *
     * @param newLength the new length of the vector
     *
     * @return the new [Vector2] instance with a [newLength] and same direction as current vector.
     */
    fun withLength(newLength: Float): Vector2 {
        return times(newLength / length())
    }

    operator fun minus(vector: Vector2): Vector2 {
        return minus(vector.x, vector.y)
    }

    operator fun plus(vector: Vector2): Vector2 {
        return plus(vector.x, vector.y)
    }

    operator fun times(value: Float): Vector2 {
        return multiplyBy(value)
    }

    operator fun times(vector: Vector2): Vector2 {
        return multiplyBy(vector)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vector2

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

    override fun toString(): String {
        return "(${FORMAT.format(x)}, ${FORMAT.format(y)})"
    }
}