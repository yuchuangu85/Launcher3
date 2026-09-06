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

package com.android.launcher3.util.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Mark this node as a container that contains one or more test tag descendants.
 *
 * Should be used on the top level composable.
 */
@Stable
fun Modifier.testTagContainer(): Modifier {
    return this.then(Modifier.semantics { testTagsAsResourceId = true })
}

/**
 * A test tag that appears as a resource id when parent in the hierarchy is marked with
 * [testTagContainer].
 *
 * For example, in TAPL tests, if you wish to have your composable to appear with same res ID as
 * your old view, pass the resID to this modifier.
 */
@Composable
fun Modifier.testTag(resId: String): Modifier {
    val context = LocalContext.current
    return this.semantics { testTag = "${context.packageName}:id/$resId" }
}
