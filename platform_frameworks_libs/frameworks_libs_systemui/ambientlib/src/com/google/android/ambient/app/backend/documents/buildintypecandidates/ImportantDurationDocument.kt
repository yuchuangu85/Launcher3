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
package com.google.android.ambient.app.backend.documents.builtintypecandidates

import androidx.appsearch.annotation.Document
import com.google.android.ambient.app.MetaData
import java.time.Instant

/** A document describing an important duration in an [AmbientRankingMetaDataDocument]. */
@Document
data class ImportantDurationDocument(
    // Required field for an AppSearch document class.
    @Document.Id val id: String,
    // Required field for an AppSearch document class.
    @Document.Namespace val namespace: String = NAMESPACE,
    @Document.LongProperty val startTimeMillis: Long,
    @Document.LongProperty val endTimMillis: Long,
) {

    fun toDuration(): MetaData.ImportantTimeDuration {
        return MetaData.ImportantTimeDuration(
            startTime = Instant.ofEpochMilli(startTimeMillis),
            endTime = Instant.ofEpochMilli(endTimMillis),
        )
    }

    companion object {
        const val NAMESPACE = "ImportantDuration"

        fun fromImportantDuration(
            parentId: String,
            duration: MetaData.ImportantTimeDuration,
        ): ImportantDurationDocument {
            return ImportantDurationDocument(
                startTimeMillis = duration.startTime.toEpochMilli(),
                endTimMillis = duration.endTime.toEpochMilli(),
                id = "$parentId:ImportantDuration:${duration.startTime.toEpochMilli()}}",
            )
        }
    }
}
