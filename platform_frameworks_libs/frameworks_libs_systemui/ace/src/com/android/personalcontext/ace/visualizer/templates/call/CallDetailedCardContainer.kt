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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.personalcontext.ace.visualizer.R
import com.android.personalcontext.ace.visualizer.templates.call.CallVisualizerRowItemText.TextSize
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.IconSizeLarge
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.RoundedCornerSizeLarge
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.RoundedCornerSizeMedium
import com.android.personalcontext.ace.visualizer.templates.utils.RemoteActionUtils.execute

private const val TAG = "CallDetailedCardContainer"

/** The container for a single detailed card in the Magic Cue Call widget. */
@Composable
internal fun CallDetailedCardContainer(card: CallVisualizerDetailedCard) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
        shape = RoundedCornerShape(RoundedCornerSizeLarge),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            CardHeader(cardTitle = card.title, dataSource = card.dataSource)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (row in card.rows) {
                    CardRow(row)
                }
            }
            card.feedback?.let { CardFeedback(feedbackInsight = it, shouldReportEvent = true) }
        }
    }
}

/**
 * Represents a single row in the card. Not to be confused with a single item, which is represented
 * by [CardRowItem].
 */
@Composable
private fun CardRow(row: CallVisualizerRow) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        when (row) {
            is CallVisualizerRow.FullLength -> {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CardRowItem(row.value.item)
                }
            }
            is CallVisualizerRow.HalfHalfSplit -> {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CardRowItem(row.value.itemOne)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CardRowItem(row.value.itemTwo)
                }
            }
        }
    }
}

/** Represents a single item in a row. This is the smallest unit of data in a row. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardRowItem(rowItem: CallVisualizerRowItem) {
    @Composable
    fun getTextSize(textSize: TextSize): TextStyle =
        when (textSize) {
            TextSize.SMALL -> MaterialTheme.typography.labelSmall
            TextSize.MEDIUM -> MaterialTheme.typography.titleMedium
            TextSize.LARGE -> MaterialTheme.typography.titleLargeEmphasized
            else -> MaterialTheme.typography.labelMedium
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(RoundedCornerSizeMedium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .clearAndSetSemantics { this.contentDescription = rowItem.contentDescription }
        ) {
            Text(
                text = rowItem.label.text,
                style = getTextSize(rowItem.label.textSize),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rowItem.content.text,
                style = getTextSize(rowItem.content.textSize),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardHeader(cardTitle: String, dataSource: RemoteAction?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = cardTitle,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(0.dp))
        dataSource?.let { SourceNavigationButton(it) }
    }
}

@Composable
private fun SourceNavigationButton(dataSource: RemoteAction) {
    val context = LocalContext.current
    val buttonContentDescription = dataSource.contentDescription.toString()

    IconButton(modifier = Modifier.size(IconSizeLarge), onClick = { dataSource.execute(context) }) {
        Icon(
            modifier = Modifier.size(IconSizeLarge),
            painter = painterResource(R.drawable.gs_open_in_new_vd_theme_24),
            tint = MaterialTheme.colorScheme.secondary,
            contentDescription = buttonContentDescription,
        )
    }
}
