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
package com.android.personalcontext.ace.client.clientsdk.state

/** Like [androidx.compose.ui.geometry.Rect], but allows for nullable values. */
data class NullableRect(
    val left: Float? = null,
    val top: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
) {

    companion object {
        /** A rectangle with left, top, right, and bottom edges all at zero. */
        val Empty: NullableRect = NullableRect()
    }
}

/** Returns whether any of the values in the [NullableRect] match the given [predicate]. */
fun NullableRect.any(predicate: (Float) -> Boolean): Boolean {
    if (left != null && predicate(left)) return true
    if (top != null && predicate(top)) return true
    if (right != null && predicate(right)) return true
    if (bottom != null && predicate(bottom)) return true
    return false
}

/**
 * Returns a new [NullableRect] describing the inner area of this rectangle after applying the
 * [insets].
 *
 * The operations are performed as follows:
 * * **Left** and **Top** insets are *added* to the current coordinates (moving edges inward).
 * * **Right** and **Bottom** insets are *subtracted* from the current coordinates (moving edges
 *   inward).
 *
 * **Nullability Behavior:** If a dimension is `null` in either the source rectangle or the
 * [insets], the resulting dimension will be `null`. This treats `null` as an undefined value that
 * propagates to the result.
 *
 * @param insets The insets to apply to this rectangle.
 * @return A new [NullableRect] representing the inset area, or containing nulls where data was
 *   missing.
 */
fun NullableRect.insetBy(insets: NullableRect): NullableRect {
    return NullableRect(
        left = if (left != null && insets.left != null) left + insets.left else null,
        top = if (top != null && insets.top != null) top + insets.top else null,
        right = if (right != null && insets.right != null) right - insets.right else null,
        bottom = if (bottom != null && insets.bottom != null) bottom - insets.bottom else null,
    )
}
