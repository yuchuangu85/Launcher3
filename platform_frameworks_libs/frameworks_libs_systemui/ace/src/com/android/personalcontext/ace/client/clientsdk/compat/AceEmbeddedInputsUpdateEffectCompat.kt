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
import android.view.View
import android.view.View.MeasureSpec
import androidx.core.graphics.toColor
import androidx.core.view.doOnAttach
import com.android.personalcontext.ace.client.clientlib.wrapMeasureSpec
import com.android.personalcontext.ace.client.clientsdk.compat.AceEmbeddedSurfaceViewCompat.Companion.TAG
import com.android.personalcontext.ace.client.clientsdk.compat.launchedeffect.LaunchedEffectCompat
import com.android.personalcontext.ace.client.clientsdk.compat.observable.DistinctObservableDelegates
import kotlinx.coroutines.yield

/**
 * Handles changes in properties (measure spec, background color, visible state, etc), and updates
 * the DUI session with the new inputs.
 *
 * [AceEmbeddedSurfaceViewCompat] should instantiate an instance of this class as a field at
 * construction. For each DelegatedUiSurfaceView method that has a matching method signature in this
 * class, delegate the operation to this instance in an overridden method implementation.
 *
 * Call [invoke] on this instance when external input properties change.
 */
internal class AceEmbeddedInputsUpdateEffectCompat(
    override val view: AceEmbeddedSurfaceViewCompat
) : AceEmbeddedSurfaceViewCompat.Effect {

    private var isVisibleAndAttached: Boolean by
        DistinctObservableDelegates.observable(false) {
            this()
            view.overlapFadeEffectCompat.animatedAlpha.value = 1f
            view.invalidate()
        }

    /**
     * Other components may opportunistically [invoke] this component, even if inputs don't end up
     * being updated. Use a
     * [com.android.personalcontext.ace.client.clientsdk.compat.launchedeffect.LaunchedEffectCompat]
     * to avoid calling update() unnecessarily when inputs haven't changed.
     */
    private val launchedEffect by lazy { LaunchedEffectCompat(view.coroutineScope) }

    /**
     * Invoke [AceEmbeddedInputsUpdateEffectCompat] when external input properties change, which
     * will cause it to trigger prepare(), update(), and reset() as needed.
     */
    operator fun invoke() {
        view.doOnAttach {
            launchedEffect(
                isVisibleAndAttached,
                view.hints,
                widthMeasureSpec.wrapMeasureSpec(),
                heightMeasureSpec.wrapMeasureSpec(),
                view.backgroundSurfaceColor,
                view.clientNestedScrollAxes,
                view.clientNestedScrollAxisLocked,
                view.shouldBlur,
                view.themeResourceId,
            ) {
                // Multiple invocations on the same event loop, even with different keys, should
                // debounce.
                // Since [view.coroutineScope] dispatches on [Dispatchers.Main.immediate], create a
                // suspension
                // point with yield() to allow for such cancellation.
                yield() // TODO: Double check this before sending out for review.

                Log.d(
                    TAG,
                    String.format(
                        "InputsUpdateEffect isVisibleAndAttached: %s, hints: %s, widthMeasureSpec: %s, heightMeasureSpec: %s, backgroundSurfaceColor: %s, clientNestedScrollAxes: %s, clientNestedScrollAxisLocked: %s, shouldBlur: %s, themeResourceId: %s",
                        isVisibleAndAttached,
                        view.hints,
                        widthMeasureSpec.wrapMeasureSpec(),
                        heightMeasureSpec.wrapMeasureSpec(),
                        view.backgroundSurfaceColor,
                        view.clientNestedScrollAxes,
                        view.clientNestedScrollAxisLocked,
                        view.shouldBlur,
                        view.themeResourceId,
                    ),
                )

                view.sessionState.update(
                    hints = view.hints,
                    width = widthMeasureSpec.wrapMeasureSpec(),
                    height = heightMeasureSpec.wrapMeasureSpec(),
                    backgroundColor = view.backgroundSurfaceColor.toColor(),
                    nestedScrollAxes = view.clientNestedScrollAxes,
                    nestedScrollAxisLocked = view.clientNestedScrollAxisLocked,
                    shouldBlur = view.shouldBlur,
                    themeResourceId = view.themeResourceId,
                )

                // Trigger the SurfaceView's updateSurface(), which eventually triggers
                // SurfaceHolder.Callback's surfaceCreated(). But since that is a private API, we
                // had to
                // find a property that triggers this code path but without any drawbacks.
                // windowVisibility
                // is largely unused by SurfaceView or any of its superclasses except to draw
                // scrollbars.
                view.surfaceView.dispatchWindowVisibilityChanged(
                    if (isVisibleAndAttached) View.VISIBLE else View.GONE
                )

                if (isVisibleAndAttached) {
                    view.sessionState.connect(view.context)
                } else {
                    view.sessionState.cancel()
                }
            }
        }
    }

    /**
     * Whether we should accept the next measure spec passed into [onMeasure]. Some parents (like
     * [LinearLayout]) will do a second measure pass where they temporarily modify our layout params
     * before measuring us.
     */
    private var expectingMeasureSpec = false

    private var widthMeasureSpec: Int = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        set(value) {
            if (field != value) {
                field = value
                this()
            }
        }

    private var heightMeasureSpec: Int = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        set(value) {
            if (field != value) {
                field = value
                this()
            }
        }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    fun onDetachedFromWindow() {
        this.isVisibleAndAttached = false
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] method with the
     * same signature to implement the standard policy.
     */
    fun onVisibilityChanged(isVisible: Boolean) {
        // This method is only called when the view is attached.
        this.isVisibleAndAttached = isVisible
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] method with the
     * same signature to implement the standard policy.
     */
    fun requestLayout() {
        expectingMeasureSpec = true
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] method with the
     * same signature to implement the standard policy.
     */
    fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (expectingMeasureSpec) {
            this.widthMeasureSpec = widthMeasureSpec
            this.heightMeasureSpec = heightMeasureSpec

            expectingMeasureSpec = false
        }
    }
}
