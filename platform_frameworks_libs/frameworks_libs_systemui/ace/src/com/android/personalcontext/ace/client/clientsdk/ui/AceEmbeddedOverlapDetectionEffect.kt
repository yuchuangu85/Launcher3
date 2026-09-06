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

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.toSize
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior.SetZOrderBehind
import com.android.personalcontext.ace.client.clientsdk.utils.windowBounds

/**
 * While [AceEmbeddedOverlapDetectionEffect] is in the composition, it checks whether the
 * [AceEmbeddedSurfaceView]'s bounds intersect with any of the overlap zones defined by
 * [overlapState], and sets the result onto [AceEmbeddedOverlapStateImpl.overlapDistances].
 */
@Composable
internal fun AceEmbeddedOverlapDetectionEffect(
    modifier: Modifier,
    overlapState: AceEmbeddedOverlapState,
) {
    val overlapState = overlapState as AceEmbeddedOverlapStateImpl

    val context = LocalContext.current
    val windowBounds = remember(context) { context.windowBounds().toComposeRect() }

    val overlapBehavior = overlapState.overlapBehavior
    val overlapInsets = overlapState.overlapInsets

    if (overlapBehavior is SetZOrderBehind) {
        Box(
            modifier =
                modifier.onGloballyPositioned { layoutCoordinates ->
                    Log.v(
                        TAG,
                        "AceEmbeddedOverlapDetectionEffect windowBounds: $windowBounds, overlapInsets: $overlapInsets",
                    )

                    val childBounds = layoutCoordinates.unclippedBoundsInWindow()
                    val safeBounds = windowBounds.insetBy(overlapInsets)

                    overlapState.isOverlapping = childBounds.exceeds(safeBounds)

                    Log.v(
                        TAG,
                        "AceEmbeddedOverlapDetectionEffect childBounds: $childBounds, isOverlapping: ${overlapState.isOverlapping}",
                    )
                }
        )
    } else {
        // Run once when overlapBehavior is not SetZOrderBehind.
        LaunchedEffect(Unit) { overlapState.isOverlapping = false }
    }
}

private const val TAG = "AceEmbeddedSurfaceView"

/** Returns unclipped bounds for the [LayoutCoordinates]. */
fun LayoutCoordinates.unclippedBoundsInWindow(): Rect {
    return Rect(positionInWindow(), size.toSize())
}

/** Returns clipped bounds for the [LayoutCoordinates]. */
fun LayoutCoordinates.clippedBoundsInWindow(): Rect {
    return boundsInWindow()
}
