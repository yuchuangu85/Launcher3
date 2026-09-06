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

package com.android.quickstep

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.DeviceProfile
import com.android.launcher3.deviceprofile.DeviceConfiguration
import com.android.launcher3.deviceprofile.DeviceProperties
import com.android.launcher3.deviceprofile.TaskbarConfiguration
import com.android.quickstep.recents.data.RecentsDeviceProfile
import com.android.quickstep.recents.data.RecentsDeviceProfileRepositoryImpl
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RecentsDeviceProfileRepositoryImplTest {
    private val desktopState = FakeDesktopState()

    @Before
    fun setUp() {
        desktopState.canEnterDesktopMode = false
    }

    @Test
    fun deviceProfileMappedCorrectlyForPhone() {
        val deviceProfileRepo =
            RecentsDeviceProfileRepositoryImpl(
                createDeviceProfileGetter(isTablet = false),
                desktopState,
            )

        assertThat(deviceProfileRepo.getRecentsDeviceProfile())
            .isEqualTo(RecentsDeviceProfile(isLargeScreen = false, canEnterDesktopMode = false))
    }

    @Test
    fun deviceProfileMappedCorrectlyForTablet() {
        desktopState.canEnterDesktopMode = true
        val deviceProfileRepo =
            RecentsDeviceProfileRepositoryImpl(
                createDeviceProfileGetter(isTablet = true),
                desktopState,
            )

        assertThat(deviceProfileRepo.getRecentsDeviceProfile())
            .isEqualTo(RecentsDeviceProfile(isLargeScreen = true, canEnterDesktopMode = true))
    }

    private fun createDeviceProfileGetter(isTablet: Boolean = false): DeviceProfile.Getter {
        val deviceProperties =
            DeviceProperties(
                windowX = 1080,
                windowY = 1920,
                rotationHint = -1,
                widthPx = 1080,
                heightPx = 1920,
                availableWidthPx = 1080,
                availableHeightPx = 1920,
                aspectRatio = 1f,
                isLargeScreen = isTablet,
                isPhone = false,
                isTwoPanels = false,
                isLandscape = false,
                deviceConfiguration =
                    DeviceConfiguration(
                        isExternalDisplay = false,
                        transposeLayoutWithOrientation = false,
                        isMultiDisplay = false,
                        isGestureMode = false,
                        isWorkspaceItemsLabelHidden = false,
                    ),
                insets = Rect(0, 0, 0, 0),
                taskbarConfiguration = TaskbarConfiguration(isTaskbarPresent = false),
            )

        val deviceProfile = mock<DeviceProfile>()
        whenever(deviceProfile.deviceProperties).thenReturn(deviceProperties)
        return DeviceProfile.Getter { deviceProfile }
    }
}
