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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionState
import com.android.personalcontext.ace.client.prototype.PrototypeInsightUtils.toPrototypeInsight
import com.android.personalcontext.ace.client.prototype.embeddedscroll.EmbeddedScrollInsight
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_DELTA
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_START
import com.android.personalcontext.ace.common.EmbeddedScrollEventType.SCROLL_STOP
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * While [AceEmbeddedScrollDispatchEffect] is in the composition, it collects the DUI session's
 * nested scroll and nested fling events, and dispatches them to the composition.
 */
@Composable
internal fun AceEmbeddedScrollDispatchEffect(sessionState: AceEmbeddedSessionState) {
    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            // No-op by design, since we only care about dispatching the nested scroll event.
        }
    }

    Box(modifier = Modifier.nestedScroll(nestedScrollConnection, nestedScrollDispatcher))

    LaunchedEffect(Unit) {
        launch {
            var flingJob: Job? = null

            sessionState.insightFlow
                .mapNotNull { it.toPrototypeInsight<EmbeddedScrollInsight>() }
                .collect { event ->
                    when (event.type) {
                        SCROLL_START -> {
                            nestedScrollDispatcher.handleScrollStart(event)
                        }
                        SCROLL_DELTA -> {
                            nestedScrollDispatcher.handleScrollDelta(event)
                        }
                        SCROLL_STOP -> {
                            flingJob?.cancel()
                            flingJob = nestedScrollDispatcher.handleScrollStop(event)
                        }
                    }
                }
        }
    }
}

private fun NestedScrollDispatcher.handleScrollStart(event: EmbeddedScrollInsight) {
    Log.v(TAG, "AceEmbeddedScrollDispatchEffect EmbeddedScrollStart: $event")

    // Cancel any ongoing fling animations.
    coroutineScope.coroutineContext.cancelChildren()
}

private fun NestedScrollDispatcher.handleScrollDelta(event: EmbeddedScrollInsight) {
    Log.v(TAG, "AceEmbeddedScrollDispatchEffect EmbeddedScrollDelta: $event")

    // Ask parents first if they want to pre consume.
    val dx = event.x
    val dy = event.y

    if (dx != 0f || dy != 0f) {
        val parentsConsumed =
            dispatchPreScroll(
                available = Offset(x = dx, y = dy),
                source = NestedScrollSource.UserInput,
            )

        // Dispatch as a post scroll what's left after pre-scroll.
        dispatchPostScroll(
            consumed = Offset.Zero,
            available = Offset(x = dx - parentsConsumed.x, y = dy - parentsConsumed.y),
            source = NestedScrollSource.UserInput,
        )
    }
}

private fun NestedScrollDispatcher.handleScrollStop(event: EmbeddedScrollInsight): Job? {
    Log.v(TAG, "AceEmbeddedScrollDispatchEffect EmbeddedScrollStop: $event")

    // Ask parents first if they want to pre consume.
    val velocityX = event.x
    val velocityY = event.y

    if (velocityX == 0f && velocityY == 0f) return null

    return coroutineScope.launch {
        val parentsConsumed = dispatchPreFling(available = Velocity(x = velocityX, y = velocityY))

        // Dispatch as a post fling what's left after pre-fling.
        dispatchPostFling(
            consumed = Velocity.Zero,
            available =
                Velocity(x = velocityX - parentsConsumed.x, y = velocityY - parentsConsumed.y),
        )
    }
}

private const val TAG = "AceEmbeddedSurfaceView"
