/**
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.icons

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LuminanceComputerTest {

    @Test
    fun computeLuminance_solidColor_average_hsl() {
        val color = Color.RED // R=255, G=0, B=0
        val width = 2
        val height = 2

        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE, //
                colorSpace = LuminanceColorSpace.HSL,
            )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // Calculate expected HSL luminance (L component) for red
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val expectedLuminance = hsl[2].toDouble()

        val actualLuminance = computer.computeLuminance(bitmap, scale = false)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_solidColor_median_hsl() {
        val color = Color.GREEN // R=0, G=255, B=0
        val width = 3
        val height = 3

        val computer =
            LuminanceComputer(
                computationType = ComputationType.MEDIAN,
                colorSpace = LuminanceColorSpace.HSL,
            )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // Calculate expected HSL luminance (L component) for green
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val expectedLuminance = hsl[2].toDouble()

        val actualLuminance = computer.computeLuminance(bitmap, scale = false)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_solidColor_average_hsl_with_scale() {
        val color = Color.RED // R=255, G=0, B=0
        val width = 2
        val height = 2

        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE,
                colorSpace = LuminanceColorSpace.HSL,
            )

        // Create a real solid color bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // Calculate expected HSL luminance (L component) for red
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val expectedLuminance = hsl[2].toDouble()

        // Call computeLuminance with scale = true
        val actualLuminance = computer.computeLuminance(bitmap, scale = true)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_solidColor_median_hsl_with_scale() {
        val color = Color.GREEN // R=0, G=255, B=0
        val width = 3
        val height = 3

        val computer =
            LuminanceComputer(
                computationType = ComputationType.MEDIAN,
                colorSpace = LuminanceColorSpace.HSL,
            )

        // Create a real solid color bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // Calculate expected HSL luminance (L component) for green
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val expectedLuminance = hsl[2].toDouble()

        // Call computeLuminance with scale = true
        val actualLuminance = computer.computeLuminance(bitmap, scale = true)
        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_solidColor_average_lab() {
        val color = Color.BLUE // R=0, G=0, B=255
        val width = 4
        val height = 4

        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE,
                colorSpace = LuminanceColorSpace.LAB,
            )

        // Create a real solid color bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // Calculate expected LAB luminance (L component) for blue
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color, lab)
        val expectedLuminance = lab[0].toDouble() / 100.0 // LAB L is 0-100, convert to 0-1

        // Call computeLuminance with scale = true
        val actualLuminance = computer.computeLuminance(bitmap, scale = false)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_solidColor_median_lab() {
        val color = Color.YELLOW // R=255, G=255, B=0
        val width = 5
        val height = 5

        val computer =
            LuminanceComputer(
                computationType = ComputationType.MEDIAN,
                colorSpace = LuminanceColorSpace.LAB,
            )

        // Create a real solid color bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // Calculate expected LAB luminance (L component) for yellow
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color, lab)
        val expectedLuminance = lab[0].toDouble() / 100.0

        // Call computeLuminance with scale = true
        val actualLuminance = computer.computeLuminance(bitmap, scale = false)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_mixedColors_average_hsl() {
        val width = 2 // Use a small 2x2 real bitmap
        val height = 2

        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE,
                colorSpace = LuminanceColorSpace.HSL,
            )

        val color1 = Color.RED
        val color2 = Color.GREEN
        val color3 = Color.BLUE
        val color4 = Color.YELLOW

        // Create a real 2x2 bitmap with mixed colors
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, color1)
        bitmap.setPixel(1, 0, color2)
        bitmap.setPixel(0, 1, color3)
        bitmap.setPixel(1, 1, color4)

        val hsl1 = FloatArray(3).also { ColorUtils.colorToHSL(color1, it) }
        val hsl2 = FloatArray(3).also { ColorUtils.colorToHSL(color2, it) }
        val hsl3 = FloatArray(3).also { ColorUtils.colorToHSL(color3, it) }
        val hsl4 = FloatArray(3).also { ColorUtils.colorToHSL(color4, it) }
        val expectedLuminance =
            (hsl1[2] + hsl2[2] + hsl3[2] + hsl4[2]).toDouble() / (width * height)
        // Call computeLuminance with scale = true
        val actualLuminance = computer.computeLuminance(bitmap, scale = true)
        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_mixedColors_median_hsl() {
        val width = 2 // Use a small 2x2 real bitmap
        val height = 2

        val computer =
            LuminanceComputer(
                computationType = ComputationType.MEDIAN,
                colorSpace = LuminanceColorSpace.HSL,
                options = LuminanceComputer.Options(),
            )

        val color1 = Color.RED
        val color2 = Color.GREEN
        val color3 = Color.BLUE
        val color4 = Color.YELLOW

        // Create a real 2x2 bitmap with mixed colors
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, color1)
        bitmap.setPixel(1, 0, color2)
        bitmap.setPixel(0, 1, color3)
        bitmap.setPixel(1, 1, color4)

        val hsl1 = FloatArray(3).also { ColorUtils.colorToHSL(color1, it) }
        val hsl2 = FloatArray(3).also { ColorUtils.colorToHSL(color2, it) }
        val hsl3 = FloatArray(3).also { ColorUtils.colorToHSL(color3, it) }
        val hsl4 = FloatArray(3).also { ColorUtils.colorToHSL(color4, it) }

        // Calculate expected median HSL luminance
        val luminances =
            listOf(hsl1[2].toDouble(), hsl2[2].toDouble(), hsl3[2].toDouble(), hsl4[2].toDouble())
                .sorted()

        val expectedLuminance = (luminances[1] + luminances[2]) / 2.0 // Median for 4 values

        // Call computeLuminance with scale = true
        val actualLuminance = computer.computeLuminance(bitmap, scale = true)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

      @Test
    fun computeLuminance_solidColor_spread_hsl() {
        val color = Color.BLUE // R=0, G=0, B=255
        val width = 4
        val height = 4

        val computer =
            LuminanceComputer(
                computationType = ComputationType.SPREAD,
                colorSpace = LuminanceColorSpace.HSL,
            )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // For a solid color, the spread should be 0
        val expectedLuminance = 0.0

        val actualLuminance = computer.computeLuminance(bitmap, scale = false)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_solidColor_spread_lab() {
        val color = Color.YELLOW // R=255, G=255, B=0
        val width = 5
        val height = 5

        val computer =
            LuminanceComputer(
                computationType = ComputationType.SPREAD,
                colorSpace = LuminanceColorSpace.LAB,
            )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, color)
            }
        }

        // For a solid color, the spread should be 0
        val expectedLuminance = 0.0

        val actualLuminance = computer.computeLuminance(bitmap, scale = false)

        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_mixedColors_spread_hsl() {
        val width = 2 // Use a small 2x2 real bitmap
        val height = 2

        val computer =
            LuminanceComputer(
                computationType = ComputationType.SPREAD,
                colorSpace = LuminanceColorSpace.HSL,
            )

        val color1 = Color.RED
        val color2 = Color.GREEN
        val color3 = Color.BLUE
        val color4 = Color.YELLOW

        // Create a real 2x2 bitmap with mixed colors
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, color1)
        bitmap.setPixel(1, 0, color2)
        bitmap.setPixel(0, 1, color3)
        bitmap.setPixel(1, 1, color4)

        // Calculate expected spread HSL luminance by processing the bitmap like the computeLuminance method
        val bitmapToProcess =
            Bitmap.createScaledBitmap(bitmap, LuminanceComputer.BITMAP_SAMPLE_SIZE, LuminanceComputer.BITMAP_SAMPLE_SIZE, true)

        val processedWidth = bitmapToProcess.width
        val processedHeight = bitmapToProcess.height
        val pixels = IntArray(processedWidth * processedHeight)
        bitmapToProcess.getPixels(pixels, 0, processedWidth, 0, 0, processedWidth, processedHeight)

        val luminances = pixels.map {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(it, hsl)
            hsl[2].toDouble()
        }

        val expectedLuminance = luminances.max() - luminances.min()

        val actualLuminance = computer.computeLuminance(bitmap, scale = true)
        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun computeLuminance_mixedColors_spread_lab() {
        val width = 2 // Use a small 2x2 real bitmap
        val height = 2

        val computer =
            LuminanceComputer(
                computationType = ComputationType.SPREAD,
                colorSpace = LuminanceColorSpace.LAB,
            )

        val color1 = Color.RED
        val color2 = Color.GREEN
        val color3 = Color.BLUE
        val color4 = Color.YELLOW

        // Create a real 2x2 bitmap with mixed colors
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, color1)
        bitmap.setPixel(1, 0, color2)
        bitmap.setPixel(0, 1, color3)
        bitmap.setPixel(1, 1, color4)

        // Calculate expected spread LAB luminance (L component, scaled to 0-1) by processing the bitmap
        val bitmapToProcess =
            Bitmap.createScaledBitmap(bitmap, LuminanceComputer.BITMAP_SAMPLE_SIZE, LuminanceComputer.BITMAP_SAMPLE_SIZE, true)

        val processedWidth = bitmapToProcess.width
        val processedHeight = bitmapToProcess.height
        val pixels = IntArray(processedWidth * processedHeight)
        bitmapToProcess.getPixels(pixels, 0, processedWidth, 0, 0, processedWidth, processedHeight)

        val luminances = pixels.map {
            val lab = DoubleArray(3)
            ColorUtils.colorToLAB(it, lab)
            lab[0].toDouble() / 100.0 // LAB L is 0-100, convert to 0-1
        }

        val expectedLuminance = luminances.max() - luminances.min()

        val actualLuminance = computer.computeLuminance(bitmap, scale = true)
        assertEquals(expectedLuminance, actualLuminance, TOLERANCE)
    }

    @Test
    fun adaptColorLuminance_basic() {
        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE,
                colorSpace = LuminanceColorSpace.HSL,
            )
        val targetColor = Color.GRAY // HSL L ~ 0.5
        val basisColor = Color.BLACK // HSL L = 0
        val luminanceDelta = 0.3
        val minimumContrast = 0.0

        val adaptedColor =
            computer.adaptColorLuminance(targetColor, basisColor, luminanceDelta, minimumContrast)

        val adaptedHsl = FloatArray(3)
        ColorUtils.colorToHSL(adaptedColor, adaptedHsl)

        // Expected luminance should be basisLuminance + luminanceDelta = 0 + 0.3 = 0.3
        assertEquals(0.3, adaptedHsl[2].toDouble(), TOLERANCE)
    }

    @Test
    fun adaptColorLuminance_withContrastAdjustment_meetsMinimumContrast() {
        val options =
            LuminanceComputer.Options(ensureMinContrast = true, absoluteLuminanceDelta = false)
        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE,
                colorSpace = LuminanceColorSpace.HSL,
                options = options,
            )
        val targetColor = Color.GRAY // HSL L ~ 0.5
        val basisColor = Color.BLACK // HSL L = 0
        val luminanceDelta = 0.1 // Small delta
        val minimumContrast = 2.0 // High minimum contrast

        val adaptedColor =
            computer.adaptColorLuminance(targetColor, basisColor, luminanceDelta, minimumContrast)

        val adaptedHsl = FloatArray(3)
        ColorUtils.colorToHSL(adaptedColor, adaptedHsl)
        val adaptedLuminance = adaptedHsl[2].toDouble()

        // Expected luminance should be basisLuminance + (luminanceDelta * minimumContrast)
        // 0 + (0.1 * 2.0) = 0.2
        assertEquals(0.2, adaptedLuminance, TOLERANCE)
    }

    @Test
    fun adaptColorLuminance_withContrastAdjustment_alreadyMeetsMinimumContrast() {
        val options =
            LuminanceComputer.Options(ensureMinContrast = true, absoluteLuminanceDelta = false)

        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE,
                colorSpace = LuminanceColorSpace.HSL,
                options = options,
            )
        val targetColor = Color.WHITE // HSL L = 1.0
        val basisColor = Color.BLACK // HSL L = 0.0
        val luminanceDelta = 0.5
        val minimumContrast = 0.1 // Low minimum contrast

        val adaptedColor =
            computer.adaptColorLuminance(targetColor, basisColor, luminanceDelta, minimumContrast)

        val adaptedHsl = FloatArray(3)
        ColorUtils.colorToHSL(adaptedColor, adaptedHsl)
        val adaptedLuminance = adaptedHsl[2].toDouble()

        // Expected luminance should be basisLuminance + luminanceDelta = 0 + 0.5 = 0.5
        // Since the original contrast (infinite) is already higher than minimumContrast,
        // the contrast adjustment should not change the luminance calculated from delta.
        assertEquals(0.5, adaptedLuminance, TOLERANCE)
    }

    @Test
    fun adaptColorLuminance_withAbsoluteLuminanceDelta() {
        val options =
            LuminanceComputer.Options(ensureMinContrast = false, absoluteLuminanceDelta = true)
        val computer =
            LuminanceComputer(
                computationType = ComputationType.AVERAGE,
                colorSpace = LuminanceColorSpace.HSL,
                options = options,
            )
        val targetColor = Color.GRAY // HSL L ~ 0.5
        val basisColor = Color.WHITE // HSL L = 1.0
        val luminanceDelta = -0.3 // Negative delta

        val adaptedColor =
            computer.adaptColorLuminance(targetColor, basisColor, luminanceDelta, 0.0)

        val adaptedHsl = FloatArray(3)
        ColorUtils.colorToHSL(adaptedColor, adaptedHsl)
        val adaptedLuminance = adaptedHsl[2].toDouble()

        // Expected luminance should be basisLuminance + abs(luminanceDelta) = 1.0 + abs(-0.3) = 1.3
        // But it should be clamped to 1.0
        assertEquals(1.0, adaptedLuminance, TOLERANCE)
    }

    @Test
    fun adaptColorLuminance_nanLuminanceDelta() {
        val computer = LuminanceComputer(LuminanceColorSpace.HSL, ComputationType.AVERAGE)
        val targetColor = Color.RED
        val basisColor = Color.BLUE
        val luminanceDelta = Double.NaN
        val minimumContrast = 0.0

        val adaptedColor =
            computer.adaptColorLuminance(targetColor, basisColor, luminanceDelta, minimumContrast)

        assertEquals(targetColor, adaptedColor)
    }

    private companion object {
        // Tolerance for floating point comparisons
        const val TOLERANCE = 0.08
    }
}
