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
package com.android.personalcontext.ace.client.clientsdk.compat.recyclerview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.NestedScrollType
import androidx.core.view.ViewCompat.ScrollAxis
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A [RecyclerView] that implements [NestedScrollingParent3], acting as a basic nested scrolling
 * parent.
 */
class NestedScrollingParentRecyclerView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    RecyclerView(context, attrs, defStyleAttr), NestedScrollingParent3 {

    private val parentHelper = NestedScrollingParentHelper(this)

    init {
        isNestedScrollingEnabled = true
    }

    @Orientation
    private val orientation: Int?
        get() = (this.layoutManager as? LinearLayoutManager)?.orientation

    override fun onStartNestedScroll(
        child: View,
        target: View,
        @ScrollAxis axes: Int,
        type: Int,
    ): Boolean {
        return when (orientation) {
            VERTICAL -> axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
            HORIZONTAL -> axes and ViewCompat.SCROLL_AXIS_HORIZONTAL != 0
            else -> false
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        return super.onNestedPreScroll(target, dx, dy, consumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
    ) {
        scrollBy(dxUnconsumed, dyUnconsumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        scrollBy(dxUnconsumed, dyUnconsumed)

        consumed[0] = dxUnconsumed
        consumed[1] = dyUnconsumed
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        return super.onNestedPreFling(target, velocityX, velocityY)
    }

    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean {
        return fling(velocityX.toInt(), velocityY.toInt())
    }

    // region NestedScrollParent3
    // -----------------------------------------------------------------------------------------------

    override fun onNestedScrollAccepted(child: View, target: View, @ScrollAxis axes: Int) {
        return parentHelper.onNestedScrollAccepted(child, target, axes)
    }

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        @ScrollAxis axes: Int,
        @NestedScrollType type: Int,
    ) {
        return parentHelper.onNestedScrollAccepted(child, target, axes, type)
    }

    @ScrollAxis
    override fun getNestedScrollAxes(): Int {
        return parentHelper.nestedScrollAxes
    }

    override fun onStopNestedScroll(target: View) {
        return parentHelper.onStopNestedScroll(target)
    }

    override fun onStopNestedScroll(target: View, @NestedScrollType type: Int) {
        return parentHelper.onStopNestedScroll(target, type)
    }

    // -----------------------------------------------------------------------------------------------
    // endregion
}
