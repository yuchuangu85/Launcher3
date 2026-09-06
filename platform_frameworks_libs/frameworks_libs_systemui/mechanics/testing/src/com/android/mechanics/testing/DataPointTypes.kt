/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.mechanics.spring.SpringParameters
import com.android.mechanics.spring.SpringState
import com.android.mechanics.testing.DataPointTypes.springParameters
import com.android.mechanics.testing.DataPointTypes.springState
import org.json.JSONObject
import platform.test.motion.golden.DataPointType
import platform.test.motion.golden.FloatTolerances
import platform.test.motion.golden.UnknownTypeException

fun SpringParameters?.asDataPoint() = springParameters.makeDataPoint(this)

val SpringParameters.Companion.dataPointType
    get() = DataPointTypes.springParameters

fun SpringState?.asDataPoint() = springState.makeDataPoint(this)

val SpringState.Companion.dataPointType
    get() = DataPointTypes.springState

object DataPointTypes {
    val springParameters: DataPointType<SpringParameters> =
        DataPointType.createWithTolerance(
            "springParameters",
            jsonToValue = {
                with(it as? JSONObject ?: throw UnknownTypeException()) {
                    SpringParameters(
                        getDouble("stiffness").toFloat(),
                        getDouble("dampingRatio").toFloat(),
                    )
                }
            },
            valueToJson = {
                JSONObject().apply {
                    put("stiffness", it.stiffness)
                    put("dampingRatio", it.dampingRatio)
                }
            },
            // stiffness is required to be > 0, so using Float.MIN_VALUE for the tolerance as
            // workaround.
            tolerance = SpringParameters(Float.MIN_VALUE, 0f),
            toleranceAwareEquality = { a, b, t ->
                with(FloatTolerances) {
                    isWithinTolerance(a.stiffness, b.stiffness, t.stiffness) &&
                        isWithinTolerance(a.dampingRatio, b.dampingRatio, t.dampingRatio)
                }
            },
        )

    val springState: DataPointType<SpringState> =
        DataPointType.createWithTolerance(
            "springState",
            jsonToValue = {
                with(it as? JSONObject ?: throw UnknownTypeException()) {
                    SpringState(
                        getDouble("displacement").toFloat(),
                        getDouble("velocity").toFloat(),
                    )
                }
            },
            valueToJson = {
                JSONObject().apply {
                    put("displacement", it.displacement)
                    put("velocity", it.velocity)
                }
            },
            tolerance = SpringState(0f, 0f),
            toleranceAwareEquality = { a, b, t ->
                with(FloatTolerances) {
                    isWithinTolerance(a.displacement, b.displacement, t.displacement) &&
                        isWithinTolerance(a.velocity, b.velocity, t.velocity)
                }
            },
        )
}
