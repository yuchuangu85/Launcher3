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

import android.content.Intent
import java.time.Instant

/**
 * A high level, abstracted definition of an [AmbientData] which always contains [MetaData] included
 * for ranking.
 */
sealed interface AmbientData {

    /** Ranking [MetaData] associated with this [AmbientData]. */
    val metaData: MetaData

    /** A notification id value that can be utilized to dedupe against. */
    val notificationDedupeId: String

    /** An intrinsic weight definition derived from the type of content, see [Ranker]. */
    val intrinsicWeight: Int

    /**
     * Tap action.
     *
     * <p>{@link Intent#parseUri()} intent.
     *
     * @see <a href="//reference/android/content/Intent#intent-structure">Intent Structure</a>
     */
    val tapAction: Intent?

    /**
     * Dismiss action.
     *
     * <p>{@link Intent#parseUri()} intent.
     *
     * @see <a href="//reference/android/content/Intent#intent-structure">Intent Structure</a>
     */
    val dismissAction: Intent?
}

/**
 * Ranking metadata with some basic defaults, reference the ambient ranking meta data document for
 * more insight.
 */
data class MetaData(
    /**
     * An unique identifier for the [AmbientData], keep this stable if you want to update an
     * existing record rather than creating a new one.
     */
    val id: String,

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
    val instanceId: String? = null,

    /**
     * The source package that published the [AmbientData].
     *
     * Will only be populated when read from the read session.
     */
    val attribution: String? = null,

    /**
     * The created time of the [AmbientData], which if it hasn't been set, defaults to
     * [Instant.MAX].
     */
    val createAtInstant: Instant = Instant.MAX,

    /**
     * The ttl time of the [AmbientData], which if it hasn't been set, defaults to [Long.MAX_VALUE].
     */
    val ttlMillis: Long = Long.MAX_VALUE,

    /**
     * The start time of the [AmbientData], which if it hasn't been set, defaults to [Instant.MAX].
     */
    val startTime: Instant = Instant.MAX,

    /**
     * The end time of the [AmbientData], which if it hasn't been set, defaults to [Instant.MAX].
     */
    val endTime: Instant = Instant.MAX,

    /**
     * The confidence score of the [AmbientData], which is utilized to break ties in ranking or sort
     * results. Defaults to 1.0 (max confidence).
     */
    val confidence: Double = 1.0,

    /**
     * A list of [ImportantTimeDuration]'s, that must fall between the [startTime] and the [endTime]
     * that provide extra signals to create a curve for the ranking methodology.
     */
    val importantTimes: List<ImportantTimeDuration> = listOf(),
) {

    /**
     * An [ImportantTimeDuration] is a range of time within the [MetaData.startTime] and
     * [MetaData.endTime] that signals the highest priority points across the generated curve.
     */
    data class ImportantTimeDuration(val startTime: Instant, val endTime: Instant)
}
