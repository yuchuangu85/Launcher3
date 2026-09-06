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

/**
 * Interface to make sure that the Transform classes that implement it return a transform matrix.
 */
interface MatrixTransform {
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
    fun toMatrix(): FloatArray
}