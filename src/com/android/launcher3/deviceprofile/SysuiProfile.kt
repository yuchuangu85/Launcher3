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
package com.android.launcher3.deviceprofile

import android.content.res.Resources
import com.android.launcher3.R
import com.android.launcher3.Utilities

data class SysuiProfile(
    // Space required for the bubble bar between the hotseat and the edge of the screen. If there's
    // not enough space, the hotseat will adjust itself for the bubble bar.
    @JvmField val mBubbleBarSpaceThresholdPx: Int,

    // Split staging
    @JvmField val splitPlaceholderInset: Int,
    val isLeftRightSplit: Boolean,
) {

    companion object Factory {
        fun createSysuiProfile(res: Resources, deviceProperties: DeviceProperties): SysuiProfile {
            // We need to use the full window bounds for split determination because on near-square
            // devices, the available bounds (bounds minus insets) may actually be in landscape
            // while
            // actually portrait
            val leftRightSplitPortraitResId =
                Resources.getSystem()
                    .getIdentifier("config_leftRightSplitInPortrait", "bool", "android")
            return SysuiProfile(
                mBubbleBarSpaceThresholdPx =
                    res.getDimensionPixelSize(R.dimen.bubblebar_hotseat_adjustment_threshold),
                splitPlaceholderInset = res.getDimensionPixelSize(R.dimen.split_placeholder_inset),
                isLeftRightSplit =
                    Utilities.calculateIsLeftRightSplit(
                        /* allowLeftRightSplitInPortrait = */ leftRightSplitPortraitResId > 0 &&
                            res.getBoolean(leftRightSplitPortraitResId),
                        /* deviceProperties = */ deviceProperties,
                        /* isExternalDisplay = */ deviceProperties.deviceConfiguration
                            .isExternalDisplay,
                    ),
            )
        }
    }
}
