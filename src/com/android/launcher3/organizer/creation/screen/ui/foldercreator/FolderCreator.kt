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

package com.android.launcher3.organizer.creation.screen.ui.foldercreator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

/** Composable that displays a modal bottom sheet for folder creation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderCreator(viewModel: FolderCreatorViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val resources = LocalResources.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorResource(R.color.materialColorSurfaceContainerLow),
    ) {
        Column(
            modifier =
                Modifier.fillMaxHeight(
                        resources.getFloat(R.dimen.folder_creator_sheet_height_ratio)
                    )
                    .fillMaxWidth()
                    .padding(
                        bottom = dimensionResource(R.dimen.folder_creator_sheet_bottom_padding)
                    )
        ) {
            TitleSection()
            val calculatedHeight =
                ((dimensionResource(R.dimen.folder_creator_preview_height) +
                    dimensionResource(R.dimen.folder_creator_list_item_spacing)) *
                    FolderCreatorConstants.MAX_VISIBLE_ROWS) +
                    (dimensionResource(R.dimen.folder_creator_list_vertical_padding) * 2)
            Box(modifier = Modifier.height(calculatedHeight)) { FolderList(state, viewModel) }
            BottomActions(state, viewModel, onDismiss)
        }
    }
}

@Composable
fun TitleSection() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.folder_creator_title),
            color = colorResource(R.color.materialColorOnSurface),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.folder_creator_title_spacing)))
    }
}

@Composable
fun FolderList(state: FolderCreatorState, viewModel: FolderCreatorViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement =
            Arrangement.spacedBy(dimensionResource(R.dimen.folder_creator_list_item_spacing)),
        verticalArrangement =
            Arrangement.spacedBy(dimensionResource(R.dimen.folder_creator_list_item_spacing)),
        modifier =
            Modifier.padding(
                horizontal = dimensionResource(R.dimen.folder_creator_list_horizontal_padding),
                vertical = dimensionResource(R.dimen.folder_creator_list_vertical_padding),
            ),
    ) {
        state.topics.forEach { topicData ->
            item {
                FolderPreview(
                    topicData,
                    isSelected = state.selectedTopics.contains(topicData.topic),
                    onFolderClick = { viewModel.toggleSelection(topicData.topic) },
                )
            }
        }
    }
}

@Composable
fun FolderPreview(topicData: FolderTopicData, isSelected: Boolean, onFolderClick: () -> Unit) {
    val cornerRadius = dimensionResource(R.dimen.folder_creator_preview_corner_radius)
    val shape = RoundedCornerShape(cornerRadius)

    Column(
        modifier =
            Modifier.width(dimensionResource(R.dimen.folder_creator_preview_width))
                .clip(shape)
                .background(colorResource(R.color.materialColorSurfaceContainerHigh))
                .then(
                    if (isSelected) {
                        Modifier.border(
                            dimensionResource(R.dimen.folder_creator_preview_border_width),
                            colorResource(R.color.materialColorPrimaryFixed),
                            shape,
                        )
                    } else Modifier
                )
                .clickable { onFolderClick.invoke() }
                .padding(
                    vertical = dimensionResource(R.dimen.folder_creator_preview_vertical_padding)
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier.padding(
                    bottom = dimensionResource(R.dimen.folder_creator_list_item_spacing)
                ),
            contentAlignment = Alignment.Center,
        ) {
            FolderItem(topicData)
        }
        Text(
            text = topicData.topic,
            color = colorResource(R.color.materialColorOnSurface),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun FolderItem(topicData: FolderTopicData) {
    Box(
        modifier =
            Modifier.size(dimensionResource(R.dimen.folder_creator_preview_icon_box_size))
                .background(colorResource(R.color.materialColorSurfaceDim), CircleShape)
    )
    FlowRow(
        maxItemsInEachRow = 2,
        horizontalArrangement =
            Arrangement.spacedBy(
                dimensionResource(R.dimen.folder_creator_preview_icon_inner_spacing)
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                dimensionResource(R.dimen.folder_creator_preview_icon_inner_spacing)
            ),
    ) {
        topicData.icons.take(4).forEach { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier =
                    Modifier.size(dimensionResource(R.dimen.folder_creator_preview_icon_size))
                        .clip(CircleShape),
            )
        }
    }
}

@Composable
fun BottomActions(
    state: FolderCreatorState,
    viewModel: FolderCreatorViewModel,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(dimensionResource(R.dimen.folder_creator_actions_padding)),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CancelButton(onDismiss)
        AddButton(state, viewModel, onDismiss)
    }
}

@Composable
fun CancelButton(onDismiss: () -> Unit) {
    Button(onClick = onDismiss) { Text(stringResource(R.string.folder_creator_cancel)) }
}

@Composable
fun AddButton(state: FolderCreatorState, viewModel: FolderCreatorViewModel, onDismiss: () -> Unit) {
    Button(
        onClick = {
            viewModel.generateFolders(state.selectedTopics.toList())
            onDismiss.invoke()
        },
        enabled = state.selectedTopics.isNotEmpty(),
    ) {
        Text(stringResource(R.string.folder_creator_add))
    }
}

private object FolderCreatorConstants {
    const val MAX_VISIBLE_ROWS = 3
}
