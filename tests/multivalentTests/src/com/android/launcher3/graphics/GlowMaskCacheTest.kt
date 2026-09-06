/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.graphics

import android.graphics.Bitmap
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.automation.AutomationChange
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.icons.IconShape
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.MutableDiffAwareRef
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class GlowMaskCacheTest {

    private val automationRepo = mock(AutomationRepository::class.java)
    private val lifeCycleTracker = mock(DaggerSingletonTracker::class.java)
    private val automationRef =
        MutableDiffAwareRef<Set<PackageUserKey>, AutomationChange>(emptySet())
    private lateinit var cache: GlowMaskCache

    @Before
    fun setUp() {
        doReturn(automationRef.asListenable()).whenever(automationRepo).automatedPackages

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            cache = GlowMaskCache(MAIN_EXECUTOR, MAIN_EXECUTOR, automationRepo, lifeCycleTracker)
        }
    }

    @Test
    fun getMasks_returnsGlowMasks() {
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val shape = createSquareShape(100)
            val future = cache.getMasks(shape)
            val result = future.get()

            assertThat(result).isNotNull()
            assertThat(result.outerMask).isNotNull()
            assertThat(result.innerMask).isNotNull()
            assertThat(result.silhouetteMask).isNotNull()
            assertThat(result.paddingOffset).isGreaterThan(0f)
        }
    }

    @Test
    fun getMasks_cachesResult() {
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val shape = createSquareShape(100)
            val shapeMasks1 = cache.getMasks(shape)
            val shapeMasks2 = cache.getMasks(shape)

            assertThat(shapeMasks1).isSameInstanceAs(shapeMasks2)
        }
    }

    @Test
    fun automationInactive_clearsCache() {
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val shape = createSquareShape(100)
            val shapeMasks1 = cache.getMasks(shape)

            automationRef.dispatchValue(emptySet(), mock(AutomationChange::class.java))

            val shapeMasks2 = cache.getMasks(shape)
            assertThat(shapeMasks1).isNotSameInstanceAs(shapeMasks2)
        }
    }

    @Test
    fun clear_cancelsPendingFutures() {
        // Use a mock executor that doesn't run the task to keep the future pending.
        val mockBgExecutor = mock(java.util.concurrent.Executor::class.java)
        val testCache =
            GlowMaskCache(MAIN_EXECUTOR, mockBgExecutor, automationRepo, lifeCycleTracker)

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {
            val shape = createSquareShape(100)
            val future = testCache.getMasks(shape)

            testCache.clear()

            assertThat(future.isCancelled).isTrue()
        }
    }

    private fun createSquareShape(size: Int): IconShape {
        val path =
            Path().apply { addRect(0f, 0f, size.toFloat(), size.toFloat(), Path.Direction.CW) }
        val shadowLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return IconShape(size, path, shadowLayer)
    }
}
