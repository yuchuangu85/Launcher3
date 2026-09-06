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

import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import android.service.personalcontext.hint.ContextHint
import android.util.Log
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_90
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapping.SnapPosition.End
import androidx.compose.foundation.gestures.snapping.SnapPosition.Start
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.cuebar.ui.viewmodel.ActionViewModel
import com.android.launcher3.R
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior.SetZOrderBehind
import com.android.personalcontext.ace.client.clientsdk.ui.AceEmbeddedSurfaceView
import com.android.personalcontext.ace.client.clientsdk.ui.rememberOverlapState
import com.android.personalcontext.ace.client.clientsdk.ui.rememberSessionState
import com.android.personalcontext.ace.client.prototype.PrototypeHintUtils.toContextHint
import com.android.personalcontext.ace.client.prototype.example.ExampleEmbeddedHint
import com.android.personalcontext.ace.client.prototype.grid.InsightGrid
import com.android.quickstep.cuebar.ui.compose.OverscrollEffect
import com.android.quickstep.cuebar.ui.compose.modifier.eduBalloon
import com.android.systemui.shared.Flags.enableSage
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.drop

@Composable
fun ActionList(
    actions: List<ActionViewModel>,
    visible: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    showEducation: Boolean = false,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    portrait: Boolean = true,
    pillCenter: Offset = Offset.Zero,
    pillWidth: Float = 0f,
    pillHeight: Float = 0f,
    rotation: Int = 0,
    taskBarMode: Boolean = false,
) {
    val density = LocalDensity.current
    val minOverscrollDelta = (-8).dp
    val maxOverscrollDelta = 0.dp
    val columnSpacing = 8.dp
    val minGradientHeight = 70.dp
    val scrimVerticalPadding = 32.dp
    val scrimHorizontalPadding = 42.dp
    val landscapeMode = !portrait && !taskBarMode
    val smartScrimAlpha by
        animateFloatAsState(
            if (expanded) {
                0.5f
            } else if (visible) {
                0.3f
            } else {
                0f
            }
        )

    val scaleStiffnessMultiplier = 1000
    val scaleDampingRatio = 0.83f
    val translateStiffnessMultiplier = 50
    val overscrollStiffness = 2063f
    var containerHeightPx by remember { mutableIntStateOf(0) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var radius by remember { mutableFloatStateOf(0f) }
    var wasEverExpanded by remember { mutableStateOf(false) }
    var actionListCenterPositionX by remember { mutableFloatStateOf(0f) }
    var actionListCenterPositionY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            wasEverExpanded = true
        }
    }

    val leftGradientColor = MaterialTheme.colorScheme.tertiaryContainer
    val rightGradientColor = MaterialTheme.colorScheme.primary

    // User should be able to drag down vertically to dismiss the action list.
    // The list will shrink as the user drags.
    val anchoredDraggableState = remember {
        AnchoredDraggableState(initialValue = if (visible && expanded) End else Start)
    }
    val minOverscrollDeltaPx = with(density) { minOverscrollDelta.toPx() }
    val maxOverscrollDeltaPx = with(density) { maxOverscrollDelta.toPx() }
    val columnSpacingPx = with(density) { columnSpacing.toPx() }
    val minGradientHeightPx = with(density) { minGradientHeight.toPx() }
    val scrimVerticalPaddingPx = with(density) { scrimVerticalPadding.toPx() }

    val scope = rememberCoroutineScope()
    val overscrollEffect = remember {
        OverscrollEffect(
            scope = scope,
            orientation = Orientation.Vertical,
            minOffset = minOverscrollDeltaPx,
            maxOffset = maxOverscrollDeltaPx,
            flingAnimationSpec =
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = overscrollStiffness),
        )
    }
    // A ratio from 0..1 representing the expansion of the list
    val progress by remember {
        derivedStateOf {
            // We combine the anchor offset with the overscroll offset to animate
            abs(anchoredDraggableState.offset + overscrollEffect.offset.value) /
                max(1, containerHeightPx)
        }
    }
    LaunchedEffect(progress) {
        if (progress == 0f) {
            onDismiss()
        }
    }

    val scrimProgress by
        animateFloatAsState(
            progress,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
        )

    var smartScrimAlphaBoost by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            animate(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec =
                    keyframes {
                        durationMillis = 2000
                        0f at 0
                        0.2f at 500
                        0.2f at 1500
                        0f at 2000
                    },
            ) { value, _ ->
                smartScrimAlphaBoost = value
            }
        } else {
            smartScrimAlphaBoost = 0f
        }
    }
    val isValidHeight = containerHeightPx > 0
    var previousIsValidHeight by remember { mutableStateOf(isValidHeight) }
    val justBecameValid = isValidHeight && !previousIsValidHeight
    previousIsValidHeight = isValidHeight

    val scrimOffsetY by
        animateFloatAsState(
            targetValue =
                if (!expanded) containerHeightPx - scrimVerticalPaddingPx
                else containerHeightPx - radius,
            animationSpec =
                if (justBecameValid) {
                    // If the height JUST became valid, snap to the target value
                    // to avoid animating from an invalid height.
                    snap<Float>()
                } else if (isValidHeight && (expanded || wasEverExpanded)) {
                    // Enable animation only when user expands/collapses the action list
                    tween(250, delayMillis = 200)
                } else {
                    // Disable animation during Compose initialization.
                    snap<Float>()
                },
            label = "scrimOffsetY",
        )

    val landscapeScrimOffsetX by
        animateFloatAsState(
            targetValue =
                if (!expanded) containerWidthPx.toFloat() + pillWidth else containerWidthPx / 2f,
            animationSpec =
                if (expanded || wasEverExpanded) {
                    // Enable animation only when user expands/collapses the action list.
                    tween(
                        durationMillis = 250,
                        // Usually, 200 ms delay is used. But the collapsed delay is reduced to be
                        // 100 ms to ensure the Action chips and background glow disappear at the
                        // same pace.
                        delayMillis = if (expanded) 200 else 100,
                    )
                } else {
                    // Disable animation during Compose initialization.
                    snap()
                },
            label = "landscapeScrimOffsetX",
        )

    var landscapeCollapsedScrimOffsetY by remember { mutableFloatStateOf(0f) }
    val landscapeScrimOffsetY by
        animateFloatAsState(
            targetValue = if (!expanded) landscapeCollapsedScrimOffsetY else containerHeightPx / 2f,
            animationSpec =
                if (expanded || wasEverExpanded) {
                    // Enable animation only when user expands/collapses the action list.
                    tween(250, delayMillis = 200)
                } else {
                    // Disable animation during Compose initialization.
                    snap()
                },
            label = "landscapeScrimOffsetY",
        )
    val landscapeScaleX by
        animateFloatAsState(
            targetValue = if (!expanded) 0.15f else 1f,
            animationSpec =
                tween(
                    durationMillis = 250,
                    // Usually, 200 ms delay is used. But the collapsed delay is reduced to be 100
                    // ms to ensure the Action chips and background glow disappear at the same pace.
                    delayMillis = if (expanded) 200 else 100,
                ),
            label = "landscapeScaleX",
        )

    LaunchedEffect(visible, expanded) {
        anchoredDraggableState.animateTo(if (visible && expanded) End else Start)
    }

    val enterEffect =
        VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_TICK, 0.5f, 0)
            .addPrimitive(PRIMITIVE_TICK, 0.75f, 51)
            .addPrimitive(PRIMITIVE_THUD, 0.5f, 27)
            .compose()

    val exitEffect =
        VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_TICK, 0.75f, 0)
            .addPrimitive(PRIMITIVE_TICK, 0.5f, 46)
            .addPrimitive(PRIMITIVE_THUD, 0.25f, 68)
            .compose()

    val dragStopEffect =
        VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_LOW_TICK, 0.25f, 0)
            .addPrimitive(PRIMITIVE_THUD, 0.25f, 60)
            .compose()

    // We can't use LocalHapticFeedback here as we're using a custom vibration effects
    val vibrator =
        LocalContext.current.getSystemService(Vibrator::class.java).takeIf {
            it?.hasVibrator() ?: false
        }

    LaunchedEffect(anchoredDraggableState.isAnimationRunning) {
        if (!anchoredDraggableState.isAnimationRunning) return@LaunchedEffect
        if (anchoredDraggableState.targetValue == anchoredDraggableState.currentValue)
            return@LaunchedEffect

        // An animation has just started that was *not* caused by a drag
        // The current and target values should be different
        // Look at the target value to determine which effect to run
        when (anchoredDraggableState.targetValue) {
            Start -> vibrator?.vibrate(enterEffect)
            End -> vibrator?.vibrate(exitEffect)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    LaunchedEffect(Unit) {
        // The user has just released a drag and the anchoredDraggable will animate towards
        // a settled position. In this case we don't know where the animation will settle towards
        // because velocity isn't observable - lastVelocity is not the velocity on drag release.
        // The value of progress is just positional threshold. The value of current, target, and
        // settledValue again only indicate positional threshold state. We need to run some haptics
        // here, so just opt for a generic vibration effect that's not a function of the eventual
        // settled position.
        snapshotFlow { isDragged }
            .drop(1) // Use a snapshotFlow to drop the initial value which is always false
            .collect { isDragged -> if (!isDragged) vibrator?.vibrate(dragStopEffect) }
    }

    Column(
        modifier =
            modifier
                .anchoredDraggable(
                    state = anchoredDraggableState,
                    orientation = Orientation.Vertical,
                    enabled = expanded,
                    overscrollEffect = overscrollEffect,
                    interactionSource = interactionSource,
                )
                .onGloballyPositioned { layoutCoordinates ->
                    containerHeightPx = layoutCoordinates.size.height
                    containerWidthPx = layoutCoordinates.size.width
                    actionListCenterPositionX =
                        layoutCoordinates.positionInWindow().x + layoutCoordinates.size.width / 2
                    actionListCenterPositionY =
                        layoutCoordinates.positionInWindow().y + layoutCoordinates.size.height / 2
                    anchoredDraggableState.updateAnchors(
                        DraggableAnchors {
                            Start at 0f // Hidden
                            End at -containerHeightPx.toFloat() // Visible
                        }
                    )
                }
                .drawBehind {
                    val sidePaddingPx =
                        if (taskBarMode) {
                            pillCenter.x - actionListCenterPositionX
                        } else {
                            with(density) { scrimHorizontalPadding.toPx() }
                        }
                    if (!(radius > 0)) return@drawBehind

                    val minScaleY = minGradientHeightPx / (radius * 2f)
                    val scaleY = max(minScaleY, size.height / (radius * 2f) * scrimProgress)

                    scale(
                        scaleX = if (!landscapeMode) 1f else landscapeScaleX,
                        scaleY = scaleY,
                        pivot = Offset(0f, if (!landscapeMode) size.height else 0f),
                    ) {
                        landscapeCollapsedScrimOffsetY =
                            size.height / 2 + pillCenter.y - actionListCenterPositionY
                        val safeScaleY = if (scaleY == 0f || scaleY.isNaN()) 1f else scaleY
                        val safeScaleX =
                            if (landscapeScaleX == 0f || landscapeScaleX.isNaN()) 1f
                            else landscapeScaleX
                        val leftOrUpGradientCenter =
                            if (!landscapeMode) {
                                Offset(size.width / 2 - sidePaddingPx, scrimOffsetY)
                            } else {
                                Offset(
                                    if (rotation == ROTATION_90) {
                                        landscapeScrimOffsetX / safeScaleX
                                    } else {
                                        (containerWidthPx - landscapeScrimOffsetX) / safeScaleX
                                    },
                                    (landscapeScrimOffsetY - pillHeight / 2) / safeScaleY,
                                )
                            }
                        val rightOrDownGradientCenter =
                            if (!landscapeMode) {
                                Offset(size.width / 2 + sidePaddingPx, scrimOffsetY)
                            } else {
                                Offset(
                                    if (rotation == ROTATION_90) {
                                        landscapeScrimOffsetX / safeScaleX
                                    } else {
                                        (containerWidthPx - landscapeScrimOffsetX) / safeScaleX
                                    },
                                    (landscapeScrimOffsetY + pillHeight / 2) / safeScaleY,
                                )
                            }

                        val leftOrUpBrush =
                            Brush.radialGradient(
                                colors =
                                    listOf(leftGradientColor, leftGradientColor.copy(alpha = 0f)),
                                center = leftOrUpGradientCenter,
                                radius = radius,
                            )
                        val rightOrDownBrush =
                            Brush.radialGradient(
                                colors =
                                    listOf(rightGradientColor, rightGradientColor.copy(alpha = 0f)),
                                center = rightOrDownGradientCenter,
                                radius = radius * 0.85f,
                            )
                        drawCircle(
                            brush = leftOrUpBrush,
                            alpha = smartScrimAlpha + smartScrimAlphaBoost,
                            radius = radius,
                            center = leftOrUpGradientCenter,
                        )
                        drawCircle(
                            brush = rightOrDownBrush,
                            alpha = smartScrimAlpha + smartScrimAlphaBoost,
                            radius = radius,
                            center = rightOrDownGradientCenter,
                        )
                    }
                }
                .padding(padding),
        verticalArrangement = Arrangement.spacedBy(columnSpacing, Alignment.Bottom),
        horizontalAlignment = horizontalAlignment,
    ) {
        if (showEducation && expanded) {
            AnimatedVisibility(
                visible = progress == 1f,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                EducationTooltip(horizontalAlignment)
            }
        }

        if (enableSage() && expanded) {
            var hints by remember {
                mutableStateOf(
                    setOf<ContextHint>(
                        ExampleEmbeddedHint(
                                totalSpanCapacity = InsightGrid.TOTAL_SPAN_CAPACITY_PHONE
                            )
                            .toContextHint()
                    )
                )
            }
            HelloWorldEmbeddedCard(hints)
        }

        val childHeights = remember(actions) { MutableList(actions.size) { 0 } }
        actions.forEachIndexed { index, action ->
            val scale by
                animateFloatAsState(
                    progress,
                    animationSpec =
                        spring(
                            dampingRatio = scaleDampingRatio,
                            stiffness =
                                Spring.StiffnessLow + index * index * scaleStiffnessMultiplier,
                        ),
                )
            val translation by
                animateFloatAsState(
                    progress,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness =
                                Spring.StiffnessLow + index * index * translateStiffnessMultiplier,
                        ),
                )

            var appxColumnY by remember(childHeights) { mutableFloatStateOf(0f) }
            LaunchedEffect(childHeights) {
                appxColumnY =
                    childHeights.subList(index, childHeights.size).sum() +
                        columnSpacingPx * max((childHeights.size - index - 1f), 0f)
            }

            var chipWidthPx by remember { mutableIntStateOf(0) }

            Chip(
                action = action,
                modifier =
                    Modifier.onSizeChanged {
                            if (index < childHeights.size) {
                                childHeights[index] = it.height
                            }
                            chipWidthPx = it.width
                        }
                        .onGloballyPositioned { radius = max(radius, it.size.width / 2f) }
                        .graphicsLayer {
                            val chipsTotalHeightPx =
                                childHeights.sum().toFloat() +
                                    columnSpacingPx * (childHeights.size - 1)
                            if (portrait || taskBarMode) {
                                translationY = (1f - translation) * appxColumnY
                                translationX = 0f
                            } else {
                                if (rotation == ROTATION_90) {
                                    translationY =
                                        (1f - translation) *
                                            (pillCenter.y - pillWidth - chipsTotalHeightPx +
                                                appxColumnY)
                                    translationX = (1f - translation) * chipWidthPx.toFloat()
                                } else if (rotation == ROTATION_270) {
                                    translationY =
                                        (1f - translation) *
                                            (pillCenter.y - pillWidth - chipsTotalHeightPx +
                                                appxColumnY)
                                    translationX = (translation - 1f) * chipWidthPx.toFloat()
                                }
                            }
                            scaleX = scale
                            scaleY = scale
                            transformOrigin =
                                if (portrait || taskBarMode) {
                                    TransformOrigin(0.5f, 1f)
                                } else {
                                    if (rotation == ROTATION_90) {
                                        TransformOrigin(1f, 0f)
                                    } else {
                                        TransformOrigin(0f, 0f)
                                    }
                                }
                        },
            )
        }
    }
}

@Composable
private fun EducationTooltip(horizontalAlignment: Alignment.Horizontal) {
    val backgroundColor = MaterialTheme.colorScheme.tertiary
    val foregroundColor = MaterialTheme.colorScheme.onTertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.eduBalloon(backgroundColor, horizontalAlignment),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ambientcue_hold_tooltip),
            contentDescription = null,
            colorFilter = ColorFilter.tint(foregroundColor),
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringResource(R.string.ambientcue_long_press_edu_text),
            style = MaterialTheme.typography.labelLarge,
            color = foregroundColor,
        )
    }
}

@Composable
private fun HelloWorldEmbeddedCard(hints: Set<ContextHint>) {
    Log.i("ActionList", "HelloWorldEmbeddedCard rendered")

    val sessionState = rememberSessionState()
    val overlapState = rememberOverlapState(SetZOrderBehind.NoFade)

    AceEmbeddedSurfaceView(
        sessionState = sessionState,
        overlapState = overlapState,
        hints = hints,
        backgroundColor = MaterialTheme.colorScheme.surface,
        constraintsModifier = Modifier.fillMaxWidth().height(250.dp),
        errorContent = { Text("Error loading card") },
    )
}
