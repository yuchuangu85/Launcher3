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

package com.android.personalcontext.ace.visualizer.templates.message

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.hint.MessagesHint
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.interaction.InsightEvent
import android.util.Log
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.personalcontext.ace.common.FindHintUtils.findContextHint
import com.android.personalcontext.ace.common.wrappers.IPublishedContextInsight
import com.android.personalcontext.ace.visualizer.compat.FlexFontCompat
import com.android.personalcontext.ace.visualizer.templates.LocalInsightEventReporter
import com.android.personalcontext.ace.visualizer.templates.LocalInsightSurfaceClientInfo
import com.android.personalcontext.ace.visualizer.templates.LocalPublishedContextInsight
import com.android.personalcontext.ace.visualizer.templates.LocalRenderToken
import com.android.personalcontext.ace.visualizer.templates.VisualizerTemplate
import com.android.personalcontext.ace.visualizer.templates.message.MessageTemplateData.Companion.toMessageTemplateData
import com.android.personalcontext.ace.visualizer.templates.utils.IconOrImage
import com.android.personalcontext.ace.visualizer.templates.utils.RemoteActionUtils.execute
import com.android.personalcontext.ace.visualizer.templates.utils.TintableIcon
import com.android.personalcontext.ace.visualizer.templates.utils.asTintableIcon
import javax.inject.Inject

