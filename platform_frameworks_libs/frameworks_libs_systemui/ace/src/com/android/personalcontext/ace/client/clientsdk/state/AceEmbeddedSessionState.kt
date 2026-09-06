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
@file:Suppress("FlaggedApi", "NewApi")

package com.android.personalcontext.ace.client.clientsdk.state

import android.content.Context
import android.graphics.Color
import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.insight.ContextInsight
import android.util.Log
import androidx.annotation.StyleRes
import androidx.compose.runtime.Stable
import androidx.core.view.ViewCompat.ScrollAxis
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedInputs
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedMeasureSpec
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedProvider
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedUiSize
import com.android.personalcontext.ace.client.clientlib.ServerSideCloseException
import com.android.personalcontext.ace.client.clientlib.SessionErrorException
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Error
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Hidden
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Pending
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Retryable
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Shown
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Suspended
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The embedded ACE session state of a [AceEmbeddedSurfaceView], allows for observing and
 * controlling the embedded ACE session.
 */
@Stable
interface AceEmbeddedSessionState {

    /** Ui state of the embedded ACE session. */
    val uiStateFlow: StateFlow<AceEmbeddedUiState>

    /** Insights emitted from the embedded ACE session. */
    val insightFlow: SharedFlow<ContextInsight>

    /**
     * This is typically called by [AceEmbeddedSurfaceView] when it first enters the composition.
     * Only call this manually if you want low-level control over the embedded ACE session.
     *
     * Calls [AceEmbeddedProvider.connect] to connect to a embedded ACE session. A SurfaceView does
     * not have to be attached to the window at this time.
     *
     * Observe the flows on this interface for embedded ACE events while the session is connected.
     *
     * The [AceEmbeddedUiVisibility] state will typically change to `Pending.PendingInputs`, then
     * `Pending.Connecting`, then `Shown.Available` and `Shown.Connected`, but can also sometimes
     * change to `Shown.ClientCancelled`, `Error.ServerCancelled` or `Retryable.ServerError` in
     * exceptional cases.
     */
    fun connect(context: Context)

    /**
     * This is typically called by [AceEmbeddedSurfaceView] when its parameters update. Only call
     * this manually if you want low-level control over the embedded ACE session.
     *
     * Updates a connected embedded ACE session with the given inputs.
     *
     * Generally, external callers should prefer to pass inputs into the [AceEmbeddedSurfaceView]
     * composable directly instead.
     */
    fun update(
        hints: Set<ContextHint>,
        width: AceEmbeddedMeasureSpec,
        height: AceEmbeddedMeasureSpec,
        backgroundColor: Color,
        @ScrollAxis nestedScrollAxes: Int,
        nestedScrollAxisLocked: Boolean,
        shouldBlur: Boolean,
        @StyleRes themeResourceId: Int,
    )

    /**
     * This is typically called by [AceEmbeddedSurfaceView] when its SurfaceView leaves the
     * composition, eg: when it is scrolled off-screen and recycled. Only call this manually if you
     * want low-level control over the embedded ACE session.
     *
     * This disconnects from the embedded ACE session. Can be followed with [connect] when the
     * SurfaceView scrolls back on-screen.
     *
     * The [AceEmbeddedUiVisibility] state may change to `Shown.ClientCancelled`.
     */
    fun cancel(onCancelled: () -> Unit = {})

    /**
     * This is not called by [AceEmbeddedSurfaceView], but can be called manually to change the
     * state back to `Hidden` while keeping [AceEmbeddedSurfaceView] in your composition.
     *
     * It's often simpler to just remove the [AceEmbeddedSurfaceView] from your composition rather
     * than calling this method.
     *
     * This also disconnects from the embedded ACE. Can be followed with [connect].
     *
     * The [AceEmbeddedUiVisibility] state will change to `Hidden.Closed`.
     */
    fun close()
}

