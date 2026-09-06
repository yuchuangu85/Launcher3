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
package com.android.personalcontext.ace.client.clientsdk.ui.modifiers

import androidx.compose.foundation.AndroidExternalSurfaceZOrder.Companion.Behind
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.android.personalcontext.ace.client.clientsdk.ui.AceEmbeddedOverlapState
import com.android.personalcontext.ace.client.clientsdk.ui.AceEmbeddedOverlapStateImpl

/**
 * A modifier that makes a Composable clickable only when the provided [overlapState]'s z-order is
 * [Behind].
 *
 * When the z-order is [Behind], the remote UI does not receive the user's touches, so no egress can
 * occur. This helps to optionally cover that case, so the touch isn't discarded.
 */
@Composable
fun Modifier.clickableWhenBehind(
    overlapState: AceEmbeddedOverlapState,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val overlapState = overlapState as AceEmbeddedOverlapStateImpl

    return clickable(
        enabled = overlapState.zOrder == Behind,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = interactionSource,
        onClick = onClick,
    )
}

/**
 * A modifier that makes a Composable clickable only when the provided [overlapState]'s z-order is
 * [Behind].
 *
 * When the z-order is [Behind], the remote UI does not receive the user's touches, so no egress can
 * occur. This helps to optionally cover that case, so the touch isn't discarded.
 */
@Composable
fun Modifier.clickableWhenBehind(
    overlapState: AceEmbeddedOverlapState,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val overlapState = overlapState as AceEmbeddedOverlapStateImpl

    return clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = overlapState.zOrder == Behind,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
}
