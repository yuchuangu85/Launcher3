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

package com.android.launcher3.popup.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHeight
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHorizontalPadding
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemWidth
import com.android.launcher3.testing.shared.TestProtocol.DEEP_SHORTCUTS_CONTAINER
import com.android.launcher3.testing.shared.TestProtocol.SYSTEM_SHORTCUTS_CONTAINER
import com.android.launcher3.util.compose.testTag
import com.android.launcher3.util.compose.testTagContainer
import kotlin.math.max

/**
 * The main composable for displaying the Launcher3 popup menu, built with Jetpack Compose.
 *
 * This composable orchestrates the display of system shortcuts and deep shortcuts, handling
 * different UI states (e.g., system shortcuts only, simple hybrid, collapsible hybrid) based on the
 * [PopupUiState] provided by the [viewModel]. It also manages the measurement of the popup's
 * maximum height to inform the underlying Android View system for proper positioning.
 *
 * @param viewModel The [PopupViewModel] that holds the UI state and business logic for the popup.
 * @param onClickListener A callback function invoked when any clickable item within the popup is
 *   tapped.
 * @param onAddIconClick A callback function specifically for when the 'add to home screen' icon is
 *   clicked for a deep shortcut.
 * @param onDeepShortcutLongPress A callback function invoked when a deep shortcut item is
 *   long-pressed.
 * @param onMaxHeightMeasured An optional callback that reports the maximum calculated height of the
 *   popup content to the caller, typically used for positioning the Android View.
 */
@Composable
fun ComposePopup(
    viewModel: PopupViewModel,
    onClickListener: (PopupClickEvent) -> Unit,
    onAddIconClick: ((ItemInfoWithIcon) -> Unit)?,
    onDeepShortcutLongPress: (ItemInfoWithIcon, Offset) -> Unit,
    onMaxHeightMeasured: ((Int) -> Unit)?,
) {
    val state = viewModel.state

    Box(
        modifier = Modifier.bottomAlignAndAllowOverflow().testTagContainer(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ComposePopupContent(
            viewModel,
            state,
            onClickListener,
            onAddIconClick,
            onDeepShortcutLongPress,
            onMaxHeightMeasured,
        )
    }
}

/**
 * A composable that aligns its content to the bottom of its parent and allows the content to
 * overflow vertically.
 *
 * This modifier is used to ensure that the popup menu is positioned correctly at the bottom of the
 * screen, while still allowing the popup's content to expand upwards without being clipped by the
 * parent's bounds. This is crucial for the expand/collapse animations and for accommodating popups
 * of varying heights.
 *
 * @return A [Modifier] that applies the custom layout behavior.
 */
private fun Modifier.bottomAlignAndAllowOverflow() =
    this.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints()) // unbounded
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/**
 * The core content of the Launcher3 popup menu, responsible for orchestrating the display of
 * different UI states.
 *
 * This composable acts as a router, deciding which specific popup layout to render based on the
 * [targetState]. It handles the logic for both accordion-style and simple list-style popups, and it
 * s responsible for calculating and reporting the maximum potential height of the popup to the
 * parent Android View system.
 *
 * @param viewModel The [PopupViewModel] that provides the UI state and handles business logic.
 * @param targetState The target [PopupUiState] to be rendered.
 * @param onClickListener A callback for when a clickable item is tapped.
 * @param onAddIconClick A callback for when the 'add to home screen' icon is clicked.
 * @param onDeepShortcutLongPress A callback for when a deep shortcut is long-pressed.
 * @param onMaxHeightMeasured An optional callback to report the maximum calculated height of the
 *   popup content.
 */
