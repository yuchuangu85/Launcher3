/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.homescreenfiles

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.launcher3.R
import com.android.launcher3.views.DialogScope

// TODO(b/493665827): Add test coverage.
/** Composable which renders the content view for the home screen files rename dialog. */
@Composable
fun DialogScope.HomeScreenFilesRenameDialogView(viewModel: HomeScreenFilesRenameDialogViewModel) {
    val textFieldFocusRequester = remember { FocusRequester() }
    val textFieldValue by viewModel.name.collectAsStateWithLifecycle()

    // Apply focus selection early to prevent flicker when the text field receives initial focus.
    LaunchedEffect(Unit) { viewModel.name.value = viewModel.name.value.focus() }

    OutlinedTextField(
        modifier =
            Modifier.testTag(TEXT_FIELD_TAG)
                .fillMaxWidth()
                .focusRequester(textFieldFocusRequester)
                .onFocusChanged { viewModel.name.value = viewModel.name.value.apply(it) },
        value = textFieldValue,
        onValueChange = { viewModel.name.value = it },
        keyboardActions =
            KeyboardActions(
                onDone = {
                    if (viewModel.onPositiveButtonClick?.invoke(viewModel) == true) {
                        dismiss(animate = true)
                    }
                }
            ),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        singleLine = true,
        trailingIcon = {
            IconButton(
                modifier = Modifier.testTag(CLEAR_BUTTON_TAG),
                onClick = {
                    viewModel.name.value = TextFieldValue()
                    textFieldFocusRequester.requestFocus()
                },
            ) {
                Icon(
                    painterResource(R.drawable.ic_home_screen_files_rename_dialog_clear),
                    stringResource(R.string.home_screen_files_rename_dialog_clear_button),
                )
            }
        },
    )

    // The text field should receive initial focus.
    LaunchedEffect(Unit) { textFieldFocusRequester.requestFocus() }
}

// Used to locate nodes in tests.
@VisibleForTesting const val CLEAR_BUTTON_TAG = "clearButton"
@VisibleForTesting const val TEXT_FIELD_TAG = "textField"

/** Text selection should be applied/cleared on focus/blur. */
private fun TextFieldValue.apply(state: FocusState): TextFieldValue =
    if (state.isFocused) focus() else blur()

/** Text selection should be cleared on blur. */
private fun TextFieldValue.blur(): TextFieldValue = copy(selection = TextRange.Zero)

/**
 * Text selection should be applied on focus. If a file extension is present, do not include it in
 * the selection so that the user can more quickly rename a file without accidentally changing its
 * extension.
 */
private fun TextFieldValue.focus(): TextFieldValue {
    val end = text.lastIndexOf(".")
    val selection = TextRange(0, if (end != -1) end else text.length)
    return copy(selection = selection)
}
