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

package com.android.quickstep.views

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.quickstep.recents.domain.usecase.PreloadThumbnailUseCase
import com.android.quickstep.recents.domain.usecase.UpdateThumbnailCacheSizeUseCase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class RecentsViewModelHelperTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(unconfinedTestDispatcher)
    private val preloadThumbnailUseCase = mock<PreloadThumbnailUseCase>()
    private val updateThumbnailCacheSizeUseCase = mock<UpdateThumbnailCacheSizeUseCase>()

    val systemUnderTest =
        RecentsViewModelHelper(
            recentsViewModel = mock(),
            recentsCoroutineScope = testScope,
            lightweightBackgroundDispatcher = unconfinedTestDispatcher,
            mainDispatcher = unconfinedTestDispatcher,
            preloadThumbnailUseCase = preloadThumbnailUseCase,
            updateThumbnailCacheSizeUseCase = updateThumbnailCacheSizeUseCase,
        )

    @DisableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun startPreloading_doesNothing_whenFlagOff() =
        testScope.runTest {
            systemUnderTest.startPreloading()

            verify(preloadThumbnailUseCase, never()).preloadThumbnails()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun startPreloading_preloads_whenFlagOn() =
        testScope.runTest {
            systemUnderTest.startPreloading()

            verify(preloadThumbnailUseCase).preloadThumbnails()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun updateCacheSizeAndPreload_updates_withPreloading() =
        testScope.runTest {
            systemUnderTest.updateCacheSizeAndPreload(shouldPreloadIfNeeded = true)

            verify(updateThumbnailCacheSizeUseCase).updateCacheSize(shouldPreloadIfNeeded = true)
        }

    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun updateCacheSizeAndPreload_updates_noPreloading() =
        testScope.runTest {
            systemUnderTest.updateCacheSizeAndPreload(shouldPreloadIfNeeded = false)

            verify(updateThumbnailCacheSizeUseCase).updateCacheSize(shouldPreloadIfNeeded = false)
        }
}