@Composable
private fun ComposePopupContent(
    viewModel: PopupViewModel,
    targetState: PopupUiState,
    onClickListener: (PopupClickEvent) -> Unit,
    onAddIconClick: ((ItemInfoWithIcon) -> Unit)?,
    onDeepShortcutLongPress: (ItemInfoWithIcon, Offset) -> Unit,
    onMaxHeightMeasured: ((Int) -> Unit)?,
) {
    val density = LocalDensity.current

    // Calculate the maximum possible height of the popup based on the current state and style.
    // This value is remembered and only recalculated when 'state' or 'density' changes.
    val maxHeight =
        remember(targetState, density) {
            with(density) {
                val spacerHeight = ComposePopupDimens.popupContentSpacerHeight.toPx()

                if (targetState.mainSegmentsStyle == MainSegmentsStyle.ACCORDION) {
                    val expandPopupMenuButtonHeight = popupMenuItemHeight.toPx()
                    val systemShortcutsHeight: Float =
                        if (targetState.compactSystemShortcuts.isEmpty()) {
                            targetState.standardSystemShortcuts.size * popupMenuItemHeight.toPx()
                        } else {
                            ((targetState.standardSystemShortcuts.size + 1) *
                                popupMenuItemHeight.toPx()) +
                                ComposePopupDimens.systemShortcutsDividerHeight.toPx()
                        }

                    val deepShortcutsHeight =
                        (targetState.deepShortcuts.size * popupMenuItemHeight.toPx())

                    val heightDeepExpanded =
                        expandPopupMenuButtonHeight + deepShortcutsHeight + spacerHeight
                    val heightSystemExpanded =
                        expandPopupMenuButtonHeight + systemShortcutsHeight + spacerHeight
                    max(heightDeepExpanded, heightSystemExpanded).toInt()
                } else { // MainSegmentsStyle.LIST
                    var systemShortcutsHeight = 0f
                    if (targetState.compactSystemShortcuts.isNotEmpty()) {
                        systemShortcutsHeight += popupMenuItemHeight.toPx()
                    }
                    if (
                        targetState.compactSystemShortcuts.isNotEmpty() &&
                            targetState.standardSystemShortcuts.isNotEmpty()
                    ) {
                        systemShortcutsHeight += ComposePopupDimens.popupContentSpacerHeight.toPx()
                    }
                    systemShortcutsHeight +=
                        targetState.standardSystemShortcuts.size * popupMenuItemHeight.toPx()

                    val deepShortcutsHeight =
                        targetState.deepShortcuts.size * popupMenuItemHeight.toPx()

                    if (deepShortcutsHeight > 0) {
                        systemShortcutsHeight +=
                            ComposePopupDimens.systemShortcutsDividerHeight.toPx()
                    }

                    (systemShortcutsHeight + deepShortcutsHeight + spacerHeight).toInt()
                }
            }
        }

    val lastReportedMaxHeight = remember { mutableIntStateOf(-1) }

    SideEffect {
        if (onMaxHeightMeasured != null && lastReportedMaxHeight.intValue != maxHeight) {
            onMaxHeightMeasured(maxHeight)
            lastReportedMaxHeight.intValue = maxHeight
        }
    }

    if (targetState.mainSegmentsStyle == MainSegmentsStyle.ACCORDION) {
        val expandedSection = viewModel.expandedSection

        val isSystemShortcutsExpanded = expandedSection == ExpandedSection.SYSTEM
        val isDeepShortcutsExpanded = expandedSection == ExpandedSection.DEEP

        ExpandableHybridPopup(
            compactSystemShortcuts = targetState.compactSystemShortcuts,
            standardSystemShortcuts = targetState.standardSystemShortcuts,
            standardDeepShortcuts = targetState.deepShortcuts,
            isSystemShortcutsExpanded = isSystemShortcutsExpanded,
            isDeepShortcutsExpanded = isDeepShortcutsExpanded,
            onToggle = viewModel::expandSection,
            onClickListener = onClickListener,
            onAddButtonClick = onAddIconClick,
            onDeepShortcutLongPress = onDeepShortcutLongPress,
        )
    } else { // MainSegmentsStyle.LIST
        ExpandableHybridPopup(
            compactSystemShortcuts = targetState.compactSystemShortcuts,
            standardSystemShortcuts = targetState.standardSystemShortcuts,
            standardDeepShortcuts = targetState.deepShortcuts,
            isSystemShortcutsExpanded = true, // Always expanded
            isDeepShortcutsExpanded = true, // Always expanded
            onToggle = {},
            onClickListener = onClickListener,
            onAddButtonClick = onAddIconClick,
            onDeepShortcutLongPress = onDeepShortcutLongPress,
        )
    }
}

/**
 * A container that animates between a collapsed and an expanded state.
 *
 * This composable is a key building block for the accordion-style popup. It uses [AnimatedContent]
 * to create a smooth transition when the content expands or collapses, providing a polished user
 * experience.
 *
 * @param collapsedContent The composable to display when the container is in its collapsed state.
 * @param expandedContent The composable to display when the container is in its expanded state.
 * @param modifier An optional [Modifier] for this composable.
 * @param transition The [Transition] that drives the animation between collapsed and expanded
 *   states.
 */
