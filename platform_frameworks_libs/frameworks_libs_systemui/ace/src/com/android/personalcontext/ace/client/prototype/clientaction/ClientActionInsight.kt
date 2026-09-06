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
@file:Suppress("NewApi")

package com.android.personalcontext.ace.client.prototype.clientaction

import android.os.Bundle
import android.os.Parcelable
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.InsightDisplayDetails
import com.android.personalcontext.ace.client.prototype.PrototypeInsight
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.ClientActionInsightId
import kotlinx.parcelize.Parcelize

/** Base interface for parameters specific to different types of client actions. */
sealed interface ClientActionParams : Parcelable

/**
 * Parameters for the SHARE_PHOTO client action.
 *
 * @property query The search query for the photo share action.
 */
@Parcelize data class SharePhotoParams(val query: String) : ClientActionParams

/**
 * Parameters for the SHOW_CARDS client action. When client app receives this action, it should:
 * 1. Create card DUI session.
 * 2. Render the card embedded UI via publishing a @link{CardHint}, which contains the
 *    clientSessionId and a list of card IDs that is wrapped within the parameters of this action.
 * 3. For cards with LiveDataQueryBundle are provided, fetch the data from GMSCore and wrap all the
 *    LiveDataResults into the @link{CardLiveDataHint} and publish it.
 *
 * @property clientSessionId The DAG client session ID of the request in PSI.
 * @property liveDataQueryBundle A map of card IDs to their corresponding optional live data query
 *   bundle.
 */
@Parcelize
data class ShowCardsParams(
    val clientSessionId: String,
    val liveDataQueryBundle: Map<String, Bundle?>,
) : ClientActionParams

/**
 * An insight for the client action.
 *
 * @property clientActionParams The parameters specific to the action type.
 * @property insightDisplayDetails Display details for the client action.
 * @property originHints The origin hints of the insight.
 */
data class ClientActionInsight(
    val clientActionParams: ClientActionParams,
    val insightDisplayDetails: InsightDisplayDetails,
    override val originHints: Set<PublishedContextHint>,
) : PrototypeInsight(ClientActionInsightId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putParcelable(CLIENT_ACTION_PARAMS_KEY, clientActionParams)
        bundle.putParcelable(INSIGHT_DISPLAY_DETAILS_KEY, insightDisplayDetails)
    }

    companion object : Creator {
        const val CLIENT_ACTION_PARAMS_KEY = "CLIENT_ACTION_PARAMS_KEY"
        const val INSIGHT_DISPLAY_DETAILS_KEY = "INSIGHT_DISPLAY_DETAILS_KEY"

        override fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight {
            bundle.classLoader = ClientActionParams::class.java.classLoader
            return ClientActionInsight(
                clientActionParams =
                    requireNotNull(
                        bundle.getParcelable(
                            CLIENT_ACTION_PARAMS_KEY,
                            ClientActionParams::class.java,
                        )
                    ),
                insightDisplayDetails =
                    requireNotNull(
                        bundle.getParcelable(
                            INSIGHT_DISPLAY_DETAILS_KEY,
                            InsightDisplayDetails::class.java,
                        )
                    ),
                originHints = originHints,
            )
        }
    }
}
