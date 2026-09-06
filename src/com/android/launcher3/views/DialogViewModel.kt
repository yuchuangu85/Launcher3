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

import androidx.compose.runtime.Composable

/** Abstract model for a [DialogView] */
abstract class DialogViewModel<T : DialogViewModel<T>>(
    val title: String,
    val content: @Composable DialogScope.(T) -> Unit,
    val neutralButton: String? = null,
    val positiveButton: String? = null,
    val onPositiveButtonClick: ((T) -> Boolean)? = null,
)

/** Model for showing a simple [DialogView]. */
class SimpleDialogViewModel(
    title: String,
    content: @Composable DialogScope.(SimpleDialogViewModel) -> Unit,
    neutralButton: String? = null,
    positiveButton: String? = null,
    onPositiveButtonClick: ((SimpleDialogViewModel) -> Boolean)? = null,
) :
    DialogViewModel<SimpleDialogViewModel>(
        title,
        content,
        neutralButton,
        positiveButton,
        onPositiveButtonClick,
    )
