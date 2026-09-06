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
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.insight.interaction.InsightEvent
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.personalcontext.ace.visualizer.R
import com.android.personalcontext.ace.visualizer.templates.LocalInsightEventReporter
import com.android.personalcontext.ace.visualizer.templates.LocalPublishedContextInsight
import com.android.personalcontext.ace.visualizer.templates.LocalRenderToken
import com.android.personalcontext.ace.visualizer.templates.LocalTrafficShaperQueue
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.IconSizeLarge
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.IconSizeMedium
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.RoundedCornerSizeExtraSmall
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.RoundedCornerSizeLarge
import com.android.personalcontext.ace.visualizer.templates.utils.IconOrImage
import com.android.personalcontext.ace.visualizer.templates.utils.RemoteActionUtils.execute
import com.android.personalcontext.ace.visualizer.templates.utils.TintableIcon
import com.android.personalcontext.ace.visualizer.templates.utils.asTintableIcon

private const val TAG = "CallGeneralCardsContainer"

/** The container for a the simple cards in the Magic Cue Call widget. */
@Composable
internal fun CallGeneralCardsContainer(cards: List<CallVisualizerGeneralCard>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        for ((i, card) in cards.withIndex()) {
            val isFirstCard = i == 0
            val isLastCard = i == cards.size - 1

            key(card.listUuid) {
                CallGeneralCardContainer(
                    card = card,
                    isFirstCard = isFirstCard,
                    isLastCard = isLastCard,
                )
            }
        }
    }
}

