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
package com.android.personalcontext.ace.client.prototype.weather

import android.os.Bundle
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.BundleInsight
import android.service.personalcontext.insight.ContextInsight
import com.android.personalcontext.ace.client.prototype.PrototypeInsight
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.WeatherInsightId
import java.time.Instant

/** An insight for the Weather. */
@SuppressWarnings("FlaggedApi", "NewApi")
data class WeatherInsight(
    val chipContents: List<ChipContent>,
    override val originHints: Set<PublishedContextHint>,
) : PrototypeInsight(WeatherInsightId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        ChipContent.toBundle(bundle, chipContents)
    }

    companion object : Creator {

        override fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight =
            WeatherInsight(chipContents = ChipContent.fromBundle(bundle), originHints = originHints)
    }

    /**
     * Check go/sundog-ace-migration for more details about the fields.
     *
     * TODO(b/473427363): strip out internal reference related fields to prepare for sysui
     *   visualizer migration
     */
    data class ChipContent(
        // The title in the chip.
        val title: String,
        // The subtitle in the chip.
        val subtitle: String,
        // The primary name of the location.
        val locationName: String,
        // The Element type ID used for logging in PSI.
        val elementType: Int,

        /* Below fields are only for Location Suggestion. */
        val source: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val sourceL1DataId: String? = null,

        /* Below fields are only for Event Suggestion. */
        val startTime: Instant? = null,
        val endTime: Instant? = null,
        val eventTitle: String? = null, // Title shows in the detailed description page.
    ) {

        fun toContextInsight(): ContextInsight {
            val bundle = Bundle()
            toBundle(bundle, listOf(this))
            return BundleInsight.Builder().setDataBundle(bundle).build()
        }

        companion object {
            private const val KEY_TITLES = "titles"
            private const val KEY_SUBTITLES = "sub_titles"
            private const val KEY_LOCATION_NAMES = "location_names"
            private const val KEY_ELEMENT_TYPES = "element_types"
            private const val KEY_SOURCES = "sources"
            private const val KEY_LATITUDES = "latitudes"
            private const val KEY_LONGITUDES = "longitudes"
            private const val KEY_SOURCE_L1_DATA_IDS = "source_l1_data_ids"
            private const val KEY_START_TIMES = "start_times"
            private const val KEY_END_TIMES = "end_times"
            private const val KEY_EVENT_TITLES = "event_titles"

            fun toBundle(bundle: Bundle, contents: List<ChipContent>) {
                bundle.putStringArrayList(KEY_TITLES, ArrayList(contents.map { it.title }))
                bundle.putStringArrayList(KEY_SUBTITLES, ArrayList(contents.map { it.subtitle }))
                bundle.putStringArrayList(
                    KEY_LOCATION_NAMES,
                    ArrayList(contents.map { it.locationName }),
                )
                bundle.putIntegerArrayList(
                    KEY_ELEMENT_TYPES,
                    ArrayList(contents.map { it.elementType }),
                )
                bundle.putStringArrayList(KEY_SOURCES, ArrayList(contents.map { it.source }))
                bundle.putSerializable(KEY_LATITUDES, ArrayList(contents.map { it.latitude }))
                bundle.putSerializable(KEY_LONGITUDES, ArrayList(contents.map { it.longitude }))
                bundle.putStringArrayList(
                    KEY_SOURCE_L1_DATA_IDS,
                    ArrayList(contents.map { it.sourceL1DataId }),
                )
                bundle.putSerializable(
                    KEY_START_TIMES,
                    ArrayList(contents.map { it.startTime?.toEpochMilli() }),
                )
                bundle.putSerializable(
                    KEY_END_TIMES,
                    ArrayList(contents.map { it.endTime?.toEpochMilli() }),
                )
                bundle.putStringArrayList(
                    KEY_EVENT_TITLES,
                    ArrayList(contents.map { it.eventTitle }),
                )
            }

            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            fun fromBundle(bundle: Bundle): List<ChipContent> {
                val titles = bundle.getStringArrayList(KEY_TITLES) ?: return emptyList()
                val subtitles = bundle.getStringArrayList(KEY_SUBTITLES) ?: return emptyList()
                val locationNames =
                    bundle.getStringArrayList(KEY_LOCATION_NAMES) ?: return emptyList()
                val elementTypes =
                    bundle.getIntegerArrayList(KEY_ELEMENT_TYPES) ?: return emptyList()
                val sources = bundle.getStringArrayList(KEY_SOURCES) ?: return emptyList()
                val latitudes =
                    bundle.getSerializable(KEY_LATITUDES) as? ArrayList<Double?>
                        ?: return emptyList()
                val longitudes =
                    bundle.getSerializable(KEY_LONGITUDES) as? ArrayList<Double?>
                        ?: return emptyList()
                val sourceL1DataIds =
                    bundle.getStringArrayList(KEY_SOURCE_L1_DATA_IDS) ?: return emptyList()
                val startTimes =
                    (bundle.getSerializable(KEY_START_TIMES) as? ArrayList<Long?>)?.map {
                        it?.let { Instant.ofEpochMilli(it) }
                    } ?: return emptyList()
                val endTimes =
                    (bundle.getSerializable(KEY_END_TIMES) as? ArrayList<Long?>)?.map {
                        it?.let { Instant.ofEpochMilli(it) }
                    } ?: return emptyList()
                val eventTitles = bundle.getStringArrayList(KEY_EVENT_TITLES) ?: return emptyList()

                val sizes =
                    listOf(
                        titles.size,
                        subtitles.size,
                        locationNames.size,
                        elementTypes.size,
                        sources.size,
                        latitudes.size,
                        longitudes.size,
                        sourceL1DataIds.size,
                        startTimes.size,
                        endTimes.size,
                        eventTitles.size,
                    )
                val size = sizes.minOrNull() ?: 0

                val list = ArrayList<ChipContent>(size)
                for (i in 0 until size) {
                    list.add(
                        ChipContent(
                            titles[i],
                            subtitles[i],
                            locationNames[i],
                            elementTypes[i],
                            sources[i],
                            latitudes[i],
                            longitudes[i],
                            sourceL1DataIds[i],
                            startTimes[i],
                            endTimes[i],
                            eventTitles[i],
                        )
                    )
                }
                return list
            }
        }
    }
}
