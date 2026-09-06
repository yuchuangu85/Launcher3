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
package com.android.personalcontext.ace.client.prototype.richcard

import android.os.Bundle
import com.android.personalcontext.ace.client.prototype.PrototypeHint
import com.android.personalcontext.ace.client.prototype.PrototypeHintId.RichCardLiveDataHintId

data class RichCardLiveDataHint(
    val clientSessionId: String = "",
    val cardIdsToLiveDataResultBundle: Map<String, Bundle?>,
) : PrototypeHint(RichCardLiveDataHintId, this) {
    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putString(CLIENT_SESSION_ID_KEY, clientSessionId)

        val cardToLiveDataResultMapBundle = Bundle()
        for ((cardId, liveDataResultBundle) in cardIdsToLiveDataResultBundle) {
            cardToLiveDataResultMapBundle.putBundle(cardId, liveDataResultBundle)
        }
        bundle.putBundle(CARD_IDS_TO_LIVE_DATA_RESULT_BUNDLE_KEY, cardToLiveDataResultMapBundle)
    }

    companion object : Creator {
        private const val CLIENT_SESSION_ID_KEY = "clientSessionId"
        private const val CARD_IDS_TO_LIVE_DATA_RESULT_BUNDLE_KEY = "cardIdsToLiveDataResultBundle"

        override fun create(bundle: Bundle): PrototypeHint {
            val clientSessionId = bundle.getString(CLIENT_SESSION_ID_KEY) ?: ""
            val cardToLiveDataResultMapBundle =
                bundle.getBundle(CARD_IDS_TO_LIVE_DATA_RESULT_BUNDLE_KEY) ?: Bundle()

            val cardIdsToLiveDataResultBundle = mutableMapOf<String, Bundle?>()
            for (key in cardToLiveDataResultMapBundle.keySet()) {
                cardIdsToLiveDataResultBundle[key] = cardToLiveDataResultMapBundle.getBundle(key)
            }

            return RichCardLiveDataHint(
                clientSessionId = clientSessionId,
                cardIdsToLiveDataResultBundle = cardIdsToLiveDataResultBundle,
            )
        }
    }
}
