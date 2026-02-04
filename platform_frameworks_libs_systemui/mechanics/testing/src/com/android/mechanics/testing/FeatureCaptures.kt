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

package com.android.mechanics.testing

import com.android.mechanics.debug.DebugInspector
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spring.SpringParameters
import com.android.mechanics.spring.SpringState
import platform.test.motion.golden.DataPointType
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.golden.asDataPoint

/** Feature captures on MotionValue's [DebugInspector] */
object FeatureCaptures {
    /** Input value of the current frame. */
    val input = FeatureCapture<DebugInspector, Float>("input") { it.frame.input.asDataPoint() }

    /** Gesture direction of the current frame. */
    val gestureDirection =
        FeatureCapture<DebugInspector, String>("gestureDirection") {
            it.frame.gestureDirection.name.asDataPoint()
        }

    /** Animated output value of the current frame. */
    val output = FeatureCapture<DebugInspector, Float>("output") { it.frame.output.asDataPoint() }

    /** Output target value of the current frame. */
    val outputTarget =
        FeatureCapture<DebugInspector, Float>("outputTarget") {
            it.frame.outputTarget.asDataPoint()
        }

    /** Spring parameters currently in use. */
    val springParameters =
        FeatureCapture<DebugInspector, SpringParameters>("springParameters") {
            it.frame.springParameters.asDataPoint()
        }

    /** Spring state currently in use. */
    val springState =
        FeatureCapture<DebugInspector, SpringState>("springState") {
            it.frame.springState.asDataPoint()
        }

    /** Whether the spring is currently stable. */
    val isStable =
        FeatureCapture<DebugInspector, Boolean>("isStable") { it.frame.isStable.asDataPoint() }

    /** A semantic value to capture in the golden. */
    fun <T> semantics(
        key: SemanticKey<T>,
        dataPointType: DataPointType<T & Any>,
        name: String = key.debugLabel,
    ): FeatureCapture<DebugInspector, T & Any> {
        return FeatureCapture(name) { dataPointType.makeDataPoint(it.frame.semantic(key)) }
    }
}
