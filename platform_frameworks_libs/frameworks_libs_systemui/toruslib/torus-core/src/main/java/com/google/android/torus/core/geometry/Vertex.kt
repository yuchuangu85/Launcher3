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

package com.google.android.torus.core.geometry

import java.nio.ByteBuffer

/**
 * Defines the information a vertex, as an array of different numbers.
 */
class Vertex(vararg input: Number) {
    private val vertexValues: ArrayList<Number> = ArrayList()

    init {
        for (item in input) {
            vertexValues.add(item)
        }
    }

    /**
     * Function that will help to add each vertex into a ByteBuffer.
     * @param buffer The [ByteBuffer] where we are adding the current vertex.
     */
    fun putInto(buffer: ByteBuffer) {
        for (vertexValue in vertexValues) {
            when (vertexValue) {
                is Float -> buffer.putFloat(vertexValue.toFloat())
                is Int -> buffer.putInt(vertexValue.toInt())
                is Short -> buffer.putShort(vertexValue.toShort())
            }
        }
    }
}