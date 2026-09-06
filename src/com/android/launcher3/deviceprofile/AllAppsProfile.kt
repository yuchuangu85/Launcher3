/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.deviceprofile

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.DisplayOptionSpec
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.getIconVisibleSizePx
import com.android.launcher3.Utilities.getNormalizedIconDrawablePadding
import com.android.launcher3.Utilities.pxFromSp
import com.android.launcher3.responsive.CalculatedCellSpec
import com.android.launcher3.responsive.CalculatedResponsiveSpec
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp
import com.android.launcher3.util.CellContentDimensions
import com.android.launcher3.util.IconSizeSteps
import kotlin.math.max
import kotlin.math.min

data class AllAppsProfile(
    val borderSpacePx: Point,
    val cellHeightPx: Int,
    val iconSizePx: Int,
    val iconTextSizePx: Float,
    val iconDrawablePaddingPx: Int,
    val maxAllAppsTextLineCount: Int,
    val cellWidthPx: Int,
    val openDuration: Int,
    val closeDuration: Int,
    val leftRightMargin: Int,
    val padding: Rect,
    val shiftRange: Int,
    val numShownAllAppsColumns: Int,
) {

    /**
     * Return a new AllAppsProfile with cellHeightPx changed. This function is needed for the java
     * code.
     */
    fun copyWithCellHeightPx(newCellHeightPx: Int): AllAppsProfile {
        return copy(cellHeightPx = newCellHeightPx)
    }

    companion object Factory {

        private fun calculateAllAppsBorderSpacePx(
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            typeIndex: Int,
            scale: Float,
        ) =
            Point(
                pxFromDp(inv.allAppsBorderSpaces[typeIndex].x, metric, scale),
                pxFromDp(inv.allAppsBorderSpaces[typeIndex].y, metric, scale),
            )

        fun createAllAppsProfileScalableGrid(
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            typeIndex: Int,
            res: Resources,
            context: Context,
            allAppsTopPadding: Int,
            workspaceProfile: WorkspaceProfile,
            deviceProperties: DeviceProperties,
            displayOptionSpec: DisplayOptionSpec,
        ): AllAppsProfile {
            val allAppsBorderSpacePx =
                calculateAllAppsBorderSpacePx(inv, metric, typeIndex, workspaceProfile.scale)
            var allAppsCellHeightPx =
                (pxFromDp(inv.allAppsCellSize[typeIndex].y, metric) + allAppsBorderSpacePx.y)
            var allAppsIconSizePx = pxFromDp(inv.allAppsIconSize[typeIndex], metric)
            val allAppsIconTextSizePx =
                pxFromSp(inv.allAppsIconTextSize[typeIndex], metric).toFloat()
            val allAppsIconDrawablePaddingPx =
                max(
                    0,
                    (workspaceProfile.iconDrawablePaddingOriginalPx -
                        ((workspaceProfile.iconSizePx -
                            getIconVisibleSizePx(workspaceProfile.iconSizePx)) / 2)),
                )
            var allAppsCellWidthPx =
                pxFromDp(inv.allAppsCellSize[typeIndex].x, metric, workspaceProfile.scale)

            if (allAppsCellWidthPx < allAppsIconSizePx) {
                // If allAppsCellWidth no longer fit allAppsIconSize, reduce allAppsBorderSpace to
                // make allAppsCellWidth bigger.
                val numBorders: Int = inv.numAllAppsColumns - 1
                val extraWidthRequired: Int =
                    (allAppsIconSizePx - allAppsCellWidthPx) * inv.numAllAppsColumns
                if (allAppsBorderSpacePx.x * numBorders >= extraWidthRequired) {
                    allAppsCellWidthPx = allAppsIconSizePx
                    allAppsBorderSpacePx.x -= extraWidthRequired / numBorders
                } else {
                    // If it still doesn't fit, set allAppsBorderSpace to 0 and distribute the space
                    // for allAppsCellWidth, and reduce allAppsIconSize.
                    allAppsCellWidthPx =
                        (allAppsCellWidthPx * inv.numAllAppsColumns +
                            allAppsBorderSpacePx.x * numBorders) / inv.numAllAppsColumns
                    allAppsIconSizePx =
                        min(allAppsIconSizePx.toDouble(), allAppsCellWidthPx.toDouble()).toInt()
                    allAppsBorderSpacePx.x = 0
                }
            }

            val cellContentHeight: Int =
                (allAppsIconSizePx +
                    Utilities.calculateTextHeight(allAppsIconTextSizePx) +
                    allAppsBorderSpacePx.y)
            if (allAppsCellHeightPx < cellContentHeight) {
                // Increase allAppsCellHeight to fit its content.
                allAppsCellHeightPx = cellContentHeight
            }

            val numShownAllAppsColumns = displayOptionSpec.numAllAppsColumns

            val allAppsPadding =
                calculateNonResponsivePadding(
                    deviceProperties = deviceProperties,
                    workspaceProfile = workspaceProfile,
                    borderSpacePx = allAppsBorderSpacePx,
                    context = context,
                    inv = inv,
                    allAppsTopPadding = allAppsTopPadding,
                )

            return AllAppsProfile(
                borderSpacePx = allAppsBorderSpacePx,
                cellHeightPx = allAppsCellHeightPx,
                iconSizePx = allAppsIconSizePx,
                iconTextSizePx = allAppsIconTextSizePx,
                iconDrawablePaddingPx = allAppsIconDrawablePaddingPx,
                maxAllAppsTextLineCount = 1,
                cellWidthPx = allAppsCellWidthPx,
                openDuration = res.getInteger(R.integer.config_allAppsOpenDuration),
                closeDuration = res.getInteger(R.integer.config_allAppsCloseDuration),
                leftRightMargin =
                    calculateallAppsLeftRightMargin(
                        allAppsCellWidthPx,
                        numShownAllAppsColumns,
                        allAppsBorderSpacePx,
                        allAppsPadding,
                        deviceProperties,
                    ),
                padding = allAppsPadding,
                numShownAllAppsColumns = numShownAllAppsColumns,
                shiftRange =
                    deviceProperties.heightPx - allAppsTopPadding + deviceProperties.insets.top,
            )
        }

        fun calculateNonResponsivePadding(
            deviceProperties: DeviceProperties,
            workspaceProfile: WorkspaceProfile,
            borderSpacePx: Point,
            context: Context,
            inv: InvariantDeviceProfile,
            allAppsTopPadding: Int,
        ): Rect {
            val allAppsStyle: TypedArray =
                context.obtainStyledAttributes(
                    if (inv.allAppsStyle != ResourceUtils.INVALID_RESOURCE_HANDLE) inv.allAppsStyle
                    else R.style.AllAppsStyleDefault,
                    R.styleable.AllAppsStyle,
                )
            var leftAndRight =
                allAppsStyle.getDimensionPixelSize(R.styleable.AllAppsStyle_horizontalPadding, 0)
            if (!deviceProperties.isLargeScreen) {
                val cellLayoutHorizontalPadding: Int =
                    (workspaceProfile.cellLayoutPaddingPx.left +
                        workspaceProfile.cellLayoutPaddingPx.right) / 2
                leftAndRight =
                    max(
                        0,
                        workspaceProfile.desiredWorkspaceHorizontalMarginPx +
                            cellLayoutHorizontalPadding - (borderSpacePx.x / 2),
                    )
            }
            allAppsStyle.recycle()
            return Rect(
                /* left */ leftAndRight,
                /* top */ allAppsTopPadding,
                /* right */ leftAndRight,
                /* bottom */ 0,
            )
        }

        fun createAllAppsProfileNonScalable(
            res: Resources,
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            typeIndex: Int,
            scale: Float,
            deviceProperties: DeviceProperties,
            displayOptionSpec: DisplayOptionSpec,
            context: Context,
            allAppsTopPadding: Int,
            workspaceProfile: WorkspaceProfile,
        ): AllAppsProfile {
            val allAppsBorderSpacePx = calculateAllAppsBorderSpacePx(inv, metric, typeIndex, scale)
            val allAppsIconSizePx = max(1, pxFromDp(inv.allAppsIconSize[typeIndex], metric, scale))
            val allAppsIconDrawablePaddingPx =
                res.getDimensionPixelSize(R.dimen.all_apps_icon_drawable_padding)
            val cellWidthPx = allAppsIconSizePx + (2 * allAppsIconDrawablePaddingPx)
            val numShownAllAppsColumns = displayOptionSpec.numAllAppsColumns

            val allAppsPadding =
                calculateNonResponsivePadding(
                    deviceProperties = deviceProperties,
                    workspaceProfile = workspaceProfile,
                    borderSpacePx = allAppsBorderSpacePx,
                    context = context,
                    inv = inv,
                    allAppsTopPadding = allAppsTopPadding,
                )

            return AllAppsProfile(
                borderSpacePx = allAppsBorderSpacePx,
                // AllApps cells don't have real space between cells,
                // so we add the border space to the cell height
                cellHeightPx =
                    (pxFromDp(inv.allAppsCellSize.get(typeIndex).y, metric) +
                        allAppsBorderSpacePx.y),
                iconSizePx = allAppsIconSizePx,
                // We need the double conversion to keep the original behaviour
                iconTextSizePx =
                    (pxFromSp(inv.allAppsIconTextSize[typeIndex], metric).toFloat() * scale)
                        .toInt()
                        .toFloat(),
                iconDrawablePaddingPx = allAppsIconDrawablePaddingPx,
                maxAllAppsTextLineCount = 1,
                cellWidthPx = cellWidthPx,
                openDuration = res.getInteger(R.integer.config_allAppsOpenDuration),
                closeDuration = res.getInteger(R.integer.config_allAppsCloseDuration),
                leftRightMargin =
                    calculateallAppsLeftRightMargin(
                        cellWidthPx,
                        numShownAllAppsColumns,
                        allAppsBorderSpacePx,
                        allAppsPadding,
                        deviceProperties,
                    ),
                numShownAllAppsColumns = numShownAllAppsColumns,
                shiftRange =
                    deviceProperties.heightPx - allAppsTopPadding + deviceProperties.insets.top,
                padding = allAppsPadding,
            )
        }

        fun calculateallAppsLeftRightMargin(
            cellWidthPx: Int,
            numShownAllAppsColumns: Int,
            borderSpacePx: Point,
            allAppsPadding: Rect,
            deviceProperties: DeviceProperties,
        ): Int {
            if (deviceProperties.isLargeScreen) {
                val usedWidth: Int =
                    ((cellWidthPx * numShownAllAppsColumns) +
                        (borderSpacePx.x * (numShownAllAppsColumns - 1)) +
                        allAppsPadding.left +
                        allAppsPadding.right)
                return max(1, (deviceProperties.availableWidthPx - usedWidth) / 2)
            } else {
                return 0
            }
        }

        fun createAllAppsWithResponsive(
            deviceProperties: DeviceProperties,
            responsiveAllAppsCellSpec: CalculatedCellSpec,
            responsiveAllAppsWidthSpec: CalculatedResponsiveSpec,
            responsiveAllAppsHeightSpec: CalculatedResponsiveSpec,
            iconSizeSteps: IconSizeSteps,
            isVerticalBarLayout: Boolean,
            res: Resources,
            displayOptionSpec: DisplayOptionSpec,
            allAppsTopPadding: Int,
        ): AllAppsProfile {
            var allAppsIconSizePx = responsiveAllAppsCellSpec.iconSize
            var allAppsIconTextSizePx: Float = responsiveAllAppsCellSpec.iconTextSize.toFloat()
            var allAppsIconDrawablePaddingPx =
                getNormalizedIconDrawablePadding(
                    allAppsIconSizePx,
                    responsiveAllAppsCellSpec.iconDrawablePadding,
                )
            var maxAllAppsTextLineCount = responsiveAllAppsCellSpec.iconTextMaxLineCount
            val allAppsBorderSpacePx =
                Point(responsiveAllAppsWidthSpec.gutterPx, responsiveAllAppsHeightSpec.gutterPx)
            var allAppsCellHeightPx = responsiveAllAppsHeightSpec.cellSizePx
            var allAppsCellWidthPx = responsiveAllAppsWidthSpec.cellSizePx

            // Reduce the size of the app icon if it doesn't fit
            if (allAppsCellWidthPx < allAppsIconSizePx) {
                // get a smaller icon size
                allAppsIconSizePx = iconSizeSteps.getIconSmallerThan(allAppsCellWidthPx)
            }

            val cellContentDimensions =
                CellContentDimensions(
                    allAppsIconSizePx,
                    allAppsIconDrawablePaddingPx,
                    allAppsIconTextSizePx.toInt(),
                    maxAllAppsTextLineCount,
                )
            if (allAppsCellHeightPx < cellContentDimensions.getCellContentHeight()) {
                if (isVerticalBarLayout) {
                    if (allAppsCellHeightPx < allAppsIconSizePx) {
                        cellContentDimensions.iconSizePx =
                            iconSizeSteps.getIconSmallerThan(allAppsCellHeightPx)
                    }
                } else {
                    cellContentDimensions.resizeToFitCellHeight(allAppsCellHeightPx, iconSizeSteps)
                }
                allAppsIconSizePx = cellContentDimensions.iconSizePx
                allAppsIconDrawablePaddingPx = cellContentDimensions.iconDrawablePaddingPx
                allAppsIconTextSizePx = cellContentDimensions.iconTextSizePx.toFloat()
                maxAllAppsTextLineCount = cellContentDimensions.maxLineCount
            }

            allAppsCellHeightPx += responsiveAllAppsHeightSpec.gutterPx

            val numShownAllAppsColumns = displayOptionSpec.numAllAppsColumns

            // This workaround is needed to align AllApps icons with Workspace icons
            // since AllApps doesn't have borders between cells
            val halfBorder: Int = allAppsBorderSpacePx.x / 2
            val allAppsPadding =
                Rect(
                    /* left */ responsiveAllAppsWidthSpec.startPaddingPx - halfBorder,
                    /* top */ allAppsTopPadding,
                    /* right */ responsiveAllAppsWidthSpec.endPaddingPx - halfBorder,
                    /* bottom */ 0,
                )

            val allAppsProfile =
                AllAppsProfile(
                    borderSpacePx = allAppsBorderSpacePx,
                    cellHeightPx = allAppsCellHeightPx,
                    iconSizePx = allAppsIconSizePx,
                    iconTextSizePx = allAppsIconTextSizePx,
                    iconDrawablePaddingPx = allAppsIconDrawablePaddingPx,
                    maxAllAppsTextLineCount = maxAllAppsTextLineCount,
                    cellWidthPx = allAppsCellWidthPx,
                    openDuration = res.getInteger(R.integer.config_allAppsOpenDuration),
                    closeDuration = res.getInteger(R.integer.config_allAppsCloseDuration),
                    leftRightMargin =
                        calculateallAppsLeftRightMargin(
                            allAppsCellWidthPx,
                            numShownAllAppsColumns,
                            allAppsBorderSpacePx,
                            allAppsPadding,
                            deviceProperties,
                        ),
                    padding = allAppsPadding,
                    shiftRange =
                        deviceProperties.heightPx - allAppsTopPadding + deviceProperties.insets.top,
                    numShownAllAppsColumns = numShownAllAppsColumns,
                )

            return when {
                isVerticalBarLayout -> autoResizeAllAppsCells(allAppsProfile)
                else -> allAppsProfile
            }
        }

        /** Re-computes the all-apps cell size to be independent of workspace */
        fun autoResizeAllAppsCells(allAppsProfile: AllAppsProfile): AllAppsProfile {
            val textHeight: Int =
                Utilities.calculateTextHeight(allAppsProfile.iconTextSizePx) *
                    allAppsProfile.maxAllAppsTextLineCount
            return allAppsProfile.copy(
                cellHeightPx =
                    allAppsProfile.iconSizePx +
                        allAppsProfile.iconDrawablePaddingPx +
                        textHeight +
                        (textHeight * 2)
            )
        }

        fun createAllAppsProfile(
            res: Resources,
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            isScalableGrid: Boolean,
            typeIndex: Int,
            workspaceProfile: WorkspaceProfile,
            deviceProperties: DeviceProperties,
            context: Context,
            allAppsTopPadding: Int,
            displayOptionSpec: DisplayOptionSpec,
        ) =
            when {
                isScalableGrid -> {
                    createAllAppsProfileScalableGrid(
                        inv = inv,
                        metric = metric,
                        typeIndex = typeIndex,
                        res = res,
                        context = context,
                        allAppsTopPadding = allAppsTopPadding,
                        workspaceProfile = workspaceProfile,
                        deviceProperties = deviceProperties,
                        displayOptionSpec = displayOptionSpec,
                    )
                }

                else -> {
                    createAllAppsProfileNonScalable(
                        res = res,
                        inv = inv,
                        metric = metric,
                        typeIndex = typeIndex,
                        scale = workspaceProfile.scale,
                        deviceProperties = deviceProperties,
                        context = context,
                        allAppsTopPadding = allAppsTopPadding,
                        workspaceProfile = workspaceProfile,
                        displayOptionSpec = displayOptionSpec,
                    )
                }
            }
    }
}
