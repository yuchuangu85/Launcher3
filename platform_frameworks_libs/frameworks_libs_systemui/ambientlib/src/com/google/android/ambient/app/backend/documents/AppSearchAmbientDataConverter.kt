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

import android.content.Intent
import android.util.Log
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.builtintypes.PotentialAction
import com.google.android.ambient.app.AmbientData
import com.google.android.ambient.app.MetaData
import com.google.android.ambient.app.Thing
import com.google.android.ambient.app.ThingAmbientData
import com.google.android.ambient.app.backend.documents.BlobStoreHandleDocument.Companion.toBlobHandle
import com.google.android.ambient.app.backend.documents.BlobStoreHandleDocument.Companion.toBlobStoreHandleDocument
import com.google.android.ambient.app.backend.documents.builtintypecandidates.ImportantDurationDocument
import com.google.android.ambient.app.backend.documents.builtintypecandidates.ThingDocument
import java.time.Instant

/**
 * Conversion library for translating between AppSearch definitions of [AmbientDataDocument]'s to
 * [AmbientData] and vice versa.
 */
// TODO(b/391934208): This file is only partially migrated.
object AppSearchAmbientDataConverter {

    private fun serializeMetaData(ambientData: AmbientData): AmbientRankingMetaDataDocument {
        val rankingMetadataId = ambientData.metaData.id + ":RankingMetaData"
        return AmbientRankingMetaDataDocument(
            id = rankingMetadataId,
            startTimeMillis = ambientData.metaData.startTime.toEpochMilli(),
            endTimeMillis = ambientData.metaData.endTime.toEpochMilli(),
            confidence = ambientData.metaData.confidence,
            importantTimeFrames =
                ambientData.metaData.importantTimes.map {
                    ImportantDurationDocument.fromImportantDuration(
                        parentId = rankingMetadataId,
                        duration = it,
                    )
                },
            instanceId = ambientData.metaData.instanceId ?: "",
        )
    }

    private fun serializeIntent(intent: Intent?): PotentialAction {
        return PotentialAction.Builder().setUri(intent?.toUri(Intent.URI_INTENT_SCHEME)).build()
    }

    /**
     * Convenience function to serialize an [AmbientData] definition to an [AmbientDataDocument].
     */
    fun serialize(ambientData: AmbientData): AmbientDataDocument {
        when (ambientData) {
            is ThingAmbientData -> {
                return ThingAmbientDataDocument(
                    builtInType =
                        ThingDocument.create(
                            id = "builtInType:${ambientData.metaData.id}",
                            name = ambientData.thing.name,
                            alternateNames = listOf(ambientData.thing.shortName),
                            description = ambientData.thing.description,
                            image = ambientData.thing.image?.tag ?: "",
                            blobStoreImage = ambientData.thing.image?.toBlobStoreHandleDocument(),
                            url = ambientData.thing.url,
                        ),
                    // General AmbientDataDocument fields
                    id = ambientData.metaData.id,
                    documentTtlMillis = ambientData.metaData.ttlMillis,
                    creationTimestamp = Instant.now().toEpochMilli(),
                    ambientRankingMetaData = serializeMetaData(ambientData),
                    notificationDedupeId = ambientData.notificationDedupeId,
                    tapAction = serializeIntent(ambientData.tapAction),
                    dismissAction = serializeIntent(ambientData.dismissAction),
                )
            }
            else -> throw IllegalArgumentException("No document type found")
        }
    }

    /**
     * Convenience function to retrieve an [AmbientData] representation from a [GenericDocument].
     */
    fun from(genericDocument: GenericDocument, packageName: String): AmbientData? {
        val feature = toFeature(genericDocument, packageName) ?: return null
        return feature
    }

    private fun toMetaData(
        ambientDataDocument: AmbientDataDocument,
        packageName: String,
    ): MetaData {
        return MetaData(
            id = ambientDataDocument.id,
            attribution = packageName,
            createAtInstant = Instant.ofEpochMilli(ambientDataDocument.creationTimestamp),
            ttlMillis = ambientDataDocument.documentTtlMillis,
            confidence = ambientDataDocument.ambientRankingMetaData.confidence,
            startTime =
                Instant.ofEpochMilli(ambientDataDocument.ambientRankingMetaData.startTimeMillis),
            endTime =
                Instant.ofEpochMilli(ambientDataDocument.ambientRankingMetaData.endTimeMillis),
            importantTimes =
                ambientDataDocument.ambientRankingMetaData.importantTimeFrames.map {
                    it.toDuration()
                },
            instanceId = ambientDataDocument.ambientRankingMetaData.instanceId,
        )
    }

    private fun toIntent(potentialAction: PotentialAction): Intent? {
        return if (potentialAction.uri != null) {
            Intent.parseUri(potentialAction.uri, Intent.URI_INTENT_SCHEME)
        } else {
            null
        }
    }

    private fun toFeature(genericDocument: GenericDocument, packageName: String): AmbientData? {
        val parentTypes: List<String>? = genericDocument.parentTypes

        if (parentTypes.isNullOrEmpty()) {
            Log.w("Ambient", "Parent types is null")
        }

        when (genericDocument.schemaType) {
            ThingAmbientDataDocument.SCHEMA_NAME -> {
                val thingAmbientDocument =
                    genericDocument.toDocumentClass(ThingAmbientDataDocument::class.java)
                val thingDocument = thingAmbientDocument.builtInType
                return ThingAmbientData(
                    thing =
                        Thing(
                            name = thingDocument.name,
                            shortName = thingDocument.alternateNames.firstOrNull() ?: "",
                            description = thingDocument.description,
                            image = thingDocument.blobStoreImage?.toBlobHandle(),
                            url = thingDocument.url,
                        ),
                    // General AmbientData fields
                    metaData = toMetaData(thingAmbientDocument, packageName),
                    notificationDedupeId = thingAmbientDocument.notificationDedupeId,
                    tapAction = toIntent(thingAmbientDocument.tapAction),
                    dismissAction = toIntent(thingAmbientDocument.dismissAction),
                )
            }
            else -> return null
        }
    }
}