@Composable
fun ExpandableContainer(
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    transition: Transition<Boolean>,
) {
    val animationProgress by
        transition.animateFloat(
            transitionSpec = { ComposePopupAnimations.popupContentSpringSpec },
            label = "ExpandCollapseProgress",
        ) { isExpanded ->
            if (isExpanded) 1f else 0f
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(ComposePopupDimens.popupCornerRadius))
                .background(colorResource(R.color.materialColorSurfaceContainer))
    ) {
        AnimatedContent(
            targetState = transition.targetState,
            modifier = Modifier,
            label = "ExpandableContent",
            transitionSpec = {
                // By providing a real fadeIn/fadeOut, we tell AnimatedContent to keep both
                // the entering and exiting content composed during the animation.
                // The actual alpha value will be controlled manually by the graphicsLayer below.
                fadeIn(animationSpec = ComposePopupAnimations.popupContentSpringSpec) togetherWith
                    fadeOut(animationSpec = ComposePopupAnimations.popupContentSpringSpec) using
                    SizeTransform(
                        clip = true,
                        sizeAnimationSpec = { _, _ ->
                            ComposePopupAnimations.popupContainerSpringSpec
                        },
                    )
            },
        ) { isExpanded ->
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        alpha =
                            if (isExpanded) {
                                // Expanded content: fades in/out over the 80% duration.
                                ((animationProgress -
                                        ComposePopupAnimations
                                            .EXPANDED_CONTENT_FADE_START_PROGRESS) /
                                        ComposePopupAnimations.EXPANDED_CONTENT_FADE_DURATION_RATIO)
                                    .coerceIn(0f, 1f)
                            } else {
                                // Collapsed content: fades in/out over the 20% duration.
                                (1f -
                                        (animationProgress /
                                            ComposePopupAnimations
                                                .COLLAPSED_CONTENT_FADE_DURATION_RATIO))
                                    .coerceIn(0f, 1f)
                            }
                    }
            ) {
                if (isExpanded) {
                    expandedContent()
                } else {
                    collapsedContent()
                }
            }
        }
    }
}

/**
 * A generic, expandable hybrid popup that can display both system and deep shortcuts.
 *
 * This composable forms the basis of the accordion-style popup. It manages two expandable
 * sections—one for system shortcuts and one for deep shortcuts—and allows them to be independently
 * expanded or collapsed. It also displays a row of compact, icon-only system shortcuts at the top.
 *
 * @param modifier An optional [Modifier] for this composable.
 * @param compactSystemShortcuts A list of [PopupItem]s to be displayed as icon-only shortcuts at
 *   the top.
 * @param standardSystemShortcuts A list of [PopupItem]s to be displayed in the expandable system
 *   shortcuts section.
 * @param standardDeepShortcuts A list of [ItemInfoWithIcon]s to be displayed in the expandable deep
 *   shortcuts section.
 * @param isSystemShortcutsExpanded A boolean indicating whether the system shortcuts section is
 *   currently expanded.
 * @param isDeepShortcutsExpanded A boolean indicating whether the deep shortcuts section is
 *   currently expanded.
 * @param onToggle A callback function invoked when an expandable section is clicked, indicating
 *   which section to toggle.
 * @param onClickListener A general click listener for all shortcut items.
 * @param onAddButtonClick A specific click listener for the 'add to home screen' icon on deep
 *   shortcuts.
 * @param onDeepShortcutLongPress A long-press listener for deep shortcuts.
 */
@Composable
fun ExpandableHybridPopup(
    modifier: Modifier = Modifier,
    compactSystemShortcuts: List<PopupItem>,
    standardSystemShortcuts: List<PopupItem>,
    standardDeepShortcuts: List<ItemInfoWithIcon?>,
    isSystemShortcutsExpanded: Boolean,
    isDeepShortcutsExpanded: Boolean,
    onToggle: (ExpandedSection) -> Unit,
    onClickListener: (PopupClickEvent) -> Unit,
    onAddButtonClick: ((ItemInfoWithIcon) -> Unit)?,
    onDeepShortcutLongPress: (ItemInfoWithIcon, Offset) -> Unit,
) {
    val systemTransitionState = remember { MutableTransitionState(isSystemShortcutsExpanded) }
    LaunchedEffect(isSystemShortcutsExpanded) {
        systemTransitionState.targetState = isSystemShortcutsExpanded
    }
    val systemTransition =
        rememberTransition(systemTransitionState, label = "SystemShortcutsTransition")

    val deepTransitionState = remember { MutableTransitionState(isDeepShortcutsExpanded) }
    LaunchedEffect(isDeepShortcutsExpanded) {
        deepTransitionState.targetState = isDeepShortcutsExpanded
    }
    val deepTransition = rememberTransition(deepTransitionState, label = "DeepShortcutsTransition")

    Column(
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Bottom),
    ) {
        ExpandableContainer(
            collapsedContent = {
                ExpandSystemShortcutsMenuButton(
                    onShowSystemShortcuts = { onToggle(ExpandedSection.SYSTEM) }
                )
            },
            expandedContent = {
                SystemShortcutsSection(
                    compactSystemShortcuts,
                    standardSystemShortcuts,
                    onClickListener,
                )
            },
            transition = systemTransition,
        )
        if (standardSystemShortcuts.isNotEmpty() && standardDeepShortcuts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(ComposePopupDimens.popupContentSpacerHeight))
        }
        if (standardDeepShortcuts.isNotEmpty()) {
            ExpandableContainer(
                collapsedContent = {
                    ExpandDeepShortcutsMenuButton(
                        onShowDeepShortcuts = { onToggle(ExpandedSection.DEEP) }
                    )
                },
                expandedContent = {
                    DeepShortcutsContent(
                        standardDeepShortcuts,
                        onClickListener,
                        onAddButtonClick,
                        onDeepShortcutLongPress,
                    )
                },
                transition = deepTransition,
            )
        }
    }
}

