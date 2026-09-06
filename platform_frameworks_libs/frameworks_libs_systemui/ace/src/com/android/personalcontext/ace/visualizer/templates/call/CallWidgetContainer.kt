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

import android.annotation.SuppressLint
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.interaction.InsightEvent
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.personalcontext.ace.visualizer.R
import com.android.personalcontext.ace.visualizer.templates.LocalInsightEventReporter
import com.android.personalcontext.ace.visualizer.templates.LocalInsightSurfaceClientInfo
import com.android.personalcontext.ace.visualizer.templates.LocalPublishedContextInsight
import com.android.personalcontext.ace.visualizer.templates.LocalRenderToken
import com.android.personalcontext.ace.visualizer.templates.LocalTrafficShaperQueue

private const val TAG = "CallWidgetContainer"

/** The container for the Magic Cue Call widget. */
@SuppressLint("FlaggedApi", "NewApi")
@Composable
internal fun CallWidgetContainer(widget: CallVisualizerWidget) {
    val scrollState = rememberScrollState()
    val info = LocalInsightSurfaceClientInfo.current
    var isDisplayingMoreResults by remember { mutableStateOf(false) }

    val detailedCards = widget.detailedCards
    val generalCardsEmails = widget.generalCardsEmails
    val generalCardsMessages = widget.generalCardsMessages

    val isDisplayMoreResultsEnabled =
        remember(detailedCards, generalCardsEmails, generalCardsMessages) {
            getIsDisplayingMoreResultsEnabled(
                detailedCards,
                generalCardsEmails,
                generalCardsMessages,
            )
        }

    val generalCardsToDisplay =
        remember(detailedCards, generalCardsEmails, generalCardsMessages, isDisplayingMoreResults) {
            getGeneralCardsToDisplay(
                detailedCards,
                generalCardsEmails,
                generalCardsMessages,
                isDisplayingMoreResults || widget.ctaDisplayMoreResults == null,
            )
        }

    Column(
        modifier = Modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (detailedCard in detailedCards) {
            CallDetailedCardContainer(card = detailedCard)
        }

        CallGeneralCardsContainer(cards = generalCardsToDisplay)

        widget.aiDisclaimer?.let { aiDisclaimer -> WidgetAiDisclaimer(aiDisclaimer) }

        if (
            widget.ctaDisplayMoreResults != null &&
                !isDisplayingMoreResults &&
                isDisplayMoreResultsEnabled
        ) {
            WidgetShowAllResultsButton(
                widget.ctaDisplayMoreResults,
                onClick = {
                    isDisplayingMoreResults = !isDisplayingMoreResults
                    info.onReceiveInsight(widget.ctaDisplayMoreResults)
                },
            )
        }
    }
}

@Composable
private fun WidgetAiDisclaimer(aiDisclaimer: DisplayInsight) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(R.drawable.gs_call_info_vd_theme_24),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = aiDisclaimer.details.title.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WidgetShowAllResultsButton(ctaInsight: DisplayInsight, onClick: () -> Unit) {
    val context = LocalContext.current
    val trafficShaperQueue = LocalTrafficShaperQueue.current
    val insightEventReporter = LocalInsightEventReporter.current
    val publishedInsight = LocalPublishedContextInsight.current
    val renderToken = LocalRenderToken.current

    val personalContextManager = remember {
        context.getSystemService(PersonalContextManager::class.java)
    }

    fun reportEvent(event: Int) {
        trafficShaperQueue.enqueue(
            action = {
                with(insightEventReporter) {
                    personalContextManager?.reportChildInsightEvent(
                        publishedInsight,
                        ctaInsight,
                        event,
                        renderToken,
                    )
                }
            }
        ) { e: Exception ->
            Log.e(
                TAG,
                "[CallEmbedded] #WidgetShowAllResultsButton Failed to report InsightEvent: $event",
                e,
            )
        }
    }

    LaunchedEffect(Unit) { reportEvent(InsightEvent.EVENT_SHOW) }

    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            reportEvent(InsightEvent.EVENT_USER_TAP)
            onClick()
        },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding =
            PaddingValues(
                horizontal =
                    ButtonDefaults.ContentPadding.calculateLeftPadding(
                        LayoutDirection.Ltr
                    ), // Keep the horizontal defaults of ButtonDefaults
                vertical = 10.dp,
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.expand_collapse_results),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
            Text(
                text = ctaInsight.details.title.toString(),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private object CallWidgetContainerConstants {
    val DEFAULT_MAX_GENERAL_CARDS_TO_DISPLAY = 3
}

/** Determines whether the "More Results" button should be displayed. */
private fun getIsDisplayingMoreResultsEnabled(
    detailedCardsList: List<CallVisualizerDetailedCard>,
    generalCardsEmails: List<CallVisualizerGeneralCard>,
    generalCardsMessages: List<CallVisualizerGeneralCard>,
): Boolean {
    val generalCardsMerged = generalCardsEmails + generalCardsMessages

    val isDisplayMoreResultsEnabled =
        if (detailedCardsList.isNotEmpty()) {
            // If there is a detailed card and there are also general cards, display the "more
            // results"
            // button.
            generalCardsMerged.isNotEmpty()
        } else {
            // If there is no detailed card but there are more than
            // DEFAULT_MAX_GENERAL_CARDS_TO_DISPLAY
            // general cards of a single type, display the "more results" button.
            Math.max(generalCardsEmails.size, generalCardsMessages.size) >
                CallWidgetContainerConstants.DEFAULT_MAX_GENERAL_CARDS_TO_DISPLAY
        }
    return isDisplayMoreResultsEnabled
}

/**
 * Determines which general cards to display.
 * 1. If there are no detailed cards, display up to
 *    [CallWidgetContainerConstants.DEFAULT_MAX_GENERAL_CARDS_TO_DISPLAY] general cards of each
 *    type.
 * 2. If there are detailed cards, do not display any general cards by default.
 * 3. If [isDisplayingMoreResults] is true, display all general cards.
 */
private fun getGeneralCardsToDisplay(
    detailedCardsList: List<CallVisualizerDetailedCard>,
    generalCardsEmails: List<CallVisualizerGeneralCard>,
    generalCardsMessages: List<CallVisualizerGeneralCard>,
    isDisplayingMoreResults: Boolean,
): List<CallVisualizerGeneralCard> {
    val generalCardsMerged = generalCardsEmails + generalCardsMessages
    val isOnlyDisplayingGeneralCards =
        detailedCardsList.isEmpty() && generalCardsMerged.isNotEmpty()

    val generalCardsEmailsNumToDisplay =
        if (isDisplayingMoreResults) {
            generalCardsEmails.size
        } else {
            CallWidgetContainerConstants.DEFAULT_MAX_GENERAL_CARDS_TO_DISPLAY
        }
    val generalCardsMessagesNumToDisplay =
        if (isDisplayingMoreResults) {
            generalCardsMessages.size
        } else {
            CallWidgetContainerConstants.DEFAULT_MAX_GENERAL_CARDS_TO_DISPLAY
        }

    val generalCardsToDisplay =
        if (isOnlyDisplayingGeneralCards) {
            generalCardsEmails.take(generalCardsEmailsNumToDisplay) +
                generalCardsMessages.take(generalCardsMessagesNumToDisplay)
        } else if (detailedCardsList.isNotEmpty() && isDisplayingMoreResults) {
            generalCardsMerged
        } else {
            emptyList()
        }

    return generalCardsToDisplay
}
