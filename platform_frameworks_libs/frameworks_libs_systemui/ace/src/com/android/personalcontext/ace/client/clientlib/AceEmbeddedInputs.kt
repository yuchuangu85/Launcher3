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

import android.graphics.Color
import android.service.personalcontext.hint.ContextHint
import androidx.annotation.StyleRes
import androidx.core.graphics.toColor
import androidx.core.view.ViewCompat.SCROLL_AXIS_HORIZONTAL
import androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL
import androidx.core.view.ViewCompat.ScrollAxis

/**
 * Inputs for an ACE Embedded UI session.
 *
 * @param hints The context hints passed from the client as input to the ACE understander service.
 * @param width An [AceEmbeddedMeasureSpec] that constrains the width of the delegated UI.
 * @param height An [AceEmbeddedMeasureSpec] that constrains the height of the delegated UI.
 * @param backgroundColor The color that the remote UI should draw behind its content. This is used
 *   when the client sets the SurfaceView to z-behind, which would normally render black pixels
 *   wherever the remote UI is not drawing.
 * @param nestedScrollAxes The bitmasked nested scroll axes supported by the client. This ensures
 *   that the ACE embedded session will only send these nested scroll events back to the client.
 * @param nestedScrollAxisLocked Whether the ACE embedded session should report a specific axis when
 *   a nested scroll gesture is detected, and whether that axis should be locked such that
 *   subsequent nested scroll events are only reported for that axis. A value of `true` is typical
 *   for Android UIs where scroll axes are locked during a gesture, while a value of `false` can be
 *   used to give the illusion of a 2D canvas. Only applicable when [nestedScrollAxes] is set to
 *   `SCROLL_AXIS_HORIZONTAL or SCROLL_AXIS_VERTICAL`.
 * @param shouldBlur Whether the remote UI should have a blur effect applied to the window. Changing
 *   this value may cause the remote UI to animate to the new state.
 * @param themeResourceId The custom [android.R.styleable#PersonalContextTheme] to be passed to a
 *   connected visualizer. A visualizer can use this name to look up the theme resource in the
 *   client's resources, which can then be used when creating an embedded surface for the client.
 */
data class AceEmbeddedInputs(
    val hints: Set<ContextHint>,
    val width: AceEmbeddedMeasureSpec,
    val height: AceEmbeddedMeasureSpec,
    val backgroundColor: Color,
    @property:ScrollAxis val nestedScrollAxes: Int,
    val nestedScrollAxisLocked: Boolean,
    val shouldBlur: Boolean,
    @param:StyleRes val themeResourceId: Int,
) {
    override fun toString(): String {
        return "AceEmbeddedInputs(" +
            "hints=[${hints.size} items redacted], " +
            "width=$width, " +
            "height=$height, " +
            "backgroundColor=$backgroundColor, " +
            "nestedScrollAxes=$nestedScrollAxes, " +
            "nestedScrollAxisLocked=$nestedScrollAxisLocked, " +
            "shouldBlur=$shouldBlur, " +
            "themeResourceId=$themeResourceId" +
            ")"
    }
}

/** Builder function for the bare minmum */
fun AceEmbeddedBasicInputs(hints: Set<ContextHint>) =
    AceEmbeddedInputs(
        hints = hints,
        width = 1.atMost,
        height = 1.atMost,
        backgroundColor = Color.TRANSPARENT.toColor(),
        nestedScrollAxes = SCROLL_AXIS_HORIZONTAL or SCROLL_AXIS_VERTICAL,
        nestedScrollAxisLocked = true,
        shouldBlur = false,
        themeResourceId = 0,
    )
