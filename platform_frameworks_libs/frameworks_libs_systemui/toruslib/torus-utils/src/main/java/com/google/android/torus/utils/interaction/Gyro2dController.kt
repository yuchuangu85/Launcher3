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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import com.google.android.torus.math.MathUtils
import com.google.android.torus.math.Vector2
import kotlin.math.abs
import kotlin.math.sign

/**
 * Class that analyzed the gyroscope and generates a rotation out of it.
 * This class only calculates the gyroscope rotation for two angles/degrees:
 * - Pitch (rotation around device X axis).
 * - Yaw (rotation around device Y axis).
 *
 * (Check https://developer.android.com/guide/topics/sensors/sensors_motion for more info).
 */
class Gyro2dController(context: Context, config: GyroConfig = GyroConfig()) {
    companion object {
        private const val TAG = "Gyro2dController"
        const val NANOS_TO_S = 1.0f / 1_000_000_000.0f
        const val RAD_TO_DEG = (180f / Math.PI).toFloat()
        const val BASE_FPS = 60f
        const val DEFAULT_EASING = 0.8f
    }

    /**
     * Defines the final rotation.
     *
     * - [Vector2.x] represents the Pitch (in degrees).
     * - [Vector2.y] represents the Yaw (in degrees).
     */
    var rotation: Vector2 = Vector2()
        private set

    /**
     * Defines if gyro is considered to be settled.
     * TODO: remove once clients are switched to the new |isCurrentlySettled(Vector2)| API.
     */
    var isSettled: Boolean = false
        private set

    /**
     * Defines whether the gyro animation is almost settled.
     * TODO: remove once clients are switched to the new |isNearlySettled(Vector2)| API.
     */
    var isAlmostSettled: Boolean = false
        private set

    /**
     * The config that defines the behavior of the gyro..
     */
    var config: GyroConfig = config
        set(value) {
            field = value
            onNewConfig()
        }

    private val angles: FloatArray = FloatArray(3)
    private val sensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

