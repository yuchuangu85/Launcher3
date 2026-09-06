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
package com.android.personalcontext.ace.client.clientsdk.state

import android.view.SurfaceControlViewHost.SurfacePackage
import androidx.compose.runtime.Stable
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Error
import com.android.personalcontext.ace.client.clientsdk.state.Phase.Terminal
import com.android.personalcontext.ace.client.clientsdk.state.Phase.Transient

/**
 * Visibility state of an embedded ACE session. [ordinal] and [error] are useful for logging.
 *
 * As a sanity check, successive state ordinals should be monotonically increasing, except when
 * reset back to 0.
 *
 * [phase] may be used by the UI to display a loading composable, like a progress bar.
 */
@Stable
sealed class AceEmbeddedUiVisibility {

    internal abstract val ordinal: Int

    internal abstract val phase: Phase

    /**
     * The embedded ACE session is not shown, nor is it in the process of being shown.
     *
     * This is a stable state, and does not indicate an error. This state does not take up any space
     * except optionally to render a placeholder.
     */
    sealed class Hidden(override val ordinal: Int = 0, override val phase: Phase = Terminal) :
        AceEmbeddedUiVisibility() {

        /* Before connect(). */
        object Uninitialized : Hidden()

        /* Before connect(). */
        object ClientClosed : Hidden()

        /* Before connect(). */
        object ServerClosed : Hidden()
    }

    /**
     * The embedded ACE session is in the process of being shown, but is not shown yet and does not
     * know if it will successfully show or not.
     *
     * This is a transient state, and is expected to be followed by another state. This state does
     * not take up any space except optionally to render a loading indicator.
     */
    sealed class Pending(override val ordinal: Int, override val phase: Phase = Transient) :
        AceEmbeddedUiVisibility() {

        /* During connect(). */
        object PendingInputs : Pending(1)

        /* During connect(). */
        object Connecting : Pending(2)
    }

    /**
     * The embedded ACE session is not shown, due to an error. The error can be expected (as in the
     * case where the embedded ACE session reports that there is nothing to show), or unexpected (as
     * in the case of any server error).
     *
     * This is a stable state. This state does not take up any space except optionally to render an
     * error indicator.
     *
     * External observers may be interested in handling this state, perhaps to run some custom retry
     * logic or to hide the feature.
     */
    sealed class Error(override val ordinal: Int, override val phase: Phase = Terminal) :
        AceEmbeddedUiVisibility() {

        abstract val error: Throwable

        /* After connect(). */
        object NotAvailable : Error(3) {
            override val error: Throwable = IllegalStateException("SessionNotAvailable")
        }

        /* During connect(). */
        data class ClientError(override val error: Throwable) : Error(0) {
            override fun toString() = super.toString()
        }

        /* During connect(). */
        data class ServerError(override val error: Throwable) : Error(0) {
            override fun toString() = super.toString()
        }
    }

    /**
     * The embedded ACE session is being shown, actively taking up some amount of space requested by
     * the embedded ACE session.
     *
     * This is a stable meta-state.
     */
    sealed class Shown(override val ordinal: Int, override val phase: Phase) :
        AceEmbeddedUiVisibility() {

        abstract val surfacePackage: SurfacePackage

        /* After connect(). */
        data class Connected(override val surfacePackage: SurfacePackage) : Shown(6, Terminal) {
            override fun toString() = super.toString()
        }

        /* After connect(). */
        data class Updating(override val surfacePackage: SurfacePackage) : Shown(6, Transient) {
            override fun toString() = super.toString()
        }
    }

    /**
     * The embedded ACE session is no longer being shown, but is expected to transition to being
     * Shown again. Unlike [Hidden.ClientClosed], this state actively takes up the same amount of
     * space as when the embedded ACE session was previously shown.
     *
     * This is a stable state.
     *
     * For example, the SurfaceView has scrolled off the screen and is detached from the window by
     * the framework, requiring a new embedded ACE session to be connected when it is re-attached to
     * the window.
     */
    sealed class Suspended(override val ordinal: Int = 0, override val phase: Phase = Terminal) :
        AceEmbeddedUiVisibility() {

        /* After connect(). */
        object ClientCancelled : Suspended(0, Terminal)
    }

    /**
     * The embedded ACE session encountered an unexpected interruption but can automatically attempt
     * to recover or reconnect.
     *
     * This is a transient state that bridges an interrupted session to a new connection attempt.
     */
    sealed class Retryable(override val ordinal: Int = 0, override val phase: Phase = Transient) :
        AceEmbeddedUiVisibility() {
        /*Empty for now.*/
    }

    override fun toString(): String {
        val qualifiedName = this::class.qualifiedName ?: "Unknown"
        val name = qualifiedName.split(".").takeLast(2).joinToString(".")
        val suffix = if (phase == Transient) "..." else ""
        return "$ordinal. $name$suffix ${error?.logString.orEmpty()}"
    }
}

private val AceEmbeddedUiVisibility.error: Throwable?
    get() =
        when (this) {
            is Error -> this.error
            else -> null
        }

private val Throwable.logString: String
    get() = "${this::class.simpleName}: $localizedMessage"

/**
 * The phase that a [AceEmbeddedUiVisibility] state is in, whether it's transient or terminal state.
 */
enum class Phase {
    /** Will be followed by another [AceEmbeddedUiVisibility] state. */
    Transient,

    /**
     * Will not be followed by another [AceEmbeddedUiVisibility] state unless acted on externally.
     */
    Terminal,
}

/** Returns whether a [AceEmbeddedUiVisibility] state is in a transient state. */
val AceEmbeddedUiVisibility.isTransient
    get() = this.phase == Transient

/** Returns whether a [AceEmbeddedUiVisibility] state is in a terminal state. */
val AceEmbeddedUiVisibility.isTerminal
    get() = this.phase == Terminal
