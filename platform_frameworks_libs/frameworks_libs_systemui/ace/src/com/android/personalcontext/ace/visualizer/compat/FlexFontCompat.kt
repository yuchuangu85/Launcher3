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
package com.android.personalcontext.ace.visualizer.compat

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

interface FlexFontCompat {

    /**
     * Modifies the [Typography] to use Google Sans Flex with the given variable format.
     *
     * @param slant the slant of the font.
     * @param width the width of the font.
     * @param grade the grade of the font.
     * @param round the round of the font.
     */
    fun flexFont(
        typography: Typography,
        slant: Float = 0.0f,
        width: Float = 100.0f,
        grade: Int = 0,
        round: Float = 100.0f,
    ): Typography = typography

    /**
     * Returns the Google Sans Flex text style with the given variable format.
     *
     * @param slant the slant of the font.
     * @param width the width of the font.
     * @param weight the weight of the font.
     * @param grade the grade of the font.
     * @param round the round of the font.
     */
    fun flexFont(
        style: TextStyle,
        weight: Int,
        slant: Float = 0.0f,
        width: Float = 100.0f,
        grade: Int = 0,
        round: Float = 100.0f,
    ): TextStyle = style
}