/** Layout for a single general card. */
@VisibleForTesting
@Composable
internal fun CallGeneralCardContainer(
    card: CallVisualizerGeneralCard,
    isFirstCard: Boolean,
    isLastCard: Boolean,
) {
    // Metrics logging START
    val trafficShaperQueue = LocalTrafficShaperQueue.current
    val context = LocalContext.current
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
                        card.originalInsight,
                        event,
                        renderToken,
                    )
                }
            }
        ) { e: Exception ->
            Log.e(
                TAG,
                "[CallEmbedded] #CallGeneralCardContainer Failed to report InsightEvent: $event",
                e,
            )
        }
    }

    LaunchedEffect(Unit) { reportEvent(InsightEvent.EVENT_SHOW) }

    // Metrics logging END

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
        shape =
            RoundedCornerShape(
                topStart = if (isFirstCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
                topEnd = if (isFirstCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
                bottomStart =
                    if (isLastCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
                bottomEnd = if (isLastCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
            ),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) { this.role = Role.Button }
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (card.dataSource != null) {
                    SourceIcon(card.dataSource)
                    Spacer(modifier = Modifier.width(16.dp))
                } else {
                    // Ensure the detailedText and title are left aligned
                    Spacer(modifier = Modifier.width(IconSizeLarge + 16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    // Calculate the top padding needed for the date Text to align its top edge with
                    // the
                    // title Text.
                    // Only add padding if the title's top offset is larger than the date's.
                    val titleTextStyle = MaterialTheme.typography.titleMedium
                    val dateTextStyle = MaterialTheme.typography.bodySmall
                    val dateTopOffsetSp =
                        ((titleTextStyle.lineHeight.value - dateTextStyle.lineHeight.value) / 2)
                            .coerceAtLeast(0f)
                            .toInt()
                            .sp
                    val dateTopPaddingDp =
                        with(LocalDensity.current) { dateTopOffsetSp.toPx().toDp() }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false).animateContentSize(),
                            text = card.title,
                            style = titleTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines =
                                if (isExpanded) {
                                    2
                                } else {
                                    1
                                },
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            modifier = Modifier.padding(top = dateTopPaddingDp),
                            text = card.date,
                            style = dateTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                ExpandIcon(isExpanded = isExpanded)
            }

            card.detailedText?.let { detailedText ->
                Spacer(modifier = Modifier.height(6.dp))
                CallGeneralCardText(
                    isExpanded = isExpanded,
                    text = detailedText,
                    icon = card.detailedTextIcon,
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeedbackButtons(card.originalInsight)
                    card.dataSource?.let { dataSource ->
                        val buttonText = dataSource.title.toString()
                        val buttonContentDescription = dataSource.contentDescription.toString()
                        val context = LocalContext.current

                        Button(
                            onClick = {
                                // TODO: b/491345447 - Report click event once callback is able to
                                // distinguish
                                // between different elements within a single insight
                                dataSource.execute(context)
                            },
                            modifier =
                                Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = buttonContentDescription
                                },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MaterialIcon(
                                    modifier = Modifier.size(IconSizeMedium),
                                    painter =
                                        painterResource(R.drawable.gs_open_in_new_vd_theme_24),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    contentDescription = null,
                                )
                                Text(text = buttonText)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Displays the summary text for the email insight */
@Composable
private fun CallGeneralCardText(isExpanded: Boolean, text: String, icon: Icon?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SummaryIcon(isExpanded = isExpanded, icon = icon)
        Text(
            modifier = Modifier.animateContentSize(),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines =
                if (isExpanded) {
                    4
                } else {
                    1
                },
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Displays an icon beside the summary text */
@Composable
private fun RowScope.SummaryIcon(isExpanded: Boolean, icon: Icon?) {
    val detailedTextStyle = MaterialTheme.typography.bodyMedium
    val fontSizeSp = detailedTextStyle.fontSize
    val fontSizeDp = with(LocalDensity.current) { fontSizeSp.toPx().toDp() }

    // The size of the Icon. It's dynamically based on iconSizeDp, but is clamped between 16.dp and
    // 32.dp.
    val dynamicIconSize = max(min(fontSizeDp, 32.dp), 14.dp)
    val dynamicIconWrapperSizeDp = dynamicIconSize

    // Calculate how much padding to apply to the icon to vertically center it with the text.
    val fontHeightSp = detailedTextStyle.lineHeight
    val dynamicIconWrapperSizeSp =
        with(LocalDensity.current) { dynamicIconWrapperSizeDp.toPx().toSp() }
    val iconPaddingTopSp =
        ((fontHeightSp.value - dynamicIconWrapperSizeSp.value) / 2).coerceAtLeast(0f).toInt().sp
    val iconPaddingTopDp = with(LocalDensity.current) { iconPaddingTopSp.toPx().toDp() }

    // Calculate the left padding for the icon. This padding ensures the icon is center-aligned
    // with a reference icon of IconSizeMedium (24.dp). If dynamicIconSize is 24.dp or larger,
    // no left padding is needed. Otherwise, the padding is half the difference.
    val iconPaddingLeftDp = ((IconSizeLarge - dynamicIconSize) / 2).coerceAtLeast(0.dp)

    // Ensures the title and detailed text are left aligned.
    val spacerWidth = (40.dp - dynamicIconWrapperSizeDp - iconPaddingLeftDp).coerceAtLeast(8.dp)

    val context = LocalContext.current
    val detailedTextIcon: TintableIcon? =
        remember(icon) { icon?.loadDrawable(context)?.toBitmap()?.asTintableIcon(true) }

    Box(
        modifier =
            Modifier.padding(top = iconPaddingTopDp, start = iconPaddingLeftDp)
                .size(dynamicIconWrapperSizeDp),
        contentAlignment = Alignment.Center,
    ) {
        detailedTextIcon?.let {
            IconOrImage(
                modifier = Modifier.size(dynamicIconSize),
                icon = it,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Spacer(modifier = Modifier.width(spacerWidth))
}

/**
 * The icon for the source. For example, an icon for the Gmail app if the data comes from Gmail.
 * Renders nothing if no icon was found.
 */
@Composable
private fun SourceIcon(dataSource: RemoteAction) {
    val context = LocalContext.current
    val tintableIcon: TintableIcon? =
        remember(dataSource.icon) {
            dataSource.icon.loadDrawable(context)?.toBitmap()?.asTintableIcon(false)
        }

    if (tintableIcon != null) {
        IconOrImage(modifier = Modifier.size(IconSizeLarge), icon = tintableIcon)
    }
}

/**
 * Displays an icon indicating the expanded state.
 *
 * @param isExpanded Whether the card is currently expanded.
 */
@Composable
private fun ExpandIcon(isExpanded: Boolean) {
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    MaterialIcon(
        modifier = Modifier.size(IconSizeLarge).rotate(rotationAngle),
        painter = painterResource(R.drawable.gs_keyboard_arrow_down_vd_theme_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
