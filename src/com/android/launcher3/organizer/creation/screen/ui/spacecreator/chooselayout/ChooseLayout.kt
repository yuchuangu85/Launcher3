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

package com.android.launcher3.organizer.creation.screen.ui.spacecreator.chooselayout

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.organizer.creation.screen.ui.components.CellLayoutCompose
import com.android.launcher3.organizer.creation.screen.ui.components.CellLayoutComposeItemSpacing
import com.android.launcher3.organizer.creation.screen.ui.components.CellLayoutComposeSize
import com.android.launcher3.organizer.creation.screen.ui.components.ItemLocation
import com.android.launcher3.organizer.creation.screen.ui.spacecreator.SpaceCreatorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseLayout(viewModel: SpaceCreatorViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.padding(ChooseLayoutDimens.contentSidePadding),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                title = {
                    Text(
                        text = stringResource(R.string.organizer_choose_layout_title),
                        color = colorResource(R.color.materialColorOnSurface),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier =
                            Modifier.background(
                                ChooseLayoutDimens.AndroidOnlySe1Se1,
                                shape = ChooseLayoutDimens.circleShape,
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
        bottomBar = {
            Box(
                modifier =
                    Modifier.fillMaxWidth().padding(bottom = ChooseLayoutDimens.contentSidePadding),
                contentAlignment = Alignment.Center,
            ) {
                AddButton {
                    viewModel.addSelectedLayoutToWorkspace()
                    onAdd.invoke()
                }
            }
        },
        content = { padding -> ChooseLayoutContent(padding = padding, viewModel = viewModel) },
    )
}

@Composable
fun ChooseLayoutContent(padding: PaddingValues, viewModel: SpaceCreatorViewModel) {
    val pagerState = rememberPagerState { viewModel.chooseLayoutState.layouts.size }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val contentPadding = (screenWidth - ChooseLayoutDimens.itemWidth) / 2

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { index -> viewModel.setSelectedLayout(index) }
    }
    Column(
        modifier = Modifier.padding(padding).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = contentPadding),
            pageSpacing = ChooseLayoutDimens.carouselItemSpacing,
            beyondViewportPageCount = 1,
        ) { i ->
            LayoutPreview(viewModel.chooseLayoutState.layouts[i], viewModel)
        }
        PaginationDots(pagerState)
    }
}

