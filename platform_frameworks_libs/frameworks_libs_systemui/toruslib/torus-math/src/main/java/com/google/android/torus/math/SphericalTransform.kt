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

import android.opengl.Matrix
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Defines an immutable transformation using Spherical Coordinates, which might be more
 * suitable for certain animations or behaviors than [AffineTransform] (i.e. camera orbit control).
 *
 * The position of a point P in Spherical Coordinates system is specified by:
 * - A point [center], which defines the origin of spherical coordinate system.
 * - A [distance] of the point P from the [center].
 * - And some polar angles [elevation] which defines the angle from the reference plane (which is
 * parallel to the ZY plane and contains [center]) to the zenith direction (which is parallel to
 * the Y axis and the normal of the reference plane, and passes though [center]) of the Point P;
 * and [azimuth] that defines the angle of rotation of the projected point P into the
 * reference plane (from Z to Y).
 *
 * In addition we add [roll] to the model (that rotates around the model's (intrinsic) Z axis), and
 * [scale].
 *
 * The order of operations is: scale => roll => Spherical Coordinates position and rotation.
 */
class SphericalTransform @JvmOverloads constructor(
    /** The azimuth angle (from Z to X, counter clockwise, in degrees). */
    val azimuth: Float = 0f,

    /**
     * The elevation angle (from ZX plane to Y axis; positive is up negative is down, in degrees).
     */
    val elevation: Float = 0f,

    /** The roll rotation (around the model's (intrinsic) Z axis, in degrees). */
    val roll: Float = 0f,

    /** Center position of the spherical transform (the target). */
    val center: Vector3 = Vector3(0f, 0f, 0f),

    /**
     * Distance of the transform from [center] which defines a sphere of radius = distance.
     * The distance value has to be >= 0.
     */
    val distance: Float = 1f,

    /** The scale of the transform. */
    val scale: Vector3 = Vector3(1f, 1f, 1f)

) : MatrixTransform {
    constructor(transform: SphericalTransform) : this(
        transform.azimuth,
        transform.elevation,
        transform.roll,
        transform.center,
        transform.distance,
        transform.scale
    )

    init {
        if (distance < 0) throw IllegalArgumentException("Distance cannot be negative!")
    }

    /**
     * Creates a new [SphericalTransform] with a new [azimuth] rotation.
     *
     * @param azimuth The new azimuth rotation (from Z to X, counter clockwise, in degrees).
     *
     * @return The new [SphericalTransform] with the new rotation.
     */
    fun withAzimuth(azimuth: Float): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, center, distance, scale)
    }

    /**
     * Creates a new [SphericalTransform] with a new [elevation] rotation.
     *
     * @param elevation The new elevation rotation (from ZX plane to Y axis; positive is up negative
     * is down). In degrees.
     *
     * @return The new [SphericalTransform] with the new rotation.
     */
    fun withElevation(elevation: Float): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, center, distance, scale)
    }

    /**
     * Creates a new [SphericalTransform] with a new [roll] rotation.
     *
     * @param roll The new elevation rotation (around Z axis, in degrees).
     *
     * @return The new [SphericalTransform] with the new rotation.
     */
    fun withRoll(roll: Float): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, center, distance, scale)
    }

    /**
     * Creates a new [SphericalTransform] with a new [center].
     *
     * @param center The center of the spherical transform.
     *
     * @return The new [SphericalTransform] with the new center.
     */
    fun withCenter(center: Vector3): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, center, distance, scale)
    }

    /**
     * Creates a new [SphericalTransform] with a new ([x], [y], [z]) center.
     *
     * @param x The scale in the X direction.
     * @param y The scale in the Y direction.
     * @param z The scale in the Z direction.
     *
     * @return The new [SphericalTransform] with the new center.
     */
    fun withCenter(x: Float, y: Float, z: Float): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, Vector3(x, y, z), distance, scale)
    }

    /**
     * Creates a new [SphericalTransform] with a new [distance] from the [center].
     *
     * @param distance The new distance (cannot be smaller than 0).
     *
     * @return The new [SphericalTransform] with the new rotation.
     */
    fun withDistance(distance: Float): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, center, distance, scale)
    }

    /**
     * Creates a new [SphericalTransform] with ([x], [y], [z]) as the new scale.
     *
     * @param x The scale in the X direction.
     * @param y The scale in the Y direction.
     * @param z The scale in the Z direction.
     *
     * @return The new [SphericalTransform] with the new scale.
     */
    fun withScale(x: Float, y: Float, z: Float): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, center, distance, Vector3(x, y, z))
    }

    /**
     * Creates a new [SphericalTransform] with ([scale], [scale], [scale]) as the new scale.
     *
     * @param scale The scale applied in the X,Y and Z directions.
     *
     * @return The new [SphericalTransform] with the new scale.
     */
    fun withScale(scale: Float): SphericalTransform {
        return withScale(scale, scale, scale)
    }

    fun rotateByAzimuth(azimuth: Float): SphericalTransform {
        return SphericalTransform(
            this.azimuth + azimuth,
            elevation,
            roll,
            center,
            distance,
            scale
        )
    }

    fun rotateByElevation(elevation: Float): SphericalTransform {
        return SphericalTransform(
            azimuth,
            this.elevation + elevation,
            roll,
            center,
            distance,
            scale
        )
    }

    fun rollBy(roll: Float): SphericalTransform {
        return SphericalTransform(
            azimuth,
            elevation,
            this.roll + roll,
            center,
            distance,
            scale
        )
    }

    fun translateCenterBy(x: Float, y: Float, z: Float): SphericalTransform {
        return SphericalTransform(
            azimuth,
            elevation,
            roll,
            center + Vector3(x, y, z),
            distance,
            scale
        )
    }

    fun translateBy(distance: Float): SphericalTransform {
        return SphericalTransform(azimuth, elevation, roll, center, this.distance + distance, scale)
    }

    fun scaleBy(scale: Float): SphericalTransform {
        return SphericalTransform(
            azimuth,
            elevation,
            roll,
            center,
            distance,
            this.scale + Vector3(scale)
        )
    }

    fun scaleBy(x: Float, y: Float, z: Float): SphericalTransform {
        return SphericalTransform(
            azimuth,
            elevation,
            roll,
            center,
            distance,
            scale + Vector3(x, y, z)
        )
    }

    /**
     * Returns a 4x4 transform Matrix. The format of the matrix follows the OpenGL ES matrix format
     * stored in float arrays.
     * Matrices are 4 x 4 column-vector matrices stored in column-major order:
     *
     * <pre>
     *  m[0] m[4] m[8]  m[12]
     *  m[1] m[5] m[9]  m[13]
     *  m[2] m[6] m[10] m[14]
     *  m[3] m[7] m[11] m[15]
     *  </pre>
     *
     *  @return a 16 value [FloatArray] representing the transform as a 4x4 matrix.
     */
    override fun toMatrix(): FloatArray {
        val transformMatrix = FloatArray(16)
        val tmp = FloatArray(16)
        val azimuthRad = (azimuth * MathUtils.DEG_TO_RAD).toFloat()
        val elevationRad = (elevation * MathUtils.DEG_TO_RAD).toFloat()
        Matrix.setIdentityM(transformMatrix, 0)
        Matrix.scaleM(transformMatrix, 0, scale.x, scale.y, scale.z)
        Matrix.rotateM(transformMatrix, 0, roll, 0f, 0f, 1f)
        Matrix.setLookAtM(
            tmp, 0,
            center.x + distance * sin(azimuthRad) * cos(elevationRad),
            center.y + distance * sin(elevationRad),
            center.z + distance * cos(azimuthRad) * cos(elevationRad),
            center.x,
            center.y,
            center.z,
            Vector3.Y_AXIS.x,
            Vector3.Y_AXIS.y,
            Vector3.Y_AXIS.z
        )
        Matrix.multiplyMM(transformMatrix, 0, tmp, 0, transformMatrix, 0)
        return transformMatrix
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SphericalTransform

        if (azimuth != other.azimuth) return false
        if (elevation != other.elevation) return false
        if (roll != other.roll) return false
        if (center != other.center) return false
        if (distance != other.distance) return false
        if (scale != other.scale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = azimuth.hashCode()
        result = 31 * result + elevation.hashCode()
        result = 31 * result + roll.hashCode()
        result = 31 * result + center.hashCode()
        result = 31 * result + distance.hashCode()
        result = 31 * result + scale.hashCode()
        return result
    }

    override fun toString(): String {
        return "Rotation (az, el, ro): (${azimuth}, ${elevation}, ${roll})\n" +
                "Center: $center\n" +
                "Distance: $distance\n" +
                "Scale: $scale\n"
    }
}