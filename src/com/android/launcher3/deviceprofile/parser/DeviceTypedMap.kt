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

package com.android.launcher3.deviceprofile.parser

import android.content.res.TypedArray
import android.graphics.PointF

/** Class to parse values corresponding to various device types */
object DeviceTypedMap {

    // Used for arrays to specify different sizes (e.g. border spaces, width/height) in different
    // constraints
    const val COUNT_SIZES: Int = 4

    const val INDEX_DEFAULT: Int = 0
    const val INDEX_LANDSCAPE: Int = 1
    const val INDEX_TWO_PANEL_PORTRAIT: Int = 2
    const val INDEX_TWO_PANEL_LANDSCAPE: Int = 3

    inline fun <reified T : Any, reified R> TypedArray.parseTypedMap(
        startValue: T,
        indexDefault: R,
        indexLandscape: R,
        indexTwoPanelPortrait: R,
        indexTwoPanelLandscape: R,
        mapper: TypedArray.(R, T) -> T,
    ): Array<T> {

        val firstValue = mapper.invoke(this, indexDefault, startValue)
        return arrayOf(
            firstValue,
            mapper.invoke(this, indexLandscape, firstValue),
            mapper.invoke(this, indexTwoPanelPortrait, firstValue),
            mapper.invoke(this, indexTwoPanelLandscape, firstValue),
        )
    }

    private fun TypedArray.parsePoint(indexX: Int, defaultX: Float, indexY: Int, defaultY: Float) =
        PointF(getFloat(indexX, defaultX), getFloat(indexY, defaultY))

    fun TypedArray.parsePointMap(
        indexDefaultX: Int,
        indexDefaultY: Int,
        indexLandscapeX: Int,
        indexLandscapeY: Int,
        indexTwoPanelPortraitX: Int,
        indexTwoPanelPortraitY: Int,
        indexTwoPanelLandscapeX: Int,
        indexTwoPanelLandscapeY: Int,
    ) =
        parseTypedMap(
            PointF(0f, 0f),
            indexDefaultX to indexDefaultY,
            indexLandscapeX to indexLandscapeY,
            indexTwoPanelPortraitX to indexTwoPanelPortraitY,
            indexTwoPanelLandscapeX to indexTwoPanelLandscapeY,
        ) { indexPair, v ->
            parsePoint(indexPair.first, v.x, indexPair.second, v.y)
        }

    fun TypedArray.parsePointMapFromDefaults(
        defaults: Array<Float>,
        indexDefaultX: Int,
        indexDefaultY: Int,
        indexLandscapeX: Int,
        indexLandscapeY: Int,
        indexTwoPanelPortraitX: Int,
        indexTwoPanelPortraitY: Int,
        indexTwoPanelLandscapeX: Int,
        indexTwoPanelLandscapeY: Int,
    ) =
        arrayOf(
            parsePoint(
                indexDefaultX,
                defaults[INDEX_DEFAULT],
                indexDefaultY,
                defaults[INDEX_DEFAULT],
            ),
            parsePoint(
                indexLandscapeX,
                defaults[INDEX_LANDSCAPE],
                indexLandscapeY,
                defaults[INDEX_LANDSCAPE],
            ),
            parsePoint(
                indexTwoPanelPortraitX,
                defaults[INDEX_TWO_PANEL_PORTRAIT],
                indexTwoPanelPortraitY,
                defaults[INDEX_TWO_PANEL_PORTRAIT],
            ),
            parsePoint(
                indexTwoPanelLandscapeX,
                defaults[INDEX_TWO_PANEL_LANDSCAPE],
                indexTwoPanelLandscapeY,
                defaults[INDEX_TWO_PANEL_LANDSCAPE],
            ),
        )
}
