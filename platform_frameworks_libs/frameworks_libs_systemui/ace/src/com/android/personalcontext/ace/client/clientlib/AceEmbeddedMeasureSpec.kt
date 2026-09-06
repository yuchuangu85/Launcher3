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
package com.android.personalcontext.ace.client.clientlib

import android.view.View.MeasureSpec

/**
 * Value type representation of [MeasureSpec], used by Kotlin APIs to ensure type safety when
 * accepting measure spec parameters.
 */
@JvmInline
value class AceEmbeddedMeasureSpec internal constructor(@PublishedApi internal val value: Int) {

    @PublishedApi
    internal constructor(
        size: Int,
        mode: MeasureSpecMode,
    ) : this(MeasureSpec.makeMeasureSpec(size, mode.value))

    override fun toString(): String = "MeasureSpec(value=${MeasureSpec.toString(value)})"
}

@PublishedApi
internal enum class MeasureSpecMode(internal val value: Int) {
    EXACTLY(MeasureSpec.EXACTLY),
    AT_MOST(MeasureSpec.AT_MOST),
    UNSPECIFIED(MeasureSpec.UNSPECIFIED),
}

/**
 * Creates a [AceEmbeddedMeasureSpec] using an [Int] in pixels that represents an
 * [MeasureSpec.EXACTLY] constraint.
 */
inline val Int.exactly: AceEmbeddedMeasureSpec
    get() = AceEmbeddedMeasureSpec(this, MeasureSpecMode.EXACTLY)

/**
 * Creates a [AceEmbeddedMeasureSpec] using an [Int] in pixels that represents an
 * [MeasureSpec.AT_MOST] constraint.
 */
inline val Int.atMost: AceEmbeddedMeasureSpec
    get() = AceEmbeddedMeasureSpec(this, MeasureSpecMode.AT_MOST)

/** Creates a [AceEmbeddedMeasureSpec] that represents an [MeasureSpec.UNSPECIFIED] constraint. */
inline val Int.unspecified: AceEmbeddedMeasureSpec
    get() = AceEmbeddedMeasureSpec(this, MeasureSpecMode.UNSPECIFIED)

/** Creates a [AceEmbeddedMeasureSpec] that wraps an existing [MeasureSpec] value. */
fun Int.wrapMeasureSpec(): AceEmbeddedMeasureSpec = AceEmbeddedMeasureSpec(this)
