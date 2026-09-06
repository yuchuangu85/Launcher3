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

import android.content.res.Resources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.mechanics.effects.MagneticDetach
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.spec.builder.spatialMotionSpec
import com.android.mechanics.view.standardViewMotionBuilderContext

/** Util class for creating MotionSpecs for gesture nav animations. */
object GestureMotionSpecUtils {

    /**
     * Create a MotionSpec that is snaps magnetically before the window attach threshold.
     *
     * NOTE: This exists for Java/View interoperability only
     */
    @JvmStatic
    fun generateMotionSpec(resources: Resources): MotionSpec {
        return with(standardViewMotionBuilderContext(resources.displayMetrics.density)) {
            generateMotionSpec(
                Dp(resources.getDimension(R.dimen.gesture_nav_window_attach_threshold)),
                Dp(resources.getDimension(R.dimen.motion_pause_detector_min_displacement_from_app)),
            )
        }
    }

    /** Create a MotionSpec that is snaps magnetically before the window attach threshold. */
    private fun MotionBuilderContext.generateMotionSpec(
        attachThreshold: Dp,
        detachThreshold: Dp,
    ): MotionSpec {
        return spatialMotionSpec {
            val config =
                MagneticDetach(attachPosition = attachThreshold, detachPosition = detachThreshold)

            after(0.dp.toPx(), config)
        }
    }
}
