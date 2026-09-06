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

package com.android.personalcontext.ace.common

import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.insight.ContextInsight

/**
 * Utility object for extracting specific typed hints from a
 * [android.service.personalcontext.insight.ContextInsight].
 */
object FindHintUtils {

    /**
     * Finds the first [android.service.personalcontext.hint.ContextHint] of type [T] within this
     * [android.service.personalcontext.insight.ContextInsight].
     *
     * @param T The specific type of [android.service.personalcontext.hint.ContextHint] to find.
     * @return The first hint of type [T], or `null` if no matching hint is found.
     */
    @JvmSynthetic
    inline fun <reified T : ContextHint> ContextInsight.findContextHint(): T? =
        originHints.firstNotNullOfOrNull { it.contextHint as? T }
}
