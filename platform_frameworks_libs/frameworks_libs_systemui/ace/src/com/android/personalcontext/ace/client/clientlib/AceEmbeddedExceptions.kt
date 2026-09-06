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

import android.service.personalcontext.embedded.InsightSurfaceSession

/**
 * When the server wants to close an open session, it will send the client a
 * [com.android.personalcontext.ace.client.prototype.serversideclose.ServerSideCloseInsight].
 */
data class ServerSideCloseException(val session: InsightSurfaceSession) :
    Exception("Session was server-side closed: $session")

/**
 * When the server encounters an error, possibly before the session is created, it will signal the
 * [android.service.personalcontext.embedded.InsightSurfaceClient.ClientCallback.onError] callback.
 */
data class SessionErrorException(override val cause: Exception) :
    Exception("Session encountered error: $cause", cause)
