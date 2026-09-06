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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Interface used by widget picker composable UI to observe the widget picker host (e.g.
 * [WidgetPickerActivity]]) state.
 */
interface WidgetPickerHostStateProvider {
    /** Registers a listener to be called when the hosting activity's top resumed state changes */
    fun observeIsTopResumed(listener: (Boolean) -> Unit)

    /** Unregisters a listener previously registered using [observeIsTopResumed] */
    fun stopObservingIsTopResumed(listener: (Boolean) -> Unit)
}

class NoOpWidgetPickerHostStateProvider : WidgetPickerHostStateProvider {
    override fun observeIsTopResumed(listener: (Boolean) -> Unit) {}

    override fun stopObservingIsTopResumed(listener: (Boolean) -> Unit) {}
}

/**
 * Wrapper around [DisposableEffect] that registers a callback to react to available host state
 * changes.
 */
@Composable
fun WidgetPickerHostStateEffect(
    hostStateProvider: WidgetPickerHostStateProvider,
    onTopResumedChanged: (Boolean) -> Unit = {},
) {
    DisposableEffect(hostStateProvider) {
        var observer: (Boolean) -> Unit = { onTopResumedChanged(it) }

        hostStateProvider.observeIsTopResumed(observer)

        onDispose { hostStateProvider.stopObservingIsTopResumed(observer) }
    }
}

/**
 * A composition local available for widget picker's UI code to observe, and react to the widget
 * picker host state changes.
 */
val LocalWidgetPickerHostStateProvider =
    staticCompositionLocalOf<WidgetPickerHostStateProvider> { NoOpWidgetPickerHostStateProvider() }
