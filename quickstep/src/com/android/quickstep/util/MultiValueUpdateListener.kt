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
package com.android.quickstep.util

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.Utilities

/**
 * Utility class to update multiple values with different interpolators and durations during the
 * same animation.
 */
abstract class MultiValueUpdateListener
@JvmOverloads
constructor(val defaultInterpolator: Interpolator = LINEAR) : AnimatorUpdateListener {
    private val allProperties = ArrayList<FloatProp>()

    override fun onAnimationUpdate(animator: ValueAnimator) {
        val percent = animator.animatedFraction

        allProperties.forEach {
            val interpolatedPercent = it.interpolator.getInterpolation(percent)
            it.value = Utilities.mapRange(interpolatedPercent, it.startValue, it.endValue)
        }
        onUpdate(percent, false /* initOnly */)
    }

    /**
     * @param percent The total animation progress.
     * @param initOnly When true, only does enough work to initialize the animation.
     */
    abstract fun onUpdate(percent: Float, initOnly: Boolean)

    inner class FloatProp
    @JvmOverloads
    constructor(
        val startValue: Float,
        val endValue: Float,
        internal val interpolator: Interpolator = defaultInterpolator,
    ) {
        @JvmField var value: Float = startValue

        init {
            allProperties.add(this)
        }
    }
}
