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

import com.google.android.torus.math.MathUtils.DEG_TO_RAD
import com.google.android.torus.math.MathUtils.RAD_TO_DEG
import java.util.*
import kotlin.math.*

/**
 * A unit quaternion representing a rotation.
 */
class RotationQuaternion {
    companion object {
        /**
         * Creates a rotation quaternion from a quaternion.
         *
         * @param w The w value of a quaternion.
         * @param x The x value of a quaternion.
         * @param y The y value of a quaternion.
         * @param z The z value of a quaternion.
         */
        @JvmStatic
        fun fromQuaternion(w: Double, x: Double, y: Double, z: Double): RotationQuaternion {
            val rotation = 2.0 * atan2(sqrt(x * x + y * y + z * z), w) * RAD_TO_DEG
            val direction = Vector3(x.toFloat(), y.toFloat(), z.toFloat()).toNormalized()
            return RotationQuaternion(rotation, direction)
        }

        /**
         * Creates a rotation quaternion from some Euler angles (ZYX sequence).
         *
         * @param eulerAngles The Euler rotation angles around each axis, in degrees.
         */
        @JvmStatic
        fun fromEuler(eulerAngles: Vector3): RotationQuaternion {
            return fromEuler(eulerAngles.x, eulerAngles.y, eulerAngles.z)
        }

        /**
         * Creates a rotation quaternion from some Euler angles (ZYX sequence).
         *
         * @param rotationX The Euler rotation angle around the X axis, in degrees.
         * @param rotationY The Euler rotation angle around the Y axis, in degrees.
         * @param rotationZ The Euler rotation angle around the Z axis, in degrees.
         */
        @JvmStatic
        fun fromEuler(rotationX: Float, rotationY: Float, rotationZ: Float): RotationQuaternion {
            val halfDegToRad = 0.5 * DEG_TO_RAD
            val cy = cos(rotationZ * halfDegToRad)
            val sy = sin(rotationZ * halfDegToRad)
            val cp = cos(rotationY * halfDegToRad)
            val sp = sin(rotationY * halfDegToRad)
            val cr = cos(rotationX * halfDegToRad)
            val sr = sin(rotationX * halfDegToRad)

            val w = cr * cp * cy + sr * sp * sy
            val x = sr * cp * cy - cr * sp * sy
            val y = cr * sp * cy + sr * cp * sy
            val z = cr * cp * sy - sr * sp * cy

            return fromQuaternion(w, x, y, z)
        }
    }

    private val w: Double
    private val x: Double
    private val y: Double
    private val z: Double
    val direction: Vector3
    val angle: Double

    /**
     * Creates a unit quaternion representing a rotation.
     *
     * @param angle The angle of rotation around [direction] vector, in degrees. The rotation is
     * counterclockwise (if the [direction] vector is pointing at the point of sight).
     * @param direction The angle of rotation, in degrees.
     */
    constructor(angle: Double, direction: Vector3) {
        this.direction = direction.toNormalized()
        this.angle = angle

        val angleRad = angle * DEG_TO_RAD
        val sinAngle = sin(angleRad)
        w = cos(angleRad)
        x = sinAngle * this.direction.x
        y = sinAngle * this.direction.y
        z = sinAngle * this.direction.z
    }

    /**
     * Creates a identity rotation quaternion, with the direction pointing to the X axis.
     */
    constructor() : this(0.0, Vector3.X_AXIS)

    /**
     * Creates a rotation quaternion from another rotation quaternion.
     */
    constructor(rotationQuaternion: RotationQuaternion) : this(
        rotationQuaternion.angle,
        rotationQuaternion.direction
    )

    /**
     * Returns a [Vector3] representing a quaternion as some Rotation Euler Angles (ZYX sequence).
     *
     * @return A [Vector3] containing the Euler rotation angle around each axis, in degrees.
     */
    fun toEulerAngles(): Vector3 {
        val angleX = atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y))

        val tmp = 2 * (w * y - z * x)
        val angleY = if (abs(tmp) >= 1) {
            tmp.sign * PI / 2.0
        } else {
            asin(tmp)
        }

        val angleZ = atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z))

        return Vector3(
            (angleX * RAD_TO_DEG).toFloat(),
            (angleY * RAD_TO_DEG).toFloat(),
            (angleZ * RAD_TO_DEG).toFloat()
        )
    }

    /**
     * Inverts the rotation quaternion (q^-1).
     *
     * @return The new inverted quaternion.
     */
    fun inverse(): RotationQuaternion {
        return fromQuaternion(w, -x, -y, -z)
    }

    /**
     * Applies the current rotation quaternion to a [Vector3].
     *
     * @param vector the [Vector3] that will be rotated.
     *
     * @return the rotated [vector].
     */
    fun applyRotationTo(vector: Vector3): Vector3 {
        return (this * (fromQuaternion(
            0.0,
            vector.x.toDouble(),
            vector.y.toDouble(),
            vector.z.toDouble()
        ) * this.inverse())).direction * vector.length()
    }

    operator fun times(q: RotationQuaternion): RotationQuaternion {
        return fromQuaternion(
            w * q.w - x * q.x - y * q.y - z * q.z,
            w * q.x + x * q.w + y * q.z - z * q.y,
            w * q.y - x * q.z + y * q.w + z * q.x,
            w * q.z + x * q.y - y * q.x + z * q.w
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RotationQuaternion

        if (direction != other.direction) return false
        if (angle != other.angle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = direction.hashCode()
        result = 31 * result + angle.hashCode()
        return result
    }

    override fun toString(): String {
        return "Angle: ${angle}º, Direction: $direction"
    }
}