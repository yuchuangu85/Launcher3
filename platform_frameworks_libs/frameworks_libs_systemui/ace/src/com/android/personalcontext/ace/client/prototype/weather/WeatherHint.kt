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
import com.android.personalcontext.ace.client.prototype.PrototypeHint
import com.android.personalcontext.ace.client.prototype.PrototypeHintId.WeatherHintId
import java.util.UUID

/**
 * A hint for the Weather Event Suggestion / Location Suggestion use case. Check
 * go/sundog-ace-migration for more details.
 */
data class WeatherHint(
    /* account email for eligibility checking. */
    val accountEmail: String,
    /* The type of the weather suggestion. */
    val suggestionType: SuggestionType,
    /* The threshold in meters for the same location. */
    val sameLocationThresholdMeters: Int,
    /* The list of weather saved locations.
     * For Location Suggestion, we filter out those locations.
     * For Event Suggestion, we only search events for the first location. */
    val locationInfos: List<LocationInfo>,
    /* The weather description in subtitle for the event suggestion.Only for Event Suggestion. */
    val weatherDescription: String? = null,
    /* Client session UUID. */
    val clientSessionId: String = UUID.randomUUID().toString(),
) : PrototypeHint(WeatherHintId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putString(KEY_CLIENT_SESSION_ID, clientSessionId)
        bundle.putString(KEY_ACCOUNT_EMAIL, accountEmail)
        bundle.putInt(KEY_SUGGESTION_TYPE, suggestionType.value)
        bundle.putInt(KEY_SAME_LOCATION_THRESHOLD, sameLocationThresholdMeters)
        LocationInfo.toBundle(bundle, locationInfos)
        bundle.putString(KEY_WEATHER_DESCRIPTION, weatherDescription)
    }

    companion object : Creator {
        private const val KEY_CLIENT_SESSION_ID = "client_session_id"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_SUGGESTION_TYPE = "suggestion_type"
        private const val KEY_SAME_LOCATION_THRESHOLD = "same_location_threshold_meters"
        private const val KEY_WEATHER_DESCRIPTION = "weather_description"

        override fun create(bundle: Bundle): PrototypeHint =
            WeatherHint(
                clientSessionId =
                    bundle.getString(KEY_CLIENT_SESSION_ID) ?: UUID.randomUUID().toString(),
                accountEmail = bundle.getString(KEY_ACCOUNT_EMAIL) ?: "",
                suggestionType = SuggestionType.fromValue(bundle.getInt(KEY_SUGGESTION_TYPE)),
                sameLocationThresholdMeters = bundle.getInt(KEY_SAME_LOCATION_THRESHOLD),
                locationInfos = LocationInfo.fromBundle(bundle),
                weatherDescription = bundle.getString(KEY_WEATHER_DESCRIPTION),
            )
    }

    enum class SuggestionType(val value: Int) {
        EVENT(0),
        LOCATION(1);

        companion object {
            fun fromValue(value: Int): SuggestionType = entries.find { it.value == value } ?: EVENT
        }
    }

    data class LocationInfo(
        val locationPrimaryName: String?,
        val locationLatitude: Double?,
        val locationLongitude: Double?,
    ) {
        companion object {
            private const val KEY_NAMES = "location_names"
            private const val KEY_LATS = "location_lats"
            private const val KEY_LONGS = "location_longs"

            fun toBundle(bundle: Bundle, infos: List<LocationInfo>) {
                bundle.putStringArrayList(
                    KEY_NAMES,
                    ArrayList(infos.map { it.locationPrimaryName }),
                )
                bundle.putSerializable(KEY_LATS, ArrayList(infos.map { it.locationLatitude }))
                bundle.putSerializable(KEY_LONGS, ArrayList(infos.map { it.locationLongitude }))
            }

            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            fun fromBundle(bundle: Bundle): List<LocationInfo> {
                val names = bundle.getStringArrayList(KEY_NAMES) ?: return emptyList()
                val lats =
                    bundle.getSerializable(KEY_LATS) as? ArrayList<Double?> ?: return emptyList()
                val longs =
                    bundle.getSerializable(KEY_LONGS) as? ArrayList<Double?> ?: return emptyList()

                val size = minOf(names.size, lats.size, longs.size)
                val list = ArrayList<LocationInfo>(size)
                for (i in 0 until size) {
                    list.add(LocationInfo(names[i], lats[i], longs[i]))
                }
                return list
            }
        }
    }
}
