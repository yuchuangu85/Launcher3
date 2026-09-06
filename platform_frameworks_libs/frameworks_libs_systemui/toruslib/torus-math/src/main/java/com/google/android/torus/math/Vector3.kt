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
import kotlin.math.sqrt

/**
 * An immutable three-dimensional vector.
 */
class Vector3 @JvmOverloads constructor(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val Y_AXIS = Vector3(0f, 1f, 0f)
        val NEG_Y_AXIS = Vector3(0f, -1f, 0f)
        val X_AXIS = Vector3(1f, 0f, 0f)
        val NEG_X_AXIS = Vector3(-1f, 0f, 0f)
        val Z_AXIS = Vector3(0f, 0f, 1f)
        val NEG_Z_AXIS = Vector3(0f, 0f, -1f)
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
        fun lerp(vectorStart: Vector3, vectorEnd: Vector3, amount: Float): Vector3 {
            return Vector3(
                MathUtils.lerp(vectorStart.x, vectorEnd.x, amount),
                MathUtils.lerp(vectorStart.y, vectorEnd.y, amount),
                MathUtils.lerp(vectorStart.z, vectorEnd.z, amount)
            )
        }

        /**
         * Calculates the cross product from [firstVector] to [secondVector]
         * (that means [firstVector]x[secondVector]).
         *
         * @param firstVector The first three-dimensional vector.
         * @param secondVector The second three-dimensional vector.
         *
         * @return A [Vector3] that represents the result of [firstVector]x[secondVector].
         */
        @JvmStatic
        fun cross(firstVector: Vector3, secondVector: Vector3): Vector3 {
            return Vector3(
                firstVector.y * secondVector.z - firstVector.z * secondVector.y,
                firstVector.z * secondVector.x - firstVector.x * secondVector.z,
                firstVector.x * secondVector.y - firstVector.y * secondVector.x
            )
        }
    }

    constructor() : this(0f)
    constructor(value: Float) : this(value, value, value)
    constructor(vector: Vector3) : this(vector.x, vector.y, vector.z)


    fun plus(x: Float, y: Float, z: Float): Vector3 {
        return Vector3(this.x + x, this.y + y, this.z + z)
    }

    fun minus(x: Float, y: Float, z: Float): Vector3 {
        return Vector3(this.x - x, this.y - y, this.z - z)
    }

    fun multiplyBy(value: Float): Vector3 {
        return multiplyBy(value, value, value)
    }

    fun multiplyBy(vector: Vector3): Vector3 {
        return multiplyBy(vector.x, vector.y, vector.z)
    }

    fun multiplyBy(x: Float, y: Float, z: Float): Vector3 {
        return Vector3(this.x * x, this.y * y, this.z * z)
    }

    /**
     * Returns a new [Vector3] instance with a normalize length (length = 1), and same direction
     * as the current vector.
     *
     * @return the new [Vector3] instance with a normalized length and same direction as current
     * vector.
     */
    fun toNormalized(): Vector3 {
        val length = length()
        return Vector3(x / length, y / length, z / length)
    }

    /**
     * Performs the algebraic dot product operation.
     *
     * @param vector The second vector of the dot product.
     *
     * @return the result of the dot product of current vector and [vector].
     */
    fun dot(vector: Vector3): Float {
        return dot(vector.x, vector.y, vector.z)
    }

    /**
     * Performs the algebraic dot product operation.
     *
     * @param x The first component of a three-dimensional vector.
     * @param y The second component of a three-dimensional vector.
     * @param y The third component of a three-dimensional vector.
     *
     * @return the result of the dot product of current vector and ([x], [y], [z]).
     */
    fun dot(x: Float, y: Float, z: Float): Float {
        return this.x * x + this.y * y + this.z * z
    }

    /**
     * Returns the distance between the current 3D point defined by current vector and [vector].
     *
     * @param vector The second point.
     *
     * @return the distance between current vector and [vector].
     */
    fun distanceTo(vector: Vector3): Float {
        return distanceTo(vector.x, vector.y, vector.z)
    }

    /**
     * Returns the distance between the current 3D point defined by current vector
     * and ([x], [y], [z]).
     *
     * @param x The first component of a three-dimensional vector.
     * @param y The second component of a three-dimensional vector.
     * @param z The third component of a three-dimensional vector.
     *
     * @return the distance between current vector and ([x], [y], [z]).
     */
    fun distanceTo(x: Float, y: Float, z: Float): Float {
        val dx = x - this.x
        val dy = y - this.y
        val dz = z - this.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Returns the length of the current vector (which is the distance from origin (0, 0, 0) to the
     * position defined by the current vector).
     *
     * @return The length of the current vector.
     */
    fun length(): Float {
        return sqrt(dot(x, y, z))
    }

    /**
     * Returns a new vector with same direction as the current vector and a [newLength] length.
     *
     * @param newLength the new length of the vector
     *
     * @return the new [Vector3] instance with a [newLength] and same direction as current vector.
     */
    fun withLength(newLength: Float): Vector3 {
        return multiplyBy(newLength / length())
    }

    operator fun minus(vector: Vector3): Vector3 {
        return minus(vector.x, vector.y, vector.z)
    }

    operator fun plus(vector: Vector3): Vector3 {
        return plus(vector.x, vector.y, vector.z)
    }

    operator fun times(value: Float): Vector3 {
        return multiplyBy(value)
    }

    operator fun times(vector: Vector3): Vector3 {
        return multiplyBy(vector)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vector3

        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    override fun toString(): String {
        return "(${FORMAT.format(x)}, ${FORMAT.format(y)}, ${FORMAT.format(z)})"
    }
}