/** A [VisualizerTemplate] that renders a simple message template UI. */
class MessageVisualizerTemplate @Inject internal constructor(val flexFontCompat: FlexFontCompat) :
    VisualizerTemplate {

    override fun handleInsight(
        publishedInsight: IPublishedContextInsight
    ): (@Composable () -> Unit)? {
        Log.d(TAG, "[MessagesEmbedded] handleInsight")
        val insight = publishedInsight.insight
        val unused = insight.findContextHint<MessagesHint>() ?: return null
        val messageTemplateData = insight.toMessageTemplateData()
        return { MessageTemplate(messageTemplateData) }
    }

    @Composable
    private fun MessageTemplate(messageTemplateData: MessageTemplateData) {
        // TODO: b/469695123 - add blur effect
        // TODO: b/469697415 - show pii hints
        MainTheme() { MergedChipsRow(messageTemplateData) }
    }

    @Composable
    private fun MergedChipsRow(messageTemplateData: MessageTemplateData) {
        Log.d(
            TAG,
            "[MessagesEmbedded] MergedChipsRow chip count: ${messageTemplateData.messageChipList.size}",
        )
        // TODO: b/469699341 - isStandaloneRowEnabled
        Row(
            modifier = Modifier.padding(vertical = 4.dp).wrapContentWidth().heightIn(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
            verticalAlignment = Alignment.Bottom,
        ) {
            val suggestionEnterEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
            if (messageTemplateData.messageChipList.isNotEmpty()) {
                MessageAnimatedListItemVisibility(
                    values = messageTemplateData.messageChipList,
                    itemEnter = { _ ->
                        scaleIn(
                            animationSpec =
                                tween(
                                    durationMillis =
                                        MessageConstants.ANIMATION_REVEAL_DURATION_MILLIS,
                                    delayMillis = MessageConstants.ANIMATION_REVEAL_DELAY_MILLIS,
                                    easing = suggestionEnterEasing,
                                )
                        )
                    },
                ) { messageChip ->
                    when (messageChip) {
                        is SuggestionChip -> MessageSuggestionChip(messageChip)
                        is RemoteActionChip -> MessageRemoteActionChip(messageChip)
                        is ClientActionChip -> MessageClientActionChip(messageChip)
                    }
                }
            }
        }
    }

    @Composable
    fun MessageRemoteActionChip(remoteActionChip: RemoteActionChip) {
        Log.d(TAG, "[MessagesEmbedded] MessageRemoteActionChip: ${remoteActionChip.title}")
        val context = LocalContext.current
        MessageOutlinedButton(
            chipOnClick = {
                Log.d(TAG, "[MessagesEmbedded] remote action clicked")
                remoteActionChip.remoteAction.execute(context)
            },
            insight = remoteActionChip.insight,
        ) {
            MessageRowContent(
                title = remoteActionChip.title,
                contentDescription = remoteActionChip.contentDescription,
                icon = remoteActionChip.icon?.toBitmap(context)?.asTintableIcon(tintable = false),
            )
        }
    }

    @Composable
    fun MessageClientActionChip(clientActionChip: ClientActionChip) {
        Log.d(TAG, "[MessagesEmbedded] MessageClientActionChip: ${clientActionChip.title}")
        val context = LocalContext.current
        val info = LocalInsightSurfaceClientInfo.current
        MessageOutlinedButton(
            chipOnClick = {
                Log.d(TAG, "[MessagesEmbedded] client action clicked")
                info.onReceiveInsight(clientActionChip.insight)
            },
            insight = clientActionChip.insight,
        ) {
            MessageRowContent(
                title = clientActionChip.title,
                contentDescription = clientActionChip.contentDescription,
                icon = clientActionChip.icon?.toBitmap(context)?.asTintableIcon(tintable = false),
            )
        }
    }

    @Composable
    internal fun MessageSuggestionChip(suggestionChip: SuggestionChip) {
        Log.d(TAG, "[MessagesEmbedded] MessageSuggestionChip: ${suggestionChip.title}")
        val context = LocalContext.current
        val info = LocalInsightSurfaceClientInfo.current
        MessageOutlinedButton(
            chipOnClick = {
                Log.d(TAG, "[MessagesEmbedded] display insight clicked")
                info.onReceiveInsight(suggestionChip.insight)
            },
            insight = suggestionChip.insight,
        ) {
            MessageRowContent(
                title = suggestionChip.title,
                subtitle = suggestionChip.subtitle,
                contentDescription = suggestionChip.contentDescription,
                icon = suggestionChip.icon?.toBitmap(context)?.asTintableIcon(tintable = true),
            )
        }
    }

    @Composable
    private fun MessageOutlinedButton(
        chipOnClick: () -> Unit,
        insight: ContextInsight,
        chipContents: @Composable () -> Unit,
    ) {
        val shape = RoundedCornerShape(MessageConstants.CornerRadius)
        val interactionSource = remember { MutableInteractionSource() }
        val context = LocalContext.current
        val insightEventReporter = LocalInsightEventReporter.current
        val publishedInsight = LocalPublishedContextInsight.current
        val renderToken = LocalRenderToken.current

        val personalContextManager = remember {
            context.getSystemService(PersonalContextManager::class.java)
        }
        fun reportEvent(event: Int) {
            with(insightEventReporter) {
                personalContextManager?.reportChildInsightEvent(
                    publishedInsight,
                    insight,
                    event,
                    renderToken,
                )
            }
        }
        LaunchedEffect(Unit) { reportEvent(InsightEvent.EVENT_SHOW) }
        // TODO: b/469698749 - outer glow & inner glow
        Box(
            modifier =
                Modifier.clip(shape)
                    .widthIn(min = 30.dp, max = 320.dp)
                    .heightIn(min = 40.dp)
                    .background(color = Color.Transparent, shape = shape)
                    .combinedClickable(
                        onClick = {
                            chipOnClick()
                            reportEvent(InsightEvent.EVENT_USER_TAP)
                        },
                        onLongClick = {
                            Log.d(TAG, "[MessagesEmbedded] chip long clicked")
                            reportEvent(InsightEvent.EVENT_USER_LONG_PRESS)
                        },
                        interactionSource = interactionSource,
                        indication = ripple(color = MaterialTheme.colorScheme.onSurface),
                    )
                    .animatedActionBorder(
                        strokeWidth = MessageConstants.BorderStrokeWidth,
                        innerGlowStrokeWidth = MessageConstants.InnerBorderStrokeWidth,
                    )
                    .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            chipContents()
        }
    }

    @Composable
    private fun MessageRowContent(
        title: String,
        subtitle: String? = null,
        contentDescription: String,
        icon: TintableIcon?,
    ) {
        Row(
            modifier =
                Modifier.clearAndSetSemantics(contentDescription)
                    .padding(
                        horizontal = MessageConstants.ButtonHorizontalPadding,
                        vertical = MessageConstants.ButtonVerticalPadding,
                    ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            icon?.let {
                IconOrImage(
                    icon = icon,
                    modifier = Modifier.size(18.dp).align(Alignment.CenterVertically),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Text
            if (subtitle.isNullOrEmpty()) {
                SuggestionText(
                    title,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            } else {
                Column {
                    SuggestionText(title, maxLines = 1)
                    Text(
                        text = subtitle,
                        style =
                            flexFontCompat.flexFont(
                                style = MaterialTheme.typography.bodyMedium,
                                weight = 550,
                                round = 0f,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    private fun SuggestionText(text: String, maxLines: Int, modifier: Modifier = Modifier) {
        Text(
            text = text,
            modifier = modifier,
            style =
                flexFontCompat.flexFont(
                    style = MaterialTheme.typography.labelLarge,
                    weight = 500,
                    round = 0f,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
        )
    }

    private fun Icon.toBitmap(context: Context): Bitmap? {
        return try {
            this.loadDrawable(context)?.toBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "[MessagesEmbedded] Failed to load icon to bitmap", e)
            null
        }
    }

    private fun Modifier.clearAndSetSemantics(description: String?): Modifier {
        if (description != null) {
            return clearAndSetSemantics { contentDescription = description }
        } else {
            return this
        }
    }

    private data class MessageColorScheme(
        val outlineVariant: Color,
        val onSurface: Color,
        val primary: Color,
        val backgroundColor: Color,
    )

    @Composable
    private fun MainTheme(content: @Composable () -> Unit) {
        // TODO: b/481128881 - Switch to Delegated Rendering Theming once go/ace-delegated-theming
        // is
        // effective.
        val context = LocalContext.current
        val colorScheme =
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)

        MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
    }

    companion object {
        const val TAG = "MessageVisualizerTemplate"
    }
}