class AceEmbeddedSessionStateImpl(
    private val coroutineScope: CoroutineScope,
    private val provider: AceEmbeddedProvider,
) : AceEmbeddedSessionState {

    private val inputsFlow =
        MutableSharedFlow<AceEmbeddedInputs>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private val _uiStateFlow = MutableStateFlow(AceEmbeddedUiState())
    override val uiStateFlow: StateFlow<AceEmbeddedUiState> = _uiStateFlow.asStateFlow()

    private val _insightFlow =
        MutableSharedFlow<ContextInsight>(
            replay = 0,
            extraBufferCapacity = INSIGHTS_FLOW_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val insightFlow: SharedFlow<ContextInsight> = _insightFlow.asSharedFlow()

    private var job: Job? = null

    override fun connect(context: Context) {
        when (_uiStateFlow.value.visibility) {
            is Hidden -> {}
            is Pending -> return
            is Error -> {}
            is Shown -> return
            is Suspended -> {}
            is Retryable -> {}
        }

        if (job != null) {
            Log.v(TAG, "connect() ignored: job not null.")
            return
        }

        Log.i(TAG, "[AceEmbeddedLifecycle] Client-sdk running connect()")
        _uiStateFlow.update { state -> state.copy(visibility = Pending.PendingInputs) }

        job =
            coroutineScope.launch(CoroutineName("prepareDelegatedUiSession()")) {
                try {
                    val inputs = inputsFlow.first()

                    _uiStateFlow.update { state -> state.copy(visibility = Pending.Connecting) }

                    Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk connecting: $inputs")
                    provider.connect(
                        context = context,
                        inputs = inputs,
                        onSizeChange = { _uiStateFlow.update { state -> state.copy(size = it) } },
                        onInsight = _insightFlow::tryEmit,
                    ) { surfacePackage ->
                        Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk connect() is connected.")
                        _uiStateFlow.update { state ->
                            state.copy(visibility = Shown.Connected(surfacePackage))
                        }

                        // We want to call update() on strictly new values of inputsFlow. We drop(1)
                        // to
                        // avoid using the same inputs already passed to connect(). But in
                        // that case that inputsFlow already has a new value, then we don't want to
                        // drop that
                        // value, so we end up with these flow operators.
                        inputsFlow
                            .onStart { emit(inputs) }
                            .distinctUntilChanged()
                            .drop(1)
                            .collectLatest { update ->
                                Log.d(TAG, "[AceEmbeddedLifecycle] Client-sdk updating: $update")

                                _uiStateFlow.update { state ->
                                    state.copy(visibility = Shown.Updating(surfacePackage))
                                }
                                update(inputs = update)

                                Log.d(
                                    TAG,
                                    "[AceEmbeddedLifecycle] Client-sdk update() is complete.",
                                )
                                _uiStateFlow.update { state ->
                                    state.copy(visibility = Shown.Connected(surfacePackage))
                                }
                            }

                        awaitCancellation()
                    }
                } catch (e: Throwable) {
                    val cancellationCause = (e as? CancellationException)?.cause

                    when {
                        // Server-side close.
                        cancellationCause is ServerSideCloseException -> {
                            val session = cancellationCause.session
                            Log.i(
                                TAG,
                                "[AceEmbeddedLifecycle] Client-sdk connect() received server-side close: $session.",
                            )

                            _uiStateFlow.update { state ->
                                state.copy(visibility = Hidden.ServerClosed)
                            }
                        }

                        // Server-side error.
                        cancellationCause is SessionErrorException -> {
                            val serverError = cancellationCause.cause
                            Log.w(
                                TAG,
                                "[AceEmbeddedLifecycle] Client-sdk connect() received server-side error: $serverError.",
                            )

                            _uiStateFlow.update { state ->
                                state.copy(visibility = Error.ServerError(serverError))
                            }
                        }

                        // Client-side cancel.
                        e is CancellationException -> {
                            Log.i(
                                TAG,
                                "[AceEmbeddedLifecycle] Client-sdk connect() received client-side cancel: $cancellationCause.",
                            )

                            _uiStateFlow.update { state ->
                                state.copy(visibility = Suspended.ClientCancelled)
                            }
                        }

                        // Client-side error.
                        else -> {
                            Log.e(
                                TAG,
                                "[AceEmbeddedLifecycle] Client-sdk connect() received client-side error: $e.",
                            )

                            _uiStateFlow.update { state ->
                                state.copy(visibility = Error.ClientError(e))
                            }
                        }
                    }

                    if (_uiStateFlow.value.visibility is Error) {
                        _uiStateFlow.update { state -> state.copy(size = AceEmbeddedUiSize(0, 0)) }
                    }

                    if (e is CancellationException) {
                        throw e
                    }
                } finally {
                    job = null
                }
            }
    }

    override fun update(
        hints: Set<ContextHint>,
        width: AceEmbeddedMeasureSpec,
        height: AceEmbeddedMeasureSpec,
        backgroundColor: Color,
        @ScrollAxis nestedScrollAxes: Int,
        nestedScrollAxisLocked: Boolean,
        shouldBlur: Boolean,
        @StyleRes themeResourceId: Int,
    ) {
        Log.i(TAG, "[AceEmbeddedLifecycle] Client-sdk running update()")

        val unused =
            inputsFlow.tryEmit(
                AceEmbeddedInputs(
                    hints = hints,
                    width = width,
                    height = height,
                    backgroundColor = backgroundColor,
                    nestedScrollAxes = nestedScrollAxes,
                    nestedScrollAxisLocked = nestedScrollAxisLocked,
                    shouldBlur = shouldBlur,
                    themeResourceId = themeResourceId,
                )
            )
    }

    override fun cancel(onCancelled: () -> Unit) {
        Log.i(TAG, "[AceEmbeddedLifecycle] Client-sdk running reset()")
        job.cancelAndNotify(onCancelled)
        job = null
    }

    override fun close() {
        Log.i(TAG, "[AceEmbeddedLifecycle] Client-sdk running close()")
        cancel { _uiStateFlow.update { state -> state.copy(visibility = Hidden.ClientClosed) } }
    }

    private fun Job?.cancelAndNotify(onCancelled: () -> Unit) {
        if (this == null) {
            onCancelled()
            return
        }
        invokeOnCompletion { onCancelled() }
        cancel()
    }

    companion object {
        private const val TAG = "AceEmbeddedSessionState"
        private const val INSIGHTS_FLOW_CAPACITY = 100
    }
}

/**
 * Ui state for the embedded ACE session.
 *
 * @property visibility The visibility state of the embedded ACE session. Observe this to adjust
 *   your native UI to match the state of the [AceEmbeddedSurfaceView].
 * @property size The current remote size of the embedded ACE UI, or `null` if not yet determined.
 */
data class AceEmbeddedUiState(
    val visibility: AceEmbeddedUiVisibility = Hidden.Uninitialized,
    val size: AceEmbeddedUiSize? = null,
)
