/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("FlaggedApi", "NewApi")

package com.android.personalcontext.ace.client.prototype

import android.service.personalcontext.insight.BundleInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.InsightCollection
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.CardInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.ClientActionInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.ClientSignalInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.EmbeddedScrollInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.EmptyRenderInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.ExampleEmbeddedInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.InsightGridId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.RenderTokenInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.ServerSideCloseInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.WeatherInsightId
import com.android.personalcontext.ace.client.prototype.PrototypeInsightUtils.toContextInsight
import com.android.personalcontext.ace.client.prototype.card.CardInsight
import com.android.personalcontext.ace.client.prototype.clientaction.ClientActionInsight
import com.android.personalcontext.ace.client.prototype.clientsignal.ClientSignalInsight
import com.android.personalcontext.ace.client.prototype.embeddedscroll.EmbeddedScrollInsight
import com.android.personalcontext.ace.client.prototype.empty.EmptyRenderInsight
import com.android.personalcontext.ace.client.prototype.example.ExampleEmbeddedInsight
import com.android.personalcontext.ace.client.prototype.grid.InsightGrid
import com.android.personalcontext.ace.client.prototype.rendertoken.RenderTokenInsight
import com.android.personalcontext.ace.client.prototype.serversideclose.ServerSideCloseInsight
import com.android.personalcontext.ace.client.prototype.weather.WeatherInsight

private const val PROTOTYPE_INSIGHT_ID_KEY = "prototype_insight_id_key"

/**
 * Utilities for marshalling [PrototypeInsight] objects to and from the generic [ContextInsight]
 * framework.
 *
 * This class handles the serialization strategy of wrapping a strongly-typed prototype object into
 * a generic [InsightCollection] for transport.
 *
 * ### Serialization Structure
 * When a [PrototypeInsight] is converted via [toContextInsight], it results in an
 * [InsightCollection] with a strict layout:
 * 1. **Header (Index 0):** A [BundleInsight] containing metadata and flattened data. **ID:** The
 *    [PROTOTYPE_INSIGHT_ID_KEY] identifies the specific [PrototypeInsight] subclass. **Data:** The
 *    result of [PrototypeInsight.exportDataToBundle]. **Context:** Contains all
 *    [tokens][PrototypeInsight.tokens] and [originHints][PrototypeInsight.originHints].
 * 2. **Children (Index 1..N):** The list of nested insights returned by
 *    [PrototypeInsight.exportInsightsToList]. Used for container types (e.g., Grids, Lists) that
 *    hold other insights. `null` children are serialized as [EmptyRenderInsight] to maintain index
 *    alignment.
 */
object PrototypeInsightUtils {

    /**
     * Serializes this [PrototypeInsight] into a generic [ContextInsight] for transport.
     *
     * The resulting insight is always an [InsightCollection] where the first element is a
     * [BundleInsight] containing the prototype's ID and data, followed by any child insights.
     */
    fun <T : PrototypeInsight> T.toContextInsight(): ContextInsight {
        val prototype = this

        val data =
            BundleInsight.Builder()
                .setInsightTypeName(prototype.id.typeName)
                .apply {
                    for (hint in prototype.originHints) addOriginHint(hint)
                    for (token in prototype.tokens) addToken(token)
                }
                .build()
                .apply {
                    prototype.exportDataToBundle(dataBundle)
                    dataBundle.putInt(PROTOTYPE_INSIGHT_ID_KEY, prototype.id.uid)
                }
        val children =
            prototype.exportInsightsToList().map {
                it ?: EmptyRenderInsight(emptySet()).toContextInsight()
            }

        return InsightCollection.Builder(listOf(data) + children).build()
    }

    /**
     * Deserializes a [ContextInsight] back into a specific [PrototypeInsight] implementation.
     *
     * This function inspects the first element of the [InsightCollection] to determine the
     * [PrototypeInsightId] and uses the corresponding factory to reconstruct the object.
     *
     * @return The reconstructed [PrototypeInsight], or `null` if the input is not a valid prototype
     *   collection or the ID is unrecognized.
     */
    fun ContextInsight.toPrototypeInsight(): PrototypeInsight? {
        if (this !is InsightCollection || !this.isPrototypeInsight()) return null

        val data = insights.first() as BundleInsight
        val children =
            insights.drop(1).map { if (it.isPrototypeInsight<EmptyRenderInsight>()) null else it }

        val uid = data.dataBundle.getInt(PROTOTYPE_INSIGHT_ID_KEY)
        val id = PrototypeInsightId.entries.find { it.uid == uid } ?: return null

        val creator =
            when (id) {
                ExampleEmbeddedInsightId -> ExampleEmbeddedInsight
                EmbeddedScrollInsightId -> EmbeddedScrollInsight
                ClientActionInsightId -> ClientActionInsight
                WeatherInsightId -> WeatherInsight
                EmptyRenderInsightId -> EmptyRenderInsight
                CardInsightId -> CardInsight
                InsightGridId -> InsightGrid
                ServerSideCloseInsightId -> ServerSideCloseInsight
                RenderTokenInsightId -> RenderTokenInsight
                ClientSignalInsightId -> ClientSignalInsight
            }

        return creator.create(data.dataBundle, children, originHints)
    }

    /** Deserialize a [ContextInsight] back into a [PrototypeInsight] as [T], if possible. */
    @JvmName("toTypedPrototypeInsight")
    inline fun <reified T : PrototypeInsight> ContextInsight.toPrototypeInsight(): T? {
        return toPrototypeInsight() as? T
    }

    /**
     * Returns whether a [ContextInsight] is a [PrototypeInsight] of type [T] by attempting to
     * deserialize it.
     */
    @JvmName("toTypedPrototypeInsight")
    inline fun <reified T : PrototypeInsight> ContextInsight.isPrototypeInsight(): Boolean {
        return toPrototypeInsight() is T
    }

    /**
     * Return whether a [InsightCollection] is a wrapped [PrototypeInsight], without deserializing
     * it. There are other legitimate use cases for [InsightCollection] which may fail to
     * deserialize into a [PrototypeInsight].
     */
    fun InsightCollection.isPrototypeInsight(): Boolean {
        val data = insights.firstOrNull()
        return data is BundleInsight && data.dataBundle.getInt(PROTOTYPE_INSIGHT_ID_KEY) > 0
    }
}