@Composable
fun LayoutPreview(itemsInScreen: List<ItemInfo>, viewModel: SpaceCreatorViewModel) {
    Row(
        Modifier.wrapContentSize()
            .background(
                color = Color(ChooseLayoutDimens.previewPageBackgroundColor),
                shape = RoundedCornerShape(size = ChooseLayoutDimens.previewCornerSize),
            )
    ) {
        CellLayoutCompose(
            width = ChooseLayoutDimens.itemWidth,
            height = ChooseLayoutDimens.itemHeight,
            gridSize =
                CellLayoutComposeSize(
                    x = viewModel.chooseLayoutState.chooseLayoutGridSize.x,
                    y = viewModel.chooseLayoutState.chooseLayoutGridSize.y,
                ),
            spacing =
                CellLayoutComposeItemSpacing(
                    ChooseLayoutDimens.cellItemSpacing,
                    ChooseLayoutDimens.cellItemSpacing,
                ),
            modifier = Modifier.padding(ChooseLayoutDimens.carouselPadding),
        ) {
            for (itemInfo in itemsInScreen) {
                when (itemInfo) {
                    is LauncherAppWidgetInfo -> {
                        item(
                            cellAndSpan =
                                ItemLocation(
                                    itemInfo.cellX,
                                    itemInfo.cellY,
                                    spanX = itemInfo.spanX,
                                    spanY = itemInfo.spanY,
                                )
                        ) { size ->
                            Box(
                                Modifier.width(size.width).height(size.height),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("" + itemInfo.targetComponent?.className)
                            }
                        }
                    }
                    is WorkspaceItemInfo -> {
                        item(cellAndSpan = ItemLocation(itemInfo.cellX, itemInfo.cellY)) { size ->
                            Box(
                                Modifier.width(size.width).height(size.height),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(itemInfo, size.width, size.height)
                            }
                        }
                    }
                    is FolderInfo -> {
                        item(cellAndSpan = ItemLocation(itemInfo.cellX, itemInfo.cellY)) { size ->
                            Box(
                                Modifier.width(size.width).height(size.height),
                                contentAlignment = Alignment.Center,
                            ) {
                                FolderPreviewIcon(itemInfo, size.width, size.height)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(item: WorkspaceItemInfo, width: Dp, height: Dp) {
    var sizeScale by remember { mutableStateOf(Size(1f, 1f)) }
    val density = LocalDensity.current
    Box(
        Modifier.graphicsLayer {
            scaleX = sizeScale.width
            scaleY = sizeScale.height
        }
    ) {
        AndroidView(
            modifier =
                Modifier.onSizeChanged {
                    if (it.width == 0 || it.height == 0) return@onSizeChanged
                    val minScale =
                        minOf(
                            with(density) { width.toPx() } / it.width,
                            with(density) { height.toPx() } / it.height,
                        )
                    sizeScale = Size(minScale, minScale)
                },
            factory = { context ->
                (LayoutInflater.from(Launcher.ACTIVITY_TRACKER.getCreatedContext())
                        .inflate(R.layout.app_icon, LinearLayout(context), false) as BubbleTextView)
                    .apply { applyFromWorkspaceItem(item) }
            },
        )
    }
}

@Composable
fun FolderPreviewIcon(item: FolderInfo, width: Dp, height: Dp) {
    var sizeScale by remember { mutableStateOf(Size(1f, 1f)) }
    val density = LocalDensity.current
    Box(
        Modifier.graphicsLayer {
            scaleX = sizeScale.width
            scaleY = sizeScale.height
        }
    ) {
        AndroidView(
            modifier =
                Modifier.onSizeChanged {
                    if (it.width == 0 || it.height == 0) return@onSizeChanged
                    val minScale =
                        minOf(
                            with(density) { width.toPx() } / it.width,
                            with(density) { height.toPx() } / it.height,
                        )
                    sizeScale = Size(minScale, minScale)
                },
            factory = {
                val launcher = Launcher.ACTIVITY_TRACKER.getCreatedContext<Launcher>()
                FolderIcon.inflateFolderAndIcon(R.layout.folder_icon, launcher, null, item)
            },
        )
    }
}

@Composable
fun PaginationDots(pagerState: PagerState) {
    val tint = colorResource(R.color.materialColorPrimary)
    val scope = rememberCoroutineScope()
    Row(
        Modifier.width(ChooseLayoutDimens.paginationDotsWidth)
            .height(ChooseLayoutDimens.paginationDotsHeight)
            .padding(all = 0.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    if (pagerState.currentPage > 0) {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Preview layout",
                tint = tint,
            )
        }
        IconButton(
            onClick = {
                scope.launch {
                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next layout",
                tint = tint,
            )
        }
    }
}

@Composable
fun AddButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier =
            Modifier.background(
                    color = colorResource(R.color.materialColorOnPrimary),
                    shape = ChooseLayoutDimens.addButtonShape,
                )
                .width(ChooseLayoutDimens.addButtonWidth)
                .height(ChooseLayoutDimens.addButtonHeight),
    ) {
        Text(
            "Add",
            style = MaterialTheme.typography.titleMedium,
            color = colorResource(R.color.materialColorOnSurface),
        )
    }
}

// TODO(b/493996430): Remove hardcoded dimensions and move them to resources.
object ChooseLayoutDimens {
    val iconSize = 41.dp
    val itemWidth = 312.00003.dp
    val itemHeight = 580.dp
    val carouselPadding = 15.14563.dp
    val carouselItemSpacing = 16.dp
    val contentSidePadding = 24.dp
    val AndroidOnlySe1Se1: Color = Color(0x8A081034)
    val textColor = Color.White
    val addButtonWidth = 80.dp
    val addButtonHeight = 56.dp
    val addButtonShape = RoundedCornerShape(size = 100.dp)
    val paginationDotsWidth = 140.dp
    val paginationDotsHeight = 48.dp
    val circleShape = RoundedCornerShape(size = 360.dp)
    val previewPageBackgroundColor = 0x52FFFFFF
    val previewCornerSize = 16.dp
    val cellItemSpacing = 4.dp
}
