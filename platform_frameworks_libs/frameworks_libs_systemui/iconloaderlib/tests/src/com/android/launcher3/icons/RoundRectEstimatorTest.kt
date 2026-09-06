/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.icons

import android.graphics.Path
import android.graphics.Path.Direction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoundRectEstimatorTest {

    @Test
    fun `estimateRadius circle`() {
        val r = 160f
        val path = Path().apply { addCircle(r, r, r, Direction.CW) }
        assertEquals(1f, RoundRectEstimator.estimateRadius(path, r * 2))
    }

    @Test
    fun `estimateRadius picks rounded rect 0_5`() {
        val factor = 0.5f
        val path = roundedRectPath(factor, 140f)
        assertEquals(0.5f, RoundRectEstimator.estimateRadius(path, 140f))
    }

    @Test
    fun `estimateRadius picks rounded rect 0_2`() {
        val factor = 0.2f
        val path = roundedRectPath(factor, 190f)
        assertEquals(0.2f, RoundRectEstimator.estimateRadius(path, 190f))
    }

    @Test
    fun `estimateRadius fails on generic shape`() {
        val path =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(50f, 50f)
                lineTo(0f, 50f)
                close()
            }
        assertEquals(-1f, RoundRectEstimator.estimateRadius(path, 50f))
    }

    private fun roundedRectPath(factor: Float, size: Float) =
        Path().apply {
            val r = factor * size / 2
            addRoundRect(0f, 0f, size, size, r, r, Direction.CW)
        }
}