/**
 * A composable that renders the content of the system shortcuts section.
 *
 * This container displays a row of compact, icon-only shortcuts at the top, followed by a list of
 * standard system shortcuts.
 *
 * @param compactShortcuts The list of shortcuts to display as icon-only items in a row.
 * @param standardShortcuts The list of shortcuts to display as full items in a column.
 * @param onClick A callback function for when a shortcut is clicked.
 */
@Composable
private fun SystemShortcutsSection(
    compactShortcuts: List<PopupItem>,
    standardShortcuts: List<PopupItem>,
    onClick: (PopupClickEvent) -> Unit,
) {
    Column(modifier = Modifier.testTag(SYSTEM_SHORTCUTS_CONTAINER)) {
        if (compactShortcuts.isNotEmpty()) {
            Row(
                modifier =
                    Modifier.size(popupMenuItemWidth, popupMenuItemHeight)
                        .padding(vertical = 0.dp, horizontal = popupMenuItemHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                compactShortcuts.forEach { shortcut ->
                    SystemShortcutMenuItem(
                        shortcut = shortcut,
                        onClick = onClick,
                        isIconOnly = true,
                    )
                }
            }
        }

        if (compactShortcuts.isNotEmpty() && standardShortcuts.isNotEmpty()) {
            Box(
                modifier =
                    Modifier.size(
                        popupMenuItemWidth,
                        ComposePopupDimens.systemShortcutsDividerHeight,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Spacer(
                    modifier =
                        Modifier.size(
                                ComposePopupDimens.systemShortcutsDividerWidth,
                                ComposePopupDimens.systemShortcutsDividerHeight,
                            )
                            .background(colorResource(R.color.materialColorOutlineVariant))
                )
            }
        }

        if (standardShortcuts.isNotEmpty()) {
            standardShortcuts.forEach { shortcut ->
                SystemShortcutMenuItem(shortcut = shortcut, onClick = onClick)
            }
        }
    }
}

/**
 * A composable that renders the content of the deep shortcuts section.
 *
 * This container iterates through a list of [ItemInfoWithIcon]s and displays each one as a
 * [DeepShortcutMenuItem].
 *
 * @param deepShortcuts The list of deep shortcuts to display.
 * @param onClick A callback for when a shortcut is clicked.
 * @param onAddButtonClick A callback for when the 'add to home screen' icon is clicked.
 * @param onDeepShortcutLongPress A callback for when a shortcut is long-pressed.
 */
@Composable
private fun DeepShortcutsContent(
    deepShortcuts: List<ItemInfoWithIcon?>,
    onClick: (PopupClickEvent) -> Unit,
    onAddButtonClick: ((ItemInfoWithIcon) -> Unit)?,
    onDeepShortcutLongPress: (ItemInfoWithIcon, Offset) -> Unit,
) {
    Column(modifier = Modifier.testTag(DEEP_SHORTCUTS_CONTAINER)) {
        deepShortcuts.forEach { shortcut ->
            DeepShortcutMenuItem(
                shortcut = shortcut,
                onClick = onClick,
                onAddButtonClick = onAddButtonClick,
                onLongClick = { item, offset -> onDeepShortcutLongPress(item, offset) },
            )
        }
    }
}

object ComposePopupDimens {
    val popupCornerRadius = 24.dp
    val popupContentSpacerHeight = 2.dp
    val systemShortcutsDividerHeight = 1.dp
    val systemShortcutsDividerWidth = 180.dp
}

private object ComposePopupAnimations {
    val popupContentSpringSpec = spring<Float>(dampingRatio = 1f, stiffness = 300f)
    val popupContainerSpringSpec = spring<IntSize>(dampingRatio = 0.7f, stiffness = 300f)
    const val EXPANDED_CONTENT_FADE_START_PROGRESS = 0.2f
    const val EXPANDED_CONTENT_FADE_DURATION_RATIO = 0.8f
    const val COLLAPSED_CONTENT_FADE_DURATION_RATIO = 0.2f
}
