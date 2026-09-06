/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.cuebar.ui.compose.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

@Composable
fun Modifier.eduBalloon(
    backgroundColor: Color,
    horizontalAlignment: Alignment.Horizontal,
): Modifier {
    return this.padding(bottom = 12.dp)
        .drawBehind {
            val arrowPosition = with(density) { 48.dp.toPx() }
            val oneDp = with(density) { 1.dp.toPx() }
            val translationX = with(density) { 6.dp.toPx() }
            val translationY = with(density) { 10.dp.toPx() }
            val cornerRadius = with(density) { 2.dp.toPx() }

            val center =
                when (horizontalAlignment) {
                    Alignment.Start -> arrowPosition
                    Alignment.End -> size.width - arrowPosition
                    else -> size.width / 2f
                }

            translate(center, size.height - oneDp) {
                drawPath(
                    path =
                        Path().apply {
                            moveTo(-translationX, 0f)
                            // Calculate the tangent point coordinates.
                            val arrowLegLength =
                                sqrt(translationX * translationX + translationY * translationY)
                            val tangentPointX = translationY * cornerRadius / arrowLegLength
                            val tangentPointY =
                                translationY - translationX * tangentPointX / translationY
                            lineTo(-tangentPointX, tangentPointY)
                            // Draw a curve connecting the two tangent points via a control point,
                            // forming the rounded tip of the arrow.
                            quadraticTo(
                                0f,
                                translationY + cornerRadius -
                                    (arrowLegLength - cornerRadius) / translationX,
                                tangentPointX,
                                tangentPointY,
                            )
                            lineTo(translationX, 0f)
                        },
                    color = backgroundColor,
                )
            }
        }
        .clip(RoundedCornerShape(28.dp))
        .widthIn(max = 348.dp)
        .background(backgroundColor)
        .padding(16.dp)
}
