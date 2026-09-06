/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.cuebar.ui

import android.content.res.Configuration
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_90
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowSizeClass
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import com.android.cuebar.ui.viewmodel.ActionViewModel
import com.android.launcher3.widgetpicker.ui.rememberViewModel
import com.android.quickstep.cuebar.ui.compose.NavBarPill
import com.android.quickstep.cuebar.ui.compose.ShortPill
import com.android.quickstep.cuebar.ui.utils.AmbientCueAnimationState
import com.android.quickstep.cuebar.ui.utils.boundsInWindow
import com.android.quickstep.cuebar.ui.viewmodel.AmbientCueViewModel
import com.android.quickstep.cuebar.ui.viewmodel.PillStyleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AmbientCueContainer(
    ambientCueViewModelFactory: AmbientCueViewModel.Factory,
    onShouldInterceptTouches: (Boolean, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    onAnimationStateChange: (Int, AmbientCueAnimationState) -> Unit,
) {
    val viewModel = rememberViewModel { ambientCueViewModelFactory.create() }

    val visible = viewModel.isVisible
    val expanded = viewModel.isExpanded
    val actions = viewModel.actions
    val pillStyle = viewModel.pillStyle

    LaunchedEffect(expanded) {
        if (expanded) {
            viewModel.cancelDeactivation()
        } else {
            viewModel.delayAndDeactivateCueBar()
        }
    }

    Box(
        modifier.clickable(enabled = expanded, indication = null, interactionSource = null) {
            viewModel.collapse()
        }
    ) {
        when (pillStyle) {
            is PillStyleViewModel.NavBarPillStyle -> {
                NavBarAmbientCue(
                    viewModel = viewModel,
                    actions = actions,
                    visible = visible,
                    expanded = expanded,
                    onShouldInterceptTouches = onShouldInterceptTouches,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onAnimationStateChange = onAnimationStateChange,
                )
            }
            is PillStyleViewModel.ShortPillStyle -> {
                val screenWidthPx = LocalWindowInfo.current.containerSize.width
                val largeScreen =
                    LocalWindowSizeClass.current.isAtLeastBreakpoint(
                        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
                    )
                val pillCenterX = pillStyle.position?.center?.x
                val pillPositionInWindow =
                    if (pillCenterX != null) {
                        if (largeScreen && pillCenterX < screenWidthPx / 2) {
                            val pillRightPadding = with(LocalDensity.current) { 58.dp.toPx() }
                            Rect(
                                left = screenWidthPx - pillRightPadding - pillStyle.position.width,
                                top = pillStyle.position.top,
                                right = screenWidthPx - pillRightPadding,
                                bottom = pillStyle.position.bottom,
                            )
                        } else {
                            pillStyle.position
                        }
                    } else null
                TaskBarAnd3ButtonAmbientCue(
                    viewModel = viewModel,
                    actions = actions,
                    visible = visible,
                    expanded = expanded,
                    pillPositionInWindow = pillPositionInWindow,
                    onShouldInterceptTouches = onShouldInterceptTouches,
                    modifier =
                        if (pillPositionInWindow == null) {
                            Modifier.align(Alignment.BottomEnd)
                        } else {
                            Modifier
                        },
                    onAnimationStateChange = onAnimationStateChange,
                    largeScreen = largeScreen,
                )
            }
            is PillStyleViewModel.DesktopPillStyle -> {
                DesktopAmbientCue(
                    viewModel = viewModel,
                    actions = actions,
                    visible = visible,
                    expanded = expanded,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    onShouldInterceptTouches = onShouldInterceptTouches,
                    onAnimationStateChange = onAnimationStateChange,
                )
            }
            is PillStyleViewModel.Uninitialized -> {}
            is PillStyleViewModel.NoPillStyle -> {}
        }
    }
}

@Composable
private fun TaskBarAnd3ButtonAmbientCue(
    viewModel: AmbientCueViewModel,
    actions: List<ActionViewModel>,
    visible: Boolean,
    expanded: Boolean,
    pillPositionInWindow: Rect?,
    onShouldInterceptTouches: (Boolean, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    onAnimationStateChange: (Int, AmbientCueAnimationState) -> Unit,
    largeScreen: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    var pillCenter by remember { mutableStateOf(Offset.Zero) }
    var pillSize by remember { mutableStateOf(Size(0)) }
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    var touchableRegion by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(expanded, touchableRegion) {
        onShouldInterceptTouches(true, if (expanded) null else touchableRegion)
    }
    val content = LocalContext.current
    val rotation = content.display.rotation
    if (largeScreen && rotation == ROTATION_270) return

    ActionList(
        actions = actions,
        visible = visible,
        expanded = expanded,
        horizontalAlignment =
            if (!portrait && rotation == ROTATION_270) Alignment.Start else Alignment.End,
        onDismiss = { viewModel.collapse() },
        showEducation = viewModel.showLongPressEducation,
        modifier =
            modifier.graphicsLayer {
                if (portrait || largeScreen) {
                    translationX = screenWidthPx - size.width
                    translationY = pillCenter.y - size.height
                } else {
                    if (rotation == ROTATION_90) {
                        translationX =
                            screenWidthPx -
                                pillSize.width -
                                size.width -
                                LANDSCAPE_PADDING.dp.toPx()
                        translationY = pillCenter.y - pillSize.width
                    } else if (rotation == ROTATION_270) {
                        translationX = pillSize.width
                        translationY = pillCenter.y - pillSize.width
                    }
                }
            },
        padding =
            if (portrait || largeScreen) {
                PaddingValues(
                    start = ACTIONS_HORIZONTAL_PADDING.dp,
                    end = ACTIONS_HORIZONTAL_PADDING.dp,
                    top = ACTIONS_TOP_PADDING.dp,
                    bottom = SHORT_PILL_ACTIONS_VERTICAL_PADDING.dp,
                )
            } else {
                if (rotation == ROTATION_90) {
                    PaddingValues()
                } else {
                    PaddingValues(start = ACTIONS_HORIZONTAL_PADDING.dp)
                }
            },
        portrait = portrait,
        pillCenter = pillCenter,
        pillWidth = pillSize.width,
        pillHeight = pillSize.height,
        rotation = rotation,
        taskBarMode = largeScreen,
    )
    ShortPill(
        actions = actions,
        visible = visible,
        horizontal = portrait || largeScreen,
        expanded = expanded,
        rotation = rotation,
        taskBarMode = largeScreen,
        modifier =
            if (pillPositionInWindow == null) {
                modifier.padding(bottom = 12.dp, end = 24.dp).onGloballyPositioned {
                    pillCenter = it.boundsInParent().center
                }
            } else {
                Modifier.graphicsLayer {
                        translationX = pillCenter.x - size.width / 2
                        translationY = pillCenter.y - size.height / 2
                        pillSize = Size(pillPositionInWindow.width, pillPositionInWindow.height)
                    }
                    .onGloballyPositioned { layoutCoordinates ->
                        layoutCoordinates.parentCoordinates?.let { parentCoordinates ->
                            pillCenter =
                                parentCoordinates.screenToLocal(pillPositionInWindow.center)
                            touchableRegion =
                                Rect(
                                    offset =
                                        pillCenter -
                                            Offset(
                                                layoutCoordinates.size.width / 2f,
                                                layoutCoordinates.size.height / 2f,
                                            ),
                                    size = layoutCoordinates.size.toSize(),
                                )
                        }
                    }
            },
        onClick = { viewModel.expand() },
        onCloseClick = { viewModel.hide() },
        onAnimationStateChange = onAnimationStateChange,
    )
}

@Composable
private fun NavBarAmbientCue(
    viewModel: AmbientCueViewModel,
    actions: List<ActionViewModel>,
    visible: Boolean,
    expanded: Boolean,
    onShouldInterceptTouches: (Boolean, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    onAnimationStateChange: (Int, AmbientCueAnimationState) -> Unit,
) {
    val navBarWidth =
        if (
            LocalWindowSizeClass.current.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
            )
        ) {
            NAV_BAR_PILL_LARGE_WIDTH_DP.dp
        } else {
            NAV_BAR_PILL_WIDTH_DP.dp
        }

    val scope = rememberCoroutineScope()
    var touchableRegion by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(expanded, touchableRegion) {
        onShouldInterceptTouches(true, if (expanded) null else touchableRegion)
    }
    LaunchedEffect(expanded) { onShouldInterceptTouches(expanded, null) }
    ActionList(
        actions = actions,
        visible = visible,
        expanded = expanded,
        onDismiss = { viewModel.collapse() },
        showEducation = viewModel.showLongPressEducation,
        modifier = modifier,
        padding =
            PaddingValues(
                top = ACTIONS_TOP_PADDING.dp,
                bottom = NAV_BAR_ACTIONS_PADDING.dp,
                start = ACTIONS_HORIZONTAL_PADDING.dp,
                end = ACTIONS_HORIZONTAL_PADDING.dp,
            ),
    )
    NavBarPill(
        actions = actions,
        navBarWidth = navBarWidth,
        visible = visible,
        expanded = expanded,
        showEducation = viewModel.showFirstTimeEducation,
        modifier = modifier,
        onInteractiveBoundsMeasured = { bounds -> touchableRegion = bounds },
        onClick = {
            if (actions.size == 1 && actions[0].oneTapEnabled) {
                scope.launch {
                    delay(actions[0].oneTapDelayMs)
                    actions[0].onClick()
                }
            } else {
                viewModel.expand()
            }
        },
        onCloseClick = { viewModel.hide() },
        onCloseEducation = { viewModel.disableFirstTimeHint() },
        onAnimationStateChange = onAnimationStateChange,
    )
}

@Composable
private fun DesktopAmbientCue(
    viewModel: AmbientCueViewModel,
    actions: List<ActionViewModel>,
    visible: Boolean,
    expanded: Boolean,
    modifier: Modifier,
    onShouldInterceptTouches: (Boolean, Rect?) -> Unit,
    onAnimationStateChange: (Int, AmbientCueAnimationState) -> Unit,
) {
    var shortPillWindowRegion by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(expanded, shortPillWindowRegion) {
        onShouldInterceptTouches(true, if (expanded) null else shortPillWindowRegion)
    }
    val endPadding = 6.dp
    Box(
        modifier =
            modifier
                .navigationBarsPadding()
                // Ensure enough width for the ShortPill SmartScrim to be fully visible.
                .widthIn(min = 400.dp)
                .clip(RoundedCornerShape(bottomEnd = 16.dp))
    ) {
        val shortPillHeightDp =
            with(LocalDensity.current) { (shortPillWindowRegion?.height ?: 0f).toDp() }
        ActionList(
            actions = actions,
            visible = visible,
            expanded = expanded,
            onDismiss = { viewModel.collapse() },
            showEducation = viewModel.showLongPressEducation,
            modifier = modifier,
            padding =
                PaddingValues(
                    top = ACTIONS_TOP_PADDING.dp,
                    bottom = shortPillHeightDp,
                    end = endPadding,
                ),
            horizontalAlignment = Alignment.End,
        )
        ShortPill(
            actions = actions,
            horizontal = true,
            visible = visible,
            expanded = expanded,
            taskBarMode = true,
            centerEndExpandedCloseButton = true,
            modifier =
                modifier.padding(end = endPadding).onGloballyPositioned {
                    shortPillWindowRegion = it.boundsInWindow()
                },
            onClick = { viewModel.expand() },
            onCloseClick = { viewModel.hide() },
            onAnimationStateChange = onAnimationStateChange,
        )
    }
}

private const val NAV_BAR_WIDTH_DP = 108 // R.dimen.taskbar_stashed_small_screen from Launcher
private const val NAV_BAR_PILL_WIDTH_DP = NAV_BAR_WIDTH_DP + 8
private const val NAV_BAR_LARGE_WIDTH_DP = 220 // R.dimen.taskbar_stashed_handle_width from Launcher
private const val NAV_BAR_PILL_LARGE_WIDTH_DP = NAV_BAR_LARGE_WIDTH_DP + 4

private const val NAV_BAR_HEIGHT_DP = 24 // R.dimen.taskbar_stashed_size from Launcher
private const val SHORT_PILL_ACTIONS_VERTICAL_PADDING = 38
private const val NAV_BAR_ACTIONS_PADDING = NAV_BAR_HEIGHT_DP + 24
private const val ACTIONS_HORIZONTAL_PADDING = 32
private const val ACTIONS_TOP_PADDING = 42
private const val LANDSCAPE_PADDING = 65
