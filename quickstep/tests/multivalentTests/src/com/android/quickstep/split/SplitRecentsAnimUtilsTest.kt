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
package com.android.quickstep.split

import android.app.WindowConfiguration
import android.graphics.Point
import android.graphics.Rect
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.SplitRecentsAnimUtils
import com.android.wm.shell.shared.TransitionUtil.TYPE_SPLIT_SCREEN_DIM_LAYER
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever

@RunWith(AndroidJUnit4::class)
class SplitRecentsAnimUtilsTest {

    private val mockDividerLeash = mock(SurfaceControl::class.java)
    private val mockDimLayerLeash = mock(SurfaceControl::class.java)
    private val mockOtherLeash = mock(SurfaceControl::class.java)

    @Before
    fun setup() {
        whenever(mockDividerLeash.isValid).thenReturn(true)
        whenever(mockDimLayerLeash.isValid).thenReturn(true)
        whenever(mockOtherLeash.isValid).thenReturn(true)
    }

    private fun createMockTarget(leash: SurfaceControl, windowType: Int): RemoteAnimationTarget {
        return RemoteAnimationTarget(
            -1, -1, leash, false, Rect(), Rect(), -1, Point(), Rect(), Rect(),
            mock(WindowConfiguration::class.java), false, mock(SurfaceControl::class.java),
            Rect(), null, false, windowType)
    }

    @Test
    fun testConstructor_filtersCorrectSurfaces() {
        // This test indirectly tests the constructor by verifying the behavior of the methods
        val dividerTarget = createMockTarget(
            mockDividerLeash,
            TYPE_DOCK_DIVIDER
        )
        val dimLayerTarget = createMockTarget(
            mockDimLayerLeash,
            TYPE_SPLIT_SCREEN_DIM_LAYER
        )
        val otherTarget = createMockTarget(mockOtherLeash, 1234)
        val nonApps = arrayOf(dividerTarget, dimLayerTarget, otherTarget)

        val utils = SplitRecentsAnimUtils(nonApps)

        // Verify that fade methods for divider and dim layer return animators
        assert(utils.fadeInDivider(immediate = false) != null)
        assert(utils.fadeInDimLayer(immediate = false) != null)

        // If we create utils with only "other" targets, animators should be null
        val utilsWithOther = SplitRecentsAnimUtils(arrayOf(otherTarget))
        assert(utilsWithOther.fadeInDivider(immediate = false) == null)
        assert(utilsWithOther.fadeInDimLayer(immediate = false) == null)
    }

    @Test
    fun testFadeInDivider_notImmediate() {
        val target = createMockTarget(
            mockDividerLeash,
            TYPE_DOCK_DIVIDER
        )
        val utils = SplitRecentsAnimUtils(arrayOf(target))
        val animator = spy(utils.fadeInDivider(immediate = false))

        verify(animator, never())?.start()
        verify(animator, never())?.end()
    }

    @Test
    fun testFadeOutDivider_notImmediate() {
        val target = createMockTarget(
            mockDividerLeash,
            TYPE_DOCK_DIVIDER
        )
        val utils = SplitRecentsAnimUtils(arrayOf(target))
        val animator = spy(utils.fadeOutDivider(immediate = false))

        verify(animator, never())?.start()
        verify(animator, never())?.end()
    }

    @Test
    fun testFadeInDimLayer_notImmediate() {
        val target = createMockTarget(
            mockDimLayerLeash,
            TYPE_SPLIT_SCREEN_DIM_LAYER
        )
        val utils = SplitRecentsAnimUtils(arrayOf(target))
        val animator = spy(utils.fadeInDimLayer(immediate = false))

        verify(animator, never())?.start()
        verify(animator, never())?.end()
    }

    @Test
    fun testFadeOutDimLayer_notImmediate() {
        val target = createMockTarget(
            mockDimLayerLeash,
            TYPE_SPLIT_SCREEN_DIM_LAYER
        )
        val utils = SplitRecentsAnimUtils(arrayOf(target))
        val animator = spy(utils.fadeOutDimLayer(immediate = false))

        verify(animator, never())?.start()
        verify(animator, never())?.end()
    }

    @Test
    fun testEmptyNonApps_returnsNull() {
        val utils = SplitRecentsAnimUtils(arrayOf())
        assert(utils.fadeInDivider(immediate = false) == null)
        assert(utils.fadeOutDivider(immediate = false) == null)
        assert(utils.fadeInDimLayer(immediate = false) == null)
        assert(utils.fadeOutDimLayer(immediate = false) == null)
    }

    @Test
    fun testInvalidLeash_isIgnored() {
        val mockInvalidLeash = mock(SurfaceControl::class.java)
        whenever(mockInvalidLeash.isValid).thenReturn(false)
        val target = createMockTarget(
            mockInvalidLeash,
            TYPE_DOCK_DIVIDER
        )
        val utils = SplitRecentsAnimUtils(arrayOf(target))
        assert(utils.fadeInDivider(immediate = false) == null)
    }
}
