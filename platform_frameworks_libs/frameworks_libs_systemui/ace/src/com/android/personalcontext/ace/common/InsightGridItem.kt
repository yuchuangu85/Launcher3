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

import android.service.personalcontext.insight.ContextInsight

/**
 * Represents an insight grid item.
 *
 * @property insight The [android.service.personalcontext.insight.ContextInsight] to display.
 * @property span The number of columns this item should span in the grid.
 */
data class InsightGridItem(val insight: ContextInsight, val span: Int) {

    companion object {
        /** A small span, the smallest possible unit. */
        const val SMALL = 2
        /** A half span, typically taking up half of the row. */
        const val HALF = 3
        /** A medium span. */
        const val MEDIUM = 4
        /** A large span, typically taking up the entire row */
        const val LARGE = 6
    }
}
