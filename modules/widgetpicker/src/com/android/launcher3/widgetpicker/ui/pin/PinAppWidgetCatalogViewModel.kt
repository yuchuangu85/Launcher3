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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.launcher3.widgetpicker.WidgetPickerHostInfo
import com.android.launcher3.widgetpicker.WidgetPickerSingleton
import com.android.launcher3.widgetpicker.domain.interactor.WidgetsInteractor
import com.android.launcher3.widgetpicker.shared.model.CloseBehavior
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.ui.ViewModel
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.PreviewsState
import com.android.launcher3.widgetpicker.ui.model.DisplayableWidgetApp
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * View model for the widget picker view that shows a widget / shortcut requested by an app to pin.
 *
 * @param widgetAppId app for which the widget / shortcut pin was requested
 * @param previewOverridesProvider preview provided by the app in request; if preview isn't
 *   provided, the default preview will be loaded for that widget.
 * @param widgetsInteractor interactor to get widget info from data sources / repository
 * @param hostInfo info provided by the host to control how and what is shown picker view
 */
class PinAppWidgetCatalogViewModel
@AssistedInject
constructor(
    @param:Assisted private val widgetAppId: WidgetAppId,
    @param:Assisted private val previewOverridesProvider: () -> Map<WidgetId, WidgetPreview>,
    private val widgetsInteractor: WidgetsInteractor,
    @param:WidgetPickerHostInfo private val hostInfo: WidgetHostInfo,
) : ViewModel {
    private var isSheetOpen by mutableStateOf(false)

    val showDragShadow: Boolean = hostInfo.showDragShadow
    val enableSwipeUpToClose: Boolean = hostInfo.enableSwipeUpToDismiss
    val closeBehavior: CloseBehavior = hostInfo.closeBehavior

    var widgetApp by mutableStateOf<DisplayableWidgetApp?>(null)
    var widgetsPreviewsState by mutableStateOf(PreviewsState())
        private set

    override suspend fun onInit() {
        coroutineScope { launch { initWidgetApp() } }
    }

    private suspend fun initWidgetApp() {
        widgetsInteractor.getWidgetApp(widgetAppId).collect { app ->
            app?.let {
                val displayableWidgetApp = DisplayableWidgetApp.fromWidgetApp(it)
                widgetApp = displayableWidgetApp

                val previewsMap = previewOverridesProvider.invoke()
                val previewsState =
                    if (previewsMap.isEmpty()) {
                        PreviewsState(
                            it.widgets.associate { widget ->
                                widget.id to widgetsInteractor.getWidgetPreview(widget.id)
                            }
                        )
                    } else {
                        PreviewsState(previewsMap)
                    }

                widgetsPreviewsState = previewsState
            }
        }
    }

    /**
     * Callback once UI has finished animations / movements and is ready to show contents. This
     * enables us to perform heavy work such as preview rendering off the time animation runs.
     */
    fun onUiReady() {
        isSheetOpen = true
    }

    @AssistedFactory
    @WidgetPickerSingleton
    interface Factory {
        fun create(
            widgetAppId: WidgetAppId,
            previewOverridesProvider: () -> Map<WidgetId, WidgetPreview>,
        ): PinAppWidgetCatalogViewModel
    }
}
