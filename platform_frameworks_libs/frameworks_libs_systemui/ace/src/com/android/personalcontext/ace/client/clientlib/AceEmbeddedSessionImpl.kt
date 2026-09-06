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

package com.android.personalcontext.ace.client.clientlib

import android.content.Context
import android.os.OutcomeReceiver
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.embedded.ClientUpdateException
import android.service.personalcontext.embedded.InsightSurfaceClient
import android.service.personalcontext.embedded.InsightSurfaceClientUpdate
import android.service.personalcontext.embedded.InsightSurfaceSession
import android.service.personalcontext.embedded.InsightSurfaceSessionException
import android.service.personalcontext.hint.HintInvalidationHint
import android.service.personalcontext.insight.ContextInsight
import android.util.Log
import android.view.SurfaceControlViewHost.SurfacePackage
import com.android.personalcontext.ace.client.prototype.PrototypeInsightUtils.isPrototypeInsight
import com.android.personalcontext.ace.client.prototype.serversideclose.ServerSideCloseInsight
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal class AceEmbeddedSessionImpl(
    val backgroundScope: CoroutineScope?,
    val timeout: Duration,
    val invalidatePreviousHintOnUpdate: Boolean,
) : AceEmbeddedSession {

    private lateinit var _context: Context
    private lateinit var _client: InsightSurfaceClient
    private lateinit var _session: InsightSurfaceSession

    private var cachedInputs: AceEmbeddedInputs? = null
    private var updateComplete: CompletableDeferred<Unit>? = null

    suspend fun withConnection(
        context: Context,
        inputs: AceEmbeddedInputs,
        onSizeChange: (AceEmbeddedUiSize) -> Unit,
        onInsight: (ContextInsight) -> Unit,
        block: suspend AceEmbeddedSessionScope.(SurfacePackage) -> Nothing,
    ): Nothing = coroutineScope {
        Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib connect($inputs)")

        _context = context
        checkIsPersonalContextEnabled()

        cachedInputs = inputs

        val surfacePackageReady = CompletableDeferred<SurfacePackage>()

        _client =
            InsightSurfaceClient.Builder(context)
                .setMeasureSpecs(inputs.width.value, inputs.height.value)
                .setBackgroundColor(inputs.backgroundColor)
                .setNestedScrollAxes(inputs.nestedScrollAxes)
                .setNestedScrollAxisLocked(inputs.nestedScrollAxisLocked)
                .setShouldBlur(inputs.shouldBlur)
                .setThemeResourceId(inputs.themeResourceId)
                .addReceiver { insight ->
                    if (insight.isPrototypeInsight<ServerSideCloseInsight>()) {
                        cancel("ServerSideCloseInsight", ServerSideCloseException(_session))
                        return@addReceiver true
                    }
                    onInsight(insight)
                    true
                }
                .build()

        try {
            Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib client.register()")
            _client.register(
                null,
                object : InsightSurfaceClient.ClientCallback {

                    override fun onSessionCreated(session: InsightSurfaceSession) {
                        Log.d(TAG, "[AceEmbeddedLifecycle] Client-lib register → onSessionCreated.")

                        this@AceEmbeddedSessionImpl._session = session

                        val surfacePackage = session.surfacePackage
                        if (surfacePackage != null) {
                            surfacePackageReady.complete(surfacePackage)
                        } else {
                            surfacePackageReady.completeExceptionally(
                                IllegalStateException("Session created with null SurfacePackage.")
                            )
                        }
                    }

                    override fun onSessionUpdated(session: InsightSurfaceSession) {
                        Log.d(TAG, "[AceEmbeddedLifecycle] Client-lib register → onSessionUpdated.")
                        updateComplete?.complete(Unit)
                    }

                    override fun onSessionDestroyed(session: InsightSurfaceSession) {
                        Log.d(
                            TAG,
                            "[AceEmbeddedLifecycle] Client-lib register → onSessionDestroyed: $session.",
                        )
                    }

                    override fun onError(exception: InsightSurfaceSessionException) {
                        Log.d(
                            TAG,
                            "[AceEmbeddedLifecycle] Client-lib register → onError: $exception.",
                        )

                        cancel("onError", SessionErrorException(exception))
                    }

                    override fun onSizeChanged(width: Int, height: Int) {
                        Log.d(
                            TAG,
                            "[AceEmbeddedLifecycle] Client-lib register → onSizeChanged(width: $width, height: $height).",
                        )

                        onSizeChange(AceEmbeddedUiSize(width, height))
                    }
                },
            )

            // TODO: b/485778056 - Workaround for the ACE bug when you call client.publishHints()
            // too
            //   quickly after client.register().
            delay(20.milliseconds)
            Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib client.publishHints()")
            _client.publishHints(inputs.hints)

            Log.d(TAG, "[AceEmbeddedLifecycle] Client-lib surfacePackageReady.await()")
            val surfacePackage =
                withTimeoutOrNull(timeout) { surfacePackageReady.await() }
                    ?: throw TimeoutException("Timed out waiting for SurfacePackage.")

            Log.d(TAG, "[AceEmbeddedLifecycle] Client-lib received surfacePackage: $surfacePackage")
            AceEmbeddedSessionScopeImpl(scope = this).block(surfacePackage)
        } finally {
            _client.unregisterGracefully(delay = 100.milliseconds)
        }
    }

    override suspend fun update(inputs: AceEmbeddedInputs) {
        Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib update($inputs)")

        checkIsPersonalContextEnabled()

        val previousInputs = cachedInputs

        if (inputs == previousInputs) {
            Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib update skipped (no changes).")
            return
        }

        cachedInputs = inputs
        updateComplete = CompletableDeferred()

        val hintsChanged = inputs.hints != previousInputs?.hints
        val paramsChanged =
            previousInputs == null || inputs.copy(hints = previousInputs.hints) != previousInputs

        val result =
            withTimeoutOrNull(timeout) {
                coroutineScope {
                    if (hintsChanged) {
                        launch { updateHints(inputs, previousInputs) }
                    }

                    if (paramsChanged) {
                        launch { updateParams(inputs, previousInputs) }
                    }
                }
            }

        if (result != null) {
            Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib update() completed successfully.")
        } else {
            Log.w(TAG, "[AceEmbeddedLifecycle] Client-lib update() timed out.")
        }

        updateComplete = null
    }

    private suspend fun updateHints(inputs: AceEmbeddedInputs, previousInputs: AceEmbeddedInputs?) {
        Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib updateHints()")

        val invalidationHints =
            if (invalidatePreviousHintOnUpdate && previousInputs != null) {
                previousInputs.hints.map { HintInvalidationHint.Builder(it).build() }
            } else {
                emptyList()
            }

        val invalidationLogSuffix =
            if (invalidationHints.isNotEmpty()) {
                " + ${invalidationHints.size} x HintInvalidationHint"
            } else {
                ""
            }

        Log.i(
            TAG,
            "[AceEmbeddedLifecycle] Client-lib client.publishHints([${inputs.hints.size} items redacted$invalidationLogSuffix])",
        )
        _client.publishHints((inputs.hints + invalidationHints).toSet())

        Log.d(TAG, "[AceEmbeddedLifecycle] Client-lib updateComplete.await()")
        updateComplete?.await()
    }

    private suspend fun updateParams(
        inputs: AceEmbeddedInputs,
        previousInputs: AceEmbeddedInputs?,
    ) {
        Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib updateParams()")

        @Suppress("CheckReturnValue")
        val update =
            InsightSurfaceClientUpdate.Builder()
                .apply {
                    if (inputs.backgroundColor != previousInputs?.backgroundColor) {
                        setBackgroundColor(inputs.backgroundColor)
                    }
                    if (inputs.width != previousInputs?.width) {
                        setMeasureSpecWidth(inputs.width.value)
                    }
                    if (inputs.height != previousInputs?.height) {
                        setMeasureSpecHeight(inputs.height.value)
                    }
                    if (inputs.nestedScrollAxes != previousInputs?.nestedScrollAxes) {
                        setNestedScrollAxes(inputs.nestedScrollAxes)
                    }
                    if (inputs.nestedScrollAxisLocked != previousInputs?.nestedScrollAxisLocked) {
                        setNestedScrollAxisLocked(inputs.nestedScrollAxisLocked)
                    }
                    if (inputs.shouldBlur != previousInputs?.shouldBlur) {
                        setShouldBlur(inputs.shouldBlur)
                    }
                    if (inputs.themeResourceId != previousInputs?.themeResourceId) {
                        setThemeResourceId(inputs.themeResourceId)
                    }
                }
                .build()

        suspendCancellableCoroutine { cont ->
            Log.i(
                TAG,
                "[AceEmbeddedLifecycle] Client-lib session.update(): ${update.toLogString()}",
            )
            _session.update(
                update,
                object : OutcomeReceiver<InsightSurfaceClientUpdate, ClientUpdateException> {
                    override fun onResult(update: InsightSurfaceClientUpdate?) {
                        Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib updateParams → onResult.")
                        cont.resume(Unit)
                    }

                    override fun onError(error: ClientUpdateException) {
                        Log.w(
                            TAG,
                            "[AceEmbeddedLifecycle] Client-lib updateParams → onError: $error",
                        )
                        cont.resume(Unit)
                    }
                },
            )
        }
    }

    private fun checkIsPersonalContextEnabled() {
        val personalContextManager = _context.getSystemService(PersonalContextManager::class.java)
        val packageName = _context.packageName

        check(personalContextManager.isEnabled) { "PersonalContextManager.isEnabled check failed" }
        check(personalContextManager.isPersonalContextModeEnabled(packageName)) {
            "PersonalContextManager.isPersonalContextModeEnabled($packageName) check failed"
        }
    }

    /**
     * [InsightSurfaceClient.unregister] immediately disconnects the session. If the [SurfaceView]
     * happens to be in z-below mode, this will render a black box without giving the UI a chance to
     * react to the session disconnection.
     *
     * This extension function allows us to delay the unregister() call, allowing the UI to detach
     * the SurfaceView from the view hierarchy and preventing the black box from being shown.
     */
    private fun InsightSurfaceClient.unregisterGracefully(delay: Duration) {
        if (backgroundScope?.isActive == true) {
            backgroundScope.launch(NonCancellable) {
                delay(delay)

                Log.i(TAG, "[AceEmbeddedLifecycle] Client-lib client.unregister()")
                unregister()
            }
        } else {
            unregister()
        }
    }

    private fun InsightSurfaceClientUpdate.toLogString(): String {
        val updates = buildList {
            fun addIfUpdated(key: String, label: String) {
                if (hasUpdate(key)) add(label)
            }

            addIfUpdated(
                InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_WIDTH,
                "measureSpecWidth=$measureSpecWidth",
            )
            addIfUpdated(
                InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_HEIGHT,
                "measureSpecHeight=$measureSpecHeight",
            )
            addIfUpdated(
                InsightSurfaceClientUpdate.KEY_BACKGROUND_COLOR,
                "backgroundColor=${backgroundColor?.let { "#${Integer.toHexString(it.toArgb())}" }}",
            )
            addIfUpdated(
                InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXES,
                "nestedScrollAxes=$nestedScrollAxes",
            )
            addIfUpdated(
                InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXIS_LOCKED,
                "nestedScrollAxisLocked=$isNestedScrollAxisLocked",
            )
            addIfUpdated(InsightSurfaceClientUpdate.KEY_SHOULD_BLUR, "shouldBlur=${shouldBlur()}")
            addIfUpdated(
                InsightSurfaceClientUpdate.KEY_THEME_RESOURCE_NAME,
                "themeResourceId=$themeResourceId",
            )
            addIfUpdated(
                InsightSurfaceClientUpdate.KEY_CONFIGURATION,
                "configuration=$configuration",
            )
        }

        return updates.joinToString(separator = ", ", prefix = "{", postfix = "}").ifEmpty {
            "no changes"
        }
    }

    companion object {
        private const val TAG = "AceEmbeddedSessionImpl"
    }

    private inner class AceEmbeddedSessionScopeImpl(scope: CoroutineScope) :
        AceEmbeddedSessionScope, AceEmbeddedSession by this, CoroutineScope by scope
}
