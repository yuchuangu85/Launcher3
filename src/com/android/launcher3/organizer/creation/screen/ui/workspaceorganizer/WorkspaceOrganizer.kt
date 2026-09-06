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

package com.android.launcher3.organizer.creation.screen.ui.workspaceorganizer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceOrganizer(
    onArrowBack: () -> Unit,
    onNavigateToSpaceCreator: () -> Unit,
    viewModel: WorkspaceOrganizerViewModel,
) {
    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = { FloatingMenu(viewModel) },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.padding(WorkspaceOrganizerDimens.contentSidePadding),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onArrowBack,
                        modifier =
                            Modifier.width(WorkspaceOrganizerDimens.SizeMediumMedium3)
                                .height(WorkspaceOrganizerDimens.SizeMediumMedium3)
                                .background(
                                    WorkspaceOrganizerDimens.AndroidOnlySe1Se1,
                                    shape = RoundedCornerShape(size = 360.dp),
                                ),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Back",
                            tint = colorResource(R.color.materialColorOnSurface),
                        )
                    }
                },
            )
        },
    ) { padding ->
        WorkspaceOrganizerContent(viewModel, padding, onNavigateToSpaceCreator)
    }
}

@Composable
fun WorkspaceOrganizerContent(
    viewModel: WorkspaceOrganizerViewModel,
    padding: PaddingValues,
    onNavigateToSpaceCreator: () -> Unit,
) {
    val pages: List<WorkspacePage> by viewModel.workspacePages.collectAsState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = spacedBy(WorkspaceOrganizerDimens.workspaceOrganizerSpacing),
        verticalArrangement = spacedBy(WorkspaceOrganizerDimens.workspaceOrganizerSpacing),
        contentPadding =
            padding.plus(PaddingValues(horizontal = WorkspaceOrganizerDimens.contentSidePadding)),
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
    ) {
        itemsIndexed(pages, key = { _, item -> item.screenId }) { index, item ->
            WorkspaceOrganizerPage(
                item,
                index,
                index == viewModel.workspaceOrganizerState.selectedPage,
                viewModel,
                onClick = { viewModel.setSelectedWorkspacePage(index) },
            )
        }
        item { WorkspaceOrganizerAddPage(onNavigateToSpaceCreator) }
    }
}

