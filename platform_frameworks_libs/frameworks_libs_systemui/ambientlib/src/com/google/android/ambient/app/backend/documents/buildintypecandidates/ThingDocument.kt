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
import com.google.android.ambient.app.backend.documents.BlobStoreHandleDocument
import com.google.android.ambient.app.backend.documents.builtintypecandidates.ThingDocument.Companion.SCHEMA_NAME

/**
 * A placeholder [Document] that represents a [Thing] in AppSearch, until we upstream the other
 * definitions.
 */
@Document(name = SCHEMA_NAME)
interface ThingDocument {
    // Required field for an AppSearch document class.
    @get:Document.Id val id: String
    // Required field for an AppSearch document class.
    @get:Document.Namespace val namespace: String

    @get:Document.StringProperty val name: String
    @get:Document.StringProperty val description: String
    @get:Document.StringProperty val image: String
    @get:Document.DocumentProperty val blobStoreImage: BlobStoreHandleDocument?
    @get:Document.StringProperty val url: String
    @get:Document.StringProperty val alternateNames: List<String>

    companion object {
        const val NAMESPACE = "Thing"
        const val SCHEMA_NAME = "builtIn:Thing"

        // Required static creator
        @JvmStatic
        fun create(
            id: String,
            namespace: String = NAMESPACE,
            name: String,
            description: String,
            image: String,
            blobStoreImage: BlobStoreHandleDocument?,
            url: String,
            alternateNames: List<String>,
        ): ThingDocument {
            return ThingDocumentImpl(
                id,
                namespace,
                name,
                description,
                image,
                blobStoreImage,
                url,
                alternateNames,
            )
        }

        // Required root implementation definition
        private data class ThingDocumentImpl(
            override val id: String,
            override val namespace: String,
            override val name: String,
            override val description: String,
            override val image: String,
            override val blobStoreImage: BlobStoreHandleDocument?,
            override val url: String,
            override val alternateNames: List<String>,
        ) : ThingDocument
    }
}
