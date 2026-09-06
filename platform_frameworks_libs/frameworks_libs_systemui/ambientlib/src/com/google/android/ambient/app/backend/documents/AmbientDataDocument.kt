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
import com.google.android.ambient.app.backend.documents.builtintypecandidates.ThingDocument

/**
 * High level [Document] definition describing Ambient Data as it is to be persisted within
 * AppSearch.
 */
// TODO(b/391934208): This file is partially migrated. Sports is not migrated.
@Document
interface AmbientDataDocument {
    /** Required field for an AppSearch document class. */
    @get:Document.Id val id: String

    /**
     * Required field for a document class. All AppSearch documents MUST have a namespace. Value
     * should be set to "Ambient" so that it can be discovered by consuming surfaces.
     */
    @get:Document.Namespace val namespace: String

    /**
     * Required field for a document class. All AppSearch documents MUST have a creation timestamp.
     */
    @get:Document.CreationTimestampMillis val creationTimestamp: Long

    /**
     * Required field so that documents are auto-cleaned up by AppSearch. See [Document.TtlMillis]
     */
    @get:Document.TtlMillis val documentTtlMillis: Long

    /**
     * RANKING REQUIRED FIELDS, see go/android-ambient-data-platform-ranking From the following
     * fields, Ranking implementations can derive an importance curve and apply any of their own
     * weights to it.
     */
    @get:Document.DocumentProperty(indexNestedProperties = true)
    val ambientRankingMetaData: AmbientRankingMetaDataDocument

    /** Optional notification id field that that this Ambient Data should be deduped against. */
    @get:Document.StringProperty val notificationDedupeId: String

    /**
     * Tap action, for when the ambient data is clicked on.
     *
     * @see [PotentialAction]
     */
    @get:Document.DocumentProperty(indexNestedProperties = false) val tapAction: PotentialAction

    /**
     * Dismiss action, for when the ambient data is dismissed.
     *
     * @see [PotentialAction]
     */
    @get:Document.DocumentProperty(indexNestedProperties = false) val dismissAction: PotentialAction

    /**
     * The underlying built in derived type of the ambient data.
     *
     * By default the properties of the built in type are indexed so that they can be queried
     * independently of the ambient data definition.
     *
     * @see [ThingDocument]
     */
    @get:Document.DocumentProperty(indexNestedProperties = true) val builtInType: ThingDocument

    companion object {
        const val NAMESPACE = "AmbientData"

        // Required static creator
        @JvmStatic
        fun create(
            id: String,
            namespace: String,
            creationTimestamp: Long,
            documentTtlMillis: Long,
            ambientRankingMetaData: AmbientRankingMetaDataDocument,
            notificationDedupeId: String,
            tapAction: PotentialAction,
            dismissAction: PotentialAction,
            builtInType: ThingDocument,
        ): AmbientDataDocument {
            return when (builtInType) {
                else ->
                    ThingAmbientDataDocument.ThingAmbientDataDocumentImpl(
                        id,
                        namespace,
                        creationTimestamp,
                        documentTtlMillis,
                        ambientRankingMetaData,
                        notificationDedupeId,
                        tapAction,
                        dismissAction,
                        builtInType,
                    )
            }
        }
    }
}
