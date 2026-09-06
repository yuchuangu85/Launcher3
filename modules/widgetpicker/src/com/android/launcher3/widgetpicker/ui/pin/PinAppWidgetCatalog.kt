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

package com.android.launcher3.widgetpicker.ui.pin

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.ui.LocalWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.WidgetInteractionSource
import com.android.launcher3.widgetpicker.ui.WidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.components.WidgetsGrid
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.SheetSize
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheet
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTag
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTagContainer
import com.android.launcher3.widgetpicker.ui.pin.PinAppWidgetCatalogDimens.ButtonsGroupHorizontalPadding
import com.android.launcher3.widgetpicker.ui.pin.PinAppWidgetCatalogDimens.ButtonsSpacing
import com.android.launcher3.widgetpicker.ui.pin.PinAppWidgetCatalogDimens.ContentShape
import com.android.launcher3.widgetpicker.ui.pin.PinAppWidgetCatalogDimens.WEIGHT_FILL_REMAINING_SPACE
import com.android.launcher3.widgetpicker.ui.rememberViewModel
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import javax.inject.Inject

/** The picker UI shown for a widget / shortcut requested to be pinned via `RequestPin{*}` APIs. */
class PinAppWidgetCatalog
@Inject
constructor(private val viewModelFactory: PinAppWidgetCatalogViewModel.Factory) {
    /**
     * @param widgetAppId app for which the widget / shortcut pin was requested
     * @param previewOverridesProvider preview provided by the app in request; if preview isn't
     *   provided, the default preview will be loaded for that widget.
     * @param eventListeners listeners for the host to handle the user interactions such as close,
     *   drag and drop etc.
     * @param cuiReporter a hook for host to report the user interactions to relevant loggers.
     */
    @Composable
    fun Content(
        widgetAppId: WidgetAppId,
        previewOverridesProvider: () -> Map<WidgetId, WidgetPreview>,
        eventListeners: WidgetPickerEventListeners,
        cuiReporter: WidgetPickerCuiReporter,
    ) {
        val viewModel = rememberViewModel {
            viewModelFactory.create(widgetAppId, previewOverridesProvider)
        }

        PinAppWidgetCatalogContent(
            viewModel = viewModel,
            eventListeners = eventListeners,
            cuiReporter = cuiReporter,
        )
    }
}

@Composable
@VisibleForTesting
fun PinAppWidgetCatalogContent(
    viewModel: PinAppWidgetCatalogViewModel,
    cuiReporter: WidgetPickerCuiReporter,
    eventListeners: WidgetPickerEventListeners,
) {
    val testTagModifier =
        Modifier.widgetPickerTestTagContainer().widgetPickerTestTag(PIN_CATALOG_TEST_TAG)

    val sheetTitle = viewModel.widgetApp?.title?.toString()
    val sheetSubtitle = stringResource(R.string.widget_touch_hold_instruction)
    val onSheetOpen = { viewModel.onUiReady() }
    val onSheetClose = { eventListeners.onClose() }

    CompositionLocalProvider(LocalWidgetPickerCuiReporter provides cuiReporter) {
        TitledBottomSheet(
            modifier = testTagModifier,
            sheetSize = SheetSize.X_SMALL,
            title = sheetTitle,
            description = sheetSubtitle,
            closeBehavior = viewModel.closeBehavior,
            enableSwipeUpToDismiss = viewModel.enableSwipeUpToClose,
            onSheetProgress = eventListeners::onSheetProgress,
            onSheetOpen = onSheetOpen,
            onDismissSheet = onSheetClose,
        ) {
            SheetContent(viewModel, eventListeners)
        }
    }
}

@Composable
private fun SheetContent(
    viewModel: PinAppWidgetCatalogViewModel,
    eventListeners: WidgetPickerEventListeners,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WidgetGrid(
            viewModel = viewModel,
            eventListeners = eventListeners,
            modifier = Modifier.weight(WEIGHT_FILL_REMAINING_SPACE).fillMaxWidth(),
        )
        ButtonsGroup(eventListeners, viewModel)
    }
}

@Composable
private fun WidgetGrid(
    viewModel: PinAppWidgetCatalogViewModel,
    eventListeners: WidgetPickerEventListeners,
    modifier: Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(ContentShape)
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

@Composable
private fun ButtonsGroup(
    eventListeners: WidgetPickerEventListeners,
    viewModel: PinAppWidgetCatalogViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(ButtonsGroupHorizontalPadding),
        horizontalArrangement = Arrangement.End, // Aligns buttons to the end
    ) {
        BottomButton(
            text = stringResource(R.string.widget_cancel_pin_button_label),
            onClick = { eventListeners.onClose() },
        )
        viewModel.widgetApp?.let {
            if (it.widgetSizeGroups.isNotEmpty()) {
                val widget = it.widgetSizeGroups.first().widgets.first()
                Spacer(modifier = Modifier.width(ButtonsSpacing))
                BottomButton(
                    text = stringResource(R.string.widget_add_to_homescreen_button_label),
                    onClick = {
                        eventListeners.onWidgetInteraction(
                            WidgetInteractionInfo.WidgetAddInfo(
                                source = WidgetInteractionSource.APP_SPECIFIC_PICKER,
                                widgetInfo = widget.widgetInfo,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors()
                .copy(
                    containerColor = WidgetPickerTheme.colors.addButtonBackground,
                    contentColor = WidgetPickerTheme.colors.addButtonContent,
                ),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = WidgetPickerTheme.typography.addWidgetButtonLabel,
        )
    }
}

private const val PIN_CATALOG_TEST_TAG = "pin_widgets_catalog"

private object PinAppWidgetCatalogDimens {
    val ContentShape = RoundedCornerShape(24.dp)

    val ButtonsSpacing = 8.dp
    val ButtonsGroupHorizontalPadding = 16.dp

    const val WEIGHT_FILL_REMAINING_SPACE = 1f
}
