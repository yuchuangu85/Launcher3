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

import android.util.Log
import androidx.appsearch.app.AppSearchBatchResult
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.app.SetSchemaResponse
import com.google.android.ambient.app.AmbientData
import com.google.android.ambient.app.AmbientDataWriteSession
import com.google.android.ambient.app.backend.documents.AppSearchAmbientDataConverter
import com.google.android.ambient.app.backend.documents.ThingAmbientDataDocument
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/** An implementation of [AmbientDataWriteSession] that is backed by AppSearch. */
// TODO(b/391934208): This file is only partially migrated.
class AmbientDataAppSearchPublishingWriteSessionImpl(
    private val appSearchSession: AppSearchSession,
    private val executor: Executor,
) : AmbientDataWriteSession {

    override fun publish(ambientData: AmbientData) {
        val document = AppSearchAmbientDataConverter.serialize(ambientData)
        Log.d("Ambient", "Writing $document")
        val putDocumentRequest = PutDocumentsRequest.Builder().addDocuments(document).build()
        val schemaFuture = setSchema()

        val publishFuture =
            Futures.transformAsync(
                schemaFuture,
                { appSearchSession.putAsync(putDocumentRequest) },
                executor,
            )

        Futures.addCallback(
            publishFuture,
            object : FutureCallback<AppSearchBatchResult<String, Void>?> {
                override fun onSuccess(result: AppSearchBatchResult<String, Void>?) {
                    val successfulResults = result?.successes
                    val failedResults = result?.failures
                    Log.d(
                        "Ambient",
                        "${failedResults?.size} failed, ${successfulResults?.size} succeeded",
                    )
                    if (!failedResults.isNullOrEmpty()) {
                        Log.e("Ambient", "$failedResults")
                    }
                }

                override fun onFailure(t: Throwable) {
                    Log.e("Ambient", "Failed to put documents.", t)
                }
            },
            executor,
        )
    }

    private fun setSchema(): ListenableFuture<SetSchemaResponse> {
        Log.d("Ambient", "Setting schema for ambient data and usage reports")
        val schemaRequestBuilder =
            SetSchemaRequest.Builder()
                .addDocumentClasses(ThingAmbientDataDocument::class.java)
                .setSchemaTypeDisplayedBySystem(ThingAmbientDataDocument.SCHEMA_NAME, true)
                .setForceOverride(true)

        return appSearchSession.setSchemaAsync(schemaRequestBuilder.build())
    }

    override fun close() {
        // appSearchSession.requestFlushAsync()
        appSearchSession.close()
    }
}
