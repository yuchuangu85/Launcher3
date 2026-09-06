/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.orientation

import android.platform.test.flag.junit.SetFlagsRule
import android.view.Gravity
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.orientation.LandscapePagedViewHandler.SplitIconPositions
import com.android.quickstep.views.IconAppChipView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class SeascapePagedViewHandlerTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val sut = SeascapePagedViewHandler()

    /** [ Test getSplitIconsPosition ] */
    private fun getSplitIconsPosition(isRTL: Boolean): SplitIconPositions =
        sut.getSplitIconsPosition(PRIMARY_SNAPSHOT, TOTAL_THUMBNAIL_HEIGHT, isRTL, DIVIDER_SIZE_PX)

    @Test
    fun testChip_getSplitIconsPositions() {
        val (topLeftY, bottomRightY) = getSplitIconsPosition(isRTL = false)

        // Top-Left app chip should always be at the initial position of the first snapshot
        assertThat(topLeftY).isEqualTo(0)
        // Bottom-Right app chip should be at the end of the primary height + divider
        assertThat(bottomRightY).isEqualTo(-266)
    }

    @Test
    fun testChip_getSplitIconsPositions_isRTL() {
        val (topLeftY, bottomRightY) = getSplitIconsPosition(isRTL = true)

        assertThat(topLeftY).isEqualTo(316)
        assertThat(bottomRightY).isEqualTo(0)
    }

    @Test
    fun testChip_updateSplitIconsPosition() {
        val expectedTranslationY = 250
        val frameLayout = FrameLayout.LayoutParams(100, 100)
        val iconView = mock<IconAppChipView>()
        `when`(iconView.layoutParams).thenReturn(frameLayout)

        sut.updateSplitIconsPosition(iconView, expectedTranslationY, false)
        assertThat(frameLayout.gravity).isEqualTo(Gravity.BOTTOM or Gravity.END)
        verify(iconView).setSplitTranslationX(0f)
        verify(iconView).setSplitTranslationY(expectedTranslationY.toFloat())
    }

    @Test
    fun testChip_updateSplitIconsPosition_isRTL() {
        val expectedTranslationY = 250
        val frameLayout = FrameLayout.LayoutParams(100, 100)
        val iconView = mock<IconAppChipView>()
        `when`(iconView.layoutParams).thenReturn(frameLayout)

        sut.updateSplitIconsPosition(iconView, expectedTranslationY, true)
        assertThat(frameLayout.gravity).isEqualTo(Gravity.TOP or Gravity.START)
        verify(iconView).setSplitTranslationX(0f)
        verify(iconView).setSplitTranslationY(expectedTranslationY.toFloat())
    }

    private companion object {
        const val DIVIDER_SIZE_PX = 16
        const val PRIMARY_SNAPSHOT = 250
        const val SECONDARY_SNAPSHOT = 300
        const val TOTAL_THUMBNAIL_HEIGHT = PRIMARY_SNAPSHOT + SECONDARY_SNAPSHOT + DIVIDER_SIZE_PX
    }
}
