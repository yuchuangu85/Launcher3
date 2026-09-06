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

/** An immutable 3D transformation in homogeneous coordinates. */
open class AffineTransform @JvmOverloads constructor(
    /** The position of the transform. */
    val position: Vector3 = Vector3(0f, 0f, 0f),
    /** The rotation of the transform. */
    val rotation: RotationQuaternion = RotationQuaternion(),
    /** The scale of the transform. */
    val scale: Vector3 = Vector3(1f, 1f, 1f)
) : MatrixTransform {
    constructor(transform: AffineTransform) : this(
        transform.position,
        transform.rotation,
        transform.scale
    )

    /**
     * Creates a new [AffineTransform] with ([x], [y], [z]) as the new translation.
     *
     * @param x The X component of the translation.
     * @param y The Y component of the translation.
     * @param z The Z component of the translation.
     *
     * @return The new [AffineTransform] with the new translation.
     */
    fun withTranslation(x: Float, y: Float, z: Float): AffineTransform {
        return AffineTransform(Vector3(x, y, z), rotation, scale)
    }

    /**
     * Creates a new [AffineTransform] with ([x], [y], [z]) as the new scale.
     *
     * @param x The scale in the X direction.
     * @param y The scale in the Y direction.
     * @param z The scale in the Z direction.
     *
     * @return The new [AffineTransform] with the new scale.
     */
    fun withScale(x: Float, y: Float, z: Float): AffineTransform {
        return AffineTransform(position, rotation, Vector3(x, y, z))
    }

    /**
     * Creates a new [AffineTransform] with ([scale], [scale], [scale]) as the new scale.
     *
     * @param scale The scale applied in the X,Y and Z directions.
     *
     * @return The new [AffineTransform] with the new scale.
     */
    fun withScale(scale: Float): AffineTransform {
        return withScale(scale, scale, scale)
    }

    /**
     * Creates a new [AffineTransform] with [rotation] as the new rotation.
     *
     * @param rotation The new rotation.
     *
     * @return The new [AffineTransform] with the new rotation.
     */
    fun withRotation(rotation: RotationQuaternion): AffineTransform {
        return AffineTransform(position, RotationQuaternion(rotation), scale)
    }

    /**
     * Returns a new transform with a new rotation using Euler rotation angles (ZYX sequence).
     *
     * @param x The Euler rotation angle around X axis, in degrees.
     * @param y The Euler rotation angle around Y axis, in degrees.
     * @param z The Euler rotation angle around Z axis, in degrees.
     */
    fun withEulerRotation(x: Float, y: Float, z: Float): AffineTransform {
        return AffineTransform(position, RotationQuaternion.fromEuler(x, y, z), scale)
    }

    fun translateBy(x: Float, y: Float, z: Float): AffineTransform {
        return AffineTransform(position + Vector3(x, y, z), rotation, scale)
    }

    fun scaleBy(scale: Float): AffineTransform {
        return AffineTransform(position, rotation, this.scale + Vector3(scale))
    }

    fun scaleBy(x: Float, y: Float, z: Float): AffineTransform {
        return AffineTransform(position, rotation, this.scale + Vector3(x, y, z))
    }

    fun rotateBy(quaternion: RotationQuaternion): AffineTransform {
        return AffineTransform(position, quaternion * rotation, scale)
    }

    /**
     * Rotates the current rotation using some Euler rotation angles (ZYX sequence).
     *
     * @param x The Euler rotation angle around X axis, in degrees.
     * @param y The Euler rotation angle around Y axis, in degrees.
     * @param z The Euler rotation angle around Z axis, in degrees.
     */
    fun rotateByEuler(x: Float, y: Float, z: Float): AffineTransform {
        return rotateBy(RotationQuaternion.fromEuler(x, y, z))
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
        Matrix.setIdentityM(transformMatrix, 0)
        // The order of operations matter; we should follow the usual: Scale, Rotate and Translate.
        Matrix.scaleM(transformMatrix, 0, scale.x, scale.y, scale.z)
        Matrix.rotateM(
            transformMatrix,
            0,
            rotation.angle.toFloat(),
            rotation.direction.x,
            rotation.direction.y,
            rotation.direction.z
        )
        Matrix.translateM(transformMatrix, 0, position.x, position.y, position.z)
        return transformMatrix
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AffineTransform

        if (position != other.position) return false
        if (rotation != other.rotation) return false
        if (scale != other.scale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = position.hashCode()
        result = 31 * result + rotation.hashCode()
        result = 31 * result + scale.hashCode()
        return result
    }

    override fun toString(): String {
        return "Position: ${position}\nRotation: ${rotation}\nScale: $scale\n"
    }
}