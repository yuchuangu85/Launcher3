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

package com.android.quickstep.task.thumbnail.data

import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData

interface TaskThumbnailDataSource {
    @Deprecated(
        "Should be removed with flag: enable_low_res_thumbnail_preloading." +
            " Specify request resolution as 2nd param."
    )
    suspend fun getThumbnail(task: Task): ThumbnailData?

    suspend fun getThumbnail(
        task: Task,
        requestResolution: RequestResolution,
        shouldMakeRequestIfNeeded: Boolean = true,
    ): ThumbnailData?

    fun getCacheSize(): Int

    fun updateCacheSizeAndRemoveExcess(): Boolean

    enum class RequestResolution {
        LOW_RES,
        HIGH_RES,
        ANY_RES,
    }
}
