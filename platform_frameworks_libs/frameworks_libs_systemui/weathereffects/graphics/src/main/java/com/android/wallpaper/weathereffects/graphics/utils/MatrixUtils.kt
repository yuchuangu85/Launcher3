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

package com.android.wallpaper.weathereffects.graphics.utils

import android.graphics.Matrix
import android.util.SizeF

/** Helper functions for matrix operations. */
object MatrixUtils {
    // Member variables in this object should be only used as intermediate buffer
    // Should not be used as any return value
    private val inverseMatrix: Matrix = Matrix()
    private val concatMatrix: Matrix = Matrix()
    private val matrixValues = FloatArray(9)

    /** Returns a [Matrix] that crops the image and centers to the screen. */
    fun centerCropMatrix(surfaceSize: SizeF, imageSize: SizeF): Matrix {
        val widthScale = surfaceSize.width / imageSize.width
        val heightScale = surfaceSize.height / imageSize.height
        val scale = maxOf(widthScale, heightScale)

        return Matrix(Matrix.IDENTITY_MATRIX).apply {
            // Move the origin of the image to its center.
            postTranslate(-imageSize.width / 2f, -imageSize.height / 2f)
            // Apply scale.
            postScale(scale, scale)
            // Translate back to the center of the screen.
            postTranslate(surfaceSize.width / 2f, surfaceSize.height / 2f)
        }
    }

    // To apply parallax matrix to fragCoord, we need to invert and transpose the matrix
    fun invertAndTransposeMatrix(matrix: Matrix, outArray: FloatArray): FloatArray {
        matrix.invert(inverseMatrix)
        inverseMatrix.getValues(matrixValues)
        return transposeMatrixArray(matrixValues, outArray)
    }

    fun getScale(matrix: Matrix): Float {
        matrix.getValues(matrixValues)
        return matrixValues[0]
    }

    fun getScaleFromMatrixValues(matrixValuesArray: FloatArray): Float {
        return matrixValuesArray[0]
    }

    /**
     * Calculates the transformation matrix that, when applied to `originMatrix`, results in
     * `targetMatrix`. Current use case: Calculating parallax effect for the homescreen compared
     * with page 0.
     *
     * @param originMatrix The original transformation matrix.
     * @param targetMatrix The target transformation matrix.
     * @param outArray A pre-allocated FloatArray to store the result.
     * @return The transformation difference matrix as a FloatArray.
     */
    fun calculateTransformDifference(
        originMatrix: Matrix,
        targetMatrix: Matrix,
        outArray: FloatArray,
    ): FloatArray {
        originMatrix.invert(inverseMatrix)
        concatMatrix.set(inverseMatrix)
        concatMatrix.postConcat(targetMatrix)
        concatMatrix.getValues(matrixValues)
        return invertAndTransposeMatrix(concatMatrix, outArray)
    }

    /**
     * Calculates the difference in translation between two transformation matrices, represented as
     * FloatArrays (`centerCropMatrixValues` and `parallaxMatrixValues`), after scaling
     * `parallaxMatrixValues` to match the scale of `centerCropMatrixValues`. The resulting
     * translation difference is then stored in the provided `outArray` as a 3x3 translation matrix
     * (in column-major order).
     *
     * @param centerCropMatrixValues A FloatArray of length 9 representing the reference
     *   transformation matrix (center-cropped view) in row-major order.
     * @param parallaxMatrixValues A FloatArray of length 9 representing the transformation matrix
     *   whose translation difference relative to `centerCropMatrixValues` is to be calculated, also
     *   in row-major order. This array will be scaled to match the scale of
     *   `centerCropMatrixValues`.
     * @param outArray A FloatArray of length 9 to store the resulting 3x3 translation matrix. The
     *   translation components (deltaX, deltaY) will be placed in the appropriate positions for a
     *   column-major matrix.
     */
    fun calculateTranslationDifference(
        centerCropMatrixValues: FloatArray,
        parallaxMatrixValues: FloatArray,
        outArray: FloatArray,
    ): FloatArray {
        val scaleX = centerCropMatrixValues[0] / parallaxMatrixValues[0]
        val scaleY = centerCropMatrixValues[4] / parallaxMatrixValues[4]

        val scaledParallaxTransX = parallaxMatrixValues[2] * scaleX
        val scaledParallaxTransY = parallaxMatrixValues[5] * scaleY

        val originTransX = centerCropMatrixValues[2]
        val originTransY = centerCropMatrixValues[5]

        val deltaTransX = originTransX - scaledParallaxTransX
        val deltaTransY = originTransY - scaledParallaxTransY

        outArray[0] = 1f
        outArray[1] = 0f
        outArray[2] = 0f
        outArray[3] = 0f
        outArray[4] = 1f
        outArray[5] = 0f
        outArray[6] = deltaTransX
        outArray[7] = deltaTransY
        outArray[8] = 1f

        return outArray
    }

    // Transpose 3x3 matrix values as a FloatArray[9], write results to outArray
    private fun transposeMatrixArray(inMatrixArray: FloatArray, outArray: FloatArray): FloatArray {
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                outArray[j * 3 + i] = inMatrixArray[i * 3 + j]
            }
        }
        return outArray
    }
}
