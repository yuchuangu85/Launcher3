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
@file:Suppress("FlaggedApi", "NewApi")

package com.android.personalcontext.ace.common

import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.InsightCollection
import com.android.personalcontext.ace.common.wrappers.IPublishedContextInsight

object FlatIndexUtils {

    /**
     * Finds the flat index of the specified [insight] within the hierarchy of this
     * [android.service.personalcontext.insight.PublishedContextInsight] using a pre-order
     * (depth-first) traversal.
     *
     * The traversal starts with the root
     * [android.service.personalcontext.insight.PublishedContextInsight.insight] at index `0`. If an
     * insight in the hierarchy is an [InsightCollection], its nested insights are visited from
     * first to last before continuing to the next sibling.
     *
     * @param insight The target [ContextInsight] to search for within the hierarchy.
     * @return The zero-based index of the [insight] if it is found, or [default] if the [insight]
     *   is not present in the tree.
     */
    fun IPublishedContextInsight.flatIndexOf(
        insight: ContextInsight,
        default: () -> Int = { -1 },
    ): Int {
        val stack = mutableListOf(this.insight)
        var index = 0

        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.lastIndex)

            if (current == insight) {
                return index
            }
            index++

            if (current is InsightCollection) {
                stack.addAll(current.insights.reversed())
            }
        }

        return default()
    }

    /**
     * Retrieves the [ContextInsight] at the specified [flatIndex] within the hierarchy of this
     * [android.service.personalcontext.insight.PublishedContextInsight] using a pre-order
     * (depth-first) traversal.
     *
     * The traversal starts with the root
     * [android.service.personalcontext.insight.PublishedContextInsight.insight] at index `0`. If an
     * insight in the hierarchy is an [InsightCollection], its nested insights are visited from
     * first to last before continuing to the next sibling.
     *
     * @param flatIndex The zero-based index of the [ContextInsight] to retrieve.
     * @return The [ContextInsight] at the given index, or [default] if the index is out of bounds.
     */
    fun IPublishedContextInsight.getInsightAt(
        flatIndex: Int,
        default: () -> ContextInsight = { this.insight },
    ): ContextInsight {
        if (flatIndex < 0) return default()

        val stack = mutableListOf(this.insight)
        var currentIndex = 0

        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.lastIndex)

            if (currentIndex == flatIndex) {
                return current
            }
            currentIndex++

            if (current is InsightCollection) {
                stack.addAll(current.insights.reversed())
            }
        }

        return default()
    }
}
