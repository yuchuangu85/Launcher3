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
package com.android.personalcontext.ace.client.prototype.embeddedscroll

import android.os.Bundle
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import androidx.core.view.ViewCompat.ScrollAxis
import com.android.personalcontext.ace.client.prototype.PrototypeInsight
import com.android.personalcontext.ace.client.prototype.PrototypeInsightId.EmbeddedScrollInsightId
import com.android.personalcontext.ace.common.EmbeddedScrollEvent
import com.android.personalcontext.ace.common.EmbeddedScrollEventType

/**
 * An insight for an [EmbeddedScrollEvent] emitted by an Embedded Visualizer.
 *
 * @property axes If [type] is [SCROLL_START], the scroll axes.
 * @property x If [type] is [SCROLL_DELTA], the scroll delta in the x direction. If [type] is
 *   [SCROLL_STOP], the fling velocity in the x direction.
 * @property y If [type] is [SCROLL_DELTA], the scroll delta in the y direction. If [type] is
 *   [SCROLL_STOP], the fling velocity in the y direction.
 */
data class EmbeddedScrollInsight(
    val type: EmbeddedScrollEventType,
    @property:ScrollAxis val axes: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
) : PrototypeInsight(EmbeddedScrollInsightId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putString("type", type.name)
        bundle.putInt("axes", axes)
        bundle.putFloat("x", x)
        bundle.putFloat("y", y)
    }

    companion object : Creator {

        override fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight =
            EmbeddedScrollInsight(
                type = enumValueOf(bundle.getString("type")!!),
                axes = bundle.getInt("axes"),
                x = bundle.getFloat("x"),
                y = bundle.getFloat("y"),
            )

        /** Convert a [EmbeddedScrollEvent] to [EmbeddedScrollInsight]. */
        fun EmbeddedScrollEvent.toEmbeddedScrollInsight() =
            EmbeddedScrollInsight(type = this.type, axes = this.axes, x = this.x, y = this.y)
    }
}
