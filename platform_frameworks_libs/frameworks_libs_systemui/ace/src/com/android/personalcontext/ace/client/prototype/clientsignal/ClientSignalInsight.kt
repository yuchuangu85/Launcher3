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
package com.android.personalcontext.ace.client.prototype.clientsignal

import android.os.Bundle
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import com.android.personalcontext.ace.client.prototype.PrototypeInsight
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.ClientSignalInsightId

/**
 * An insight for the client signal use case.
 *
 * @property type The type of client signal.
 * @property data The data of the client signal.
 * @property originHints The origin hints of the insight.
 */
data class ClientSignalInsight(
    val type: ClientSignalType,
    val data: Boolean,
    override val originHints: Set<PublishedContextHint>,
) : PrototypeInsight(ClientSignalInsightId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putInt(TYPE_KEY, type.ordinal)
        bundle.putBoolean(DATA_KEY, data)
    }

    companion object : Creator {
        const val TYPE_KEY = "TYPE_KEY"
        const val DATA_KEY = "DATA_KEY"

        override fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight =
            ClientSignalInsight(
                type = ClientSignalType.entries[bundle.getInt(TYPE_KEY)],
                data = bundle.getBoolean(DATA_KEY),
                originHints = originHints,
            )
    }
}
