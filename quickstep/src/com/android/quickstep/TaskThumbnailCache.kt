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
package com.android.quickstep

import android.content.Context
import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.Flags.enableLowResThumbnailPreloading
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Preconditions
import com.android.quickstep.TaskIconCache.Companion.TASK_IMAGE_CACHE_EXECUTOR
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution.ANY_RES
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution.HIGH_RES
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution.LOW_RES
import com.android.quickstep.util.TaskKeyByLastActiveTimeCache
import com.android.quickstep.util.TaskKeyCache
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.ActivityManagerWrapper
import java.util.concurrent.Executor
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class TaskThumbnailCache
@VisibleForTesting
constructor(
    private val context: Context,
    private val bgExecutor: Executor,
    private val cache: TaskKeyCache<ThumbnailData>,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val activityManagerWrapper: ActivityManagerWrapper,
) : TaskThumbnailDataSource {
    val highResLoadingState = HighResLoadingState()
    private val enableTaskSnapshotPreloading =
        context.resources.getBoolean(R.bool.config_enableTaskSnapshotPreloading)

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Background backgroundDispatcher: CoroutineDispatcher,
        activityManagerWrapper: ActivityManagerWrapper,
    ) : this(
        context,
        TASK_IMAGE_CACHE_EXECUTOR,
        TaskKeyByLastActiveTimeCache(
            context.resources.getInteger(R.integer.recentsThumbnailCacheSize)
        ),
        backgroundDispatcher,
        activityManagerWrapper,
    )

    /**
     * Synchronously fetches the thumbnail for the given task at the specified resolution level, and
     * puts it in the cache.
     */
    fun updateThumbnailInCache(task: Task?, lowResolution: Boolean) {
        task ?: return

        Preconditions.assertUIThread()
        // Fetch the thumbnail for this task and put it in the cache
        if (task.thumbnail == null) {
            getThumbnailInBackground(task.key, lowResolution) { t: ThumbnailData? ->
                task.thumbnail = t
            }
        }
    }

    /** Synchronously updates the thumbnail in the cache if it is already there. */
    fun updateTaskSnapShot(taskId: Int, thumbnail: ThumbnailData?) {
        Preconditions.assertUIThread()
        cache.updateIfAlreadyInCache(taskId, thumbnail)
    }

    /**
     * Retrieves a thumbnail for the provided `task` on the current thread. This should not be
     * called from the main thread.
     */
    @Deprecated(
        "Should be removed with flag: enable_low_res_thumbnail_preloading." +
            " Specify request resolution as 2nd param."
    )
    @WorkerThread
    override suspend fun getThumbnail(task: Task): ThumbnailData? {
        if (enableLowResThumbnailPreloading()) {
            throw IllegalArgumentException(
                "All calls of this method should specify request resolution as a" +
                    " 2nd param when enableLowResThumbnailPreloading is enabled"
            )
        }
        val lowResolution: Boolean = !highResLoadingState.isEnabled

        // Check task for thumbnail
        val taskThumbnail: ThumbnailData? = task.thumbnail
        if (
            taskThumbnail?.thumbnail != null && (!taskThumbnail.reducedResolution || lowResolution)
        ) {
            return taskThumbnail
        }

        // Check cache for thumbnail
        val cachedThumbnail: ThumbnailData? = cache.getAndInvalidateIfModified(task.key)
        if (
            cachedThumbnail?.thumbnail != null &&
                (!cachedThumbnail.reducedResolution || lowResolution)
        ) {
            return cachedThumbnail
        }

        return withContext(backgroundDispatcher) {
            // Get thumbnail from system
            val thumbnailData = activityManagerWrapper.getTaskThumbnail(task.key.id, lowResolution)

            // Avoid an async timing issue that a low res entry replaces an existing high
            // res entry in high res enabled state, so we check before putting it to cache
            if (thumbnailData.reducedResolution && highResLoadingState.isEnabled) {
                val newCachedThumbnail = cache.getAndInvalidateIfModified(task.key)
                if (
                    newCachedThumbnail?.thumbnail != null && !newCachedThumbnail.reducedResolution
                ) {
                    return@withContext newCachedThumbnail
                }
            }
            cache.put(task.key, thumbnailData)
            return@withContext thumbnailData
        }
    }

    /**
     * Retrieves a thumbnail for the provided `task` on the current thread. This should not be
     * called from the main thread.
     *
     * NOTE: if the system does not support low resolution thumbnails this method will only return
     * high resolution thumbnails - ignoring [requestResolution].
     */
    @WorkerThread
    override suspend fun getThumbnail(
        task: Task,
        requestResolution: RequestResolution,
        shouldMakeRequestIfNeeded: Boolean,
    ): ThumbnailData? {
        if (!enableLowResThumbnailPreloading()) {
            throw IllegalArgumentException(
                "request resolution cannot be specified without enableLowResThumbnailPreloading " +
                    "being enabled"
            )
        }
        val sanitizedRequestResolution =
            if (!supportsLowResThumbnails()) HIGH_RES else requestResolution

        fun isCorrectResolution(thumbnailData: ThumbnailData) =
            when (sanitizedRequestResolution) {
                HIGH_RES -> !thumbnailData.reducedResolution
                LOW_RES -> thumbnailData.reducedResolution
                ANY_RES -> true
            }

        // Check task for thumbnail
        val taskThumbnailData = task.thumbnail
        if (taskThumbnailData?.thumbnail != null && isCorrectResolution(taskThumbnailData)) {
            return taskThumbnailData
        }

        // Check cache for thumbnail
        val cachedThumbnail: ThumbnailData? = cache.getAndInvalidateIfModified(task.key)
        if (cachedThumbnail?.thumbnail != null && isCorrectResolution(cachedThumbnail)) {
            return cachedThumbnail
        }

        if (!shouldMakeRequestIfNeeded) {
            return null
        }

        return withContext(backgroundDispatcher) {
            // Get thumbnail from system
            val lowResolution = sanitizedRequestResolution != HIGH_RES
            val thumbnailData = activityManagerWrapper.getTaskThumbnail(task.key.id, lowResolution)

            cache.put(task.key, thumbnailData)
            return@withContext thumbnailData
        }
    }

    /**
     * Asynchronously fetches the thumbnail for the given `task` defaulting to low resolution.
     *
     * @param callback The callback to receive the task after its data has been populated.
     * @return a cancelable handle to the request
     */
    fun getThumbnailInBackground(
        task: Task,
        callback: Consumer<ThumbnailData>,
    ): CancellableTask<ThumbnailData>? {
        Preconditions.assertUIThread()

        // High resolution can be retrieved by specifying it in an alternate API
        // Default to low resolution.
        val lowResolution =
            if (enableLowResThumbnailPreloading()) true else !highResLoadingState.isEnabled
        val taskThumbnail = task.thumbnail
        if (
            taskThumbnail?.thumbnail != null && (!taskThumbnail.reducedResolution || lowResolution)
        ) {
            // Nothing to load, the thumbnail is already high-resolution or matches what the
            // request, so just callback
            callback.accept(taskThumbnail)
            return null
        }

        return getThumbnailInBackground(task.key, lowResolution, callback)
    }

    /**
     * Updates cache size and remove excess entries if current size is more than new cache size.
     *
     * @return whether cache size has increased
     */
    override fun updateCacheSizeAndRemoveExcess(): Boolean {
        val newSize = context.resources.getInteger(R.integer.recentsThumbnailCacheSize)
        val oldSize = cache.maxSize
        if (newSize == oldSize) {
            // Return if no change in size
            return false
        }

        cache.updateCacheSizeAndRemoveExcess(newSize)
        return newSize > oldSize
    }

    private fun getThumbnailInBackground(
        key: TaskKey,
        lowResolution: Boolean,
        callback: Consumer<ThumbnailData>,
    ): CancellableTask<ThumbnailData>? {
        Preconditions.assertUIThread()

        val cachedThumbnail = cache.getAndInvalidateIfModified(key)
        if (
            cachedThumbnail?.thumbnail != null &&
                (!cachedThumbnail.reducedResolution || lowResolution)
        ) {
            // Already cached, lets use that thumbnail
            callback.accept(cachedThumbnail)
            return null
        }

        val request =
            CancellableTask(
                {
                    val thumbnailData =
                        activityManagerWrapper.getTaskThumbnail(key.id, lowResolution)
                    if (thumbnailData.thumbnail != null) thumbnailData
                    else activityManagerWrapper.takeTaskThumbnail(key.id)
                },
                Executors.MAIN_EXECUTOR,
                Consumer { result: ThumbnailData ->
                    // Avoid an async timing issue that a low res entry replaces an existing high
                    // res entry in high res enabled state, so we check before putting it to cache
                    if (result.reducedResolution && highResLoadingState.isEnabled) {
                        val newCachedThumbnail = cache.getAndInvalidateIfModified(key)
                        if (
                            newCachedThumbnail?.thumbnail != null &&
                                !newCachedThumbnail.reducedResolution
                        ) {
                            return@Consumer
                        }
                    }
                    cache.put(key, result)
                    callback.accept(result)
                },
            )
        bgExecutor.execute(request)
        return request
    }

    /** Clears the cache. */
    fun clear() {
        cache.evictAll()
    }

    /** Removes the cached thumbnail for the given task. */
    fun remove(key: TaskKey) {
        cache.remove(key)
    }

    /** Returns The cache size. */
    override fun getCacheSize() = cache.maxSize

    /** Returns Whether to enable background preloading of task thumbnails. */
    fun isPreloadingEnabled() = enableTaskSnapshotPreloading && highResLoadingState.visible

    /**
     * Returns Whether device supports low-res thumbnails. Low-res files are an optimization for
     * faster load times of snapshots. Devices can optionally disable low-res files so that they
     * only store snapshots at high-res scale. The actual scale can be configured in frameworks/base
     * config overlay.
     */
    private fun supportsLowResThumbnails(): Boolean {
        val resources = context.resources
        val resId = resources.getIdentifier("config_lowResTaskSnapshotScale", "dimen", "android")
        if (resId == Resources.ID_NULL) return false

        return resources.getFloat(resId) > 0
    }

    companion object {
        const val TAG = "TaskThumbnailCache"
    }
}
