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

package com.android.quickstep.recents.data

import android.graphics.Bitmap
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution.ANY_RES
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource.RequestResolution.HIGH_RES
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.delay
import org.mockito.kotlin.mock

class FakeTaskThumbnailDataSource : TaskThumbnailDataSource {

    val taskIdToBitmap: MutableMap<Int, Bitmap?> =
        (0..10).associateWith { mock<Bitmap>() }.toMutableMap()
    private val completionPrevented: MutableSet<Int> = mutableSetOf()
    private val getThumbnailCalls = mutableMapOf<Int, List<GetThumbnailRequest>>()

    var highResEnabled = true
    private var cacheSize = taskIdToBitmap.size

    /** Retrieves and sets a thumbnail on [task] from [taskIdToBitmap]. */
    override suspend fun getThumbnail(task: Task): ThumbnailData? =
        getThumbnail(task, if (highResEnabled) HIGH_RES else ANY_RES)

    override suspend fun getThumbnail(
        task: Task,
        requestResolution: RequestResolution,
        shouldMakeRequestIfNeeded: Boolean,
    ): ThumbnailData? {
        getThumbnailCalls[task.key.id] =
            (getThumbnailCalls[task.key.id] ?: emptyList()) +
                GetThumbnailRequest(requestResolution, shouldMakeRequestIfNeeded)

        while (task.key.id in completionPrevented) {
            // yield doesn't work here with an UnconfinedTestDispatcher
            delay(1L)
        }
        val isHighRes = requestResolution == HIGH_RES
        return ThumbnailData(
            thumbnail = taskIdToBitmap[task.key.id],
            reducedResolution = !isHighRes,
        )
    }

    override fun getCacheSize(): Int = cacheSize

    override fun updateCacheSizeAndRemoveExcess(): Boolean {
        if (cacheSize < taskIdToBitmap.size) {
            val newCache = taskIdToBitmap.filter { it.key < cacheSize }
            taskIdToBitmap.clear()
            taskIdToBitmap.putAll(newCache)
            return true
        }

        return false
    }

    fun setCacheSize(cacheSize: Int) {
        this.cacheSize = cacheSize
    }

    fun getNumberOfGetThumbnailCalls(taskId: Int): Int = getThumbnailCalls(taskId).size

    fun getThumbnailCallsRes(taskId: Int) = getThumbnailCalls(taskId).map { it.requestResolution }

    fun getThumbnailCalls(taskId: Int) = getThumbnailCalls[taskId] ?: emptyList()

    fun preventThumbnailLoad(taskId: Int) {
        completionPrevented.add(taskId)
    }

    fun completeLoadingForTask(taskId: Int) {
        completionPrevented.remove(taskId)
    }

    fun completeLoading() {
        completionPrevented.clear()
    }

    data class GetThumbnailRequest(
        val requestResolution: RequestResolution,
        val shouldMakeRequestIfNeeded: Boolean,
    )
}
