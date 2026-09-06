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

package com.google.android.torus.utils.animation

import com.google.android.torus.math.MathUtils
import kotlin.math.pow

/** Utilities to help implement "easing" operations. */
object EasingUtils {
    /**
     * Easing function to interpolate a smooth curve that "follows" some other signal value in
     * real-time. The "follow curve" is an exponentially-weighted moving average (EWMA) of the
     * signal values: assuming a fixed timestep, the "follow value" at time t is determined by the
     * signal value S_t as `F_t = k * S_t + (1 - k) * F_(t-1)`, for some "easing rate" k between
     * 0 and 1. Note this formulation assumes that the "signal curve" moves by discrete steps with
     * zero velocity in between. This may cause slightly unexpected "follow" behavior -- e.g. the
     * curve may start to "settle" toward the new signal value even if we don't expect it to be
     * stable, or it may lag and/or move somewhat abruptly if the "signal curve" reverses direction.
     * These discrepancies would be most noticeable at frame rates that are especially low or
     * highly-variable, and so far they haven't seemed problematic in any of our applications.
     *
     * @param currentValue The value of the "follow curve" prior to this update step (i.e., either
     * the value returned the last time this function was called, or the initial value where the
     * follow curve should start). In most applications the initial value will be set to match
     * the first reading of the signal value.
     * @param targetValue The most recent reading of the "signal value." If this value remains
     * constant, the "follow curve" will eventually settle to it (asymptotically).
     * @param easingRate A parameter to control the "follow speed" between 0 (the follow curve
     * remains at its |currentValue| regardless of the new signal) and 1 (the follow curve
     * immediately snaps to the new |targetValue|, effectively disabling easing).
     * This parameter is typically tuned empirically. If the simulation is running at 60FPS, the
     * easing function exactly matches the "fixed timestep" version above, with easing rate k.
     * @param deltaSeconds The amount of time elapsed since determining the old |currentValue|, in
     * seconds, during which the "follow curve" is assumed to have been converging towards the new
     * |targetValue|.
     *
     * @return the value of the "easing curve" after updating by |deltaSeconds|.
     */
    @JvmStatic
    fun calculateEasing(
        currentValue: Float, targetValue: Float, easingRate: Float, deltaSeconds: Float
    ): Float {
        /* The exponential form of easing we use to support variable frame rates is inverted from
         * the fixed timestep version above; an easing rate of zero "disables easing" so that the
         * follow curve "snaps" to the new value, while an easing rate of one leaves the follow
         * curve at its current value. We can simply take the complement: */
        val exponentialEasingRate = 1f - easingRate

        val lerpBy = 1f - exponentialEasingRate.pow(deltaSeconds)
        return MathUtils.lerp(currentValue, targetValue, lerpBy)
    }
}
