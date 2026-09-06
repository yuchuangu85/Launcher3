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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.popup.ui.ComposePopupDimens.popupCornerRadius
import com.android.launcher3.popup.ui.ExpandPopupMenuButtonDimens.expandPopupMenuItemHorizontalPadding
import com.android.launcher3.popup.ui.ExpandPopupMenuButtonDimens.expandPopupMenuItemImageSize
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemHeight
import com.android.launcher3.popup.ui.PopupMenuItemDimens.popupMenuItemWidth
import com.android.launcher3.util.compose.textStyleFromResource

/**
 * A generic composable for a menu item that expands a section in the popup.
 *
 * @param text The text to display for the menu item.
 * @param contentDescription The content description for the button.
 * @param onClick The callback function to invoke when the menu item is clicked.
 */
@Composable
fun ExpandPopupMenuButton(text: String, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier =
            Modifier.size(popupMenuItemWidth, popupMenuItemHeight).semantics(
                mergeDescendants = true
            ) {
                this.role = Role.Button
                this.onClick(label = contentDescription) {
                    onClick()
                    true
                }
            },
        shape = RoundedCornerShape(popupCornerRadius),
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
        Row(
            modifier =
                Modifier.padding(
                    vertical = 0.dp,
                    horizontal = expandPopupMenuItemHorizontalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = colorResource(R.color.materialColorOnSurface),
                style = ExpandButtonStyles.expandButtonTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier.size(40.dp, 32.dp)
                        .clip(CircleShape)
                        .background(colorResource(R.color.materialColorTertiary)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_expand_arrows),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorResource(R.color.materialColorOnTertiary)),
                    modifier = Modifier.size(expandPopupMenuItemImageSize),
                )
            }
        }
    }
}

/**
 * Composable for a menu item that, when clicked, expands the system shortcuts section.
 *
 * @param onShowSystemShortcuts The callback function to invoke when the menu item is clicked.
 */
@Composable
fun ExpandSystemShortcutsMenuButton(onShowSystemShortcuts: () -> Unit) {
    ExpandPopupMenuButton(
        text = stringResource(R.string.actions_popup_menu_button),
        contentDescription = stringResource(R.string.actions_popup_menu_button_description),
        onClick = onShowSystemShortcuts,
    )
}

/**
 * Composable for a menu item that, when clicked, expands the deep shortcuts section.
 *
 * @param onShowDeepShortcuts The callback function to invoke when the menu item is clicked.
 */
@Composable
fun ExpandDeepShortcutsMenuButton(onShowDeepShortcuts: () -> Unit) {
    ExpandPopupMenuButton(
        text = stringResource(R.string.shortcuts_popup_menu_button),
        contentDescription = stringResource(R.string.shortcuts_popup_menu_button_description),
        onClick = onShowDeepShortcuts,
    )
}

private object ExpandPopupMenuButtonDimens {
    val expandPopupMenuItemHorizontalPadding = 16.dp
    val expandPopupMenuItemImageSize = 24.dp
}

object ExpandButtonStyles {
    val expandButtonTextStyle: TextStyle
        @Composable get() = textStyleFromResource(R.style.ExpandButtonText)
}
