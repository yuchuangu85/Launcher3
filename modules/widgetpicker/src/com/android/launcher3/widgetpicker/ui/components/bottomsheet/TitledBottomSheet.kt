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

package com.android.launcher3.widgetpicker.ui.components.bottomsheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.android.launcher3.R
import com.android.launcher3.widgetpicker.shared.model.CloseBehavior
import com.android.launcher3.widgetpicker.ui.components.SheetDismissState
import com.android.launcher3.widgetpicker.ui.components.SheetHeader
import com.android.launcher3.widgetpicker.ui.components.accessibility.LocalAccessibilityState
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetAnimations.OpenCloseAnimationSpec
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.SHEET_HEIGHT_CAP_RATIO
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.SheetHeightCapBreakpoint
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.TALL_ASPECT_RATIO_THRESHOLD
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.contentWindowInsets
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.headerBottomMargin
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetInnerHorizontalPadding
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetInnerTopPadding
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetShape
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetWindowInsets
import com.android.launcher3.widgetpicker.ui.components.dismissibleSheet
import com.android.launcher3.widgetpicker.ui.components.dismissibleSheetContent
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * A bottom sheet with title and description on the top. Intended to serve as a common container
 * structure for different types of widget pickers.
 *
 * @param modifier modifier to be applies to the bottom sheet container.
 * @param title A top level title for the bottom sheet. If title is absent, top header isn't shown.
 * @param description an optional short (1-2 line - max 80 char) description that can be shown below
 *   the title. At max font+display size it might overflow to 3 lines.
 * @param sheetSize type of sheet to show; e.g. full, compact.
 * @param closeBehavior whether to show drag handle or a close button
 * @param enableSwipeUpToDismiss whether to handle swipe up from bottom of sheet to close it.
 *   Setting this to true doesn't exclude the gesture nav stealing the touches automatically, the
 *   host need to ensure it has disabled gesture nav when passing true here.
 * @param onDismissSheet callback to be invoked when the bottom sheet is closed
 * @param content the content to be displayed below the [title] and [description]
 */
@Composable
fun TitledBottomSheet(
    modifier: Modifier = Modifier,
    title: String?,
    description: String?,
    sheetSize: SheetSize,
    closeBehavior: CloseBehavior = CloseBehavior.DRAG_HANDLE,
    enableSwipeUpToDismiss: Boolean = false,
    onSheetProgress: (Float) -> Unit,
    onSheetOpen: () -> Unit,
    onDismissSheet: () -> Unit,
    content: @Composable () -> Unit,
) {
    var scrimAlpha by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val density = LocalDensity.current
        val accessibilityState = LocalAccessibilityState.current
        val closeSheetLabel = stringResource(R.string.widget_picker_collapse_sheet_label)
        val (containerWidth, containerHeight) =
            with(density) {
                LocalWindowInfo.current.containerSize.let { it.width.toDp() to it.height.toDp() }
            }

        Box( // scrim
            modifier =
                Modifier.fillMaxSize()
                    .alpha(scrimAlpha)
                    .background(WidgetPickerTheme.colors.sheetBackgroundScrim)
        )

        BoxWithConstraints(
            modifier =
                modifier
                    .maxSheetHeight(
                        sheetSize = sheetSize,
                        availableWidth = containerWidth,
                        availableHeight = containerHeight,
                    )
                    .maxSheetWidth(sheetSize)
                    .windowInsetsPadding(sheetWindowInsets)
        ) {
            val sheetState = remember { SheetDismissState(OpenCloseAnimationSpec) }
            val scope = rememberCoroutineScope()

            Surface(
                modifier =
                    Modifier.semantics {
                            isTraversalGroup = true
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(label = closeSheetLabel) {
                                        onDismissSheet()
                                        true
                                    }
                                )
                        }
                        .fillMaxSize()
                        .dismissibleSheet(
                            sheetState = sheetState,
                            onSheetProgress = { progress ->
                                onSheetProgress(progress)
                                scrimAlpha = progress
                            },
                            onSheetOpen = onSheetOpen,
                            onDismissSheet = onDismissSheet,
                            maxHeight = with(density) { maxHeight.toPx() },
                            enableNestedScrolling = !accessibilityState.isEnabled,
                        )
                        .background(
                            color = WidgetPickerTheme.colors.sheetBackgroundBottomLayer,
                            shape = sheetShape,
                        ),
                color = WidgetPickerTheme.colors.sheetBackground,
                shape = sheetShape,
                content = {
                    Column(
                        modifier =
                            Modifier.imePadding()
                                .windowInsetsPadding(contentWindowInsets)
                                .padding(horizontal = sheetInnerHorizontalPadding)
                                .padding(
                                    top =
                                        sheetInnerTopPadding.takeIf {
                                            closeBehavior != CloseBehavior.DRAG_HANDLE
                                        } ?: 0.dp
                                )
                                .dismissibleSheetContent(sheetState)
                    ) {
                        if (closeBehavior == CloseBehavior.DRAG_HANDLE) {
                            DecorativeDragHandle(
                                modifier =
                                    Modifier.align(alignment = Alignment.CenterHorizontally)
                                        .padding(
                                            top = sheetInnerTopPadding,
                                            bottom = headerBottomMargin,
                                        )
                            )
                        }
                        title?.let {
                            SheetHeader(
                                title = title,
                                description = description,
                                shouldShowCloseButton = closeBehavior == CloseBehavior.CLOSE_BUTTON,
                                onCloseButtonClick = onDismissSheet,
                            )
                        }
                        content()
                    }
                },
            )

            if (enableSwipeUpToDismiss) {
                SwipeUpToDismissHandler(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    contentHeight = maxHeight,
                    onProgress = { scope.launch { sheetState.backProgress.snapTo(it) } },
                    onCancel = { scope.launch { sheetState.settleProgress() } },
                    onClose = { scope.launch { sheetState.collapse() } },
                )
            }
        }
    }
}

