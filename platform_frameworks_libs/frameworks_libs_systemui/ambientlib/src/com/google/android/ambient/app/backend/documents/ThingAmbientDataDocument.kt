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
import androidx.appsearch.builtintypes.PotentialAction
import com.google.android.ambient.app.backend.documents.ThingAmbientDataDocument.Companion.SCHEMA_NAME
import com.google.android.ambient.app.backend.documents.builtintypecandidates.ThingDocument

/** A high level AmbientDataDocument describing a thing ambient data. */
@Document(name = SCHEMA_NAME, parent = [AmbientDataDocument::class])
data class ThingAmbientDataDocument(
    @get:Document.DocumentProperty(indexNestedProperties = true)
    override val builtInType: ThingDocument,
    // Inherited from AmbientDataDocument
    override val namespace: String = AmbientDataDocument.NAMESPACE,
    override val id: String,
    override val creationTimestamp: Long,
    override val documentTtlMillis: Long,
    override val ambientRankingMetaData: AmbientRankingMetaDataDocument,
    override val notificationDedupeId: String,
    override val tapAction: PotentialAction,
    override val dismissAction: PotentialAction,
) : AmbientDataDocument {
    companion object {
        const val SCHEMA_NAME = "AmbientDataSchema:Thing"
    }

    // Required root implementation definition
    data class ThingAmbientDataDocumentImpl(
        override val id: String,
        override val namespace: String,
        override val creationTimestamp: Long,
        override val documentTtlMillis: Long,
        override val ambientRankingMetaData: AmbientRankingMetaDataDocument,
        override val notificationDedupeId: String,
        override val tapAction: PotentialAction,
        override val dismissAction: PotentialAction,
        override val builtInType: ThingDocument,
    ) : AmbientDataDocument
}
