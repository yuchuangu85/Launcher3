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
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope

/**
 * [AceEmbeddedProvider] allows for using embedded ACE to securely render remote UI in a
 * SurfaceView.
 */
@UiThread
interface AceEmbeddedProvider {

    /**
     * Connects to a new embedded ACE session that can render remote UI content. This is a scoped
     * function, so that the session is only active when [session] is invoked, and the session is
     * closed when [session] ends exceptionally.
     *
     * **Using this API**
     *
     * Launch this in a coroutine from a lifecycle-aware [CoroutineScope]. This way, when the user
     * navigates away from that lifecycle component, the scope is cancelled and the session is
     * closed.
     *
     * This function suspends while the session is active, and only ends exceptionally once the
     * session is closed. Use a standard try-catch-finally block to perform cleanup operations after
     * the session is closed.
     *
     * **Handling session events**
     *
     * When the new session is established, the [session] block is invoked to deliver a
     * [SurfacePackage] which represents the successfully established session. When a session fails,
     * an Exception is thrown to report a terminal error. Other callbacks will be invoked upon their
     * corresponding asynchronous event.
     *
     * Calls to all callback functions are context-preserving - their execution context always
     * inherits the caller's [kotlin.coroutines.CoroutineContext]. This means that all callbacks
     * provided to a session function will be invoked on the same dispatcher that called this
     * function.
     *
     * **Making session requests**
     *
     * Use the suspending [session] function to make requests on the open session.
     * [AceEmbeddedSessionScope] is a union interface between [AceEmbeddedSession], which affords
     * operations only valid while the session is established, and [CoroutineScope], which affords
     * launching concurrent child jobs as subtasks of the session.
     *
     * One such session operation is [AceEmbeddedSession.update], which allows clients to provide
     * new hints to the ACE visualizer service.
     *
     * Clients can also use the [session] function for *concurrent decomposition* of work. When any
     * child coroutine in this scope fails, this scope fails, cancelling all the other children and
     * closing the session itself. Likewise, if the session is cancelled or completes for any other
     * reason, the [session] scope is cancelled as well. This makes [session] a good place to host
     * concurrent work that should only run while the session is active.
     *
     * **Keeping the session open**
     *
     * The embedded ACE session remains open while the `suspend fun` [session] block remains active.
     * When the [session] block ends exceptionally, the session is closed. You may start sub-tasks
     * within [session], by calling [kotlinx.coroutines.launch] or other suspending functions. To
     * keep the session open in the absence of sub-tasks, simply call [awaitCancellation] inside the
     * [session] block.
     *
     * **Closing the session**
     *
     * The session will be ended exceptionally when:
     * * The job or scope that invoked [connect] is cancelled via [kotlinx.coroutines.Job.cancel] or
     *   [kotlinx.coroutines.cancel].
     * * The session or remote ACE visualizer service encounters an internal error.
     * * The [session] block throws an exception.
     *
     * @param context The client app's local context.
     * @param onSizeChange Signals that the remote UI has changed in dimensions. Respects the
     *   [AceEmbeddedMeasureSpec] in the request.
     * @param onInsight Signals that the user has clicked on the embedded ACE ui and consented to
     *   data egress.
     * @param session Invoked when the embedded ACE session has been created, and provides a
     *   [SurfacePackage] for the client app's [SurfaceView.setChildSurfacePackage]. This function
     *   is scoped to [AceEmbeddedSessionScope] for making requests on the session and doing other
     *   concurrent work within the scope of a session. The session is closed when this block ends
     *   exceptionally.
     * @throws kotlinx.coroutines.CancellationException Signals that the session has closed due to a
     *   client-side cancel.
     * @throws Exception Signals that the session has closed due to a server-side cancellation or
     *   error.
     */
    suspend fun connect(
        context: Context,
        inputs: AceEmbeddedInputs,
        onSizeChange: (AceEmbeddedUiSize) -> Unit = {},
        onInsight: (ContextInsight) -> Unit = {},
        session: suspend AceEmbeddedSessionScope.(SurfacePackage) -> Nothing,
    ): Nothing

    /**
     * Sets the [SurfacePackage] received from the ACE visualizer service on the [SurfaceView].
     *
     * Defined as part of the interface so it can be faked for testing.
     */
    fun SurfaceView.setEmbeddedSurfacePackage(surfacePackage: SurfacePackage)
}

/**
 * Scoped environment provided by [AceEmbeddedProvider] when a new session is created. This
 * environment is a coroutine scope that also provides access to a [AceEmbeddedSession] to handle
 * session operations.
 */
interface AceEmbeddedSessionScope : AceEmbeddedSession, CoroutineScope

/** Defines operations that are only valid while the embedded ACE session is active. */
interface AceEmbeddedSession {

    /**
     * Publishes an update on the connected embedded ACE session.
     *
     * @see AceEmbeddedProvider.connect
     */
    suspend fun update(inputs: AceEmbeddedInputs)
}
