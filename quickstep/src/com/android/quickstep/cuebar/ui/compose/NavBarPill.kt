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

package com.android.quickstep.cuebar.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.lerp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.cuebar.ui.viewmodel.ActionType
import com.android.cuebar.ui.viewmodel.ActionViewModel
import com.android.launcher3.R
import com.android.quickstep.cuebar.ui.compose.modifier.animatedActionBorder
import com.android.quickstep.cuebar.ui.utils.AmbientCueAnimationState
import com.android.quickstep.cuebar.ui.utils.FilterUtils
import com.android.quickstep.cuebar.ui.utils.boundsInWindow
import kotlinx.coroutines.delay

@Composable
fun NavBarPill(
    actions: List<ActionViewModel>,
    navBarWidth: Dp,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    expanded: Boolean = false,
    showEducation: Boolean = false,
    onInteractiveBoundsMeasured: (Rect) -> Unit,
    onClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onCloseEducation: () -> Unit = {},
    onAnimationStateChange: (Int, AmbientCueAnimationState) -> Unit = { _, _ -> },
) {
    val maxPillWidth = 248.dp
    val backgroundColor = if (isSystemInDarkTheme()) Color.Black else Color.White
    val smartScrimColor = MaterialTheme.colorScheme.primaryFixedDim

    val density = LocalDensity.current
    val collapsedWidthPx = with(density) { navBarWidth.toPx() }
    var wasEverCollapsed by remember(actions) { mutableStateOf(false) }
    val showAnimationInProgress = remember { mutableStateOf(false) }
    val hideAnimationInProgress = remember { mutableStateOf(false) }
    val expandAnimationInProgress = remember { mutableStateOf(false) }
    val collapseAnimationInProgress = remember { mutableStateOf(false) }

    var expandedSize by remember { mutableStateOf(IntSize.Zero) }
    var pillBounds by remember { mutableStateOf<Rect?>(null) }
    var eduBounds by remember { mutableStateOf<Rect?>(null) }
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible

    val transition = rememberTransition(visibleState)
    val enterProgress by
        transition.animateFloat(
            transitionSpec = { tween(250, delayMillis = 200) },
            label = "enterProgress",
        ) {
            if (it) 1f else 0f
        }
    val smartScrimAlpha by
        transition.animateFloat(transitionSpec = { tween(500) }, label = "smartScrimAlpha") {
            if (it) 0.3f else 0f
        }
    val smartScrimAlphaBoost by
        transition.animateFloat(
            transitionSpec = {
                if (visible) {
                    keyframes {
                        durationMillis = 2000
                        0f at 0
                        0.2f at 500
                        0.2f at 1500
                        0.4f at 2000
                    }
                } else {
                    tween(500)
                }
            },
            label = "smartScrimAlphaBoost",
        ) {
            if (it) 0.4f else 0f
        }
    val expansionAlpha by
        animateFloatAsState(
            if (expanded) 0f else 1f,
            animationSpec = tween(250, delayMillis = 200),
            label = "expansion",
        )
    val smartScrimOffset by
        animateIntAsState(
            if (expanded) -18 else 10,
            animationSpec = tween(250, delayMillis = 200),
            label = "smartScrimOffset",
        )
    AmbientCueJankMonitorComposable(
        visibleTargetState = visibleState.targetState,
        enterProgress = enterProgress,
        expanded = expanded,
        expansionAlpha = expansionAlpha,
        showAnimationInProgress = showAnimationInProgress,
        hideAnimationInProgress = hideAnimationInProgress,
        expandAnimationInProgress = expandAnimationInProgress,
        collapseAnimationInProgress = collapseAnimationInProgress,
        onAnimationStateChange = onAnimationStateChange,
    )

    val blurRadius = remember { Animatable(4f) }
    LaunchedEffect(Unit) {
        delay(BLUR_DURATION_MILLIS)
        blurRadius.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = BLUR_FADE_DURATION_MILLIS),
        )
    }
    LaunchedEffect(expanded, expansionAlpha) {
        if (expanded && expansionAlpha == 0f) {
            wasEverCollapsed = true
        }
    }

    LaunchedEffect(pillBounds, eduBounds, showEducation) {
        val currentPill = pillBounds ?: return@LaunchedEffect
        val currentEdu = eduBounds
        if (showEducation && currentEdu != null) {
            val union = Rect(
                left = minOf(currentPill.left, currentEdu.left),
                top = minOf(currentPill.top, currentEdu.top),
                right = maxOf(currentPill.right, currentEdu.right),
                bottom = maxOf(currentPill.bottom, currentEdu.bottom),
            )
            onInteractiveBoundsMeasured(union)
        } else {
            onInteractiveBoundsMeasured(currentPill)
        }
    }

    val config = LocalConfiguration.current
    val isBoldTextEnabled = config.fontWeightAdjustment > 0
    val fontScale = config.fontScale
    val actionTextStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = if (isBoldTextEnabled) FontWeight.Bold else FontWeight.Medium,
            fontSize =
                with(density) {
                    (MaterialTheme.typography.labelMedium.fontSize.value * fontScale).dp.toSp()
                },
        )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier =
            modifier.defaultMinSize(minWidth = 412.dp, minHeight = 50.dp).drawBehind {
                // SmartScrim
                val smartScrimRadius = 50.dp.toPx()
                val smartScrimBrush =
                    Brush.radialGradient(
                        colors = listOf(smartScrimColor, smartScrimColor.copy(alpha = 0f)),
                        center = Offset.Zero,
                        radius = smartScrimRadius * 0.9f,
                    )
                translate(size.width / 2f, size.height + smartScrimOffset.dp.toPx()) {
                    scale(
                        scaleX = size.width / (smartScrimRadius * 2),
                        scaleY = 1f,
                        pivot = Offset.Zero,
                    ) {
                        drawCircle(
                            brush = smartScrimBrush,
                            alpha = smartScrimAlpha + smartScrimAlphaBoost,
                            radius = smartScrimRadius,
                            center = Offset.Zero,
                        )
                    }
                }
            },
    ) {
        if (visible && !expanded && showEducation) {
            AnimatedVisibility(
                visible = enterProgress == 1f,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                FirstTimeEducation(
                    Alignment.CenterHorizontally,
                    onCloseClick = onCloseEducation,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        eduBounds = coordinates.boundsInWindow()
                    }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.graphicsLayer {
                        alpha = enterProgress * expansionAlpha
                        scaleY = enterProgress
                        scaleX =
                            if (expandedSize.width != 0) {
                                val initialScale = collapsedWidthPx / expandedSize.width
                                lerp(initialScale, 1f, enterProgress)
                            } else {
                                1f
                            }
                    }
                    .onGloballyPositioned { coordinates ->
                        pillBounds = coordinates.boundsInWindow()
                    },
        ) {
            val closeButtonSize = 28.dp
            val closeButtonTouchTargetSize = 36.dp
            val filteredActions = FilterUtils.filterActions(actions)
            val expandActionLabel = stringResource(id = R.string.ambient_cue_expand_action)

            Spacer(modifier = Modifier.size(closeButtonTouchTargetSize))

            Box {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .widthIn(min = navBarWidth, max = maxPillWidth)
                            .background(backgroundColor)
                            .then(
                                if (expanded) Modifier
                                else
                                    Modifier.clickable(
                                        // Set expand action when the action is not one-tap action.
                                        onClickLabel =
                                            if (
                                                filteredActions.size == 1 &&
                                                    filteredActions[0].actionType ==
                                                        ActionType.MA &&
                                                    filteredActions[0].oneTapEnabled
                                            )
                                                null
                                            else expandActionLabel
                                    ) {
                                        onClick()
                                    }
                            )
                            .padding(2.dp)
                            .onGloballyPositioned { expandedSize = it.size },
                ) {
                    // Should have at most 1 expanded chip
                    var expandedChip = false
                    filteredActions.fastForEachIndexed { index, action ->
                        val isMrAction = action.actionType == ActionType.MR

                        // Pill rounded container
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                if (isMrAction) Modifier.weight(1f, false)
                                else Modifier.width(IntrinsicSize.Max),
                        ) {
                            val iconBorder =
                                if (action.actionType == ActionType.MR) {
                                    Modifier
                                } else {
                                    Modifier.border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = CircleShape,
                                    )
                                }
                            if ((filteredActions.size == 1 || isMrAction) && !expandedChip) {
                                expandedChip = true
                                val hasBackground = (!wasEverCollapsed && filteredActions.size > 1)
                                // Expanded chip for single action or MR
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier.padding(end = 3.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (hasBackground) {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .height(24.dp)
                                            .padding(start = 6.dp, end = 6.dp),
                                ) {
                                    Image(
                                        painter = rememberDrawablePainter(action.icon.small),
                                        contentDescription =
                                            stringResource(
                                                id = R.string.ambient_cue_icon_content_description
                                            ),
                                        modifier =
                                            Modifier.size(16.dp).then(iconBorder).clip(CircleShape),
                                    )
                                    if (
                                        isMrAction &&
                                            !(wasEverCollapsed && filteredActions.size > 1)
                                    ) {
                                        Text(
                                            text = action.label,
                                            style = actionTextStyle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.widthIn(0.dp, maxPillWidth * 0.5f),
                                        )
                                        if (action.icon.repeatCount > 0) {
                                            Text(
                                                text = "+${action.icon.repeatCount}",
                                                style = actionTextStyle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 3.dp),
                                            )
                                        }
                                    } else if (action.icon.repeatCount == 0) {
                                        Text(
                                            text = action.label,
                                            style = actionTextStyle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.widthIn(0.dp, maxPillWidth * 0.5f),
                                        )
                                    }
                                }
                            } else {
                                // Smaller app icons
                                Image(
                                    painter = rememberDrawablePainter(action.icon.small),
                                    contentDescription = action.label,
                                    modifier =
                                        Modifier.then(
                                                when (index) {
                                                    0 -> Modifier.padding(start = 5.dp)
                                                    filteredActions.size - 1 ->
                                                        Modifier.padding(end = 5.dp)
                                                    else -> Modifier
                                                }
                                            )
                                            .padding(horizontal = 3.dp, vertical = 4.dp)
                                            .size(16.dp)
                                            .then(iconBorder)
                                            .clip(CircleShape),
                                )
                            }
                        }
                    }
                }
                // Inner glow
                Box(
                    Modifier.matchParentSize()
                        // Prevent the border from being invisible due to blur.
                        .animatedActionBorder(
                            strokeWidth = 1.dp,
                            cornerRadius = 16.dp,
                            visible = visible,
                        )
                        .blur(blurRadius.value.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .animatedActionBorder(
                            strokeWidth = 1.dp,
                            cornerRadius = 16.dp,
                            visible = visible,
                        )
                )
            }

            // Expand the clickable area.
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(closeButtonTouchTargetSize)
                        .clickable(
                            onClick = onCloseClick,
                            interactionSource = null,
                            indication = null,
                        ),
            ) {
                // Close button
                FilledIconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.size(closeButtonSize),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_white_rounded),
                        contentDescription =
                            stringResource(id = R.string.underlay_close_button_content_description),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
    }
}

private const val BLUR_DURATION_MILLIS = 1500L
private const val BLUR_FADE_DURATION_MILLIS = 500
