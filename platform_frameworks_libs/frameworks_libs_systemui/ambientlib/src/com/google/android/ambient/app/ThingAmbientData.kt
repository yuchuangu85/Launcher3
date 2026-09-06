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
package com.google.android.ambient.app

import android.app.blob.BlobHandle
import android.content.Intent

/** Ambient data describing a generic thing. */
data class ThingAmbientData(
    val thing: Thing,
    override val metaData: MetaData,
    override val notificationDedupeId: String = "",
    override val tapAction: Intent? = null,
    override val dismissAction: Intent? = null,
) : AmbientData {
    override val intrinsicWeight: Int
        get() = Integer.MAX_VALUE
}

data class Thing(
    val name: String,
    val shortName: String,
    val description: String,
    val image: BlobHandle?,
    val url: String,
)
