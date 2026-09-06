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
import android.graphics.drawable.Icon
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight

/**
 * Data class for the Call Visualizer Widget.
 *
 * @property generalCardsEmails A list of general cards for emails.
 * @property generalCardsMessages A list of general cards for messages.
 * @property detailedCards A list of detailed cards.
 * @property ctaDisplayMoreResults The CTA to display more results.
 * @property aiDisclaimer The AI disclaimer.
 */
data class CallVisualizerWidget(
    val generalCardsEmails: List<CallVisualizerGeneralCard>,
    val generalCardsMessages: List<CallVisualizerGeneralCard>,
    val detailedCards: List<CallVisualizerDetailedCard>,
    val ctaDisplayMoreResults: DisplayInsight?,
    val aiDisclaimer: DisplayInsight?,
)

/**
 * Data class for the Call Visualizer General Card.
 *
 * @property title The title of the card.
 * @property date The date of the card.
 * @property detailedText The detailed text of the card.
 * @property dataSource The data source of the card.
 * @property cardExpandButtonAccessibilityContentDescription The accessibility content description
 *   of the card expand button.
 * @property listUuid A random UUID so Jetpack Compose can keep track of the cards in a list.
 *   Created by and used by the frontend only. Not to be used for any other purposes.
 */
data class CallVisualizerGeneralCard(
    val title: String,
    val date: String,
    val detailedText: String? = null,
    val detailedTextIcon: Icon? = null,
    val dataSource: RemoteAction? = null,
    val cardExpandButtonAccessibilityContentDescription: String,
    val listUuid: String,
    val originalInsight: ContextInsight,
)

/**
 * Data class for the Call Visualizer Detailed Card.
 *
 * @property title The title of the card.
 * @property rows The rows of the card.
 * @property dataSource The data source of the card.
 * @property listUuid A random UUID so Jetpack Compose can keep track of the cards in a list.
 *   Created by and used by the frontend only. Not to be used for any other purposes.
 * @property feedback The feedback for the card.
 */
data class CallVisualizerDetailedCard(
    val title: String,
    val rows: List<CallVisualizerRow> = emptyList(),
    val dataSource: RemoteAction? = null,
    val listUuid: String,
    val feedback: DisplayInsight? = null,
)

/**
 * The layout type of the row in the Call Visualizer Detailed Card.
 *
 * @property UNKNOWN The unknown layout type.
 * @property FULL_LENGTH The full length layout type.
 * @property HALF_HALF_SPLIT The half half split layout type.
 * @property SEVEN_THREE_SPLIT The seven three split layout type.
 */
enum class CallVisualizerRowLayoutType {
    UNKNOWN,
    FULL_LENGTH,
    HALF_HALF_SPLIT,
    SEVEN_THREE_SPLIT,
}

/**
 * A sealed class representing a row in the Call Visualizer Detailed Card.
 *
 * @property FullLength A data class representing a full length row. The value is a
 *   [CallVisualizerFullLengthRow].
 * @property HalfHalfSplit A data class representing a half half split row. The value is a
 *   [CallVisualizerTwoItemRow].
 */
sealed class CallVisualizerRow {
    data class FullLength(val value: CallVisualizerFullLengthRow) : CallVisualizerRow()

    data class HalfHalfSplit(val value: CallVisualizerTwoItemRow) : CallVisualizerRow()
}

/** Data class for a full length row in the Call Visualizer Detailed Card. */
data class CallVisualizerFullLengthRow(val item: CallVisualizerRowItem)

/** Data class for a two item row in the Call Visualizer Detailed Card. */
data class CallVisualizerTwoItemRow(
    val itemOne: CallVisualizerRowItem,
    val itemTwo: CallVisualizerRowItem,
)

/**
 * Data class for a row item in the Call Visualizer Detailed Card.
 *
 * @property label The label of the row item.
 * @property content The content of the row item.
 * @property contentSummary The summary of the content of the row item.
 * @property contentDescription The content description of the row item.
 */
data class CallVisualizerRowItem(
    val label: CallVisualizerRowItemText,
    val content: CallVisualizerRowItemText,
    val contentSummary: CallVisualizerRowItemText? = null,
    val contentDescription: String,
)

/**
 * Data class for the text in a row item in the Call Visualizer Detailed Card.
 *
 * @property text The text of the row item.
 * @property textSize The text size of the row item.
 */
data class CallVisualizerRowItemText(val text: String, val textSize: TextSize) {
    enum class TextSize {
        UNKNOWN,
        SMALL,
        MEDIUM,
        LARGE,
    }
}

/**
 * Data class for the generic content descriptions in the Call Visualizer Widget.
 *
 * @property goodFeedbackContentDescription The content description for the good feedback button.
 * @property badFeedbackContentDescription The content description for the bad feedback button.
 * @property feedbackSubmittedStateDescription The content description for the feedback submitted
 *   state.
 * @property feedbackNotSubmittedStateDescription The content description for the feedback not
 *   submitted state.
 * @property cardSentimentLabel The label for the card sentiment.
 * @property cardExpandedAccessibilityStateDescription The accessibility state description for the
 *   card expanded state.
 * @property cardCollapsedAccessibilityStateDescription The accessibility state description for the
 *   card collapsed state.
 * @property cardCollapsedAccessibilityClickLabel The accessibility click label for the card
 *   collapsed state.
 * @property cardExpandedAccessibilityClickLabel The accessibility click label for the card expanded
 *   state.
 */
data class CallVisualizerGenericContentDescriptions(
    val goodFeedbackContentDescription: String,
    val badFeedbackContentDescription: String,
    val feedbackSubmittedStateDescription: String,
    val feedbackNotSubmittedStateDescription: String,
    val cardSentimentLabel: String,
    val cardExpandedAccessibilityStateDescription: String,
    val cardCollapsedAccessibilityStateDescription: String,
    val cardCollapsedAccessibilityClickLabel: String,
    val cardExpandedAccessibilityClickLabel: String,
)
