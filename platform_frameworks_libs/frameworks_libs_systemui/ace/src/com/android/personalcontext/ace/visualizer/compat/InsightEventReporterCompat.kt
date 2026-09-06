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

package com.android.personalcontext.ace.visualizer.compat

import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.insight.ContextInsight
import com.android.personalcontext.ace.common.wrappers.IPublishedContextInsight
import com.android.personalcontext.ace.common.wrappers.IRenderToken

interface InsightEventReporterCompat {

    /**
     * Reports an event that occurred on a [childInsight] from a Renderer back to the Understander
     * that published it.
     *
     * @param childInsight The child insight the event occurred on.
     * @see android.service.personalcontext.PersonalContextManager.reportInsightEvent
     * @see android.service.personalcontext.insight.interaction.InsightEvent
     */
    fun PersonalContextManager.reportChildInsightEvent(
        publishedInsight: IPublishedContextInsight,
        childInsight: ContextInsight,
        eventType: Int,
        renderToken: IRenderToken,
    ) {
        reportInsightEvent(
            publishedInsight.unwrap() ?: return,
            eventType,
            renderToken.unwrap() ?: return,
        )
    }
}
