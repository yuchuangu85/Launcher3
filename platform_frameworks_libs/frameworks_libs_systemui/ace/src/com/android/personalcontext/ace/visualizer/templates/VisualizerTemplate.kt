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
package com.android.personalcontext.ace.visualizer.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.android.personalcontext.ace.common.wrappers.IInsightSurfaceClientInfo
import com.android.personalcontext.ace.common.wrappers.IPublishedContextInsight
import com.android.personalcontext.ace.common.wrappers.IRenderToken
import com.android.personalcontext.ace.visualizer.compat.InsightEventReporterCompat
import com.android.personalcontext.ace.visualizer.templates.utils.trafficshaperqueue.TrafficShaperQueue

/** Responsible for constructing the remote template UI for a particular template type. */
interface VisualizerTemplate {

    /**
     * Returns the Composable UI content this template would render for the given
     * [publishedInsight].
     *
     * Special cases:
     * * Returns `null` if the inputs do not target this template.
     * * Throws an exception if the inputs are invalid.
     *
     * Implementations have access to ACE platform APIs via [LocalInsightSurfaceClientInfo],
     * [LocalRenderToken], [LocalPublishedContextInsight], [LocalInsightEventReporter].
     */
    fun handleInsight(publishedInsight: IPublishedContextInsight): (@Composable () -> Unit)?
}

/**
 * Provides a [android.service.personalcontext.embedded.InsightSurfaceClientInfo] that can be used
 * by the template.
 */
val LocalInsightSurfaceClientInfo: ProvidableCompositionLocal<IInsightSurfaceClientInfo> =
    compositionLocalOf {
        error("No InsightSurfaceClientInfo provided")
    }

/** Provides a [android.service.personalcontext.RenderToken] that can be used by the template. */
val LocalRenderToken: ProvidableCompositionLocal<IRenderToken> = compositionLocalOf {
    error("No RenderToken provided")
}

/**
 * Provides a [android.service.personalcontext.insight.PublishedContextInsight] that can be used by
 * the template.
 */
val LocalPublishedContextInsight: ProvidableCompositionLocal<IPublishedContextInsight> =
    compositionLocalOf {
        error("No PublishedContextInsight provided")
    }

/** Provides a [InsightEventReporterCompat] that can be used by the template. */
val LocalInsightEventReporter: ProvidableCompositionLocal<InsightEventReporterCompat> =
    compositionLocalOf {
        error("No InsightEventReporterCompat provided")
    }

/** Provides a [TrafficShaperQueue] that can be used by the template. */
val LocalTrafficShaperQueue: ProvidableCompositionLocal<TrafficShaperQueue> = compositionLocalOf {
    error("No TrafficShaperQueue provided")
}
