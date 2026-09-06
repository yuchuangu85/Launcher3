/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.client.clientsdk.compat

import android.util.Log
import androidx.core.view.doOnAttach
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.personalcontext.ace.client.clientsdk.compat.AceEmbeddedSurfaceViewCompat.Companion.TAG
import com.android.personalcontext.ace.client.clientsdk.compat.observable.DistinctObservableDelegates
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior.SetZOrderBehind.WithFade
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Shown

/**
 * Handles changes in the [AceEmbeddedSurfaceViewCompat]'s overlap state. Depending on the defined
 * [AceEmbeddedSurfaceViewCompat.overlapBehavior], this is responsible for fading the view in or
 * out, and setting the surface's [overlapZOrderOnTop]. This also draws the background color to
 * cover up the black pixels when the [AceEmbeddedSurfaceViewCompat] is not yet connected.
 *
 * [AceEmbeddedSurfaceViewCompat] should instantiate an instance of this class as a field at
 * construction. For each DelegatedUiSurfaceView method that has a matching method signature in this
 * class, delegate the operation to this instance in an overridden method implementation.
 *
 * Call [invoke] on this instance when the [AceEmbeddedOverlapDetectionEffectCompat.isOverlapping]
 * state changes, or when external properties change that may impact the fading or z-order behavior
 * on overlap state transition.
 */
internal class AceEmbeddedOverlapFadeEffectCompat(override val view: AceEmbeddedSurfaceViewCompat) :
    AceEmbeddedSurfaceViewCompat.Effect,
    DynamicAnimation.OnAnimationEndListener,
    DynamicAnimation.OnAnimationUpdateListener {

    /**
     * The resolved z-order of the [AceEmbeddedSurfaceViewCompat] after overlap detection and taking
     * into account any animations.
     */
    internal var overlapZOrderOnTop: Boolean by
        DistinctObservableDelegates.observable(true) { view.zOrderUpdateEffectCompat() }

    internal val animatedAlpha = FloatValueHolder(1f)
    private val animation =
        SpringAnimation(animatedAlpha)
            .setMinValue(0f)
            .setMaxValue(1f)
            .setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA)
            .setSpring(SpringForce().setStiffness(SpringForce.STIFFNESS_LOW))
            .addUpdateListener(this)
            .addEndListener(this)

    /**
     * Invoke [AceEmbeddedOverlapFadeEffectCompat] when the
     * [AceEmbeddedOverlapDetectionEffectCompat.isOverlapping] state changes, or when external
     * properties change that may impact the fading or z-order behavior on overlap state transition.
     */
    operator fun invoke() {
        view.doOnAttach {
            val isOverlapping = view.overlapDetectionEffectCompat.isOverlapping
            val shouldHide = view.sessionVisibility.shouldHide(isOverlapping)
            val shouldSetBehind = view.sessionVisibility.shouldSetBehind(isOverlapping)

            val targetAlpha = if (shouldHide) 1f else 0f

            if (shouldSetBehind) {
                overlapZOrderOnTop = false
                Log.d(TAG, "OverlapFadeEffect Z-order: Behind")
            }

            animation.animateToFinalPosition(targetAlpha)
        }
    }

    override fun onAnimationUpdate(animation: DynamicAnimation<*>?, value: Float, velocity: Float) {
        Log.d(TAG, "OverlapFadeEffect update: ${animatedAlpha.value}")
        view.invalidate()
    }

    override fun onAnimationEnd(
        animation: DynamicAnimation<*>?,
        canceled: Boolean,
        value: Float,
        velocity: Float,
    ) {
        val isOverlapping = view.overlapDetectionEffectCompat.isOverlapping
        val shouldHide = view.sessionVisibility.shouldHide(isOverlapping)
        val shouldSetBehind = view.sessionVisibility.shouldSetBehind(isOverlapping)

        val targetAlpha = if (shouldHide) 1f else 0f

        if (!shouldSetBehind && value == targetAlpha) {
            overlapZOrderOnTop = true
            Log.d(TAG, "OverlapFadeEffect Z-order: OnTop")
        }
    }

    private fun AceEmbeddedUiVisibility.shouldHide(isOverlapping: Boolean): Boolean =
        when {
            this !is Shown -> true
            this is Shown.Updating && view.shouldHideOnUpdating -> true
            view.overlapBehavior is WithFade && isOverlapping -> true
            else -> false
        }

    private fun AceEmbeddedUiVisibility.shouldSetBehind(isOverlapping: Boolean) =
        shouldHide(isOverlapping) || isOverlapping
}
