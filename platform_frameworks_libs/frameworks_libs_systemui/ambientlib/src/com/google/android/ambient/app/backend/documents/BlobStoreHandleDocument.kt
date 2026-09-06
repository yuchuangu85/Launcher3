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

import android.app.blob.BlobHandle
import androidx.appsearch.annotation.Document
import com.google.android.ambient.app.backend.documents.BlobStoreHandleDocument.Companion.SCHEMA_NAME
import com.google.common.io.BaseEncoding
import java.time.Duration

/**
 * A document describing information to access some blobstore data. This document does not contain
 * the data itself and the consumer needs to assemble info to fetch the image use BlobHandle APIs
 */
@Document(name = SCHEMA_NAME)
data class BlobStoreHandleDocument(
    // Required field for an AppSearch document class.
    @Document.Id val id: String,
    // Required field for an AppSearch document class.
    @Document.Namespace val namespace: String = NAMESPACE,
    // SHA 256 digest supplied to BlobStore.
    @Document.StringProperty val resourceDigest: String,
    // Publisher package name of the data blob, will be used as the label for this blob.
    @Document.StringProperty val publisherLabel: String,
    @Document.CreationTimestampMillis val creationTimestamp: Long = System.currentTimeMillis(),
    @Document.LongProperty val expiryTimeMillis: Long = TTL.toMillis(),
    @Document.StringProperty val tag: String,
) {

    companion object {
        const val NAMESPACE = "BlobStoreHandle"
        const val SCHEMA_NAME = "AmbientDataSchema:BlobStoreHandle"
        val TTL: Duration = Duration.ZERO

        fun BlobHandle.toBlobStoreHandleDocument(): BlobStoreHandleDocument {
            val hash = BaseEncoding.base64().encode(sha256Digest)
            return BlobStoreHandleDocument(
                id = hash,
                resourceDigest = hash,
                publisherLabel = label.toString(),
                expiryTimeMillis = expiryTimeMillis,
                tag = tag,
            )
        }

        fun BlobStoreHandleDocument.toBlobHandle(): BlobHandle {
            return BlobHandle.createWithSha256(
                BaseEncoding.base64().decode(resourceDigest),
                publisherLabel,
                expiryTimeMillis,
                tag,
            )
        }
    }
}
