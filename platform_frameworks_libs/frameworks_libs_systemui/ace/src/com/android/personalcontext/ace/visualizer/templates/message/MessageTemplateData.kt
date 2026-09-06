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

import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightCollection
import android.util.Log
import com.android.personalcontext.ace.client.prototype.PrototypeInsightUtils.toPrototypeInsight
import com.android.personalcontext.ace.client.prototype.clientaction.ClientActionInsight
import com.android.personalcontext.ace.client.prototype.clientaction.SharePhotoParams
import com.android.personalcontext.ace.client.prototype.clientaction.ShowCardsParams
import com.android.personalcontext.ace.visualizer.templates.message.ClientActionChip.Companion.toClientActionChip
import com.android.personalcontext.ace.visualizer.templates.message.RemoteActionChip.Companion.toRemoteActionChip
import com.android.personalcontext.ace.visualizer.templates.message.SuggestionChip.Companion.toSuggestionChip

/**
 * A data class that contains the data for the message template.
 *
 * @property messageChipList The list of chips to display in the message template. This list must be
 *   non-empty.
 */
data class MessageTemplateData(val messageChipList: List<MessageChip>) {
    companion object {
        private const val TAG = "MessageTemplateData"

        fun ContextInsight.toMessageTemplateData(): MessageTemplateData {
            Log.d(TAG, "[MessagesEmbedded] toMessageTemplateData")

            if (this !is InsightCollection) {
                error(
                    "[MessagesEmbedded] Expected a top-level InsightCollection, actual: ${this.javaClass.simpleName}"
                )
            }

            val messageChipList =
                insights.mapNotNull { insight ->
                    when (insight) {
                        is DisplayInsight -> {
                            Log.d(TAG, "[MessagesEmbedded] Find display insight")
                            insight.toSuggestionChip()
                        }
                        is ActionableInsight -> {
                            Log.d(TAG, "[MessagesEmbedded] Find actionable insight")
                            insight.toRemoteActionChip()
                        }
                        else -> {
                            val clientActionInsight =
                                insight.toPrototypeInsight<ClientActionInsight>()
                            if (clientActionInsight != null) {
                                Log.d(TAG, "[MessagesEmbedded] Find client action insight")
                                clientActionInsight.toClientActionChip(insight)
                            } else {
                                Log.w(
                                    TAG,
                                    "[MessagesEmbedded] Find unexpected insight: ${insight.javaClass.simpleName}",
                                )
                                null
                            }
                        }
                    }
                }
            if (messageChipList.isEmpty()) {
                error("[MessagesEmbedded] messageChipList is empty")
            }

            return MessageTemplateData(messageChipList)
        }
    }
}

/** A sealed interface for chips that can be displayed in the message template. */
sealed interface MessageChip {
    val title: String
    val contentDescription: String
    val icon: Icon?
    val insight: ContextInsight
}

data class SuggestionChip(
    override val title: String,
    val subtitle: String,
    override val contentDescription: String,
    override val icon: Icon?,
    override val insight: ContextInsight,
) : MessageChip {
    companion object {
        private const val TAG = "SuggestionChip"

        fun DisplayInsight.toSuggestionChip(): SuggestionChip {
            Log.d(TAG, "[MessagesEmbedded] displayInsight title: ${details.title}")
            return SuggestionChip(
                title = details.title.toString(),
                subtitle = details.subtitle.toString(),
                contentDescription = details.contentDescription.toString(),
                icon = details.icon,
                insight = this,
            )
        }
    }
}

data class RemoteActionChip(
    override val title: String,
    override val contentDescription: String,
    override val icon: Icon,
    override val insight: ContextInsight,
    val remoteAction: RemoteAction,
) : MessageChip {
    companion object {
        private const val TAG = "RemoteActionChip"

        fun ActionableInsight.toRemoteActionChip(): RemoteActionChip? {
            Log.d(
                TAG,
                "[MessagesEmbedded] actionDetails.remoteAction is null: ${actionDetails.remoteAction == null}",
            )
            return actionDetails.remoteAction?.let { remoteAction ->
                Log.d(TAG, "[MessagesEmbedded] actionableInsight title: ${remoteAction.title}")
                RemoteActionChip(
                    title = displayDetails.title.toString(),
                    contentDescription = displayDetails.contentDescription.toString(),
                    icon = remoteAction.icon,
                    insight = this,
                    remoteAction = remoteAction,
                )
            }
        }
    }
}

data class ClientActionChip(
    override val title: String,
    override val contentDescription: String,
    override val icon: Icon?,
    override val insight: ContextInsight,
) : MessageChip {
    companion object {
        private const val TAG = "ClientActionChip"

        fun ClientActionInsight.toClientActionChip(insight: ContextInsight): ClientActionChip? {
            when (val params = clientActionParams) {
                is SharePhotoParams -> {
                    Log.d(TAG, "[MessagesEmbedded] receive share photo client action")
                }
                is ShowCardsParams -> {
                    Log.d(TAG, "[MessagesEmbedded] receive show cards client action")
                }
            }
            return ClientActionChip(
                title = insightDisplayDetails.title.toString(),
                contentDescription = insightDisplayDetails.contentDescription.toString(),
                icon = insightDisplayDetails.icon,
                insight = insight,
            )
        }
    }
}
