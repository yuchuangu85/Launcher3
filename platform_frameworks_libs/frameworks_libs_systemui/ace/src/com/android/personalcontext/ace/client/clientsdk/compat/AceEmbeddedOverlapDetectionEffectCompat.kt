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

import android.graphics.Rect
import android.util.Log
import android.view.ViewTreeObserver
import androidx.compose.ui.graphics.toComposeRect
import androidx.core.graphics.toRectF
import androidx.core.view.doOnAttach
import com.android.personalcontext.ace.client.clientsdk.compat.AceEmbeddedSurfaceViewCompat.Companion.TAG
import com.android.personalcontext.ace.client.clientsdk.compat.observable.DistinctObservableDelegates
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Shown
import com.android.personalcontext.ace.client.clientsdk.ui.exceeds
import com.android.personalcontext.ace.client.clientsdk.ui.insetBy
import com.android.personalcontext.ace.client.clientsdk.utils.windowBounds

/**
 * Handles changes in [AceEmbeddedSurfaceViewCompat]'s position in the window, and sets
 * [isOverlapping] to whether the view's bounds intersect with any of the overlap zones defined by
 * the [AceEmbeddedSurfaceViewCompat.overlapInsets].
 *
 * [AceEmbeddedSurfaceViewCompat] should instantiate an instance of this class as a field at
 * construction. For each DelegatedUiSurfaceView method that has a matching method signature in this
 * class, delegate the operation to this instance in an overridden method implementation.
 *
 * Call [invoke] on this instance when external properties change that may impact the overlap state.
 */
internal class AceEmbeddedOverlapDetectionEffectCompat(
    override val view: AceEmbeddedSurfaceViewCompat
) :
    AceEmbeddedSurfaceViewCompat.Effect,
    ViewTreeObserver.OnGlobalLayoutListener,
    ViewTreeObserver.OnScrollChangedListener {

    /**
     * Whether the [AceEmbeddedSurfaceViewCompat] is currently overlapping with any overlap zones,
     * based on the value of [overlapDistances].
     */
    internal var isOverlapping: Boolean by
        DistinctObservableDelegates.observable(false) {
            view.overlapFadeEffectCompat()
            view.inputsUpdateEffectCompat()
        }

    private val windowBounds by lazy { view.context.windowBounds().toRectF() }
    private val locationInWindow = IntArray(2)
    private val childBounds = Rect()

    /**
     * Invoke [AceEmbeddedOverlapDetectionEffectCompat] when external properties change to
     * recalculate the overlap state of the [AceEmbeddedSurfaceViewCompat].
     */
    operator fun invoke() {
        view.doOnAttach {
            val isShown = view.sessionState.uiStateFlow.value.visibility is Shown
            if (isShown && view.overlapBehavior is AceEmbeddedOverlapBehavior.SetZOrderBehind) {
                Log.d(
                    TAG,
                    String.format(
                        "OverlapDetectionEffect windowBounds: %s, overlapInsets: %s",
                        windowBounds,
                        view.overlapInsets,
                    ),
                )

                view.getLocationInWindow(locationInWindow)
                childBounds.set(
                    locationInWindow[0],
                    locationInWindow[1],
                    childBounds.left + view.width,
                    childBounds.top + view.height,
                )
                val safeBounds = windowBounds.toComposeRect().insetBy(view.overlapInsets)

                isOverlapping = childBounds.toComposeRect().exceeds(safeBounds)

                Log.d(
                    TAG,
                    String.format(
                        "OverlapDetectionEffect childBounds: %s, isOverlapping: %s",
                        childBounds,
                        isOverlapping,
                    ),
                )
            }
        }
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    fun onAttachedToWindow() {
        if (view.viewTreeObserver.isAlive) {
            view.viewTreeObserver.addOnGlobalLayoutListener(this)
            view.viewTreeObserver.addOnScrollChangedListener(this)
        }
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    fun onDetachedFromWindow() {
        if (view.viewTreeObserver.isAlive) {
            view.viewTreeObserver.removeOnGlobalLayoutListener(this)
            view.viewTreeObserver.removeOnScrollChangedListener(this)
        }
    }

    override fun onGlobalLayout() {
        this()
    }

    override fun onScrollChanged() {
        this()
    }
}
