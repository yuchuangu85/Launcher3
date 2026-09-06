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

import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.hint.PublishedContextHint
import androidx.annotation.VisibleForTesting

/** Wrapper interface for [PublishedContextHint]. */
sealed interface IPublishedContextHint {

    /**
     * Returns the unwrapped [PublishedContextHint]. May return null if originally wrapped from a
     * unit test, where constructing an instance of [PublishedContextHint] is not possible.
     */
    fun unwrap(): PublishedContextHint?

    /** @see PublishedContextHint.contextHint */
    val contextHint: ContextHint

    /** @see PublishedContextHint.renderTokens */
    val renderTokens: Set<IRenderToken>
}

/** Creates an [IPublishedContextHint] from a [PublishedContextHint]. */
fun PublishedContextHint.wrap(): IPublishedContextHint = PublishedContextHintWrapper(this)

private class PublishedContextHintWrapper(private val original: PublishedContextHint) :
    IPublishedContextHint {
    override fun unwrap() = original

    override val contextHint
        get() = original.contextHint

    override val renderTokens
        get() = original.renderTokens.map { it.wrap() }.toSet()
}

@VisibleForTesting
class PublishedContextHintForTesting(
    override val contextHint: ContextHint,
    override val renderTokens: Set<IRenderToken> = emptySet(),
) : IPublishedContextHint {
    override fun unwrap() = null
}

/** Returns a collection of unwrapped [PublishedContextHint]. */
fun Collection<IPublishedContextHint>.unwrapAll(): List<PublishedContextHint> = mapNotNull {
    it.unwrap()
}