        override fun onSensorChanged(event: SensorEvent) {
            updateGyroRotation(event)
        }
    }
    private val displayRotationValues: IntArray =
        intArrayOf(
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270
        )
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var displayRotation: Int = displayRotationValues[0]
    private var timestamp: Float = 0f
    private var recenter: Boolean = false
    private var recenterMul: Float = 1f
    private var ease: Boolean = true
    private var gyroSensorRegistered: Boolean = false

    // Speed per frame, based on 60FPS.
    private var easingMul: Float = DEFAULT_EASING * BASE_FPS

    init {
        onNewConfig()
    }

    /**
     * Starts listening for gyroscope events.
     * (the rotation is also reset).
     */
    fun start() {
        gyroSensor?.let {
            sensorManager.registerListener(
                sensorEventListener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )

            gyroSensorRegistered = true
        }

        if (gyroSensor == null) Log.w(
            TAG,
            "SensorManager could not find a default TYPE_GYROSCOPE sensor"
        )
    }

    /**
     * Stops listening for the gyroscope events.
     * (the rotation is also reset).
     */
    fun stop() {
        if (gyroSensorRegistered) {
            sensorManager.unregisterListener(sensorEventListener)
            gyroSensorRegistered = false
        }
    }

    /**
     * Resets the rotation values.
     */
    fun resetValues() {
        rotation = Vector2()
        angles[0] = 0f
        angles[1] = 0f
    }

    /**
     * Updates the output rotation (mostly it is used the update and ease the rotation value based
     * on the [Gyro2dController.GyroConfig.easingSpeed] value).
     *
     * @param deltaSeconds the time in seconds elapsed since the last time
     * [Gyro2dController.update] was called.
     */
    fun update(deltaSeconds: Float) {
        /*
         * Ease if needed (specially to reduce movement variation which will allow us to use a
         * smaller fps).
         */
        rotation = if (ease) {
            Vector2(
                MathUtils.lerp(rotation.x, angles[0], easingMul * deltaSeconds),
                MathUtils.lerp(rotation.y, angles[1], easingMul * deltaSeconds)
            )
        } else {
            Vector2(angles[0], angles[1])
        }

        isSettled = isCurrentlySettled()
        isAlmostSettled = isNearlySettled()
    }

    /**
     * Call it to change how the gyro sensor is interpreted. This function is specially important
     * when the display is not being presented in its default orientation (by default
     * [Gyro2dController] will read the gyro values as if the device is in its default orientation).
     *
     * @param displayRotation The current display rotation. It can only be one of the following
     * values: [Surface.ROTATION_0], [Surface.ROTATION_90], [Surface.ROTATION_180] or
     * [Surface.ROTATION_270].
     */
    fun setDisplayRotation(displayRotation: Int) {
        if (displayRotation !in displayRotationValues) {
            throwDisplayRotationException(displayRotation)
        }

        this.displayRotation = displayRotation
    }

    /**
     * Determine whether the gyro orientation is considered to be "settled" and unexpected to change
     * in the near future. If a non-null [referenceRotation] is provided, then the gyro also won't
     * be considered "settled" if the current or (expected) future state is too far from the
     * reference. For example, clients can provide the value of our [rotation] at the time that they
     * last presented that state to the user, to determine if that reference value is now too far
     * behind.
     */
    fun isCurrentlySettled(referenceRotation: Vector2 = rotation): Boolean =
        (getErrorDistance(referenceRotation) < config.settledThreshold)

    /** Like [isCurrentlySettled], but with a wider tolerance. */
    fun isNearlySettled(referenceRotation: Vector2 = rotation): Boolean =
        (getErrorDistance(referenceRotation) < config.almostSettledThreshold)

    /**
     * Determine the amount of recent-or-expected angular rotation given our sensor values and
     * easing state as documented for [isCurrentlySettled]. This is a signal for how frequently we
     * should update based on gyro activity.
     */
    private fun getErrorDistance(referenceRotation: Vector2 = rotation): Float {
        val targetOrientation = Vector2(angles[0], angles[1])

        // Have we now updated to a state far from the last one we presented?
        val distanceFromReferenceToCurrent = referenceRotation.distanceTo(rotation)

        // Did our last frame have a long way to go to get to our current target?
        val distanceFromReferenceToTarget = referenceRotation.distanceTo(targetOrientation)

        // Are we *currently* far from the target? Note we may often expect the current value to be
        // somewhere *between* the target and the last-rendered rotation as each frame gets closer
        // to the target, but it's actually possible for the target to move between updates such
        // that the "current" value falls outside of the range.
        val distanceFromCurrentToTarget = rotation.distanceTo(targetOrientation)

        return maxOf(
            distanceFromReferenceToCurrent,
            distanceFromReferenceToTarget,
            distanceFromCurrentToTarget
        )
    }

    private fun updateGyroRotation(event: SensorEvent) {
        if (timestamp != 0f) {
            val dT = (event.timestamp - timestamp) * NANOS_TO_S
            // Adjust based on display rotation.
            var axisX: Float = when (displayRotation) {
                Surface.ROTATION_90 -> -event.values[1]
                Surface.ROTATION_180 -> -event.values[0]
                Surface.ROTATION_270 -> event.values[1]
                else -> event.values[0]
            }

            var axisY: Float = when (displayRotation) {
                Surface.ROTATION_90 -> event.values[0]
                Surface.ROTATION_180 -> -event.values[1]
                Surface.ROTATION_270 -> -event.values[0]
                else -> event.values[1]
            }

            axisX *= RAD_TO_DEG * dT * config.intensity
            axisY *= RAD_TO_DEG * dT * config.intensity

            angles[0] = updateAngle(angles[0], axisX, config.maxAngleRotation.x)
            angles[1] = updateAngle(angles[1], axisY, config.maxAngleRotation.y)
        }

        timestamp = event.timestamp.toFloat()
    }

    private fun updateAngle(angle: Float, deltaAngle: Float, maxAngle: Float): Float {
        // Adds incremental value.
        var angleCombined = angle + deltaAngle

        // Clamps to maxAngleRotation x and maxAngleRotation y.
        if (abs(angleCombined) > maxAngle) angleCombined = maxAngle * sign(angleCombined)

        // Re-centers to origin if needed.
        if (recenter) angleCombined *= recenterMul

        return angleCombined
    }

    private fun throwDisplayRotationException(displayRotation: Int) {
        throw IllegalArgumentException(
            "setDisplayRotation only accepts Surface.ROTATION_0 (0), " +
                    "Surface.ROTATION_90 (1), Surface.ROTATION_180 (2) or \n" +
                    "[Surface.ROTATION_270 (3); Instead the value was $displayRotation."
        )
    }

    private fun onNewConfig() {
        recenter = config.recenterSpeed > 0f
        recenterMul = 1f - MathUtils.clamp(config.recenterSpeed, 0f, 1f)
        ease = config.easingSpeed < 1f
        easingMul = MathUtils.clamp(config.easingSpeed, 0f, 1f) * BASE_FPS
    }

    /**
     * Class that contains the config attributes for the gyro.
     */
    data class GyroConfig(
        /**
         * Adjusts the maximum output rotation (in degrees) for both positive and negative angles,
         * for each direction (x for the rotation around the X axis, y for the rotation
         * around the Y axis).
         *
         * i.e. if [maxAngleRotation] = (2, 4), the output rotation would be inside
         * ([-2º, 2º], [-4º, 4º]).
         */
        val maxAngleRotation: Vector2 = Vector2(2f),

        /**
         * Adjusts how much movement we need to apply to the device to make it rotate. This value
         * multiplies the original rotation values; thus if the value is < 1f, we would need to
         * rotate more the device than the actual rotation; if it is 1 it would be the default
         * phone rotation; if it is > 1f it will magnify the rotation.
         */
        val intensity: Float = 0.05f,

        /**
         * Adjusts how much the end rotation is eased. This value can be from range [0, 1].
         * - When 0, the eased value won't change.
         * - when 1, there isn't any easing.
         */
        val easingSpeed: Float = 0.8f,

        /**
         * How fast we want the rotation to recenter besides the gyro values.
         * - When 0, it doesn't recenter.
         * - when 1, it would make the rotation be in the center all the time.
         */
        val recenterSpeed: Float = 0f,

        /**
         * The minimum frame-over-frame delta required between gyroscope readings
         * (by L2 distance in the rotation angles) in order to consider the device to be settled
         * in a given animation frame.
         */
        val settledThreshold: Float = 0.0005f,

        /**
         * The minimum frame-over-frame delta required between the target orientation and the
         * current orientation, in order to define if the orientation is almost settled.
         */
        val almostSettledThreshold: Float = 0.01f
    )
}
