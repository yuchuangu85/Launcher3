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

package com.android.personalcontext.ace.visualizer.templates.call

import android.service.personalcontext.hint.CallHint
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.personalcontext.ace.common.FindHintUtils.findContextHint
import com.android.personalcontext.ace.common.wrappers.IPublishedContextInsight
import com.android.personalcontext.ace.visualizer.compat.CardInsightCompat
import com.android.personalcontext.ace.visualizer.templates.VisualizerTemplate
import com.android.personalcontext.ace.visualizer.templates.call.CallInsightConverter.toCallVisualizerWidget
import javax.inject.Inject

/** A [VisualizerTemplate] that renders the Magic Cue Call UI. */
class CallVisualizerTemplate
@Inject
internal constructor(private val cardInsightCompat: CardInsightCompat) : VisualizerTemplate {

    override fun handleInsight(
        publishedInsight: IPublishedContextInsight
    ): (@Composable () -> Unit)? {
        Log.i(TAG, "[CallEmbedded] handleInsight init")
        val insight = publishedInsight.insight
        if (insight.findContextHint<CallHint>() == null) {
            Log.v(TAG, "[CallEmbedded] No CallHint found")
            return null
        }
        Log.i(TAG, "[CallEmbedded] CallHint found, converting to widget")
        val widget: CallVisualizerWidget = insight.toCallVisualizerWidget(cardInsightCompat)
        Log.i(TAG, "[CallEmbedded] Returning CallTemplate")
        return { CallTemplate(widget) }
    }

    companion object {
        private const val TAG = "CallVisualizerTemplate"
    }
}

@Composable
private fun CallTemplate(widget: CallVisualizerWidget) {
    CallTheme { CallWidgetContainer(widget) }
}

@Composable
private fun CallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography()) { content() }
}
