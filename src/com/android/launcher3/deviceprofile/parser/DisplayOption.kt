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

import android.content.Context
import android.content.res.TypedArray
import android.graphics.PointF
import com.android.launcher3.R
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.COUNT_SIZES
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_DEFAULT
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.parsePointMap
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.parsePointMapFromDefaults
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.parseTypedMap
import com.android.launcher3.display.LauncherDisplayInfo

class DisplayOption
private constructor(@JvmField val grid: GridOption, context: Context, ta: TypedArray) {

    @JvmField val minWidthDps: Float = ta.getFloat(R.styleable.ProfileDisplayOption_minWidthDps, 0f)
    @JvmField
    val minHeightDps: Float = ta.getFloat(R.styleable.ProfileDisplayOption_minHeightDps, 0f)
    @JvmField
    val canBeDefault: Boolean = ta.getBoolean(R.styleable.ProfileDisplayOption_canBeDefault, false)

    @JvmField
    val minCellSize: Array<PointF> =
        ta.parsePointMap(
            R.styleable.ProfileDisplayOption_minCellWidth,
            R.styleable.ProfileDisplayOption_minCellHeight,
            R.styleable.ProfileDisplayOption_minCellWidthLandscape,
            R.styleable.ProfileDisplayOption_minCellHeightLandscape,
            R.styleable.ProfileDisplayOption_minCellWidthTwoPanelPortrait,
            R.styleable.ProfileDisplayOption_minCellHeightTwoPanelPortrait,
            R.styleable.ProfileDisplayOption_minCellWidthTwoPanelLandscape,
            R.styleable.ProfileDisplayOption_minCellHeightTwoPanelLandscape,
        )

    private val defaultBorderSpace =
        ta.parseTypedMap(
            0f,
            R.styleable.ProfileDisplayOption_borderSpace,
            R.styleable.ProfileDisplayOption_borderSpaceLandscape,
            R.styleable.ProfileDisplayOption_borderSpaceTwoPanelPortrait,
            R.styleable.ProfileDisplayOption_borderSpaceTwoPanelLandscape,
        ) { index, v ->
            getFloat(index, v)
        }

    @JvmField
    val borderSpaces: Array<PointF> =
        ta.parsePointMapFromDefaults(
            defaultBorderSpace,
            R.styleable.ProfileDisplayOption_borderSpaceHorizontal,
            R.styleable.ProfileDisplayOption_borderSpaceVertical,
            R.styleable.ProfileDisplayOption_borderSpaceLandscapeHorizontal,
            R.styleable.ProfileDisplayOption_borderSpaceLandscapeVertical,
            R.styleable.ProfileDisplayOption_borderSpaceTwoPanelPortraitHorizontal,
            R.styleable.ProfileDisplayOption_borderSpaceTwoPanelPortraitVertical,
            R.styleable.ProfileDisplayOption_borderSpaceTwoPanelLandscapeHorizontal,
            R.styleable.ProfileDisplayOption_borderSpaceTwoPanelLandscapeVertical,
        )

    @JvmField
    val horizontalMargin: FloatArray =
        ta.parseTypedMap(
                0f,
                R.styleable.ProfileDisplayOption_horizontalMargin,
                R.styleable.ProfileDisplayOption_horizontalMarginLandscape,
                R.styleable.ProfileDisplayOption_horizontalMarginTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_horizontalMarginTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val hotseatBarBottomSpace: FloatArray =
        ta.parseTypedMap(
                context.resources.getFloat(R.dimen.hotseat_bar_bottom_space_default),
                R.styleable.ProfileDisplayOption_hotseatBarBottomSpace,
                R.styleable.ProfileDisplayOption_hotseatBarBottomSpaceLandscape,
                R.styleable.ProfileDisplayOption_hotseatBarBottomSpaceTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_hotseatBarBottomSpaceTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val hotseatQsbSpace: FloatArray =
        ta.parseTypedMap(
                context.resources.getFloat(R.dimen.hotseat_qsb_space_default),
                R.styleable.ProfileDisplayOption_hotseatQsbSpace,
                R.styleable.ProfileDisplayOption_hotseatQsbSpaceLandscape,
                R.styleable.ProfileDisplayOption_hotseatQsbSpaceTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_hotseatQsbSpaceTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val iconSizes: FloatArray =
        ta.parseTypedMap(
                0f,
                R.styleable.ProfileDisplayOption_iconImageSize,
                R.styleable.ProfileDisplayOption_iconSizeLandscape,
                R.styleable.ProfileDisplayOption_iconSizeTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_iconSizeTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val textSizes: FloatArray =
        ta.parseTypedMap(
                0f,
                R.styleable.ProfileDisplayOption_iconTextSize,
                R.styleable.ProfileDisplayOption_iconTextSizeLandscape,
                R.styleable.ProfileDisplayOption_iconTextSizeTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_iconTextSizeTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val allAppsCellSize: Array<PointF> =
        ta.parsePointMap(
            R.styleable.ProfileDisplayOption_allAppsCellWidth,
            R.styleable.ProfileDisplayOption_allAppsCellHeight,
            R.styleable.ProfileDisplayOption_allAppsCellWidthLandscape,
            R.styleable.ProfileDisplayOption_allAppsCellHeightLandscape,
            R.styleable.ProfileDisplayOption_allAppsCellWidthTwoPanelPortrait,
            R.styleable.ProfileDisplayOption_allAppsCellHeightTwoPanelPortrait,
            R.styleable.ProfileDisplayOption_allAppsCellWidthTwoPanelLandscape,
            R.styleable.ProfileDisplayOption_allAppsCellHeightTwoPanelLandscape,
        )

    @JvmField
    val allAppsIconSizes: FloatArray =
        ta.parseTypedMap(
                iconSizes[INDEX_DEFAULT],
                R.styleable.ProfileDisplayOption_allAppsIconSize,
                R.styleable.ProfileDisplayOption_allAppsIconSizeLandscape,
                R.styleable.ProfileDisplayOption_allAppsIconSizeTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_allAppsIconSizeTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val allAppsIconTextSizes: FloatArray =
        ta.parseTypedMap(
                textSizes[INDEX_DEFAULT],
                R.styleable.ProfileDisplayOption_allAppsIconTextSize,
                R.styleable.ProfileDisplayOption_allAppsIconTextSize,
                R.styleable.ProfileDisplayOption_allAppsIconTextSizeTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_allAppsIconTextSizeTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val allAppsBorderSpaces: Array<PointF> =
        ta.parsePointMapFromDefaults(
            defaults =
                ta.parseTypedMap(
                    defaultBorderSpace[INDEX_DEFAULT],
                    R.styleable.ProfileDisplayOption_allAppsBorderSpace,
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceLandscape,
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelPortrait,
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelLandscape,
                ) { index, v ->
                    getFloat(index, v)
                },
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceHorizontal,
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceVertical,
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceLandscapeHorizontal,
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceLandscapeVertical,
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelPortraitHorizontal,
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelPortraitVertical,
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelLandscapeHorizontal,
            R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelLandscapeVertical,
        )

    @JvmField
    val transientTaskbarIconSize: FloatArray =
        ta.parseTypedMap(
                context.resources.getFloat(R.dimen.taskbar_icon_size),
                R.styleable.ProfileDisplayOption_transientTaskbarIconSize,
                R.styleable.ProfileDisplayOption_transientTaskbarIconSizeLandscape,
                R.styleable.ProfileDisplayOption_transientTaskbarIconSizeTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_transientTaskbarIconSizeTwoPanelLandscape,
            ) { i, v ->
                getFloat(i, v)
            }
            .toFloatArray()

    @JvmField
    val startAlignTaskbar: BooleanArray =
        ta.parseTypedMap(
                false,
                R.styleable.ProfileDisplayOption_startAlignTaskbar,
                R.styleable.ProfileDisplayOption_startAlignTaskbarLandscape,
                R.styleable.ProfileDisplayOption_startAlignTaskbarTwoPanelPortrait,
                R.styleable.ProfileDisplayOption_startAlignTaskbarTwoPanelLandscape,
            ) { i, v ->
                getBoolean(i, v)
            }
            .toBooleanArray()

    /** Update current values by performing [op] on current and [other] value */
    fun merge(
        other: DisplayOption,
        boolOp: (Boolean, Boolean) -> Boolean,
        op: (Float, Float) -> Float,
    ) {
        for (i in 0..<COUNT_SIZES) {
            iconSizes[i] = op(iconSizes[i], other.iconSizes[i])
            textSizes[i] = op(textSizes[i], other.textSizes[i])
            hotseatQsbSpace[i] = op(hotseatQsbSpace[i], other.hotseatQsbSpace[i])
            allAppsIconSizes[i] = op(allAppsIconSizes[i], other.allAppsIconSizes[i])
            allAppsIconTextSizes[i] = op(allAppsIconTextSizes[i], other.allAppsIconTextSizes[i])
            transientTaskbarIconSize[i] =
                op(transientTaskbarIconSize[i], other.transientTaskbarIconSize[i])
            hotseatBarBottomSpace[i] = op(hotseatBarBottomSpace[i], other.hotseatBarBottomSpace[i])
            horizontalMargin[i] = op(horizontalMargin[i], other.horizontalMargin[i])

            borderSpaces[i].x = op(borderSpaces[i].x, other.borderSpaces[i].x)
            borderSpaces[i].y = op(borderSpaces[i].y, other.borderSpaces[i].y)

            minCellSize[i].x = op(minCellSize[i].x, other.minCellSize[i].x)
            minCellSize[i].y = op(minCellSize[i].y, other.minCellSize[i].y)

            allAppsCellSize[i].x = op(allAppsCellSize[i].x, other.allAppsCellSize[i].x)
            allAppsCellSize[i].y = op(allAppsCellSize[i].y, other.allAppsCellSize[i].y)

            allAppsBorderSpaces[i].x = op(allAppsBorderSpaces[i].x, other.allAppsBorderSpaces[i].x)
            allAppsBorderSpaces[i].y = op(allAppsBorderSpaces[i].y, other.allAppsBorderSpaces[i].y)

            startAlignTaskbar[i] = boolOp(startAlignTaskbar[i], other.startAlignTaskbar[i])
        }
    }

    companion object {

        @JvmStatic
        fun getPredefinedDisplayOptions(
            displayInfo: LauncherDisplayInfo,
            isFixedLandscapeMode: Boolean,
        ): List<DisplayOption> {
            val deviceType = displayInfo.deviceType
            val context = displayInfo.context
            return GridOption.parseAllDefined(
                    context,
                    displayInfo,
                    filter = {
                        it.isEnabled(deviceType) &&
                            it.filterByFlag(deviceType, isFixedLandscapeMode)
                    },
                    mapper = { option, el ->
                        el.children("display-option")
                            .map {
                                val ta = it.obtainAttrs(context, R.styleable.ProfileDisplayOption)
                                DisplayOption(option, context, ta).also { ta.recycle() }
                            }
                            .toList()
                    },
                )
                .flatten()
        }

        @JvmStatic
        fun createEmpty(context: Context, grid: GridOption): DisplayOption {
            val ta = context.obtainStyledAttributes(R.styleable.ProfileDisplayOption)
            return DisplayOption(grid, context, ta).also {
                it.merge(it, boolOp = { _, _ -> false }, op = { _, _ -> 0f })
                ta.recycle()
            }
        }
    }
}
