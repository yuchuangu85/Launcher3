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

import platform.test.motion.golden.Feature
import platform.test.motion.golden.TimeSeries
import platform.test.motion.golden.ValueDataPoint

val TimeSeries.input: List<Float>
    get() = dataPoints("input")

val TimeSeries.output: List<Float>
    get() = dataPoints("output")

val TimeSeries.outputTarget: List<Float>
    get() = dataPoints("outputTarget")

val TimeSeries.isStable: List<Boolean>
    get() = dataPoints("isStable")

/**
 * Returns data points for the given [featureName].
 *
 * Throws a [ClassCastException] if any data point is not a [ValueDataPoint] of type [T].
 */
inline fun <reified T : Any> TimeSeries.dataPoints(featureName: String): List<T> {
    return (features[featureName] as Feature<*>).dataPoints.map {
        (it as ValueDataPoint).value as T
    }
}

/**
 * Returns data points for the given [featureName].
 *
 * Returns `null` for all data points that are not a [ValueDataPoint] of type [T].
 */
inline fun <reified T : Any> TimeSeries.nullableDataPoints(featureName: String): List<T?> {
    return (features[featureName] as Feature<*>).dataPoints.map {
        (it as? ValueDataPoint)?.value as T?
    }
}
