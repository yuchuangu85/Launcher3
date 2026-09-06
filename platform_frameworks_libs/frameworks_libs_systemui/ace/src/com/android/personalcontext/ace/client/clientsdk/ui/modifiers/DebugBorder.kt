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

import androidx.compose.foundation.AndroidExternalSurfaceZOrder.Companion.OnTop
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.android.personalcontext.ace.client.clientsdk.ui.AceEmbeddedOverlapState
import com.android.personalcontext.ace.client.clientsdk.ui.AceEmbeddedOverlapStateImpl

/** Draws a debug border outside the bounds of the composable, showing z-order and overlap. */
fun Modifier.debugBorder(width: Dp, overlapState: AceEmbeddedOverlapState): Modifier = drawBehind {
    val overlapState = overlapState as AceEmbeddedOverlapStateImpl

    val strokeWidth = width.toPx()
    val topLeft = Offset(-strokeWidth, -strokeWidth)
    val borderSize = Size(size.width + 2 * strokeWidth, size.height + 2 * strokeWidth)

    drawRect(
        color = if (overlapState.zOrder == OnTop) Color.Green else Color.Red,
        topLeft = topLeft,
        size = borderSize,
        style = Stroke(width = strokeWidth),
    )
}