@Composable
fun WorkspaceOrganizerPage(
    page: WorkspacePage,
    index: Int,
    isSelected: Boolean,
    viewModel: WorkspaceOrganizerViewModel,
    onClick: () -> Unit,
) {
    DisposableEffect(index) {
        viewModel.loadPageBitmap(index)
        onDispose { viewModel.unloadPageBitmap(index) }
    }
    Box(
        modifier =
            Modifier.width(WorkspaceOrganizerDimens.workspacePageWidth)
                .height(WorkspaceOrganizerDimens.workspacePageHeight)
                .background(
                    color = Color(WorkspaceOrganizerDimens.workspacePageBackgroundColor),
                    shape = RoundedCornerShape(size = WorkspaceOrganizerDimens.workspaceCornerSize),
                )
                .let {
                    if (isSelected)
                        it.border(
                            width = WorkspaceOrganizerDimens.workspaceOrganizerPageBorder,
                            color = colorResource(R.color.materialColorTertiary),
                            shape =
                                RoundedCornerShape(
                                    size = WorkspaceOrganizerDimens.workspaceCornerSize
                                ),
                        )
                    else it
                }
                .clickable { onClick() }
                .padding(WorkspaceOrganizerDimens.workspacePagePadding)
    ) {
        page.bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = "Previous workspace",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun WorkspaceOrganizerAddPage(onAddClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.width(WorkspaceOrganizerDimens.workspacePageWidth)
                .height(WorkspaceOrganizerDimens.workspacePageHeight)
                .background(
                    color = Color(WorkspaceOrganizerDimens.workspacePageBackgroundColor),
                    shape = RoundedCornerShape(size = WorkspaceOrganizerDimens.workspaceCornerSize),
                )
                .padding(WorkspaceOrganizerDimens.workspacePagePadding)
                .clickable { onAddClick() },
    ) {
        Icon(
            modifier =
                Modifier.width(WorkspaceOrganizerDimens.workspaceAddButtonSize)
                    .height(WorkspaceOrganizerDimens.workspaceAddButtonSize),
            imageVector = Icons.Rounded.Add,
            contentDescription = "Previous workspace",
            tint = Color.White,
        )
    }
}

@Composable
fun FloatingMenu(viewModel: WorkspaceOrganizerViewModel) {
    colorResource(R.color.materialColorOnSurfaceVariant)
    Row(
        modifier =
            Modifier.wrapContentWidth()
                .height(WorkspaceOrganizerDimens.floatingMenuHeight)
                .background(
                    color = colorResource(R.color.materialColorTertiary),
                    shape =
                        RoundedCornerShape(
                            size = WorkspaceOrganizerDimens.IconButtonIconContainerRadius
                        ),
                )
                .padding(WorkspaceOrganizerDimens.floatingMenuPadding),
        horizontalArrangement = spacedBy(WorkspaceOrganizerDimens.SpaceExtraSmallNone),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationArrows(
            { viewModel.moveSelectedWorkspacePage(-1) },
            { viewModel.moveSelectedWorkspacePage(1) },
        )
        DeleteWorkspace({ viewModel.removeSelectedWorkspacePage() })
    }
}

@Composable
fun DeleteWorkspace(onDeleteClick: () -> Unit) {
    val tint = colorResource(R.color.materialColorOnTertiaryFixed)
    TextButton(
        onClick = onDeleteClick,
        modifier =
            Modifier.wrapContentSize()
                .background(
                    color = colorResource(R.color.materialColorTertiaryFixedDim),
                    shape = RoundedCornerShape(size = 100.dp),
                ),
    ) {
        Icon(imageVector = Icons.Default.Delete, contentDescription = "Next workspace", tint = tint)
        Text("Delete", style = TextStyle(color = tint))
    }
}

@Composable
fun NavigationArrows(onPrevClick: () -> Unit, onNextClick: () -> Unit) {
    val tint = colorResource(R.color.materialColorOnTertiaryFixed)
    val iconBackground = colorResource(R.color.materialColorTertiaryFixedDim)
    Row(
        Modifier.wrapContentSize().padding(all = 0.dp),
        horizontalArrangement =
            spacedBy(WorkspaceOrganizerDimens.navigationArrowsSpacing, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevClick,
            modifier =
                Modifier.wrapContentSize()
                    .background(
                        color = iconBackground,
                        shape =
                            RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 8.dp,
                                bottomStart = 24.dp,
                                bottomEnd = 8.dp,
                            ),
                    )
                    .padding(all = WorkspaceOrganizerDimens.navigationArrowIconPadding),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous workspace",
                tint = tint,
            )
        }
        IconButton(
            onClick = onNextClick,
            modifier =
                Modifier.wrapContentSize()
                    .background(
                        color = iconBackground,
                        shape =
                            RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 24.dp,
                                bottomStart = 8.dp,
                                bottomEnd = 24.dp,
                            ),
                    )
                    .padding(all = WorkspaceOrganizerDimens.navigationArrowIconPadding),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next workspace",
                tint = tint,
            )
        }
    }
}

object WorkspaceOrganizerDimens {
    val iconSize = 41.dp
    val workspaceCornerSize = 16.dp
    val IconButtonIconContainerRadius: Dp = 360.dp
    val SpaceExtraSmallNone: Dp = 12.dp
    val SizeMediumMedium3: Dp = 40.dp
    val contentSidePadding = 24.dp
    val AndroidOnlySe1Se1: Color = Color(0x8A081034)
    val workspaceOrganizerPageBorder = 3.dp
    val workspacePageHeight = 278.31549.dp
    val workspacePageWidth = 174.dp
    val workspacePagePadding =
        PaddingValues(start = 0.dp, top = 8.4466.dp, end = 0.dp, bottom = 8.4466.dp)
    val workspacePageBackgroundColor = 0x52FFFFFF
    val workspaceOrganizerSpacing = 16.dp
    val workspaceAddButtonSize = 40.dp
    val floatingMenuHeight = 64.dp
    val floatingMenuPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
    val navigationArrowsSpacing = 4.dp
    val navigationArrowIconPadding = 8.dp
}
