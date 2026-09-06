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
package com.android.personalcontext.ace.visualizer.templates.utils

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Contains functions to access the current client-provided theme values provided at the call site's
 * position in the hierarchy.
 */
object EmbeddedTheme {

    /**
     * Contains client-provided theme values specific to Inline Suggestions.
     *
     * @see android.R.attr.inlineSuggestion
     */
    object InlineSuggestion {

        /**
         * Retrieves the current [EmbeddedColorScheme] at the call site's position in the hierarchy.
         */
        val colorScheme: EmbeddedColorScheme
            @Composable @ReadOnlyComposable get() = LocalEmbeddedColorScheme.current

        /** Retrieves the current [EmbeddedShapes] at the call site's position in the hierarchy. */
        val shapes: EmbeddedShapes
            @Composable @ReadOnlyComposable get() = LocalEmbeddedShapes.current

        /**
         * This color scheme holds all the named color parameters for a
         * [EmbeddedTheme.InlineSuggestion].
         *
         * @property stroke the client-provided [android.R.attr.strokeColor].
         * @property text the client-provided [android.R.attr.textColor].
         * @property icon the client-provided [android.R.attr.iconColor].
         * @property suggestionBackground the client-provided
         *   [android.R.attr.suggestionBackgroundColor].
         */
        data class EmbeddedColorScheme(
            val stroke: Color? = null,
            val text: Color? = null,
            val icon: Color? = null,
            val suggestionBackground: Color? = null,
        )

        /**
         * Holds all the named shapes parameters for a [EmbeddedTheme.InlineSuggestion].
         *
         * @property suggestion a shape using the client-provided
         *   [android.R.attr.suggestionCornerRadius].
         */
        data class EmbeddedShapes(val suggestion: CornerBasedShape? = null)

        /** Provides an [EmbeddedColorScheme] for Inline Suggestions. */
        val LocalEmbeddedColorScheme: ProvidableCompositionLocal<EmbeddedColorScheme> =
            compositionLocalOf {
                error("No EmbeddedColorScheme provided")
            }
        /** Provides an [EmbeddedShapes] for Inline Suggestions. */
        val LocalEmbeddedShapes: ProvidableCompositionLocal<EmbeddedShapes> = compositionLocalOf {
            error("No EmbeddedShapes provided")
        }
    }
}
