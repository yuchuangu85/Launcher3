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

/** Note: Before accessing any of the vals, first check [isCardInsight]. */
interface CardInsightCompat {

    /** Whether the [ContextInsight] is a card insight. */
    fun ContextInsight.isCardInsight(): Boolean =
        ((this as? InsightCollection)?.insights?.firstOrNull() as? BundleInsight)
            ?.insightTypeName == "CardInsight" &&
            runCatching { title }.isSuccess &&
            runCatching { header }.isSuccess &&
            runCatching { body }.isSuccess &&
            runCatching { footer }.isSuccess &&
            runCatching { actions }.isSuccess

    /** Returns the title of this card insight. */
    val ContextInsight.title: ContextInsight?
        get() = (this as InsightCollection).insights.getOrNull(1)

    /** Returns the header of this card insight. */
    val ContextInsight.header: ContextInsight?
        get() = (this as InsightCollection).insights.getOrNull(2)

    /** Returns the body of this card insight. */
    val ContextInsight.body: ContextInsight
        get() = (this as InsightCollection).insights[3]

    /** Returns the footer of this card insight. */
    val ContextInsight.footer: ContextInsight?
        get() = (this as InsightCollection).insights.getOrNull(4)

    /** Returns the actions of this card insight. */
    val ContextInsight.actions: ContextInsight?
        get() = (this as InsightCollection).insights.getOrNull(5)
}
