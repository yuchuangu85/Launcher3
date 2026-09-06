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
package com.google.android.ambient.app.backend

import android.content.Context
import android.util.Log
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.platformstorage.PlatformStorage
import com.google.android.ambient.app.AmbientDataPublishingManager
import com.google.android.ambient.app.AmbientDataWriteSession
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.guava.await

/** An implementation of [AmbientDataPublishingManager] that is backed by AppSearch. */
class AmbientDataAppSearchPublishingManagerImpl : AmbientDataPublishingManager {

    override suspend fun createWriteSession(
        executor: Executor,
        context: Context,
    ): AmbientDataWriteSession? {
        try {
            Log.d("AmbientDataAppSearchPublishingManagerImpl", "createWriteSession")
            val platformSession = createPlatformSession(context, executor)
            return AmbientDataAppSearchPublishingWriteSessionImpl(platformSession, executor)
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun createPlatformSession(
        context: Context,
        executor: Executor,
    ): AppSearchSession {
        val deferred = CompletableDeferred<AppSearchSession>()
        val platformStorageFuture =
            PlatformStorage.createSearchSessionAsync(
                PlatformStorage.SearchContext.Builder(context, DATABASE_NAME)
                    .setWorkerExecutor(executor)
                    .build()
            )
        Futures.transform(platformStorageFuture, { result -> deferred.complete(result) }, executor)
            .await()
        return deferred.await()
    }

    companion object {
        private const val DATABASE_NAME: String = "AMBIENT_DATA_DB"
    }
}
