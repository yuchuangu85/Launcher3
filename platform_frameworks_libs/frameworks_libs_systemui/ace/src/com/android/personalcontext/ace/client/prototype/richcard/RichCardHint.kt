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
import com.android.personalcontext.ace.client.prototype.PrototypeHintId.RichCardHintId

/**
 * A hint for the rich card use case. It is published from the client app to indicate that a card
 * preview chip is clicked and a rich card should be rendered.
 *
 * @property clientSessionId The DAG client session ID for generating the card preview chip that is
 *   clicked.
 * @property cardIds A list of card IDs within that DAG session that will be rendered as rich cards
 */
data class RichCardHint(val clientSessionId: String, val cardIds: List<String>) :
    PrototypeHint(RichCardHintId, this) {
    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putString(CLIENT_SESSION_ID_KEY, clientSessionId)
        bundle.putStringArrayList(CARD_IDS_KEY, ArrayList(cardIds))
    }

    companion object : Creator {
        private const val CLIENT_SESSION_ID_KEY = "clientSessionId"
        private const val CARD_IDS_KEY = "cardIds"

        override fun create(bundle: Bundle): PrototypeHint =
            RichCardHint(
                bundle.getString(CLIENT_SESSION_ID_KEY) ?: "",
                bundle.getStringArrayList(CARD_IDS_KEY) ?: emptyList(),
            )
    }
}
