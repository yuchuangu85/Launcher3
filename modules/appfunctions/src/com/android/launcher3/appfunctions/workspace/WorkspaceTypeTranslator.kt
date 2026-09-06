/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.appfunctions.workspace

/** A marker interface for all translators that can be used for Dagger multibindings. */
interface Translator<C, S> {

    /**
     * Converts a [C] to a [S].
     *
     * @param info The [C] to convert.
     * @return The converted [S].
     */
    fun toSpec(info: C): S
}

/**
 * A translator for converting between the flat [WorkspaceSpec] and a structured workspace type [T]
 * actually used by the launcher.
 */
interface WorkspaceTypeTranslator<T> : Translator<T, WorkspaceSpec>

/**
 * A translator for converting between the flat [UnplacedAppSpec] and a structured app type [T]
 * actually used by the launcher.
 */
interface UnplacedAppTypeTranslator<T> : Translator<T, UnplacedAppSpec>

/**
 * A translator for converting between the flat [UnplacedWidgetSpec] and a structured widget type [T]
 * actually used by the launcher.
 */
interface UnplacedWidgetTypeTranslator<T> : Translator<T, UnplacedWidgetSpec>
