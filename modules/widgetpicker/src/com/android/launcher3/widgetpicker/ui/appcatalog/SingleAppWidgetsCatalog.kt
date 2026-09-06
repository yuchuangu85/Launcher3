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

package com.android.launcher3.widgetpicker.ui.appcatalog

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.ui.LocalWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetInteractionSource
import com.android.launcher3.widgetpicker.ui.WidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.components.LocalWidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.WidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.WidgetsGrid
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.SheetSize
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheet
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTag
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTagContainer
import com.android.launcher3.widgetpicker.ui.rememberViewModel
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import javax.inject.Inject

/**
 * A catalog of all widgets hosted by a specific app currently available on the device.
 *
 * Widgets can be dragged or added via add button to the host.
 */
class SingleAppWidgetsCatalog
@Inject
constructor(private val viewModelFactory: SingleAppWidgetsCatalogViewModel.Factory) {
    @Composable
    fun Content(
        widgetAppId: WidgetAppId,
        eventListeners: WidgetPickerEventListeners,
        cuiReporter: WidgetPickerCuiReporter,
        hostStateProvider: WidgetPickerHostStateProvider,
    ) {
        val viewModel: SingleAppWidgetsCatalogViewModel = rememberViewModel {
            viewModelFactory.create(widgetAppId)
        }

        SingleAppWidgetsCatalogContent(
            viewModel = viewModel,
            cuiReporter = cuiReporter,
            eventListeners = eventListeners,
            hostStateProvider = hostStateProvider,
        )
    }
}

@Composable
@VisibleForTesting
fun SingleAppWidgetsCatalogContent(
    viewModel: SingleAppWidgetsCatalogViewModel,
    cuiReporter: WidgetPickerCuiReporter,
    eventListeners: WidgetPickerEventListeners,
    hostStateProvider: WidgetPickerHostStateProvider,
) {
    CompositionLocalProvider(
        LocalWidgetPickerCuiReporter provides cuiReporter,
        LocalWidgetPickerHostStateProvider provides hostStateProvider,
    ) {
        TitledBottomSheet(
            modifier =
                Modifier.widgetPickerTestTagContainer().widgetPickerTestTag("app_widgets_catalog"),
            sheetSize = SheetSize.COMPACT,
            title = viewModel.widgetApp?.title?.toString(),
            description = null,
            closeBehavior = viewModel.closeBehavior,
            enableSwipeUpToDismiss = viewModel.enableSwipeUpToClose,
            onSheetProgress = eventListeners::onSheetProgress,
            onSheetOpen = { viewModel.onUiReady() },
            onDismissSheet = { eventListeners.onClose() },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(WidgetPickerTheme.colors.widgetsContainerBackground)
                        .verticalScroll(rememberScrollState()),
            ) {
                viewModel.widgetApp?.let { widgetApp ->
                    if (viewModel.widgetsPreviewsState.previews.isNotEmpty()) {
                        WidgetsGrid(
                            widgetSizeGroups = widgetApp.widgetSizeGroups,
                            showAllWidgetDetails = true,
                            previews = viewModel.widgetsPreviewsState.previews,
                            modifier = Modifier.fillMaxWidth(),
                            // app icon not necessary as user is already context of the app
                            appIcons = emptyMap(),
                            showDragShadow = viewModel.showDragShadow,
                            widgetInteractionSource = WidgetInteractionSource.APP_SPECIFIC_PICKER,
                            onWidgetInteraction = eventListeners::onWidgetInteraction,
                        )
                    }
                }
            }
        }
    }
}
