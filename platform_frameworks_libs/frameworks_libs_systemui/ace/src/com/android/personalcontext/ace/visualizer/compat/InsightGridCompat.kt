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

import android.service.personalcontext.insight.BundleInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.InsightCollection
import com.android.personalcontext.ace.common.InsightGridItem

/** Note: Before accessing any of the vals, first check [isInsightGrid]. */
interface InsightGridCompat {

    /** Whether the [ContextInsight] is an insight grid. */
    fun ContextInsight.isInsightGrid(): Boolean =
        ((this as? InsightCollection)?.insights?.firstOrNull() as? BundleInsight)
            ?.insightTypeName == "InsightGrid" && runCatching { items }.isSuccess

    /** Returns the items of this insight grid. */
    val ContextInsight.items: List<InsightGridItem>
        get() {
            val insights = (this as InsightCollection).insights.drop(1)
            val spans =
                (insights.first() as BundleInsight).dataBundle.getIntArray("spans")!!.toList()

            return insights.zip(spans).map { (insight, span) -> InsightGridItem(insight, span) }
        }
}
