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
package com.android.quickstep

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution
import com.android.quickstep.util.TaskKeyCache
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class TaskThumbnailCacheTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val resource = mock<Resources>()

    private val context = mock<Context>().apply { whenever(resources).thenReturn(resource) }

    private val taskKeyCache = mock<TaskKeyCache<ThumbnailData>>()
    private val activityManagerWrapper = mock<ActivityManagerWrapper>()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    val systemUnderTest =
        TaskThumbnailCache(
            context,
            mock<Executor>(),
            taskKeyCache,
            testDispatcher,
            activityManagerWrapper,
        )

    @Before
    fun setUp() {
        whenever(resource.getIdentifier("config_lowResTaskSnapshotScale", "dimen", "android"))
            .thenReturn(LOW_RES_RESOURCE_ID)
        whenever(resource.getFloat(LOW_RES_RESOURCE_ID)).thenReturn(LOW_RES_SCALING)
    }

    @Test
    fun increaseCacheSize() {
        // Mock a cache size increase from 3 to 8
        whenever(taskKeyCache.getMaxSize()).thenReturn(3)
        whenever(resource.getInteger((R.integer.recentsThumbnailCacheSize))).thenReturn(8)

        // Preload is needed when increasing size
        assertThat(systemUnderTest.updateCacheSizeAndRemoveExcess()).isTrue()
        verify(taskKeyCache, times(1)).updateCacheSizeAndRemoveExcess(8)
    }

    @Test
    fun decreaseCacheSize() {
        // Mock a cache size decrease from 8 to 3
        whenever(taskKeyCache.getMaxSize()).thenReturn(8)
        whenever(resource.getInteger((R.integer.recentsThumbnailCacheSize))).thenReturn(3)

        // Preload is not needed when decreasing size
        assertThat(systemUnderTest.updateCacheSizeAndRemoveExcess()).isFalse()
        verify(taskKeyCache, times(1)).updateCacheSizeAndRemoveExcess(3)
    }

    @Test
    fun keepSameCacheSize() {
        whenever(taskKeyCache.getMaxSize()).thenReturn(3)
        whenever(resource.getInteger((R.integer.recentsThumbnailCacheSize))).thenReturn(3)

        // Preload is not needed when it has the same cache size
        assertThat(systemUnderTest.updateCacheSizeAndRemoveExcess()).isFalse()
        verify(taskKeyCache, never()).updateCacheSizeAndRemoveExcess(anyInt())
    }

    @Test
    fun getCacheSize_getsValueFromCache() {
        val expectedCacheSize = 10
        whenever(taskKeyCache.getMaxSize()).thenReturn(expectedCacheSize)

        assertThat(systemUnderTest.getCacheSize()).isEqualTo(expectedCacheSize)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @UiThreadTest
    fun getThumbnailInBackground_defaultsToRequestingLowRes() {
        val thumbnailData = ThumbnailData(thumbnail = mock(), reducedResolution = true)
        val task = Task(createTaskKey(TASK_ID))
        whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, true)).thenReturn(thumbnailData)

        val cancellableTask = systemUnderTest.getThumbnailInBackground(task) {}
        MAIN_EXECUTOR.execute(cancellableTask!!)

        verify(activityManagerWrapper).getTaskThumbnail(TASK_ID, true)
        verify(activityManagerWrapper, never()).takeTaskThumbnail(anyInt())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    @UiThreadTest
    fun getThumbnailInBackground_returnsLowResThumbnailIfInTaskObject() {
        val task = Task(createTaskKey(TASK_ID))
        val expectedTaskThumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = true)
        task.thumbnail = expectedTaskThumbnail

        var actualTaskThumbnail: ThumbnailData? = null
        systemUnderTest.getThumbnailInBackground(task) { actualTaskThumbnail = it }

        verify(activityManagerWrapper, never()).getTaskThumbnail(eq(TASK_ID), any())
        verify(activityManagerWrapper, never()).takeTaskThumbnail(anyInt())
        assertThat(actualTaskThumbnail).isEqualTo(expectedTaskThumbnail)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithCorrectRes_returnsTaskThumbnail_WhenHighResAvailable() =
        testScope.runTest {
            val task =
                Task(createTaskKey(TASK_ID)).apply {
                    thumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = false)
                }

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.ANY_RES))
                .isEqualTo(task.thumbnail)
            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.HIGH_RES))
                .isEqualTo(task.thumbnail)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithCorrectRes_returnsTaskThumbnail_WhenLowResAvailable() =
        testScope.runTest {
            val task =
                Task(createTaskKey(TASK_ID)).apply {
                    thumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = true)
                }

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.ANY_RES))
                .isEqualTo(task.thumbnail)
            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES))
                .isEqualTo(task.thumbnail)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithCorrectRes_returnsCachedThumbnail_WhenHighResAvailable() =
        testScope.runTest {
            val cachedThumbnailData = ThumbnailData(thumbnail = mock(), reducedResolution = false)
            val taskKey = createTaskKey(TASK_ID)
            whenever(taskKeyCache.getAndInvalidateIfModified(taskKey))
                .thenReturn(cachedThumbnailData)

            assertThat(systemUnderTest.getThumbnail(Task(taskKey), RequestResolution.ANY_RES))
                .isEqualTo(cachedThumbnailData)
            assertThat(systemUnderTest.getThumbnail(Task(taskKey), RequestResolution.HIGH_RES))
                .isEqualTo(cachedThumbnailData)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithCorrectRes_returnsCachedThumbnail_WhenLowResAvailable() =
        testScope.runTest {
            val cachedThumbnailData = ThumbnailData(thumbnail = mock(), reducedResolution = true)
            val taskKey = createTaskKey(TASK_ID)
            whenever(taskKeyCache.getAndInvalidateIfModified(taskKey))
                .thenReturn(cachedThumbnailData)

            assertThat(systemUnderTest.getThumbnail(Task(taskKey), RequestResolution.ANY_RES))
                .isEqualTo(cachedThumbnailData)
            assertThat(systemUnderTest.getThumbnail(Task(taskKey), RequestResolution.LOW_RES))
                .isEqualTo(cachedThumbnailData)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithLowRes_doesNotRequest_whenNoRequestSettingOnNoCachedValueAvailable() =
        testScope.runTest {
            val task = Task(createTaskKey(TASK_ID))
            val thumbnailData = ThumbnailData()

            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, true))
                .thenReturn(thumbnailData)

            assertThat(
                    systemUnderTest.getThumbnail(
                        task,
                        RequestResolution.LOW_RES,
                        shouldMakeRequestIfNeeded = false,
                    )
                )
                .isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithLowRes_requestsLowRes_whenNoCachedValueAvailable() =
        testScope.runTest {
            val task = Task(createTaskKey(TASK_ID))
            val thumbnailData = ThumbnailData()

            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, true))
                .thenReturn(thumbnailData)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES))
                .isEqualTo(thumbnailData)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithAnyRes_requestsLowRes_whenNoCachedValueAvailable() =
        testScope.runTest {
            val task = Task(createTaskKey(TASK_ID))
            val thumbnailData = ThumbnailData()

            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, true))
                .thenReturn(thumbnailData)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.ANY_RES))
                .isEqualTo(thumbnailData)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithHighRes_requestsHighRes_whenNoCachedValueAvailable() =
        testScope.runTest {
            val task = Task(createTaskKey(TASK_ID))
            val thumbnailData = ThumbnailData()

            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, false))
                .thenReturn(thumbnailData)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.HIGH_RES))
                .isEqualTo(thumbnailData)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithHighRes_requestsHighRes_whenLowResAvailable() =
        testScope.runTest {
            val highResThumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = false)
            val task =
                Task(createTaskKey(TASK_ID)).apply {
                    thumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = true)
                }

            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, false))
                .thenReturn(highResThumbnail)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.HIGH_RES))
                .isEqualTo(highResThumbnail)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithLowRes_requestsLowRes_whenHighResAvailable() =
        testScope.runTest {
            val lowResThumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = true)
            val task =
                Task(createTaskKey(TASK_ID)).apply {
                    thumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = false)
                }

            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, true))
                .thenReturn(lowResThumbnail)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES))
                .isEqualTo(lowResThumbnail)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithHighRes_requestsHighRes_whenLowResAvailableInCache() =
        testScope.runTest {
            val highResThumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = false)
            val taskKey = createTaskKey(TASK_ID)
            val task = Task(taskKey)
            val cachedThumbnailData = ThumbnailData(thumbnail = mock(), reducedResolution = true)

            whenever(taskKeyCache.getAndInvalidateIfModified(taskKey))
                .thenReturn(cachedThumbnailData)
            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, false))
                .thenReturn(highResThumbnail)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.HIGH_RES))
                .isEqualTo(highResThumbnail)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithLowRes_requestsLowRes_whenHighResAvailableInCache() =
        testScope.runTest {
            val lowResThumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = true)
            val taskKey = createTaskKey(TASK_ID)
            val task = Task(taskKey)
            val cachedThumbnailData = ThumbnailData(thumbnail = mock(), reducedResolution = false)

            whenever(taskKeyCache.getAndInvalidateIfModified(taskKey))
                .thenReturn(cachedThumbnailData)
            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, true))
                .thenReturn(lowResThumbnail)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES))
                .isEqualTo(lowResThumbnail)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithLowRes_returnsHighRes_whenHighResAvailableInTaskAndLowResUnsupported() =
        testScope.runTest {
            setLowResUnsupported()

            val task =
                Task(createTaskKey(TASK_ID)).apply {
                    thumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = false)
                }

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES))
                .isEqualTo(task.thumbnail)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithLowRes_returnsHighRes_whenHighResAvailableInCacheAndLowResUnsupported() =
        testScope.runTest {
            setLowResUnsupported()

            val taskKey = createTaskKey(TASK_ID)
            val task = Task(taskKey)
            val cachedThumbnailData = ThumbnailData(thumbnail = mock(), reducedResolution = false)
            whenever(taskKeyCache.getAndInvalidateIfModified(taskKey))
                .thenReturn(cachedThumbnailData)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES))
                .isEqualTo(cachedThumbnailData)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithLowRes_requestsHighRes_whenNoCachedValueAvailableAndLowResUnsupported() =
        testScope.runTest {
            setLowResUnsupported()

            val highResThumbnail = ThumbnailData(thumbnail = mock(), reducedResolution = false)
            val task = Task(createTaskKey(TASK_ID))

            whenever(activityManagerWrapper.getTaskThumbnail(TASK_ID, false))
                .thenReturn(highResThumbnail)

            assertThat(systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES))
                .isEqualTo(highResThumbnail)
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithRequestResolution_throwsException_whenFlagIsOff() =
        testScope.runTest {
            val task = Task(createTaskKey(TASK_ID))

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { systemUnderTest.getThumbnail(task, RequestResolution.ANY_RES) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { systemUnderTest.getThumbnail(task, RequestResolution.HIGH_RES) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { systemUnderTest.getThumbnail(task, RequestResolution.LOW_RES) }
            }
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_RES_THUMBNAIL_PRELOADING)
    fun getThumbnailWithoutRequestResolution_throwsException_whenFlagIsOn() =
        testScope.runTest {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { systemUnderTest.getThumbnail(Task(createTaskKey(TASK_ID))) }
            }
        }

    private fun createTaskKey(id: Int = 1) =
        TaskKey(id, 0, Intent().setPackage(""), ComponentName("", ""), 0, 0)

    private fun setLowResUnsupported() {
        whenever(resource.getIdentifier("config_lowResTaskSnapshotScale", "dimen", "android"))
            .thenReturn(Resources.ID_NULL)
        whenever(resource.getFloat(Resources.ID_NULL))
            .thenThrow(IllegalArgumentException("Cannot get unknown value"))
        whenever(resource.getFloat(LOW_RES_RESOURCE_ID))
            .thenThrow(IllegalArgumentException("Cannot get unknown value"))
    }

    private companion object {
        const val TASK_ID = 1
        const val LOW_RES_RESOURCE_ID = 0x1234
        const val LOW_RES_SCALING = 0.5f
    }
}
