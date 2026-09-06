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
package com.android.personalcontext.ace.client.clientsdk.ui

import androidx.compose.ui.geometry.Rect
import com.android.personalcontext.ace.client.clientsdk.state.NullableRect
import com.android.personalcontext.ace.client.clientsdk.state.insetBy

/** Converts the [Rect] to a [NullableRect]. */
fun Rect.toNullableRect(): NullableRect = NullableRect(left, top, right, bottom)

/** @see NullableRect.insetBy */
fun Rect.insetBy(insets: NullableRect): NullableRect {
    return toNullableRect().insetBy(insets)
}

/**
 * Returns true if this [NullableRect] fully encloses the [other] Rect.
 *
 * Null bounds represent infinite space.
 */
fun NullableRect.encloses(other: Rect): Boolean {
    val l = left
    val t = top
    val r = right
    val b = bottom

    return (l == null || l <= other.left) &&
        (t == null || t <= other.top) &&
        (r == null || r >= other.right) &&
        (b == null || b >= other.bottom)
}

/**
 * Returns true if the [bounds] do not fully [encloses] this Rect.
 *
 * Null bounds represent infinite space.
 */
fun Rect.exceeds(bounds: NullableRect): Boolean {
    return !bounds.encloses(this)
}
