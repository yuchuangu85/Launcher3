/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.organizer.creation.screen.ui.spacecreator

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.launcher3.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    onArrowBack: () -> Unit,
    viewModel: SpaceCreatorViewModel,
    onNavigateToChooser: (topic: String) -> Unit,
) {
    val state by viewModel.createScreenState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.padding(CreateScreenDimens.contentSidePadding),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                title = {
                    Text(
                        text = stringResource(R.string.organizer_create_screen_title),
                        color = colorResource(R.color.materialColorOnSurface),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onArrowBack,
                        modifier =
                            Modifier.background(
                                CreateScreenDimens.AndroidOnlySe1Se1,
                                shape = RoundedCornerShape(size = 360.dp),
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colorResource(R.color.materialColorOnSurface),
                        )
                    }
                },
            )
        },
        content = { padding ->
            CreateScreenContent(
                padding = padding,
                topics = state.topics,
                onTopicClick = { topic -> onNavigateToChooser.invoke(topic) },
            )
        },
    )
}

@Composable
fun CreateScreenContent(
    padding: PaddingValues,
    topics: List<TopicData>,
    onTopicClick: (topic: String) -> Unit,
) {
    if (topics.isEmpty()) return
    val mainTopicData = topics[0]
    val otherTopicsData = topics.drop(1)
    Column(
        modifier =
            Modifier.padding(padding)
                .padding(
                    start = CreateScreenDimens.contentEndStartPadding,
                    end = CreateScreenDimens.contentEndStartPadding,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier =
                Modifier.padding(
                    top = CreateScreenDimens.textDescriptionTopBottomPadding,
                    bottom = CreateScreenDimens.textDescriptionTopBottomPadding,
                    start = CreateScreenDimens.textDescriptionLeftRightPadding,
                    end = CreateScreenDimens.textDescriptionLeftRightPadding,
                ),
            text = stringResource(R.string.organizer_create_screen_description),
            textAlign = TextAlign.Center,
            color = CreateScreenDimens.textColor,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(CreateScreenDimens.topicSpacing),
        ) {
            item(span = { GridItemSpan(this.maxLineSpan) }) {
                TopicPreview(
                    previewTitle = mainTopicData.topic,
                    topic = mainTopicData.topic,
                    icons = mainTopicData.icons,
                    onTopicClick = onTopicClick,
                    numberOfIconsToShow = 5,
                )
            }

            items(otherTopicsData.size) { i ->
                val topicData = otherTopicsData[i]
                TopicPreview(
                    previewTitle = topicData.topic,
                    topic = topicData.topic,
                    icons = topicData.icons,
                    onTopicClick = onTopicClick,
                )
            }
        }
    }
}

@Composable
fun TopicPreview(
    previewTitle: String,
    topic: String,
    icons: List<Bitmap>,
    onTopicClick: (topic: String) -> Unit,
    modifier: Modifier = Modifier,
    numberOfIconsToShow: Int = 3,
) {
    Column(
        modifier = modifier.height(CreateScreenDimens.topicHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier =
                Modifier.clip(RoundedCornerShape(CreateScreenDimens.topicRadius))
                    .clickable(onClick = { onTopicClick(topic) })
                    .align(Alignment.CenterHorizontally)
                    .background(
                        color = CreateScreenDimens.topicPreviewBackgroundColor,
                        RoundedCornerShape(CreateScreenDimens.topicRadius),
                    )
                    .padding(CreateScreenDimens.topicPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(numberOfIconsToShow) { index ->
                if (index < icons.size) {
                    Image(
                        bitmap = icons[index].asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(CreateScreenDimens.iconSize).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.size(CreateScreenDimens.iconSize)
                                .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
                    )
                }
            }
        }
        Text(text = previewTitle, color = CreateScreenDimens.textColor)
    }
}

// TODO(b/493996430): Remove hardcoded dimensions and move them to resources.
object CreateScreenDimens {
    val iconSize = 41.dp
    val topicPadding = 20.dp
    val topicRadius = 24.dp
    val topicHeight = 116.dp
    val textDescriptionTopBottomPadding = 32.dp
    val textDescriptionLeftRightPadding = 16.dp
    val contentEndStartPadding = 16.dp
    val topicSpacing = 4.dp
    val contentSidePadding = 24.dp
    val topicPreviewBackgroundColor = Color(0x52FFFFFF)
    val AndroidOnlySe1Se1: Color = Color(0x8A081034)
    val textColor = Color.White
}
