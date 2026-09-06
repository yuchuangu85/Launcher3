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

import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.popup.ui.PopupMenuItemDimens.deepShortcutAddButtonSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.deepShortcutIconSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.iconOnlyButtonSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHeight
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHorizontalPadding
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemIconSpacerWidth
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemWidth
import com.android.launcher3.popup.ui.PopupMenuItemDimens.systemShortcutIconPadding
import com.android.launcher3.util.compose.testTag
import com.android.launcher3.util.compose.textStyleFromResource

/**
 * A Composable for displaying a shortcut item with an icon, title, and optional trailing content.
 *
 * @param modifier Optional [Modifier] for this composable.
 * @param icon A Composable function that renders the leading icon.
 * @param title The primary text to display.
 * @param itemContentDescription Content description for the entire clickable item and the main
 *   icon.
 * @param onClick The click listener for the main item area.
 * @param onLongClick An optional long click listener for the main item area.
 * @param trailingContent An optional Composable function for content aligned to the end of the
 *   item.
 */
@Composable
fun PopupMenuItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    itemContentDescription: String,
    onClick: () -> Unit,
    onLongClick: ((Offset) -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowHovered by rowInteractionSource.collectIsHoveredAsState()
    val isRowFocused by rowInteractionSource.collectIsFocusedAsState()

    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Surface(
        modifier = modifier.size(popupMenuItemWidth, popupMenuItemHeight),
        shape = RoundedCornerShape(ComposePopupDimens.popupCornerRadius),
        color =
            colorResource(
                if (isRowHovered) R.color.materialColorSurfaceVariant
                else R.color.materialColorSurfaceContainer
            ),
        border =
            if (isRowFocused) {
                BorderStroke(3.dp, colorResource(R.color.materialColorSecondary))
            } else {
                null
            },
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { layoutCoordinates = it }
                        .hoverable(rowInteractionSource)
                        .combinedClickable(
                            interactionSource = rowInteractionSource,
                            indication = null,
                            onClick = onClick,
                            onLongClick =
                                onLongClick?.let {
                                    {
                                        val position =
                                            layoutCoordinates?.positionInWindow() ?: Offset.Zero
                                        val size = layoutCoordinates?.size ?: IntSize.Zero
                                        onLongClick.invoke(
                                            position + Offset(size.width / 2f, size.height / 2f)
                                        )
                                    }
                                },
                        )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(
                                start = popupMenuItemHorizontalPadding,
                                end =
                                    popupMenuItemHorizontalPadding.takeIf {
                                        trailingContent == null
                                    } ?: 0.dp,
                                top = 0.dp,
                                bottom = 0.dp,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon()
                    Spacer(modifier = Modifier.width(popupMenuItemIconSpacerWidth))
                    Text(
                        text = title,
                        color = colorResource(R.color.materialColorOnSurface),
                        style = PopupMenuStyles.popupMenuItemTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            trailingContent?.let {
                Box(
                    modifier = Modifier.fillMaxHeight().wrapContentWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    it()
                }
            }
        }
    }
}

/**
 * Composable for displaying a system shortcut item. This can render as an icon-only item or a full
 * menu item with text and an icon, based on [isIconOnly].
 *
 * @param shortcut The [SystemShortcut] data to display.
 * @param onClick The callback function to invoke when the shortcut item is clicked.
 * @param isIconOnly If true, renders only the icon; otherwise, renders the icon with text.
 */
@Composable
fun SystemShortcutMenuItem(
    shortcut: PopupItem,
    onClick: (PopupClickEvent) -> Unit,
    isIconOnly: Boolean = false,
) {
    val title = stringResource(id = shortcut.labelResId)
    val painter: Painter = painterResource(id = shortcut.iconResId)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    if (isIconOnly) {
        Surface(
            onClick = { onClick(SystemShortcutClickEvent(shortcut)) },
            modifier =
                Modifier.size(iconOnlyButtonSize).clearAndSetSemantics {
                    contentDescription = title
                    role = Role.Button
                    onClick {
                        onClick(SystemShortcutClickEvent(shortcut))
                        true
                    }
                },
            shape = CircleShape,
            color =
                colorResource(
                    if (isHovered) R.color.materialColorSurfaceVariant
                    else R.color.materialColorSurfaceContainer
                ),
            border =
                if (isFocused) {
                    BorderStroke(3.dp, colorResource(R.color.materialColorSecondary))
                } else {
                    null
                },
            interactionSource = interactionSource,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(systemShortcutIconPadding),
            ) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = colorResource(R.color.materialColorOnSurface),
                )
            }
        }
    } else {
        val iconComposable: @Composable () -> Unit = {
            Spacer(modifier = Modifier.width(systemShortcutIconPadding))
            Icon(
                painter = painter,
                contentDescription = null,
                tint = colorResource(R.color.materialColorOnSurface),
            )
        }

        PopupMenuItem(
            modifier = Modifier.testTag(title),
            icon = iconComposable,
            title = title,
            itemContentDescription = title,
            onClick = { onClick(SystemShortcutClickEvent(shortcut)) },
            trailingContent = null,
        )
    }
}

