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

package com.android.launcher3.widget.resize

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.android.launcher3.R
import com.android.launcher3.util.compose.testTag
import com.android.launcher3.util.compose.testTagContainer
import com.android.launcher3.widget.resize.ResizeFrameAnimations.lowBounceMediumLowStiffness
import com.android.launcher3.widget.resize.ResizeFrameAnimations.noBounceHighStiffness
import com.android.launcher3.widget.resize.ResizeFrameAnimations.noBounceMediumLowStiffness
import com.android.launcher3.widget.resize.ResizeFrameDimensions.BorderWidth
import com.android.launcher3.widget.resize.ResizeFrameDimensions.DotPadding
import com.android.launcher3.widget.resize.ResizeFrameDimensions.ResizeButtonContentSize
import com.android.launcher3.widget.resize.ResizeFrameDimensions.TouchTargetSize
import com.android.launcher3.widget.resize.ResizeFrameDimensions.resizeButtonPadding
import com.android.launcher3.widget.resize.ResizeFrameDimensions.resizeButtonShape

/**
 * A bordered frame that fills the available space and insets the border by touch target size.
 * - Shows handles / dots at center of each edge from which the widget can be resized.
 * - Can be dragged from any side or corner to expand / shrink
 * - Tapping on any handle (dot) reveals a set of resize buttons based on whether a widget can be
 *   expanded or collapsed from that side.
 *
 * Parent should reserve extra size equivalent to touch target for the resize buttons.
 *
 * @param viewModel holds state necessary for the UI and reacts to the events within the UI.
 * @param onInteraction a callback for the parent view to know about interactions in the frame. This
 *   enables the parent to dismiss popups etc. outside the frame.
 * @param onDismiss informs parent that user tapped outside the interactable region; we might want
 *   to dismiss the frame.
 */
@Composable
fun ResizeFrame(viewModel: ResizeFrameViewModel, onInteraction: () -> Unit, onDismiss: () -> Unit) {
    val interactionCallback by rememberUpdatedState(onInteraction)

    // Strings used for accessibility adapt to the layout direction.
    val accessibilityLayoutDirection = LocalLayoutDirection.current
    // While, we use always use display direction as LTR for rendering the frame content.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ResizeFrame(
            handlesState = viewModel.handlesState,
            resizeButtonState = viewModel.resizeButtonsState,
            draggingHandles = viewModel.draggingHandles,
            accessibilityLayoutDirection = accessibilityLayoutDirection,
            onShrink = viewModel::onShrink,
            onExpand = viewModel::onExpand,
            onDotHandleTapped = {
                interactionCallback()
                viewModel.onDotHandleTapped(it)
            },
            onDragStart = { handle, offset, size ->
                onInteraction()
                viewModel.onDragStart(handle, offset, size)
            },
            onDrag = viewModel::onDrag,
            onDragEnd = viewModel::onDragEnd,
            onDismiss = onDismiss,
        )
    }
}

/**
 * An internal implementation of resize frame that takes in raw state and event callbacks. This
 * enables previewing the composable independent of the view model in android studio.
 */