@Composable
private fun SwipeUpToDismissHandler(
    modifier: Modifier,
    contentHeight: Dp,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current

    var currentDragDistanceY by remember { mutableFloatStateOf(0f) }
    val targetDistanceY = remember { with(density) { contentHeight.toPx() / 2 } }
    // Distance user should have swiped up when releasing the drag that should lead to closing the
    // sheet.
    val swipeUpDistanceToClosePx = remember {
        with(density) { SwipeUpToDismissHandlerDimens.swipeUpDistanceToClose.toPx() }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SwipeUpToDismissHandlerDimens.gestureBoxHeight)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentDragDistanceY = it.y },
                        onDragCancel = { onCancel() },
                        onDragEnd = {
                            if (currentDragDistanceY < -swipeUpDistanceToClosePx) {
                                onClose()
                            } else {
                                onCancel()
                            }
                        },
                    ) { _, dragAmount ->
                        currentDragDistanceY += dragAmount.y
                        onProgress(abs(currentDragDistanceY / targetDistanceY).coerceIn(0f, 1f))
                    }
                }
    )
}

@Composable
private fun DecorativeDragHandle(modifier: Modifier) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(WidgetPickerTheme.colors.dragHandle)
                .size(
                    width = DragHandleDimens.dragHandleWidth,
                    height = DragHandleDimens.dragHandleHeight,
                )
    )
}

@Composable
private fun Modifier.maxSheetHeight(
    sheetSize: SheetSize,
    availableWidth: Dp,
    availableHeight: Dp,
): Modifier =
    this.then(
        when {
            // Use fixed size for closable sheets used in desktop form factor.
            sheetSize == SheetSize.WINDOW ->
                Modifier.heightIn(max = dimensionResource(R.dimen.window_bottom_sheet_max_height))

            sheetSize == SheetSize.X_SMALL ->
                Modifier.heightIn(max = dimensionResource(R.dimen.x_small_bottom_sheet_max_height))

            // Cap height to 5/6th if in an elongated (i.e. tall) orientation on larger devices.
            availableHeight > SheetHeightCapBreakpoint &&
                availableHeight.value / availableWidth.value >= TALL_ASPECT_RATIO_THRESHOLD ->
                Modifier.heightIn(max = SHEET_HEIGHT_CAP_RATIO * availableHeight)

            else -> Modifier
        }
    )

@Composable
private fun Modifier.maxSheetWidth(sheetSize: SheetSize): Modifier =
    this.then(
        when (sheetSize) {
            SheetSize.WINDOW ->
                Modifier.widthIn(max = dimensionResource(R.dimen.window_bottom_sheet_max_width))

            SheetSize.X_SMALL,
            SheetSize.COMPACT ->
                Modifier.widthIn(max = dimensionResource(R.dimen.compact_bottom_sheet_max_width))

            // Don't cap width even in large handheld devices for sheet size large
            else -> Modifier
        }
    )

private object DragHandleDimens {
    val dragHandleHeight = 4.dp
    val dragHandleWidth = 32.dp
}

private object SwipeUpToDismissHandlerDimens {
    val gestureBoxHeight = 20.dp
    val swipeUpDistanceToClose = 28.dp
}

private object TitledBottomSheetDimens {
    val sheetInnerTopPadding = 16.dp
    val sheetInnerHorizontalPadding = 10.dp
    val headerBottomMargin = 16.dp

    /** Height beyond which its ideal to cap the sheet's height using [SHEET_HEIGHT_CAP_RATIO]. */
    val SheetHeightCapBreakpoint = 1200.dp

    /**
     * Post [SheetHeightCapBreakpoint] if aspect ratio of available size is greater than this, then
     * consider it a tall layout and cap its height.
     */
    const val TALL_ASPECT_RATIO_THRESHOLD = 1.5f

    /**
     * Proportion of available height to fill when window is too large (greater than
     * [SheetHeightCapBreakpoint] and aspect ratio is > [TALL_ASPECT_RATIO_THRESHOLD]).
     */
    const val SHEET_HEIGHT_CAP_RATIO = 5 / 6f

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val sheetWindowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.statusBars.union(
                WindowInsets.displayCutout.only(
                    sides = WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                )
            )

    val contentWindowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.safeDrawing.only(sides = WindowInsetsSides.Bottom + WindowInsetsSides.Top)
}

private object TitledBottomSheetAnimations {
    val OpenCloseAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 500, easing = FastOutSlowInEasing)
}

/** Different size variations supported for a [TitledBottomSheet] component. */
enum class SheetSize {
    /** Full size sheet for content heavy use cases. Height is capped in certain taller layouts. */
    FULL,
    /** Small sheet for single app content use cases; values can overridden with resource xml. */
    COMPACT,
    /** An extra small sheet for single widget use cases. */
    X_SMALL,
    /** A window like sheet for desktop like uses cases; values can overridden with resource xml. */
    WINDOW,
}
