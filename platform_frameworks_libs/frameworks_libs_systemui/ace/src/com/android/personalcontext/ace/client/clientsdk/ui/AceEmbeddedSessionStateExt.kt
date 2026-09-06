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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedProviderImpl
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionState
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionStateImpl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Create a new [AceEmbeddedSessionState] to allow observing and controlling an embedded ACE
 * session. This must be passed into a single [AceEmbeddedSurfaceView].
 *
 * @param invalidatePreviousHintOnUpdate Whether this DUI session should automatically publish a
 *   [android.service.personalcontext.hint.HintInvalidationHint] when hints are updated.
 */
@Composable
fun rememberSessionState(
    timeout: Duration = 30.seconds,
    invalidatePreviousHintOnUpdate: Boolean = false,
): AceEmbeddedSessionState {
    val scope = rememberCoroutineScope()

    return remember {
        val provider =
            AceEmbeddedProviderImpl(
                backgroundScope = scope,
                timeout = timeout,
                invalidatePreviousHintOnUpdate = invalidatePreviousHintOnUpdate,
            )
        AceEmbeddedSessionStateImpl(coroutineScope = scope, provider = provider)
    }
}
