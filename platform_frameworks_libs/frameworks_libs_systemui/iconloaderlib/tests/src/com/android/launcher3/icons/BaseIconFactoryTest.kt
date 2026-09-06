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

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseIconFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fullBleed_has_no_alpha() {
        val info =
            factory(drawFullBleedIcons = true)
                .createBadgedIconBitmap(AdaptiveIconDrawable(ColorDrawable(Color.RED), null))

        assertFalse(info.icon.hasAlpha())
        assertEquals(BitmapInfo.FLAG_FULL_BLEED, info.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    @Test
    fun non_fullBleed_has_alpha() {
        val info =
            factory(drawFullBleedIcons = false)
                .createBadgedIconBitmap(AdaptiveIconDrawable(ColorDrawable(Color.RED), null))
        assertTrue(info.icon.hasAlpha())
        assertEquals(0, info.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    @Test
    fun icon_options_overrides_fullBleed() {
        val info =
            factory(drawFullBleedIcons = false)
                .createBadgedIconBitmap(
                    AdaptiveIconDrawable(ColorDrawable(Color.RED), null),
                    IconOptions().setDrawFullBleed(true),
                )
        assertFalse(info.icon.hasAlpha())
        assertEquals(BitmapInfo.FLAG_FULL_BLEED, info.flags and BitmapInfo.FLAG_FULL_BLEED)

        val info2 =
            factory(drawFullBleedIcons = true)
                .createBadgedIconBitmap(
                    AdaptiveIconDrawable(ColorDrawable(Color.RED), null),
                    IconOptions().setDrawFullBleed(false),
                )
        assertTrue(info2.icon.hasAlpha())
        assertEquals(0, info2.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    private fun factory(
        fullResIconDpi: Int = context.resources.displayMetrics.densityDpi,
        iconBitmapSize: Int = 64,
        drawFullBleedIcons: Boolean = false,
        themeController: IconThemeController? = null,
    ) =
        BaseIconFactory(
            context = context,
            fullResIconDpi = fullResIconDpi,
            iconBitmapSize = iconBitmapSize,
            drawFullBleedIcons = drawFullBleedIcons,
            themeController = themeController,
        )
}
