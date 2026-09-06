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

import android.widget.Toast
import androidx.compose.ui.text.input.TextFieldValue
import com.android.launcher3.R
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.DialogViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/** Model for showing a home screen files rename dialog. */
class HomeScreenFilesRenameDialogViewModel(
    activityContext: ActivityContext,
    file: HomeScreenFile,
    provider: HomeScreenFilesProvider,
) :
    DialogViewModel<HomeScreenFilesRenameDialogViewModel>(
        title =
            activityContext
                .asContext()
                .getString(
                    if (file.isDirectory) R.string.home_screen_files_rename_dialog_folder_title
                    else R.string.home_screen_files_rename_dialog_file_title
                ),
        content = { viewModel -> HomeScreenFilesRenameDialogView(viewModel) },
        neutralButton =
            activityContext
                .asContext()
                .getString(R.string.home_screen_files_rename_dialog_neutral_button),
        positiveButton =
            activityContext
                .asContext()
                .getString(R.string.home_screen_files_rename_dialog_positive_button),
        onPositiveButtonClick = onPositiveButtonClick@{ viewModel ->
                val name = viewModel.name.value.text.trim()

                // TODO(b/489772913): Implement additional user input validation. Note that
                //  [name] is also sanitized by the media provider downstream so this is
                //  just a UX optimization.
                if (name.isEmpty()) {
                    return@onPositiveButtonClick false
                }

                provider.rename(file.uri, name).whenComplete { result, throwable ->
                    if (throwable != null || !result) {
                        activityContext.uiExecutor.post {
                            Toast.makeText(
                                    activityContext.asContext(),
                                    R.string.something_went_wrong,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                    }
                }

                return@onPositiveButtonClick true
            },
    ) {

    // The name which a [file] should be renamed to. Updated via user interaction with the dialog.
    val name = MutableStateFlow(TextFieldValue(text = file.displayName))
}
