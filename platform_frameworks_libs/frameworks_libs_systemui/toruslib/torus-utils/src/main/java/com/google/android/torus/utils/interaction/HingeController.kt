/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.torus.utils.interaction

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import com.google.android.torus.utils.animation.EasingUtils
import kotlin.math.abs

/**
 * Class that listens to changes to the device hinge angle.
 * This class can only be used on Android R or above
 */
@RequiresApi(Build.VERSION_CODES.R)
class HingeController(context: Context) {

    companion object {

        const val DEFAULT_EASING = 0.36f
        const val ALMOST_SETTLED_THRESHOLD = 1f
        const val SETTLED_THRESHOLD = .01f
    }

    private var hingeAngleSensorValue: Float = 0f
    var hingeAngle: Float = 0f
        private set

    /**
     * Adjusts how much the end hinge angle is eased. This value can be from range [0, 1].
     * - When 0, the eased value won't change.
     * - when 1, there isn't any easing.
     */
    @FloatRange(from = 0.0, to = 1.0)
    var hingeEasingSpeed: Float = DEFAULT_EASING

    /**
     * Defines if hinge angle is considered to be settled.
     */
    var isSettled: Boolean = false
        private set

    /**
     * Defines whether the hinge animation is almost settled.
     */
    var isAlmostSettled: Boolean = false
        private set

    private var sensorManager: SensorManager =
        context.getSystemService(SENSOR_SERVICE) as SensorManager
    private var hingeAngleSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private var sensorListener: SensorEventListener? = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            hingeAngleSensorValue = event.values[0]
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

    /**
     * Starts listening for hinge events.
     */
    fun start() {
        hingeAngleSensor?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    /**
     * Stops listening for hinge events.
     */
    fun stop() {
        hingeAngleSensor?.let {
            sensorManager.unregisterListener(sensorListener, it)
        }
    }

    /**
     * Updates the output hinge angle using easing settings.
     *
     * @param deltaSeconds the time in seconds elapsed since the last time
     * [HingeController.update] was called.
     */
    fun update(deltaSeconds: Float) {
        /*
         * Ease if needed (specially to reduce movement variation which will allow us to use a
         * smaller fps).
         */
        hingeAngle = EasingUtils.calculateEasing(
            hingeAngle,
            hingeAngleSensorValue,
            hingeEasingSpeed,
            deltaSeconds
        )

        isSettled = isCurrentlySettled()
        isAlmostSettled = isNearlySettled()
    }

    /**
     * Determine the amount of recent-or-expected angular rotation given our sensor values and
     * easing state as documented for [isCurrentlySettled]. This is a signal for how frequently we
     * should update based on hinge activity.
     */
    private fun getErrorDistance(referenceHingeAngle: Float = hingeAngle): Float {
        // Have we now updated to a state far from the last one we presented?
        val distanceFromReferenceToCurrent = abs(referenceHingeAngle - hingeAngle)

        // Did our last frame have a long way to go to get to our current target?
        val distanceFromReferenceToTarget = abs(referenceHingeAngle - hingeAngleSensorValue)

        // Are we *currently* far from the target? Note we may often expect the current value to be
        // somewhere *between* the target and the last-rendered angle as each frame gets closer
        // to the target, but it's actually possible for the target to move between updates such
        // that the "current" value falls outside of the range.
        val distanceFromCurrentToTarget = abs(hingeAngleSensorValue - hingeAngle)

        return maxOf(
            distanceFromReferenceToCurrent,
            distanceFromReferenceToTarget,
            distanceFromCurrentToTarget
        )
    }

    /**
     * Determine whether the hinge angle is considered to be "settled" and unexpected to change
     * in the near future.
     */
    fun isCurrentlySettled(referenceHingeAngle: Float = hingeAngle): Boolean =
        (getErrorDistance(referenceHingeAngle) < SETTLED_THRESHOLD)

    /** Like [isCurrentlySettled], but with a wider tolerance. */
    fun isNearlySettled(referenceHingeAngle: Float = hingeAngle): Boolean =
        (getErrorDistance(referenceHingeAngle) < ALMOST_SETTLED_THRESHOLD)
}
