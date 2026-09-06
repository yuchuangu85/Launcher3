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

import android.content.Context
import android.util.Log
import com.android.launcher3.Flags.enableLowResThumbnailPreloading
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.quickstep.recents.data.RecentTasksKeysDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution.LOW_RES
import com.android.systemui.shared.recents.model.Task
import javax.inject.Inject

/** Use case to preload low resolution thumbnails into a cache to speed up initial rendering. */
class PreloadThumbnailUseCase
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val taskThumbnailDataSource: TaskThumbnailDataSource,
    private val recentTasksKeysDataSource: RecentTasksKeysDataSource,
) {
    private val enableTaskSnapshotPreloading =
        context.resources.getBoolean(R.bool.config_enableTaskSnapshotPreloading)

    /**
     * Preloads thumbnails. This should not be called while high resolution thumbnails are actively
     * being used. It can evict high resolution thumbnails from cache.
     */
    suspend fun preloadThumbnails() {
        // Skip if flag is off
        if (!enableLowResThumbnailPreloading()) return

        // Skip if we aren't preloading.
        if (!enableTaskSnapshotPreloading) return

        recentTasksKeysDataSource
            .getTaskKeys(taskThumbnailDataSource.getCacheSize())
            .flatMap { it.tasks }
            .also { Log.d(TAG, "Preloading thumbnails for task ids: $it") }
            .forEach { t: Task -> taskThumbnailDataSource.getThumbnail(t, LOW_RES) }
    }

    private companion object {
        const val TAG = "PreloadThumbnailUseCase"
    }
}
