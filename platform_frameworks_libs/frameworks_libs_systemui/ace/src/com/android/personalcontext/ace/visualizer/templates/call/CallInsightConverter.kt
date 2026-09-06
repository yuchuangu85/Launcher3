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

import android.app.RemoteAction
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightActionDetails
import android.service.personalcontext.insight.InsightCollection
import android.util.Log
import com.android.personalcontext.ace.visualizer.compat.CardInsightCompat
import java.util.UUID

internal object CallInsightConverter {
    private val TAG = "CallInsightConverter"

    /**
     * Converts a top-level [ContextInsight] to a [CallVisualizerWidget].
     *
     * The top-level [ContextInsight] is expected to be a [CardInsight]. The top-level [CardInsight]
     * is expected to have the following properties:
     * 1. [Required] A body [InsightCollection] representing the dynamic data from OSI
     * 2. [Optional] A footer [DisplayInsight], representing the AI disclaimer
     * 3. [Optional] An actions [InsightCollection] containing a [DisplayInsight], representing the
     *    "Show more" button
     */
    fun ContextInsight.toCallVisualizerWidget(
        // TODO Use dagger injection instead of prop drilling
        cardInsightCompat: CardInsightCompat
    ): CallVisualizerWidget =
        with(cardInsightCompat) {
            Log.i(
                TAG,
                "[CallEmbedded] #toCallVisualizerWidget getting top level widget CardInsight",
            )
            val mainInsight = this@toCallVisualizerWidget
            if (!mainInsight.isCardInsight()) {
                error(
                    "[CallEmbedded] #toCallVisualizerWidget Expected mainInsight to be a CardInsight, actual: ${mainInsight.javaClass.simpleName}."
                )
            }

            val dynamicDataInsightCollection =
                (mainInsight.body as? InsightCollection)
                    ?: error(
                        "[CallEmbedded] #toCallVisualizerWidget Expected mainInsight.body to be an InsightCollection, actual: ${mainInsight.body.javaClass.simpleName}."
                    )
            val dynamicData = dynamicDataInsightCollection.insights
            if (dynamicData.size != 3) {
                error(
                    "[CallEmbedded] #toCallVisualizerWidget Expected body to have 3 inner insights, actual: ${dynamicData.size}"
                )
            }
            val detailedCardsInsight = dynamicData.getOrNull(0) as? InsightCollection
            val generalCardsEmailsInsight = dynamicData.getOrNull(1) as? InsightCollection
            val generalCardsMessagesInsight = dynamicData.getOrNull(2) as? InsightCollection

            val detailedCards = detailedCardsInsight?.toDetailedCards() ?: emptyList()
            val generalCardsEmails =
                generalCardsEmailsInsight?.toCallVisualizerGeneralCards() ?: emptyList()
            val generalCardsMessages =
                generalCardsMessagesInsight?.toCallVisualizerGeneralCards() ?: emptyList()

            val aiDisclaimer: DisplayInsight? = mainInsight.footer?.toAiDisclaimer()
            val ctaDisplayMoreResults: DisplayInsight? =
                mainInsight.actions?.toCtaDisplayMoreResults()

            if (
                detailedCards.isEmpty() &&
                    generalCardsEmails.isEmpty() &&
                    generalCardsMessages.isEmpty()
            ) {
                error(
                    "[CallEmbedded] #toCallVisualizerWidget No actual cards were included in the InsightCollection."
                )
            }

            Log.i(
                TAG,
                "[CallEmbedded] #toCallVisualizerWidget Returning CallVisualizerWidget with ${detailedCards.size} detailed cards, ${generalCardsEmails.size} general cards emails, ${generalCardsMessages.size} general cards messages",
            )

            return CallVisualizerWidget(
                detailedCards = detailedCards,
                generalCardsEmails = generalCardsEmails,
                generalCardsMessages = generalCardsMessages,
                aiDisclaimer = aiDisclaimer,
                ctaDisplayMoreResults = ctaDisplayMoreResults,
            )
        }

    /** Converts an [InsightCollection] to a list of [CallVisualizerDetailedCard]. */
    private fun InsightCollection.toDetailedCards(): List<CallVisualizerDetailedCard> {
        return this.insights.mapNotNull { detailedCardInsight: ContextInsight ->
            (detailedCardInsight as? InsightCollection)?.toSingleDetailedCard()
        }
    }

    /**
     * Converts an [InsightCollection] to a single [CallVisualizerDetailedCard].
     *
     * The [InsightCollection] is expected to have 3 insights in the following order:
     * 1. A header [DisplayInsight], representing the title of the detailed card
     * 2. A rows [InsightCollection], representing a single row of "details"
     * 3. An action [ActionableInsight] for deeplinks
     * 4. A feedback [DisplayInsight] for providing feedback
     *
     * If any of the insights are not in the expected order, or are not the expected type, null will
     * be returned.
     */
    private fun InsightCollection.toSingleDetailedCard(): CallVisualizerDetailedCard? {
        val detailedCollection = this as? InsightCollection ?: return null
        val elements: List<ContextInsight> = detailedCollection.insights
        if (elements.size != 4) return null

        val headerInsight = elements.getOrNull(0) as? DisplayInsight ?: return null
        val rowsInsight = elements.getOrNull(1) as? InsightCollection ?: return null
        val actionInsight = elements.getOrNull(2) as? ActionableInsight
        val feedbackInsight = elements.getOrNull(3) as? DisplayInsight

        val title = headerInsight.details.title?.toString() ?: ""
        val rows = rowsInsight.toCallVisualizerRows()
        val dataSource = actionInsight?.actionDetails?.toRemoteAction()

        return CallVisualizerDetailedCard(
            title = title,
            rows = rows,
            dataSource = dataSource,
            listUuid = UUID.randomUUID().toString(),
            feedback = feedbackInsight,
        )
    }

