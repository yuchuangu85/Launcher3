/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.cuebar.logger

import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.RenderToken
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.PublishedContextInsight
import android.service.personalcontext.insight.interaction.InsightEvent
import android.util.Log
import com.android.personalcontext.ace.common.FlatIndexUtils.flatIndexOf
import com.android.personalcontext.ace.common.PackedIntUtils.packValue
import com.android.personalcontext.ace.common.wrappers.wrap

/** Logger for Ambient Cue Ace insights. */
class AmbientCueAceLogger(private val personalContextManager: PersonalContextManager?) {
    var lastPublishedInsight: PublishedContextInsight? = null
        private set

    var lastRenderToken: RenderToken? = null
        private set

    fun onInsightReceived(insight: PublishedContextInsight, token: RenderToken) {
        lastPublishedInsight = insight
        lastRenderToken = token
    }

    fun reportCloseEvent() {
        reportInsightEvent(InsightEvent.EVENT_USER_DISMISS)
    }

    fun reportInsightEvent(event: Int, childInsight: ContextInsight? = null) {
        val publishedInsight = lastPublishedInsight
        val token = lastRenderToken
        if (publishedInsight != null && token != null) {
            val flatIndex =
                if (childInsight != null) publishedInsight.wrap().flatIndexOf(childInsight) else 0
            Log.d(TAG, "reportInsightEvent: $event, index: $flatIndex")
            val packedEvent = event.packValue(flatIndex)
            personalContextManager?.reportInsightEvent(publishedInsight, packedEvent, token)
        }
    }

    companion object {
        private const val TAG = "AmbientCueAceLogger"
    }
}
