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

package com.android.launcher3.pageindicators

import android.platform.uiautomatorhelpers.DeviceHelpers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageIndicatorDotsTest {

    private val pageIndicatorDots: PageIndicatorDots = PageIndicatorDots(DeviceHelpers.context)

    @Test
    fun `setActiveMarker should set the active page to the parameter passed`() {
        pageIndicatorDots.setActiveMarker(2)

        assertThat(pageIndicatorDots.activePage).isEqualTo(2)
    }

    @Test
    fun `setActiveMarker should set the active page to the parameter passed divided by two in two panel layouts`() {
        pageIndicatorDots.mIsTwoPanels = true

        pageIndicatorDots.setActiveMarker(5)

        assertThat(pageIndicatorDots.activePage).isEqualTo(2)
    }

    @Test
    fun `setMarkersCount should set the number of pages to the passed parameter and if the last page gets removed we want to go to the previous page`() {
        pageIndicatorDots.setMarkersCount(3)

        assertThat(pageIndicatorDots.numPages).isEqualTo(3)
    }

    @Test
    fun `for setMarkersCount if the last page gets removed we want to go to the previous page`() {
        pageIndicatorDots.setActiveMarker(2)

        pageIndicatorDots.setMarkersCount(2)

        assertThat(pageIndicatorDots.activePage).isEqualTo(1)
        assertThat(pageIndicatorDots.currentPosition)
            .isEqualTo(pageIndicatorDots.activePage.toFloat())
    }
}
