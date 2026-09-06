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

import android.service.personalcontext.hint.ContextHint
import android.util.Log
import androidx.annotation.StyleRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toColorLong
import androidx.compose.ui.unit.Constraints
import androidx.core.graphics.toColor
import androidx.core.view.ViewCompat.ScrollAxis
import com.android.personalcontext.ace.client.clientlib.atMost
import com.android.personalcontext.ace.client.clientlib.exactly
import com.android.personalcontext.ace.client.clientlib.unspecified
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionState

/**
 * While [AceEmbeddedInputsUpdateEffect] is in the composition, it reconciles the [modifier] of the
 * [AceEmbeddedSurfaceView] child with the constraints passed down by the parent, and updates the
 * [AceEmbeddedSessionState] with the new inputs.
 */
@Composable
internal fun AceEmbeddedInputsUpdateEffect(
    sessionState: AceEmbeddedSessionState,
    modifier: Modifier,
    hints: Set<ContextHint>,
    backgroundColor: Color,
    @ScrollAxis nestedScrollAxes: Int,
    nestedScrollAxisLocked: Boolean,
    shouldBlur: Boolean,
    @StyleRes themeResourceId: Int,
    onInputsUpdated: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val constraints = this.constraints

        LaunchedEffect(
            constraints,
            hints,
            backgroundColor,
            nestedScrollAxes,
            nestedScrollAxisLocked,
            shouldBlur,
            themeResourceId,
        ) {
            Log.v(
                TAG,
                "AceEmbeddedInputsUpdateEffect hints: $hints, constraints: $constraints, backgroundColor: $backgroundColor, nestedScrollAxes: $nestedScrollAxes, nestedScrollAxisLocked: $nestedScrollAxisLocked,  shouldBlur: $shouldBlur, themeResourceId: $themeResourceId",
            )
            sessionState.update(
                hints = hints,
                width = constraints.widthMeasureSpec,
                height = constraints.heightMeasureSpec,
                backgroundColor = backgroundColor.toColorLong().toColor(),
                nestedScrollAxes = nestedScrollAxes,
                nestedScrollAxisLocked = nestedScrollAxisLocked,
                shouldBlur = shouldBlur,
                themeResourceId = themeResourceId,
            )
            onInputsUpdated()
        }
    }
}

private const val TAG = "AceEmbeddedSurfaceView"

private val Constraints.widthMeasureSpec
    get() =
        when {
            hasBoundedWidth && hasFixedWidth -> this.maxWidth.exactly
            hasBoundedWidth && !hasFixedWidth -> this.maxWidth.atMost
            else -> this.minWidth.unspecified
        }

private val Constraints.heightMeasureSpec
    get() =
        when {
            hasBoundedHeight && hasFixedHeight -> this.maxHeight.exactly
            hasBoundedHeight && !hasFixedHeight -> this.maxHeight.atMost
            else -> this.minHeight.unspecified
        }
