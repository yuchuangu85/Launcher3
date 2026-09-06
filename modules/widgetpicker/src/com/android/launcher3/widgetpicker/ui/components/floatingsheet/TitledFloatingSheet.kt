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

package com.android.launcher3.widgetpicker.ui.components.floatingsheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.widgetpicker.ui.components.LocalWidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.SheetDismissState
import com.android.launcher3.widgetpicker.ui.components.SheetHeader
import com.android.launcher3.widgetpicker.ui.components.WidgetPickerHostStateEffect
import com.android.launcher3.widgetpicker.ui.components.accessibility.LocalAccessibilityState
import com.android.launcher3.widgetpicker.ui.components.dismissibleSheet
import com.android.launcher3.widgetpicker.ui.components.dismissibleSheetContent
import com.android.launcher3.widgetpicker.ui.components.floatingsheet.TitledFloatingSheetDimens.contentWindowInsets
import com.android.launcher3.widgetpicker.ui.components.floatingsheet.TitledFloatingSheetDimens.sheetBottomPadding
import com.android.launcher3.widgetpicker.ui.components.floatingsheet.TitledFloatingSheetDimens.sheetInnerHorizontalPadding
import com.android.launcher3.widgetpicker.ui.components.floatingsheet.TitledFloatingSheetDimens.sheetInnerTopPadding
import com.android.launcher3.widgetpicker.ui.components.floatingsheet.TitledFloatingSheetDimens.sheetShape
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlinx.coroutines.launch

/**
 * A floating sheet detached from the screen edges with title and description on the top. Intended
 * to serve as a common container structure for different types of widget pickers.
 *
 * @param modifier modifier to be applies to the floating sheet container.
 * @param title A top level title for the floating sheet. If title is absent, top header isn't
 *   shown.
 * @param description an optional short (1-2 line - max 80 char) description that can be shown below
 *   the title. At max font+display size it might overflow to 3 lines.
 * @param onDismissSheet callback to be invoked when the floating sheet is closed.
 * @param onSheetOpen callback to be invoked after the transition to the floating sheet is
 *   completed.
 * @param content the content to be displayed below the [title] and [description]
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitledFloatingSheet(
    modifier: Modifier = Modifier,
    title: String?,
    description: String?,
    shouldEnableDragOnScrollToEnd: Boolean = false,
    onDismissSheet: () -> Unit,
    onSheetOpen: () -> Unit,
    onSheetProgress: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.slowSpatialSpec()
    val sheetState = remember { SheetDismissState(expandCollapseAnimationSpec = animSpec) }
    var scrimAlpha by remember { mutableFloatStateOf(0f) }

    WidgetPickerHostStateEffect(LocalWidgetPickerHostStateProvider.current) { isTopResumed ->
        if (!isTopResumed) {
            scope.launch { sheetState.collapse() }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { scope.launch { sheetState.collapse() } } }
                .focusRequester(focusRequester)
                .focusTarget()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Escape) {
                        scope.launch { sheetState.collapse() }
                        true
                    } else {
                        false
                    }
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val sheetMaxWidth = dimensionResource(id = R.dimen.window_bottom_sheet_max_width)
        val sheetMaxHeight = dimensionResource(id = R.dimen.window_bottom_sheet_max_height)
        val accessibilityState = LocalAccessibilityState.current
        var sheetHeight by remember { mutableStateOf(0f) }

        Box( // scrim
            modifier =
                Modifier.fillMaxSize()
                    .alpha(scrimAlpha)
                    .background(WidgetPickerTheme.colors.sheetBackgroundScrim)
        )

        Surface(
            modifier =
                Modifier.padding(bottom = sheetBottomPadding)
                    .widthIn(max = sheetMaxWidth)
                    .heightIn(max = sheetMaxHeight)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .onSizeChanged { sheetHeight = it.height.toFloat() }
                    .dismissibleSheet(
                        sheetState = sheetState,
                        onSheetOpen = onSheetOpen,
                        onSheetProgress = { progress ->
                            onSheetProgress(progress)
                            scrimAlpha = progress
                        },
                        onDismissSheet = onDismissSheet,
                        maxHeight = sheetHeight,
                        enableNestedScrolling = !accessibilityState.isEnabled,
                        enableDragOnScrollToEnd = shouldEnableDragOnScrollToEnd,
                    )
                    .background(
                        color = WidgetPickerTheme.colors.sheetBackgroundBottomLayer,
                        shape = sheetShape,
                    )
                    // Consume clicks on the sheet itself, preventing them from
                    // passing through to the parent Box and dismissing the sheet.
                    .pointerInput(Unit) { detectTapGestures {} },
            color = WidgetPickerTheme.colors.sheetBackground,
            shape = sheetShape,
            content = {
                Column(
                    modifier =
                        Modifier.imePadding()
                            .windowInsetsPadding(contentWindowInsets)
                            .padding(
                                horizontal = sheetInnerHorizontalPadding,
                                vertical = sheetInnerTopPadding,
                            )
                            .dismissibleSheetContent(sheetState)
                ) {
                    title?.let {
                        SheetHeader(
                            title = title,
                            description = description,
                            shouldShowCloseButton = true,
                            onCloseButtonClick = { scope.launch { sheetState.collapse() } },
                        )
                    }
                    content()
                }
            },
        )
    }
}

private object TitledFloatingSheetDimens {
    val sheetInnerTopPadding = 16.dp
    val sheetInnerHorizontalPadding = 10.dp
    val sheetBottomPadding = 30.dp

    val sheetShape = RoundedCornerShape(28.dp)

    val contentWindowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.safeDrawing.only(sides = WindowInsetsSides.Bottom + WindowInsetsSides.Top)
}
