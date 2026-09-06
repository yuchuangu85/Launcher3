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

package com.android.quickstep.recents.domain.usecase

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UpdateThumbnailCacheSizeUseCaseTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(unconfinedTestDispatcher)
    private val preloadThumbnailUseCase = mock<PreloadThumbnailUseCase>()
    private val taskThumbnailDataSource = mock<TaskThumbnailDataSource>()

    val systemUnderTest =
        UpdateThumbnailCacheSizeUseCase(
            preloadThumbnailUseCase = preloadThumbnailUseCase,
            taskThumbnailDataSource = taskThumbnailDataSource,
            recentsCoroutineScope = testScope,
            lightweightBackgroundDispatcher = unconfinedTestDispatcher,
        )

    @DisableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun updateCacheSize_doesNothing_whenFlagOff() =
        testScope.runTest {
            systemUnderTest.updateCacheSize(shouldPreloadIfNeeded = true)

            verify(taskThumbnailDataSource, never()).updateCacheSizeAndRemoveExcess()
            verify(preloadThumbnailUseCase, never()).preloadThumbnails()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun updateCacheSize_doesNotPreload_whenFlagOnAndCacheSizeUnchanged() =
        testScope.runTest {
            whenever(taskThumbnailDataSource.updateCacheSizeAndRemoveExcess()).thenReturn(false)
            systemUnderTest.updateCacheSize(shouldPreloadIfNeeded = true)

            verify(preloadThumbnailUseCase, never()).preloadThumbnails()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun updateCacheSize_doesNotPreload_whenFlagOnAndCacheSizeChanged_withoutPreloading() =
        testScope.runTest {
            whenever(taskThumbnailDataSource.updateCacheSizeAndRemoveExcess()).thenReturn(true)
            systemUnderTest.updateCacheSize(shouldPreloadIfNeeded = false)

            verify(preloadThumbnailUseCase, never()).preloadThumbnails()
        }

    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @Test
    fun updateCacheSize_preloads_whenFlagOnAndCacheSizeChanged() =
        testScope.runTest {
            whenever(taskThumbnailDataSource.updateCacheSizeAndRemoveExcess()).thenReturn(true)
            systemUnderTest.updateCacheSize(shouldPreloadIfNeeded = true)

            verify(preloadThumbnailUseCase).preloadThumbnails()
        }
}
