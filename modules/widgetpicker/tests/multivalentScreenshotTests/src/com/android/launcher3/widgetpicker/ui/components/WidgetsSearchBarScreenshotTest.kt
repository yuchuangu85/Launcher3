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

import android.platform.test.rule.DisableAnimationsRule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.goldenpathmanager.WidgetPickerGoldenPathManager
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.getEmulatedDevicePathConfig
import platform.test.screenshot.utils.compose.ComposeScreenshotTestRule

@RunWith(ParameterizedAndroidJunit4::class)
class WidgetsSearchBarScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0) val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ComposeScreenshotTestRule(
            emulationSpec,
            WidgetPickerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun focusRing() {
        screenshotRule.screenshotTest("searchBar_focusRing") {
            val inputModeManager =
                object : InputModeManager {
                    override val inputMode: InputMode = InputMode.Keyboard

                    override fun requestInputMode(inputMode: InputMode) = true
                }
            CompositionLocalProvider(LocalInputModeManager provides inputModeManager) {
                WidgetPickerTheme {
                    Box(
                        modifier =
                            Modifier.wrapContentSize()
                                .background(MaterialTheme.colorScheme.surfaceBright)
                                .padding(16.dp)
                    ) {
                        WidgetsSearchBar(
                            text = "Search text",
                            isSearching = true,
                            onSearch = {},
                            onToggleSearchMode = {},
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false,
            )
        }
    }
}
