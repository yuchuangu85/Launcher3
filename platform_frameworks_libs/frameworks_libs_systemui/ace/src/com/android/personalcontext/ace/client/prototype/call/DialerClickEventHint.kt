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
package com.android.personalcontext.ace.client.prototype.call

import android.os.Bundle
import com.android.personalcontext.ace.client.prototype.PrototypeHint
import com.android.personalcontext.ace.client.prototype.PrototypeHintId.DialerClickEventHintId

/** A hint for the click event on a Dialer Magic Cue card. */
data class DialerClickEventHint(val eventType: EventType) :
    PrototypeHint(DialerClickEventHintId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putInt(KEY_EVENT_TYPE, eventType.value)
    }

    companion object : Creator {
        private const val KEY_EVENT_TYPE = "event_type"

        override fun create(bundle: Bundle): PrototypeHint {
            val eventType = EventType.fromValue(bundle.getInt(KEY_EVENT_TYPE))
            return DialerClickEventHint(eventType)
        }
    }

    enum class EventType(val value: Int) {
        UNKNOWN(0),
        SUGGEST_MORE_CLICK(1);

        companion object {
            fun fromValue(value: Int): EventType = entries.find { it.value == value } ?: UNKNOWN
        }
    }
}
