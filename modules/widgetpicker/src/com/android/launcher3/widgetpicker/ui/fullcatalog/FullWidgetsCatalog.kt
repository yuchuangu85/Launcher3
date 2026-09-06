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

package com.android.launcher3.widgetpicker.ui.fullcatalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.WidgetPickerUi
import com.android.launcher3.widgetpicker.shared.model.CloseBehavior
import com.android.launcher3.widgetpicker.shared.model.SheetStyle
import com.android.launcher3.widgetpicker.ui.LocalWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetPickerCui
import com.android.launcher3.widgetpicker.ui.WidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.components.LocalWidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.WidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.accessibility.LocalAccessibilityState
import com.android.launcher3.widgetpicker.ui.components.accessibility.produceAccessibilityState
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.SheetSize
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheet
import com.android.launcher3.widgetpicker.ui.components.floatingsheet.TitledFloatingSheet
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTag
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTagContainer
import com.android.launcher3.widgetpicker.ui.fullcatalog.FullWidgetsCatalogDimens.compactHeightBreakpoint
import com.android.launcher3.widgetpicker.ui.fullcatalog.FullWidgetsCatalogDimens.compactWidthBreakpoint
import com.android.launcher3.widgetpicker.ui.fullcatalog.FullWidgetsCatalogViewModel.Screen
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreen
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.search.SearchScreen
import com.android.launcher3.widgetpicker.ui.rememberViewModel
import javax.inject.Inject

/**
 * A catalog of all widgets available on device.
 *
 * When opened, first shows a landing page that comprises of the featured widgets and the list of
 * apps hosting widgets. User can enter search mode by tapping the search bar and see matching
 * results.
 */
class FullWidgetsCatalog
@Inject
constructor(
    private val viewModelFactory: FullWidgetsCatalogViewModel.Factory,
) {
    @Composable
    fun Content(
        eventListeners: WidgetPickerEventListeners,
        cuiReporter: WidgetPickerCuiReporter,
        hostStateProvider: WidgetPickerHostStateProvider,
    ) {
        val context = LocalContext.current
        val viewModel: FullWidgetsCatalogViewModel = rememberViewModel { viewModelFactory.create() }

        val density = LocalDensity.current
        val windowSize = LocalWindowInfo.current.containerSize
        val isCompactHeight =
            with(density) { windowSize.height < compactHeightBreakpoint.roundToPx() }
        val isCompactWidth = with(density) { windowSize.width < compactWidthBreakpoint.roundToPx() }

        val accessibilityState by produceAccessibilityState(context)

        CompositionLocalProvider(
            LocalWidgetPickerCuiReporter provides cuiReporter,
            LocalAccessibilityState provides accessibilityState,
            LocalWidgetPickerHostStateProvider provides hostStateProvider,
        ) {
            FullWidgetsCatalogContent(
                viewModel = viewModel,
                isCompactHeight = isCompactHeight,
                isCompactWidth = isCompactWidth,
                eventListeners = eventListeners,
            )
        }
    }

    @Composable
    private fun FullWidgetsCatalogContent(
        viewModel: FullWidgetsCatalogViewModel,
        isCompactHeight: Boolean,
        isCompactWidth: Boolean,
        eventListeners: WidgetPickerEventListeners,
    ) {
        val cuiReporter = LocalWidgetPickerCuiReporter.current
        val localView = LocalView.current
        val testTagModifier =
            Modifier.widgetPickerTestTagContainer().widgetPickerTestTag(WIDGET_CATALOG_TEST_TAG)

        val onDismissSheet = {
            // Report end of cui in case user tried to close picker while it was opening.
            // If there was no begin, this won't do anything.
            cuiReporter.report(WidgetPickerCui.OPEN_ANIMATION_END, localView)
            eventListeners.onClose()
        }
        val onSheetOpen = {
            cuiReporter.report(WidgetPickerCui.OPEN_ANIMATION_END, localView)
            viewModel.landingScreenViewModel.onUiReady()
        }

        LaunchedEffect(Unit) { cuiReporter.report(WidgetPickerCui.OPEN_ANIMATION_BEGIN, localView) }

        if (viewModel.sheetStyle == SheetStyle.FLOATING_SHEET) {
            TitledFloatingSheet(
                modifier = testTagModifier,
                title = viewModel.title.takeIf { !isCompactHeight },
                description = viewModel.description,
                onSheetProgress = eventListeners::onSheetProgress,
                onDismissSheet = onDismissSheet,
                onSheetOpen = onSheetOpen,
            ) {
                ActiveScreen(viewModel, isCompactWidth, eventListeners)
            }
        } else {
            TitledBottomSheet(
                modifier = testTagModifier,
                sheetSize =
                    if (viewModel.closeBehavior == CloseBehavior.CLOSE_BUTTON) {
                        SheetSize.WINDOW
                    } else {
                        SheetSize.FULL
                    },
                title = viewModel.title.takeIf { !isCompactHeight },
                description = viewModel.description,
                closeBehavior = viewModel.closeBehavior,
                enableSwipeUpToDismiss = viewModel.enableSwipeUpToClose,
                onDismissSheet = onDismissSheet,
                onSheetOpen = onSheetOpen,
                onSheetProgress = eventListeners::onSheetProgress,
            ) {
                ActiveScreen(viewModel, isCompactWidth, eventListeners)
            }
        }
    }

    @Composable
    private fun ActiveScreen(
        viewModel: FullWidgetsCatalogViewModel,
        isCompactWidth: Boolean,
        eventListeners: WidgetPickerEventListeners,
    ) {
        when (viewModel.activeScreen) {
            Screen.LANDING -> {
                LandingScreen(
                    isCompact = isCompactWidth,
                    onEnterSearchMode = { viewModel.onActiveScreenChange(Screen.SEARCH) },
                    onWidgetInteraction = eventListeners::onWidgetInteraction,
                    showDragShadow = viewModel.showDragShadow,
                    viewModel = viewModel.landingScreenViewModel,
                )
            }

            Screen.SEARCH -> {
                SearchScreen(
                    isCompact = isCompactWidth,
                    onExitSearchMode = { viewModel.onActiveScreenChange(Screen.LANDING) },
                    onWidgetInteraction = eventListeners::onWidgetInteraction,
                    showDragShadow = viewModel.showDragShadow,
                    viewModel = viewModel.searchScreenViewModel,
                )
            }
        }
    }
}

private const val WIDGET_CATALOG_TEST_TAG = "widgets_catalog"

private object FullWidgetsCatalogDimens {
    /**
     * Height below which screen is considered compact and the vertically compact view of catalog
     * can be displayed. e.g. hide header etc.
     *
     * Same breakpoint as material3's `WindowHeightSizeClass.Compact`
     */
    val compactHeightBreakpoint = 480.dp

    /**
     * Width below which screen is considered compact and the horizontally compact view of catalog
     * can be displayed e.g. single pane.
     *
     * Same breakpoint as material3's `WindowWidthSizeClass.Compact`
     */
    val compactWidthBreakpoint = 600.dp
}
