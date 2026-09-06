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
package com.google.android.ambient.app.backend.documents

import androidx.appsearch.annotation.Document
import com.google.android.ambient.app.backend.documents.builtintypecandidates.ImportantDurationDocument

/**
 * The ranking metadata document with sufficient signals from the publisher to understand when an
 * Ambient event is happening, ending, and the important timeframes within that range.
 *
 * Also contains a confidence score to allow sorting during tie breaker events.
 */
@Document
data class AmbientRankingMetaDataDocument(
    /** Required field for an AppSearch document class. */
    @Document.Id val id: String,

    /** Required field for an AppSearch document class. */
    @Document.Namespace val namespace: String = NAMESPACE,

    /**
     * Field to uniquely identify the message corresponding to an instance of [AmbientData]
     * document.
     *
     * [instanceId] identify a message used to update the [AmbientDataDocument] document. The
     * [instanceId] is different from the [id] of the [AmbientDataDocument]. The [id] is a unique
     * identifier for the event it represents. and it remains stable when the document is
     * overwritten everytime new data is published. But the [instanceId] uniquely identifies a
     * message and changes when the document is overwritten with the data in the new message.
     *
     * When logging interactions, consumers of this data document must make sure to read the value
     * of this field and set it to the [mQuery] field of [TakenAction] documents.
     */
    @Document.StringProperty val instanceId: String?,

    /** Required field for Ambient document ranking. Describes when the content becomes relevant. */
    @Document.LongProperty val startTimeMillis: Long,

    // Required field for Ambient document ranking. Describes when the content
    // is no longer relevant.
    @Document.LongProperty val endTimeMillis: Long,

    // Required field for Ambient document ranking. A value between 0 and 1 detailing
    // the confidence of the published ambient data.
    @Document.DoubleProperty val confidence: Double,

    // Required field for Ambient document ranking. Describes the points
    // in time where the content is most relevant.
    @get:Document.DocumentProperty(indexNestedProperties = true)
    val importantTimeFrames: List<ImportantDurationDocument>,
) {
    companion object {
        const val NAMESPACE = "AmbientRankingMetaData"
    }
}
