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

package com.android.personalcontext.ace.common.wrappers

import android.service.personalcontext.insight.interaction.InsightEvent
import androidx.annotation.VisibleForTesting

/** Wrapper interface for [InsightEvent]. */
sealed interface IInsightEvent {

    /**
     * Returns the unwrapped [InsightEvent]. May return null if originally wrapped from a unit test,
     * where constructing an instance of [InsightEvent] is not possible.
     */
    fun unwrap(): InsightEvent?

    /** @see InsightEvent.getEventType */
    val eventType: Int

    /** @see InsightEvent.getInsight */
    val insight: IPublishedContextInsight

    /** @see InsightEvent.getTimestamp */
    val timestamp: Long

    /** @see InsightEvent.getRenderToken */
    val renderToken: IRenderToken
}

/** Creates an [IInsightEvent] from an [InsightEvent]. */
fun InsightEvent.wrap(): IInsightEvent = InsightEventWrapper(this)

private class InsightEventWrapper(private val original: InsightEvent) : IInsightEvent {
    override fun unwrap() = original

    override val eventType: Int
        get() = original.eventType

    override val insight: IPublishedContextInsight
        get() = original.insight.wrap()

    override val timestamp: Long
        get() = original.timestamp

    override val renderToken: IRenderToken
        get() = original.renderToken.wrap()
}

@VisibleForTesting
class InsightEventForTesting(
    override val eventType: Int,
    override val insight: IPublishedContextInsight,
    override val timestamp: Long = 0L,
    override val renderToken: IRenderToken = RenderTokenForTesting(),
) : IInsightEvent {
    override fun unwrap() = null
}
