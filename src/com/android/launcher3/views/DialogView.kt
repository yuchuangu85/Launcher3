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

package com.android.launcher3.views

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import com.android.launcher3.R

// TODO(b/493665827): Add test coverage.
/** Composable which renders the view for a [Dialog<T>]. */
@Composable
fun <T> DialogScope.DialogView(viewModel: T) where T : DialogViewModel<T> {
    if (viewModel.title.isEmpty()) {
        throw IllegalStateException("Dialog must have a title.")
    }

    val hasNeutralButton = !viewModel.neutralButton.isNullOrEmpty()
    val hasPositiveButton = !viewModel.positiveButton.isNullOrEmpty()

    if (!hasNeutralButton && !hasPositiveButton) {
        throw IllegalStateException("Dialog must have a neutral or positive button.")
    }

    DialogTheme {
        Surface(
            modifier = Modifier.testTag(DIALOG_TAG).width(dimensionResource(R.dimen.dialog_width)),
            color = DialogTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(dimensionResource(R.dimen.dialog_padding))) {
                // Title.
                Text(
                    modifier = Modifier.testTag(TITLE_TAG),
                    text = viewModel.title,
                    style = DialogTheme.typography.headlineSmall,
                )

                // Content.
                Box(
                    modifier =
                        Modifier.testTag(CONTENT_TAG)
                            .padding(
                                top = dimensionResource(R.dimen.dialog_content_padding_top),
                                bottom = dimensionResource(R.dimen.dialog_content_padding_bottom),
                            )
                ) {
                    viewModel.content.invoke(this@DialogView, viewModel)
                }

                // Buttons.
                Row(modifier = Modifier.align(Alignment.End)) {

                    // Neutral button.
                    if (hasNeutralButton) {
                        OutlinedButton(
                            modifier = Modifier.testTag(NEUTRAL_BUTTON_TAG),
                            onClick = { dismiss(animate = true) },
                        ) {
                            Text(text = viewModel.neutralButton!!)
                        }
                    }

                    // Spacing.
                    if (hasNeutralButton && hasPositiveButton) {
                        Box(
                            modifier =
                                Modifier.padding(
                                    end = dimensionResource(R.dimen.dialog_button_spacing)
                                )
                        )
                    }

                    // Positive button.
                    if (hasPositiveButton) {
                        Button(
                            modifier = Modifier.testTag(POSITIVE_BUTTON_TAG),
                            onClick = {
                                if (viewModel.onPositiveButtonClick?.invoke(viewModel) == true) {
                                    dismiss(animate = true)
                                }
                            },
                        ) {
                            Text(text = viewModel.positiveButton!!)
                        }
                    }
                }
            }
        }
    }
}

// Used to locate nodes in tests.
@VisibleForTesting const val CONTENT_TAG = "content"
@VisibleForTesting const val DIALOG_TAG = "dialog"
@VisibleForTesting const val NEUTRAL_BUTTON_TAG = "neutralButton"
@VisibleForTesting const val POSITIVE_BUTTON_TAG = "positiveButton"
@VisibleForTesting const val TITLE_TAG = "title"
