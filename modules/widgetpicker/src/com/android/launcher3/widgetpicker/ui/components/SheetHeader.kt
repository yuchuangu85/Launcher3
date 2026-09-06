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

package com.android.launcher3.widgetpicker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * A composable that displays the header for a sheet (either bottom sheet or floating sheet).
 *
 * @param title A top level title of the sheet.
 * @param description An optional short (1-2 line - max 80 char) description that can be shown below
 *   the title. At max font+display size it might overflow to 3 lines.
 * @param shouldShowCloseButton Whether to show a close button in the header.
 * @param onCloseButtonClick The action to perform when the close button (if available) is clicked.
 */
@Composable
fun SheetHeader(
    title: String,
    description: String?,
    shouldShowCloseButton: Boolean,
    onCloseButtonClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = SheetDimens.headerBottomMargin).fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
                text = title,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = WidgetPickerTheme.typography.sheetTitle,
                color = WidgetPickerTheme.colors.sheetTitle,
            )
            if (shouldShowCloseButton) {
                IconButton(
                    onClick = onCloseButtonClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription =
                            stringResource(R.string.widget_picker_collapse_sheet_label),
                        tint = WidgetPickerTheme.colors.sheetTitle,
                    )
                }
            }
        }
        description?.let {
            Text(
                text = it,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = WidgetPickerTheme.typography.sheetDescription,
                color = WidgetPickerTheme.colors.sheetDescription,
            )
        }
    }
}

private object SheetDimens {
    val headerBottomMargin = 16.dp
}