/**
 * Composable for displaying a deep shortcut item.
 *
 * This composable displays a single deep shortcut, including its icon, title, and an "add to home
 * screen" button. It handles both the display of a valid shortcut and a placeholder state for when
 * the shortcut data is null (e.g., during loading).
 *
 * @param shortcut The [ItemInfoWithIcon] data for the deep shortcut. If null, a placeholder UI is
 *   shown.
 * @param onClick The callback function to invoke when the main body of the shortcut item is
 *   clicked.
 * @param onAddButtonClick The callback function to invoke when the "add to home screen" icon is
 *   clicked.
 * @param onLongClick The callback function to invoke when the shortcut item is long-pressed.
 */
@Composable
fun DeepShortcutMenuItem(
    shortcut: ItemInfoWithIcon?,
    onClick: (PopupClickEvent) -> Unit,
    onAddButtonClick: ((ItemInfoWithIcon) -> Unit)?,
    onLongClick: (ItemInfoWithIcon, Offset) -> Unit,
) {
    val iconComposable: @Composable () -> Unit
    val itemTitle: String
    val itemContentDescription: String
    val itemOnClick: () -> Unit
    val itemOnLongClick: ((Offset) -> Unit)?
    val itemTrailingContent: @Composable (() -> Unit)?
    var iconWindowBounds by remember { mutableStateOf(Rect()) }

    val addInteractionSource =
        if (onAddButtonClick != null) remember { MutableInteractionSource() } else null

    if (shortcut != null) {
        itemTitle = shortcut.title.toString()
        itemContentDescription = itemTitle
        iconComposable = {
            Icon(
                bitmap = shortcut.bitmap.icon.asImageBitmap(),
                contentDescription = null,
                modifier =
                    Modifier.size(deepShortcutIconSize).clip(CircleShape).onGloballyPositioned {
                        coordinates ->
                        val position = coordinates.positionInWindow()
                        val size = coordinates.size
                        iconWindowBounds =
                            Rect(
                                position.x.toInt(),
                                position.y.toInt(),
                                (position.x + size.width).toInt(),
                                (position.y + size.height).toInt(),
                            )
                    },
                tint = Color.Unspecified,
            )
        }
        itemOnClick = { onClick(DeepShortcutClickEvent(shortcut, iconWindowBounds)) }
        itemOnLongClick = { screenOffset -> onLongClick(shortcut, screenOffset) }
        itemTrailingContent =
            if (onAddButtonClick != null && addInteractionSource != null) {
                {
                    val isAddHovered by addInteractionSource.collectIsHoveredAsState()
                    val isAddFocused by addInteractionSource.collectIsFocusedAsState()
                    Surface(
                        onClick = { onAddButtonClick(shortcut) },
                        modifier = Modifier.size(deepShortcutAddButtonSize),
                        shape = CircleShape,
                        color =
                            if (isAddHovered) colorResource(R.color.materialColorSurfaceVariant)
                            else Color.Transparent,
                        border =
                            if (isAddFocused) {
                                BorderStroke(3.dp, colorResource(R.color.materialColorSecondary))
                            } else {
                                null
                            },
                        interactionSource = addInteractionSource,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add_circle_filled),
                                tint = colorResource(R.color.materialColorSecondary),
                                contentDescription =
                                    stringResource(R.string.action_add_to_workspace),
                            )
                        }
                    }
                }
            } else {
                null
            }
    } else {
        // When shortcut is null, render a generic item without title or drawable
        itemTitle = ""
        itemContentDescription = ""
        iconComposable = { Spacer(modifier = Modifier.size(deepShortcutIconSize)) }
        itemOnClick = { /* Do nothing */ }
        itemOnLongClick = null
        itemTrailingContent = null
    }

    PopupMenuItem(
        icon = iconComposable,
        title = itemTitle,
        itemContentDescription = itemContentDescription,
        onClick = itemOnClick,
        onLongClick = itemOnLongClick,
        trailingContent = itemTrailingContent,
    )
}

object PopupMenuItemDimens {
    val popupMenuItemHeight = 52.dp
    val popupMenuItemWidth = 216.dp
    val popupMenuItemHorizontalPadding = 12.dp
    val popupMenuItemIconSpacerWidth = 8.dp
    val systemShortcutIconPadding = 4.dp
    val deepShortcutIconSize = 32.dp
    val deepShortcutAddButtonSize = 48.dp
    val iconOnlyButtonSize = 48.dp
}

object PopupMenuStyles {
    val popupMenuItemTextStyle: TextStyle
        @Composable get() = textStyleFromResource(R.style.PopupMenuItemText)
}