    /** Converts an [InsightCollection] to a list of [CallVisualizerRow]. */
    private fun InsightCollection.toCallVisualizerRows(): List<CallVisualizerRow> {
        return this.insights.mapNotNull { rowInsight -> rowInsight.toSingleRow() }
    }

    /** Converts a [ContextInsight] to a single [CallVisualizerRow]. */
    private fun ContextInsight.toSingleRow(): CallVisualizerRow? {
        return when (this) {
            is DisplayInsight -> CallVisualizerRow.FullLength(this.toCallVisualizerFullLengthRow())
            is InsightCollection -> { // Split Row
                val items = this.insights
                if (items.size == 2) {
                    val itemOne = items.getOrNull(0) as? DisplayInsight ?: return@toSingleRow null
                    val itemTwo = items.getOrNull(1) as? DisplayInsight ?: return@toSingleRow null

                    CallVisualizerRow.HalfHalfSplit(
                        CallVisualizerTwoItemRow(
                            itemOne = itemOne.toCallVisualizerRowItem(),
                            itemTwo = itemTwo.toCallVisualizerRowItem(),
                        )
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /** Converts a [DisplayInsight] to a [CallVisualizerFullLengthRow]. */
    private fun DisplayInsight.toCallVisualizerFullLengthRow(): CallVisualizerFullLengthRow =
        CallVisualizerFullLengthRow(item = this.toCallVisualizerRowItem())

    /** Converts a [DisplayInsight] to a [CallVisualizerRowItem]. */
    private fun DisplayInsight.toCallVisualizerRowItem(): CallVisualizerRowItem {
        val displayDetails = this.details
        return CallVisualizerRowItem(
            label =
                CallVisualizerRowItemText(
                    text = displayDetails.title?.toString() ?: "",
                    textSize = CallVisualizerRowItemText.TextSize.MEDIUM,
                ),
            content =
                CallVisualizerRowItemText(
                    text = displayDetails.contentDescription?.toString() ?: "",
                    textSize = CallVisualizerRowItemText.TextSize.MEDIUM,
                ),
            contentDescription = displayDetails.contentDescription?.toString() ?: "",
        )
    }

    /** Converts an [InsightCollection] to a list of [CallVisualizerGeneralCard]. */
    private fun InsightCollection.toCallVisualizerGeneralCards(): List<CallVisualizerGeneralCard> {
        return this.insights.mapNotNull { generalCardInsight: ContextInsight ->
            (generalCardInsight as? ActionableInsight)?.toCallVisualizerGeneralCard()
                ?: (generalCardInsight as? DisplayInsight)?.toCallVisualizerGeneralCard()
        }
    }

    /** Converts an [ActionableInsight] to a [CallVisualizerGeneralCard]. */
    private fun ActionableInsight.toCallVisualizerGeneralCard(): CallVisualizerGeneralCard {
        val displayDetails = this.displayDetails
        val actionDetails = this.actionDetails

        return CallVisualizerGeneralCard(
            title = displayDetails.title?.toString() ?: "",
            date = displayDetails.subtitle?.toString() ?: "",
            detailedText = displayDetails.contentDescription?.toString() ?: "",
            detailedTextIcon = displayDetails.icon,
            dataSource = actionDetails.toRemoteAction(),
            cardExpandButtonAccessibilityContentDescription = "",
            listUuid = UUID.randomUUID().toString(),
            originalInsight = this,
        )
    }

    /**
     * Converts a [DisplayInsight] to a [CallVisualizerGeneralCard]. Only used if the
     * [ActionableInsight] is not available (ie. a deeplink is missing).
     */
    private fun DisplayInsight.toCallVisualizerGeneralCard(): CallVisualizerGeneralCard {
        val displayDetails = this.details

        return CallVisualizerGeneralCard(
            title = displayDetails.title?.toString() ?: "",
            date = displayDetails.subtitle?.toString() ?: "",
            detailedText = displayDetails.contentDescription?.toString() ?: "",
            detailedTextIcon = displayDetails.icon,
            dataSource = null,
            cardExpandButtonAccessibilityContentDescription = "",
            listUuid = UUID.randomUUID().toString(),
            originalInsight = this,
        )
    }

    /** Converts [InsightActionDetails] to [CallVisualizerResponseSource]. */
    private fun InsightActionDetails.toRemoteAction(): RemoteAction? =
        if (this.remoteAction?.actionIntent == null) {
            null
        } else {
            this.remoteAction
        }

    /**
     * The [InsightCollection] is expected to have a single [DisplayInsight] representing the "Show
     * more" button
     */
    private fun ContextInsight.toCtaDisplayMoreResults(): DisplayInsight? {
        val insights = (this as? InsightCollection)?.insights ?: return null

        return insights.getOrNull(0) as? DisplayInsight
    }

    /** Converts a [ContextInsight] to an AI disclaimer string. */
    private fun ContextInsight.toAiDisclaimer(): DisplayInsight? {
        val insight = (this as? DisplayInsight) ?: return null

        if (insight.details.title?.toString().isNullOrEmpty()) {
            return null
        }

        return insight
    }
}
