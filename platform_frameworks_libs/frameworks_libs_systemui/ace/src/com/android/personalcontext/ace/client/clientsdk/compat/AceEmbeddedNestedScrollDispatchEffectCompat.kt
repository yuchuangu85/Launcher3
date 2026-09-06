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
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.NestedScrollType
import androidx.core.view.ViewCompat.SCROLL_AXIS_NONE
import androidx.core.view.ViewCompat.ScrollAxis
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.android.personalcontext.ace.client.clientsdk.compat.AceEmbeddedSurfaceViewCompat.Companion.TAG
import com.android.personalcontext.ace.client.prototype.embeddedscroll.EmbeddedScrollInsight
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_DELTA
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_START
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_STOP

/**
 * Collects the DUI session's nested scroll and nested fling events, and dispatches them up the view
 * hierarchy.
 *
 * [AceEmbeddedSurfaceViewCompat] should instantiate an instance of this class as a field at
 * construction. For each DelegatedUiSurfaceView method that has a matching method signature in this
 * class, delegate the operation to this instance in an overridden method implementation.
 *
 * Call [onNestedScrollEvent] on this instance on each nested scroll event.
 */
internal class AceEmbeddedNestedScrollDispatchEffectCompat(
    override val view: AceEmbeddedSurfaceViewCompat
) : AceEmbeddedSurfaceViewCompat.Effect, NestedScrollingChild3 {

    private val childHelper = NestedScrollingChildHelper(view)

    private val parentConsumed = IntArray(2)
    private val scrollOffset = IntArray(2)

    /** Invoke [onNestedScrollEvent] on each nested scroll event from the DUI session. */
    fun onNestedScrollEvent(event: EmbeddedScrollInsight) {
        @Suppress("CheckReturnValue")
        when (event.type) {
            SCROLL_START -> {
                handleScrollStart(event)
            }
            SCROLL_DELTA -> {
                handleScrollDelta(event)
            }
            SCROLL_STOP -> {
                handleScrollStop(event)
            }
        }
    }

    @Suppress("CheckReturnValue")
    private fun handleScrollStart(event: EmbeddedScrollInsight) {
        Log.d(TAG, String.format("NestedScrollDispatchEffect NestedScrollStartEvent: %s", event))

        // Cancel any ongoing fling animations.
        cancelParentFling(view)

        if (event.axes != SCROLL_AXIS_NONE) {
            startNestedScroll(event.axes)
        }
    }

    @Suppress("CheckReturnValue")
    private fun handleScrollDelta(event: EmbeddedScrollInsight) {
        Log.d(TAG, String.format("NestedScrollDispatchEffect NestedScrollDelta: %s", event))

        val dx = -event.x.toInt()
        val dy = -event.y.toInt()

        if (dx != 0 || dy != 0) {
            parentConsumed[0] = 0
            parentConsumed[1] = 0
            scrollOffset[0] = 0
            scrollOffset[1] = 0

            dispatchNestedPreScroll(dx, dy, parentConsumed, scrollOffset)
            dispatchNestedScroll(
                dxConsumed = 0,
                dyConsumed = 0,
                dxUnconsumed = dx - parentConsumed[0],
                dyUnconsumed = dy - parentConsumed[1],
                offsetInWindow = scrollOffset,
                type = ViewCompat.TYPE_TOUCH,
            )
        }
    }

    @Suppress("CheckReturnValue")
    private fun handleScrollStop(event: EmbeddedScrollInsight) {
        Log.d(TAG, String.format("NestedScrollDispatchEffect NestedScrollStopEvent: %s", event))

        val velocityX = -event.x
        val velocityY = -event.y

        if (velocityX != 0f || velocityY != 0f) {
            dispatchNestedPreFling(velocityX, velocityY)
            dispatchNestedFling(velocityX, velocityY, true)
        }

        stopNestedScroll()
    }

    private fun cancelParentFling(view: View) {
        var parent = view.parent
        while (parent != null) {
            when (parent) {
                is NestedScrollView -> parent.fling(0)
                is ScrollView -> parent.fling(0)
                is HorizontalScrollView -> parent.fling(0)
                is RecyclerView -> parent.stopScroll()
            }
            parent = parent.parent
        }
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    fun onDetachedFromWindow() {
        return childHelper.onDetachedFromWindow()
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        return childHelper.setNestedScrollingEnabled(enabled)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun hasNestedScrollingParent(): Boolean {
        return childHelper.hasNestedScrollingParent()
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun hasNestedScrollingParent(@NestedScrollType type: Int): Boolean {
        return childHelper.hasNestedScrollingParent(type)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun startNestedScroll(axes: Int): Boolean {
        return childHelper.startNestedScroll(axes)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun startNestedScroll(@ScrollAxis axes: Int, @NestedScrollType type: Int): Boolean {
        return childHelper.startNestedScroll(axes, type)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun stopNestedScroll() {
        return childHelper.stopNestedScroll()
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun stopNestedScroll(@NestedScrollType type: Int) {
        return childHelper.stopNestedScroll(type)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean {
        return childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
        )
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        @NestedScrollType type: Int,
    ): Boolean {
        return childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type,
        )
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        @NestedScrollType type: Int,
        consumed: IntArray,
    ) {
        return childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type,
            consumed,
        )
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        @NestedScrollType type: Int,
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    /**
     * This is a delegate method. Call it from your [AceEmbeddedSurfaceViewCompat] subclass method
     * with the same signature to implement the standard policy.
     */
    fun onStopNestedScroll(child: View) {
        return childHelper.onStopNestedScroll(child)
    }
}
