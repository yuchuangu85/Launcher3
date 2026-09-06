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

package com.android.personalcontext.ace.common.wrappers

import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.PublishedContextInsight
import androidx.annotation.VisibleForTesting

/** Wrapper interface for [PublishedContextInsight]. */
sealed interface IPublishedContextInsight {

    /**
     * Returns the unwrapped [PublishedContextInsight]. May return null if originally wrapped from a
     * unit test, where constructing an instance of [PublishedContextInsight] is not possible.
     */
    fun unwrap(): PublishedContextInsight?

    /** @see PublishedContextInsight.insight */
    val insight: ContextInsight
}

/** Creates an [IPublishedContextInsight] from a [PublishedContextInsight]. */
fun PublishedContextInsight.wrap(): IPublishedContextInsight = PublishedContextInsightWrapper(this)

private class PublishedContextInsightWrapper(private val original: PublishedContextInsight) :
    IPublishedContextInsight {
    override fun unwrap() = original

    override val insight
        get() = original.insight
}

@VisibleForTesting
class PublishedContextInsightForTesting(override val insight: ContextInsight) :
    IPublishedContextInsight {
    override fun unwrap() = null
}
