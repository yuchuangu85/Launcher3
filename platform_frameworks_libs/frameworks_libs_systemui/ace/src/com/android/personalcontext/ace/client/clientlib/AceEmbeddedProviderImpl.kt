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

import android.content.Context
import android.service.personalcontext.insight.ContextInsight
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.SurfaceView
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope

/**
 * Implementation of [AceEmbeddedProvider].
 *
 * @param backgroundScope An optional [CoroutineScope] for background cleanup tasks.
 */
class AceEmbeddedProviderImpl(
    private val backgroundScope: CoroutineScope? = null,
    private val timeout: Duration = 30.seconds,
    private val invalidatePreviousHintOnUpdate: Boolean = false,
) : AceEmbeddedProvider {

    override suspend fun connect(
        context: Context,
        inputs: AceEmbeddedInputs,
        onSizeChange: (AceEmbeddedUiSize) -> Unit,
        onInsight: (ContextInsight) -> Unit,
        session: suspend AceEmbeddedSessionScope.(SurfacePackage) -> Nothing,
    ): Nothing {
        AceEmbeddedSessionImpl(backgroundScope, timeout, invalidatePreviousHintOnUpdate)
            .withConnection(context, inputs, onSizeChange, onInsight) { session(it) }
    }

    override fun SurfaceView.setEmbeddedSurfacePackage(surfacePackage: SurfacePackage) =
        setChildSurfacePackage(surfacePackage)
}
