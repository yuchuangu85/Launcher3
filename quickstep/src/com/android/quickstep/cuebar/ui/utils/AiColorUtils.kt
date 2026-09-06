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

package com.android.quickstep.cuebar.ui.utils

import androidx.core.graphics.ColorUtils

object AiColorUtils {
    /**
     * Sets the chroma of a color to the maximum possible - unless it's a very desaturated color.
     */
    fun boostChroma(color: Int): Int {
        val outColor = FloatArray(3)
        ColorUtils.colorToM3HCT(color, outColor)
        val chroma = outColor[1]
        if (chroma <= 3) {
            return color
        }
        return ColorUtils.M3HCTToColor(outColor[0], 70f, outColor[2])
    }
}