@Composable
@VisibleForTesting
fun ResizeFrame(
    handlesState: HandlesState,
    resizeButtonState: ResizeButtonState?,
    draggingHandles: DraggingHandles?,
    accessibilityLayoutDirection: LayoutDirection,
    borderColor: Color = colorResource(R.color.materialColorPrimary),
    borderRadius: Dp = dimensionResource(android.R.dimen.system_app_widget_background_radius),
    onDotHandleTapped: (Edge) -> Unit,
    onShrink: (Edge) -> Unit,
    onExpand: (Edge) -> Unit,
    onDragStart: (Edge, Offset, IntSize) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Parent leaves extra space for resize buttons, so we inset border by this space.
    val borderInset = TouchTargetSize

    AnimatedFrameContainer {
        Box(
            modifier =
                Modifier.testTagContainer()
                    .testTag(resId = "widget_resize_frame")
                    .fillMaxSize()
                    .drawWithContent { // frame border drawn behind content.
                        val insetPx = borderInset.toPx()
                        val borderRect = Rect(Offset.Zero, this.size).deflate(insetPx)

                        drawRoundRect(
                            color = borderColor,
                            topLeft = borderRect.topLeft,
                            size = borderRect.size,
                            style = Stroke(width = BorderWidth.toPx()),
                            cornerRadius = CornerRadius(borderRadius.toPx()),
                        )
                        drawContent()
                    }
        ) {
            DraggableEdges(
                handlesState = handlesState,
                resizeButtonState = resizeButtonState,
                draggingHandles = draggingHandles,
                accessibilityLayoutDirection = accessibilityLayoutDirection,
                onDotHandleTapped = onDotHandleTapped,
                onShrink = onShrink,
                onExpand = onExpand,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun AnimatedFrameContainer(content: @Composable () -> Unit) {
    val frameSizeAnimation = lowBounceMediumLowStiffness<IntSize>()

    Box(modifier = Modifier.fillMaxSize().animateContentSize(frameSizeAnimation)) { content() }
}

/** Displays all the edges that can be dragged and shows a dot or a button over it. */
@Composable
private fun BoxScope.DraggableEdges(
    handlesState: HandlesState,
    resizeButtonState: ResizeButtonState?,
    draggingHandles: DraggingHandles?,
    accessibilityLayoutDirection: LayoutDirection,
    onDotHandleTapped: (Edge) -> Unit,
    onShrink: (Edge) -> Unit,
    onExpand: (Edge) -> Unit,
    onDragStart: (Edge, Offset, IntSize) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    @Composable
    fun Content(edge: Edge) {
        val dragRegionThickness = TouchTargetSize
        val visible =
            draggingHandles == null ||
                (draggingHandles.horizontal == edge || draggingHandles.vertical == edge)

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = noBounceHighStiffness()),
            exit = fadeOut(animationSpec = noBounceHighStiffness()),
            modifier =
                Modifier.run {
                        if (edge.isHorizontal()) {
                            requiredWidth(dragRegionThickness).fillMaxHeight()
                        } else {
                            requiredHeight(dragRegionThickness).fillMaxWidth()
                        }
                    }
                    .align(edge.alignment)
                    .offset {
                        val offsetAmount = dragRegionThickness.roundToPx() / 2
                        IntOffset(
                            x = -edge.expandDirX * offsetAmount,
                            y = -edge.expandDirY * offsetAmount,
                        )
                    },
        ) {
            DraggableEdge(
                edge = edge,
                buttonsState = resizeButtonState,
                accessibilityLayoutDirection = accessibilityLayoutDirection,
                onDotHandleTapped = onDotHandleTapped,
                onShrink = onShrink,
                onExpand = onExpand,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (handlesState.left) Content(Edge.Left)
    if (handlesState.top) Content(Edge.Top)
    if (handlesState.right) Content(Edge.Right)
    if (handlesState.bottom) Content(Edge.Bottom)
}

/** A draggable edge that displays either a resize handle (dot) OR the + / - buttons. */
@Composable
private fun DraggableEdge(
    edge: Edge,
    buttonsState: ResizeButtonState?,
    accessibilityLayoutDirection: LayoutDirection,
    onDotHandleTapped: (Edge) -> Unit,
    modifier: Modifier = Modifier,
    onShrink: (Edge) -> Unit,
    onExpand: (Edge) -> Unit,
    onDragStart: (Edge, Offset, IntSize) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showButtons = buttonsState?.tappedEdge == edge
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .systemGestureExclusion() // So predictive back doesn't conflict with handles.
                .pointerInput(edge) {
                    detectDragGestures(
                        onDragStart = { offset -> onDragStart(edge, offset, size) },
                        onDragEnd = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                    )
                }
                .pointerInput(edge) { detectTapGestures { onDismiss() } } // non-consumed touches
                .centerContentAndAllowOverflow(), // allow buttons to take as much space they need.
    ) {
        when {
            showButtons ->
                buttonsState?.let {
                    ResizeButtonsSet(
                        edge = edge,
                        state = it,
                        onShrink = { onShrink(edge) },
                        onExpand = { onExpand(edge) },
                    )
                }

            else -> DotHandle(
                edge = edge,
                accessibilityLayoutDirection = accessibilityLayoutDirection,
                onHandleTapped = onDotHandleTapped
            )
        }
    }
}

/** Let's the content take more space than container and centers it over parent. */
private fun Modifier.centerContentAndAllowOverflow() =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints()) // unbounded
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        layout(layoutWidth, layoutHeight) {
            // center content
            val contentOffsetX = (layoutWidth - placeable.width) / 2
            val contentOffsetY = (layoutHeight - placeable.height) / 2
            placeable.placeRelative(contentOffsetX, contentOffsetY)
        }
    }

/** The (dot) handle shown at center of each edge to indicate that edge can be dragged. */
@Composable
private fun DotHandle(
    edge: Edge,
    accessibilityLayoutDirection: LayoutDirection,
    backgroundColor: Color = colorResource(R.color.materialColorTertiaryContainer),
    borderColor: Color = colorResource(R.color.materialColorOnTertiaryContainer),
    onHandleTapped: (Edge) -> Unit,
) {
    val descriptionResId = remember(accessibilityLayoutDirection) {
        if (accessibilityLayoutDirection == LayoutDirection.Ltr) {
            edge.accessibilityLabelResId
        } else {
            edge.rtlAccessibilityLabelResId
        }
    }
    val description = stringResource(descriptionResId)

    val clickLabel = stringResource(R.string.tap_to_show_resize_buttons)
    var show by remember { mutableStateOf(false) } // Hide by default
    LaunchedEffect(Unit) { show = true }

    AnimatedVisibility(
        modifier = Modifier.requiredSize(TouchTargetSize),
        visible = show,
        enter =
            fadeIn(animationSpec = noBounceHighStiffness()) +
                scaleIn(
                    initialScale = 0.33f, // 33%-100%
                    animationSpec = noBounceHighStiffness(),
                ),
        exit =
            fadeOut(animationSpec = noBounceHighStiffness()) +
                scaleOut(
                    targetScale = 0.33f, // 100%-33%
                    animationSpec = noBounceHighStiffness(),
                ),
    ) {
        Box(
            modifier =
                Modifier.testTag(resId = edge.handleTestTag)
                    .requiredSize(TouchTargetSize)
                    .semantics {
                        role = Role.Button
                        contentDescription = description
                        onClick(label = clickLabel) {
                            onHandleTapped(edge)
                            return@onClick true
                        }
                    }
                    .pointerInput(edge) { detectTapGestures(onTap = { onHandleTapped(edge) }) }
                    .padding(DotPadding)
                    .border(width = BorderWidth, color = borderColor, shape = CircleShape)
                    .padding(BorderWidth)
                    .background(color = backgroundColor, shape = CircleShape)
        )
    }
}

/** Shows the + / - buttons depending on whether respective action is supported. */
@Composable
private fun ResizeButtonsSet(
    edge: Edge,
    state: ResizeButtonState,
    onShrink: () -> Unit,
    onExpand: () -> Unit,
) {
    val expandButton =
        @Composable {
            ResizeButton(
                enabled = state.canExpand,
                icon = Icons.Default.Add,
                accessibilityLabel = stringResource(R.string.action_increase_width),
                onClick = onExpand,
                resizeButtonType = edge.expandButtonType,
            )
        }
    val shrinkButton =
        @Composable {
            ResizeButton(
                enabled = state.canShrink,
                icon = Icons.Default.Remove,
                accessibilityLabel = stringResource(R.string.action_decrease_width),
                onClick = onShrink,
                resizeButtonType = edge.shrinkButtonType,
            )
        }

    val content: @Composable () -> Unit = {
        if (edge.expandFirst) {
            expandButton()
            shrinkButton()
        } else {
            shrinkButton()
            expandButton()
        }
    }

    if (edge.isHorizontal()) {
        Row { content() }
    } else {
        Column { content() }
    }
}

@Composable
private fun ResizeButton(
    enabled: Boolean,
    icon: ImageVector,
    accessibilityLabel: String,
    resizeButtonType: ResizeButtonType,
    onClick: () -> Unit,
    borderWidth: Dp = BorderWidth,
    backgroundColor: Color = colorResource(R.color.materialColorTertiaryContainer),
    contentColor: Color = colorResource(R.color.materialColorOnTertiaryContainer),
) {
    var show by remember { mutableStateOf(false) } // Hidden by default
    LaunchedEffect(enabled) { show = enabled }
    val transition = updateTransition(targetState = show, label = "ResizeButtonTransition")

    val buttonContainerScale by
        transition.animateFloat(
            label = "HandleScale",
            transitionSpec = { lowBounceMediumLowStiffness() },
        ) { expanded ->
            if (expanded) 1.0f else 0.5f
        }
    val buttonContainerAlpha by
        transition.animateFloat(
            label = "HandleAlpha",
            transitionSpec = { noBounceMediumLowStiffness() },
        ) { expanded ->
            if (expanded) 1.0f else 0.0f
        }

    // Overall button's animation progress
    val buttonProgression by
        transition.animateFloat(
            label = "HandleProgression",
            transitionSpec = { noBounceMediumLowStiffness() },
        ) { expanded ->
            if (expanded) 1.0f else 0.0f
        }
    val buttonLengthPercent = lerp(start = 0.7f, stop = 1.0f, buttonProgression) // 70% -> 100%

    // Icon animation runs during 0.5-1.0 of button's progress
    val iconProgression = ((buttonProgression - 0.5f) * 2.0f).coerceIn(0.0f, 1.0f)
    val iconScale = lerp(start = 0.5f, stop = 1.0f, iconProgression) // 50% -> 100%
    val iconAlpha = lerp(start = 0.0f, stop = 1.0f, iconProgression) // 0% -> 100%

    val clickModifier =
        if (enabled) {
            Modifier.clickable(interactionSource = null, indication = null, onClick = onClick)
        } else {
            Modifier
        }
    val shape =
        resizeButtonShape(
            isHorizontal = resizeButtonType.isHorizontal,
            isFirstButton = resizeButtonType.isFirstButton,
        )
    val paddingValues =
        resizeButtonPadding(
            isHorizontal = resizeButtonType.isHorizontal,
            isFirstButton = resizeButtonType.isFirstButton,
        )

    // Outer non-animating clickable container that occupies required size.
    Box(
        contentAlignment = resizeButtonType.alignment,
        modifier =
            Modifier.then(clickModifier)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                    if (!enabled) {
                        disabled()
                        hideFromAccessibility()
                    }
                }
                .requiredSize(TouchTargetSize)
                .padding(paddingValues),
    ) {
        // The shaped content that resizes during animations.
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(ResizeButtonContentSize)
                    .graphicsLayer {
                        this.alpha = buttonContainerAlpha
                        transformOrigin = resizeButtonType.transformOrigin
                        if (resizeButtonType.isHorizontal) {
                            this.scaleX = buttonContainerScale * buttonLengthPercent
                            this.scaleY = buttonContainerScale
                        } else {
                            this.scaleX = buttonContainerScale
                            this.scaleY = buttonContainerScale * buttonLengthPercent
                        }
                    }
                    .border(width = borderWidth, color = contentColor, shape = shape)
                    .padding(borderWidth)
                    .background(backgroundColor, shape),
        ) {
            Icon(
                modifier =
                    Modifier.graphicsLayer {
                        this.alpha = iconAlpha
                        this.scaleX = iconScale
                        this.scaleY = iconScale
                    },
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

/**
 * Represents the four resize edges on the widget resize frame.
 *
 * @property expandDirX integer value representing horizontal direction in which resize needs to
 *   happen on the edge to increase the size of widget. e.g. for left edge, moving handle on left
 *   (-1) or for right edge moving handle on right (+1) increases the width of the widget.
 * @property expandDirY integer value representing vertical direction in which resize needs to
 *   happen on the edge to increase the size of widget. e.g. for top edge, moving handle on top (-1)
 *   or for bottom edge moving handle towards bottom (+1) increases height of the widget.
 * @property expandFirst indicates whether the edge displays expand button first or the shrink
 * @property expandButtonType describes visual properties of the expand button for the edge
 * @property shrinkButtonType describes the visual properties of the shrink button for the edge
 * @property accessibilityLabelResId resource id of the string to be used as content description for
 *   the resize handle when in LTR mode.
 * @property rtlAccessibilityLabelResId resource id of the string to be used as content description for
 *  *   the resize handle when in RTL mode.
 * @property handleTestTag test tag representing the res id that will be used for looking up the
 *   handle in the TAPL tests.
 */
enum class Edge(
    val expandDirX: Int,
    val expandDirY: Int,
    val expandFirst: Boolean,
    val expandButtonType: ResizeButtonType,
    val shrinkButtonType: ResizeButtonType,
    val accessibilityLabelResId: Int,
    val rtlAccessibilityLabelResId: Int,
    val handleTestTag: String,
    val alignment: Alignment,
) {
    Left(
        expandFirst = true,
        expandDirX = ResizeManager.DIRECTION_X_LEFT,
        expandDirY = ResizeManager.DIRECTION_NONE,
        shrinkButtonType = ResizeButtonType.ROUNDED_RIGHT,
        expandButtonType = ResizeButtonType.ROUNDED_LEFT,
        accessibilityLabelResId = R.string.resize_handle_start,
        rtlAccessibilityLabelResId = R.string.resize_handle_end,
        handleTestTag = "widget_resize_left_handle",
        alignment = Alignment.CenterStart,
    ),
    Top(
        expandFirst = true,
        expandDirX = ResizeManager.DIRECTION_NONE,
        expandDirY = ResizeManager.DIRECTION_Y_TOP,
        shrinkButtonType = ResizeButtonType.ROUNDED_BOTTOM,
        expandButtonType = ResizeButtonType.ROUNDED_TOP,
        accessibilityLabelResId = R.string.resize_handle_top,
        rtlAccessibilityLabelResId = R.string.resize_handle_top,
        handleTestTag = "widget_resize_top_handle",
        alignment = Alignment.TopCenter,
    ),
    Right(
        expandFirst = false,
        expandDirX = ResizeManager.DIRECTION_X_RIGHT,
        expandDirY = ResizeManager.DIRECTION_NONE,
        shrinkButtonType = ResizeButtonType.ROUNDED_LEFT,
        expandButtonType = ResizeButtonType.ROUNDED_RIGHT,
        accessibilityLabelResId = R.string.resize_handle_end,
        rtlAccessibilityLabelResId = R.string.resize_handle_start,
        handleTestTag = "widget_resize_right_handle",
        alignment = Alignment.CenterEnd,
    ),
    Bottom(
        expandFirst = false,
        expandDirX = ResizeManager.DIRECTION_NONE,
        expandDirY = ResizeManager.DIRECTION_Y_BOTTOM,
        shrinkButtonType = ResizeButtonType.ROUNDED_TOP,
        expandButtonType = ResizeButtonType.ROUNDED_BOTTOM,
        accessibilityLabelResId = R.string.resize_handle_bottom,
        rtlAccessibilityLabelResId = R.string.resize_handle_bottom,
        handleTestTag = "widget_resize_bottom_handle",
        alignment = Alignment.BottomCenter,
    );

    /** If a handle enables resize in horizontal directions. */
    fun isHorizontal() = this == Left || this == Right
}

/** Various type of resize buttons based on their visual properties. */
enum class ResizeButtonType(
    val isHorizontal: Boolean,
    val isFirstButton: Boolean,
    val transformOrigin: TransformOrigin,
    val alignment: Alignment,
) {
    ROUNDED_LEFT(
        isHorizontal = true,
        isFirstButton = true,
        transformOrigin = TransformOrigin(pivotFractionX = 1.0f, pivotFractionY = 0.5f),
        alignment = Alignment.CenterEnd,
    ),
    ROUNDED_TOP(
        isHorizontal = false,
        isFirstButton = true,
        transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1.0f),
        alignment = Alignment.TopCenter,
    ),
    ROUNDED_RIGHT(
        isHorizontal = true,
        isFirstButton = false,
        transformOrigin = TransformOrigin(pivotFractionX = 0.0f, pivotFractionY = 0.5f),
        alignment = Alignment.CenterStart,
    ),
    ROUNDED_BOTTOM(
        isHorizontal = false,
        isFirstButton = false,
        transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0.0f),
        alignment = Alignment.BottomCenter,
    ),
}

private object ResizeFrameAnimations {
    fun <T> lowBounceMediumLowStiffness(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> noBounceMediumLowStiffness(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> noBounceHighStiffness(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
}

/** Dimensions for displaying the [ResizeFrame]. */
private object ResizeFrameDimensions {
    val BorderWidth = 2.dp
    val DotPadding = 16.dp

    val TouchTargetSize
        @Composable get() = dimensionResource(R.dimen.resize_frame_touch_target_size)

    val ResizeButtonContentSize = 32.dp // visual size of shaped content of button.

    /** Returns the rounded-ness of a resize button based on its orientation and position. */
    fun resizeButtonShape(isHorizontal: Boolean, isFirstButton: Boolean): RoundedCornerShape {
        val flatRadiusPercent = 20
        val roundRadiusPercent = 50

        return if (isFirstButton) {
            RoundedCornerShape(
                topStartPercent = roundRadiusPercent,
                bottomStartPercent = if (isHorizontal) roundRadiusPercent else flatRadiusPercent,
                topEndPercent = if (isHorizontal) flatRadiusPercent else roundRadiusPercent,
                bottomEndPercent = flatRadiusPercent,
            )
        } else {
            RoundedCornerShape(
                topStartPercent = flatRadiusPercent,
                bottomStartPercent = if (isHorizontal) flatRadiusPercent else roundRadiusPercent,
                topEndPercent = if (isHorizontal) roundRadiusPercent else flatRadiusPercent,
                bottomEndPercent = roundRadiusPercent,
            )
        }
    }

    /**
     * Returns the padding to be applied to a resize button based on its position and orientation.
     */
    fun resizeButtonPadding(isHorizontal: Boolean, isFirstButton: Boolean): PaddingValues {
        val outerPadding = 11.dp
        val innerPadding = 5.dp
        val axialPadding = 8.dp
        return if (isFirstButton) {
            PaddingValues(
                start = if (isHorizontal) outerPadding else axialPadding,
                top = if (isHorizontal) axialPadding else outerPadding,
                end = if (isHorizontal) innerPadding else axialPadding,
                bottom = if (isHorizontal) axialPadding else innerPadding,
            )
        } else {
            PaddingValues(
                start = if (isHorizontal) innerPadding else axialPadding,
                top = if (isHorizontal) axialPadding else innerPadding,
                end = if (isHorizontal) outerPadding else axialPadding,
                bottom = if (isHorizontal) axialPadding else outerPadding,
            )
        }
    }
}
