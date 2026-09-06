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

import android.view.Surface.ROTATION_90
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.cuebar.ui.viewmodel.ActionType
import com.android.cuebar.ui.viewmodel.ActionViewModel
import com.android.launcher3.R
import com.android.quickstep.cuebar.ui.compose.modifier.animatedActionBorder
import com.android.quickstep.cuebar.ui.utils.AmbientCueAnimationState
import com.android.quickstep.cuebar.ui.utils.FilterUtils
import kotlinx.coroutines.delay

@Composable
fun ShortPill(
    actions: List<ActionViewModel>,
    modifier: Modifier = Modifier,
    horizontal: Boolean = true,
    visible: Boolean = true,
    expanded: Boolean = false,
    rotation: Int = 0,
    taskBarMode: Boolean = false,
    centerEndExpandedCloseButton: Boolean = false,
    onClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onAnimationStateChange: (Int, AmbientCueAnimationState) -> Unit = { _, _ -> },
) {
    val backgroundColor = if (isSystemInDarkTheme()) Color.Black else Color.White
    val scrimColor = MaterialTheme.colorScheme.primaryFixedDim
    val minSize = 48.dp
    val closeButtonSize = 28.dp
    // (shortPillBoxWidth, shortPillBoxLength) is the smallest size to fully cover recent app area.
    val shortPillBoxWidth = 48.dp
    val shortPillBoxLength = 68.dp
    val transitionTween: TweenSpec<Float> = tween(250, delayMillis = 200)
    val showAnimationInProgress = remember { mutableStateOf(false) }
    val hideAnimationInProgress = remember { mutableStateOf(false) }
    val expandAnimationInProgress = remember { mutableStateOf(false) }
    val collapseAnimationInProgress = remember { mutableStateOf(false) }

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible

    val transition = rememberTransition(visibleState)
    val enterProgress by
        transition.animateFloat(transitionSpec = { transitionTween }, label = "enter") {
            if (it) 1f else 0f
        }
    val expansionAlpha by
        animateFloatAsState(
            targetValue = if (expanded) 0f else 1f,
            animationSpec = transitionTween,
            label = "expansion",
        )
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

    val blurRadius = remember { Animatable(4f) }
    LaunchedEffect(Unit) {
        delay(BLUR_DURATION_MILLIS)
        blurRadius.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = BLUR_FADE_DURATION_MILLIS),
        )
    }

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

    // State variables to store the measured size and position of the main pill.
    var pillContentSize by remember { mutableStateOf(IntSize.Zero) }
    var pillContentPosition by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val isBoldTextEnabled by remember { derivedStateOf { config.fontWeightAdjustment > 0 } }
    val fontScale = config.fontScale
    val actionTextStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = if (isBoldTextEnabled) FontWeight.Bold else FontWeight.Medium,
            fontSize =
                with(density) {
                    (MaterialTheme.typography.labelMedium.fontSize.value * fontScale).dp.toSp()
                },
        )

    Box(
        modifier =
            modifier.drawBehind {
                // SmartScrim
                val halfWidth = size.width / 2f
                val halfHeight = size.height / 2f
                val smartScrimRadius = 50.dp.toPx()
                if (!(halfWidth > 0) || !(halfHeight > 0)) return@drawBehind
                val scrimBrush =
                    Brush.radialGradient(
                        colors = listOf(scrimColor, scrimColor.copy(alpha = 0f)),
                        center = Offset.Zero,
                        radius = if (horizontal) smartScrimRadius * 0.9f else halfHeight,
                    )
                translate(
                    left =
                        if (horizontal) halfWidth
                        else {
                            if (rotation == ROTATION_90) size.width else 0f
                        },
                    top = if (taskBarMode) size.height else halfHeight,
                ) {
                    scale(
                        scaleX = if (horizontal) 4.12f else 0.3f,
                        scaleY = 1f,
                        pivot = Offset.Zero,
                    ) {
                        drawCircle(
                            brush = scrimBrush,
                            alpha = smartScrimAlpha + smartScrimAlphaBoost,
                            radius = if (horizontal) smartScrimRadius else halfHeight,
                            center = Offset.Zero,
                        )
                    }
                }
            }
    ) {
        val scaleAnimationModifier =
            Modifier.graphicsLayer {
                scaleY = enterProgress
                scaleX = enterProgress
            }
        val fadeOutModifier = Modifier.graphicsLayer { alpha = expansionAlpha }
        val pillModifier =
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .widthIn(0.dp, minSize * 2)
                .padding(4.dp)

        val filteredActions = FilterUtils.filterActions(actions)
        val expandActionLabel = stringResource(id = R.string.ambient_cue_expand_action)

        // The layout for the un-expanded state (pill + side button)
        if (horizontal) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = fadeOutModifier.then(scaleAnimationModifier),
            ) {
                Spacer(modifier = Modifier.size(closeButtonTouchTargetSize))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.widthIn(shortPillBoxLength, minSize * 2)
                            .height(shortPillBoxWidth)
                            .then(
                                if (expanded) Modifier
                                else
                                    Modifier.clickable(
                                        indication = null,
                                        interactionSource = null,
                                        // Set expand action when the action is not one-tap action.
                                        onClickLabel =
                                            if (
                                                filteredActions.size == 1 &&
                                                    filteredActions[0].actionType ==
                                                        ActionType.MA &&
                                                    filteredActions[0].oneTapEnabled
                                            )
                                                null
                                            else expandActionLabel,
                                    ) {
                                        onClick()
                                    }
                            )
                            .onGloballyPositioned { coordinates ->
                                pillContentSize = coordinates.size
                                pillContentPosition = coordinates.positionInParent()
                            },
                ) {
                    Box {
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(-4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = pillModifier.defaultMinSize(minWidth = minSize),
                        ) {
                            filteredActions.take(3).fastForEach { action ->
                                Icon(action, backgroundColor)
                                if (actions.size == 1) {
                                    Text(
                                        text = action.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = actionTextStyle,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                } else if (
                                    filteredActions.size == 1 &&
                                        action.actionType == ActionType.MR &&
                                        action.icon.repeatCount > 0
                                ) {
                                    Text(
                                        text = action.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = actionTextStyle,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                                    )
                                    Text(
                                        text = "+${action.icon.repeatCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 6.dp, end = 3.dp),
                                    )
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
                                .blur(
                                    blurRadius.value.dp,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                )
                                .animatedActionBorder(
                                    strokeWidth = 1.dp,
                                    cornerRadius = 16.dp,
                                    visible = visible,
                                )
                        )
                    }
                }

                CloseButton(onCloseClick = onCloseClick, modifier = Modifier.size(closeButtonSize))
            }
        } else { // Vertical
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = fadeOutModifier.then(scaleAnimationModifier),
            ) {
                Spacer(modifier = Modifier.size(closeButtonSize))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.widthIn(shortPillBoxWidth, minSize * 2)
                            .height(shortPillBoxLength)
                            .then(if (expanded) Modifier else Modifier.clickable { onClick() })
                            .onGloballyPositioned { coordinates ->
                                pillContentSize = coordinates.size
                                pillContentPosition = coordinates.positionInParent()
                            },
                ) {
                    Box {
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(-4.dp, Alignment.CenterVertically),
                            modifier = pillModifier.defaultMinSize(minHeight = minSize),
                        ) {
                            filteredActions.take(3).fastForEach { action ->
                                Icon(action, backgroundColor)
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
                                .blur(
                                    blurRadius.value.dp,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                )
                                .animatedActionBorder(
                                    strokeWidth = 1.dp,
                                    cornerRadius = 16.dp,
                                    visible = visible,
                                )
                        )
                    }
                }

                CloseButton(onCloseClick = onCloseClick, modifier = Modifier.size(closeButtonSize))
            }
        }

        // The layout for the expanded state (a single button)
        if (expansionAlpha < 1f && pillContentSize != IntSize.Zero) {
            if (centerEndExpandedCloseButton) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    CloseButton(
                        onCloseClick = onCloseClick,
                        modifier = Modifier.size(closeButtonSize),
                    )
                }
            } else {
                with(density) {
                    val offsetX =
                        pillContentPosition.x.toDp() + (pillContentSize.width.toDp() / 2) -
                            (closeButtonTouchTargetSize / 2)
                    val offsetY =
                        pillContentPosition.y.toDp() + (pillContentSize.height.toDp() / 2) -
                            (closeButtonTouchTargetSize / 2)
                    CloseButton(
                        onCloseClick = onCloseClick,
                        modifier =
                            Modifier.offset(x = offsetX, y = offsetY)
                                .size(closeButtonSize)
                                .graphicsLayer { alpha = 1f - expansionAlpha } // Fade IN
                                .then(scaleAnimationModifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onCloseClick: () -> Unit, modifier: Modifier = Modifier) {
    // Expand the clickable area.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(closeButtonTouchTargetSize)
                .clickable(onClick = onCloseClick, interactionSource = null, indication = null),
    ) {
        // Close button
        FilledIconButton(
            onClick = onCloseClick,
            modifier = modifier,
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

@Composable
private fun Icon(action: ActionViewModel, backgroundColor: Color, modifier: Modifier = Modifier) {
    Image(
        painter = rememberDrawablePainter(action.icon.small),
        contentDescription = stringResource(id = R.string.ambient_cue_icon_content_description),
        modifier =
            modifier
                .padding(2.dp)
                .then(
                    if (action.actionType == ActionType.MR) {
                        Modifier.size(16.dp)
                    } else {
                        Modifier.size(16.dp)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                    }
                )
                .clip(CircleShape)
                .background(backgroundColor),
    )
}

private val closeButtonTouchTargetSize = 36.dp
private const val BLUR_DURATION_MILLIS = 1500L
private const val BLUR_FADE_DURATION_MILLIS = 500
