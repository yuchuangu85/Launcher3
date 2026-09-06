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
package com.android.personalcontext.ace.common

import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.ScrollAxis
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_DELTA
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_START
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_STOP

/**
 * A scroll event emitted by an Embedded Visualizer.
 *
 * @property axes If [type] is [SCROLL_START], the scroll axes.
 * @property x If [type] is [SCROLL_DELTA], the scroll delta in the x direction. If [type] is
 *   [SCROLL_STOP], the fling velocity in the x direction.
 * @property y If [type] is [SCROLL_DELTA], the scroll delta in the y direction. If [type] is
 *   [SCROLL_STOP], the fling velocity in the y direction.
 */
data class EmbeddedScrollEvent(
    val type: EmbeddedScrollEventType,
    @property:ScrollAxis val axes: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
) {

    override fun toString() =
        when (type) {
            SCROLL_START -> "EmbeddedScrollStart(axes=${axes.toScrollAxesString()})"
            SCROLL_DELTA -> "EmbeddedScrollDelta(x=%.1f, y=%.1f)".format(x, y)
            SCROLL_STOP -> "EmbeddedScrollStop(x=%.1f, y=%.1f)".format(x, y)
        }
}

enum class EmbeddedScrollEventType {
    /**
     * Indicates the start of a scroll interaction.
     *
     * When this type is used, the [EmbeddedScrollEvent.axes] property is populated.
     */
    SCROLL_START,

    /**
     * Indicates a scroll position update (movement).
     *
     * When this type is used, [EmbeddedScrollEvent.x] and [EmbeddedScrollEvent.y] represent the
     * scroll deltas.
     */
    SCROLL_DELTA,

    /**
     * Indicates the end of a scroll interaction.
     *
     * When this type is used, [EmbeddedScrollEvent.x] and [EmbeddedScrollEvent.y] represent the
     * fling velocities.
     */
    SCROLL_STOP,
}

private fun @receiver:ScrollAxis Int.toScrollAxesString(): String {
    val axes = this

    if (axes == ViewCompat.SCROLL_AXIS_NONE) {
        return "[NONE]"
    }

    return buildList {
            if ((axes and ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0) add("HORIZONTAL")
            if ((axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0) add("VERTICAL")
        }
        .joinToString(", ", prefix = "[", postfix = "]")
}
