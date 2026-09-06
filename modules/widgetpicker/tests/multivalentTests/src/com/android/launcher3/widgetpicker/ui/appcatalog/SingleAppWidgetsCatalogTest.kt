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

import android.graphics.Bitmap
import android.platform.test.rule.DeniedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.domain.interactor.WidgetsInteractor
import com.android.launcher3.widgetpicker.domain.usecase.FilterWidgetsForHostUseCase
import com.android.launcher3.widgetpicker.domain.usecase.GroupWidgetAppsByProfileUseCase
import com.android.launcher3.widgetpicker.repository.FakeWidgetUsersRepository
import com.android.launcher3.widgetpicker.repository.FakeWidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.ui.NoOpWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.components.NoOpWidgetPickerHostStateProvider
import com.android.launcher3.widgetpicker.ui.components.buildWidgetPickerTestTag
import com.android.launcher3.widgetpicker.ui.rememberViewModel
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@DeniedDevices(denied = [DeviceProduct.ROBOLECTRIC])
@OptIn(ExperimentalCoroutinesApi::class)
class SingleAppWidgetsCatalogTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val mockito: MockitoRule = MockitoJUnit.rule()

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var appCatalogViewModel: SingleAppWidgetsCatalogViewModel

    @Mock private lateinit var eventListenersMock: WidgetPickerEventListeners

    private lateinit var widgetsRepository: FakeWidgetsRepository
    private lateinit var testWidgetApp: WidgetApp

    @Before
    fun setUp() {
        testWidgetApp = PERSONAL_TEST_APPS[0]
        widgetsRepository = FakeWidgetsRepository().apply { seedWidgets(PERSONAL_TEST_APPS) }
        appCatalogViewModel =
            SingleAppWidgetsCatalogViewModel(
                widgetAppId = testWidgetApp.id,
                hostInfo = WidgetHostInfo(),
                widgetsInteractor =
                    WidgetsInteractor(
                        widgetsRepository = widgetsRepository,
                        widgetUsersRepository = FakeWidgetUsersRepository(),
                        filterWidgetsForHostUseCase = FilterWidgetsForHostUseCase(WidgetHostInfo()),
                        getWidgetAppsByProfileUseCase = GroupWidgetAppsByProfileUseCase(),
                        backgroundContext = testDispatcher,
                    ),
            )
    }

    @Test
    fun widgetsAvailable_previewsPending_onlyTitleDisplayed() {
        testScope.runTest {
            composeTestRule.setContent { SingleAppWidgetsCatalogTestContent() }
            // assume no previews -- fake repo immediately returns placeholders without delay
            appCatalogViewModel.clearPreviews()
            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(testWidgetApp.title.toString()).assertExists()
            composeTestRule.onAllNodesWithTag(PREVIEW_TEST_TAG).assertCountEquals(0)
        }
    }

    @Test
    fun widgetsAndPreviewsDisplayed() {
        testScope.runTest {
            widgetsRepository.seedWidgetPreviews(
                testWidgetApp.widgets.associate { // real previews
                    it.id to newWidgetPreview()
                }
            )
            composeTestRule.setContent { SingleAppWidgetsCatalogTestContent() }
            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(testWidgetApp.title.toString()).assertExists()
            composeTestRule
                .onAllNodesWithTag(PREVIEW_TEST_TAG)
                .assertCountEquals(testWidgetApp.widgets.size)
        }
    }

    @Test
    fun closeWithSwipeDown_callbackInvoked() =
        testScope.runTest {
            composeTestRule.setContent { SingleAppWidgetsCatalogTestContent() }
            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onRoot().performTouchInput { swipeDown() }
            runCurrent()
            composeTestRule.waitForIdle()

            verify(eventListenersMock).onClose()
        }

    @Composable
    private fun SingleAppWidgetsCatalogTestContent() {
        val viewModel = rememberViewModel { appCatalogViewModel }

        val hostStateProvider = NoOpWidgetPickerHostStateProvider()

        WidgetPickerTheme {
            SingleAppWidgetsCatalogContent(
                viewModel = viewModel,
                cuiReporter = NoOpWidgetPickerCuiReporter(),
                eventListeners = eventListenersMock,
                hostStateProvider = hostStateProvider,
            )
        }
    }

    companion object {
        private val PREVIEW_TEST_TAG = buildWidgetPickerTestTag("widget_preview")

        private fun newWidgetPreview() =
            WidgetPreview.BitmapWidgetPreview(Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))
    }
}
