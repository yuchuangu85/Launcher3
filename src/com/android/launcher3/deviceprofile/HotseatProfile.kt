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

import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities.getIconVisibleSizePx
import com.android.launcher3.deviceprofile.HotseatProfileInitialValues.Factory.calculateHotseatBarSizePx
import com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR
import kotlin.math.ceil
import kotlin.math.min

data class HotseatBorderAndSpace(var widthPx: Int, var columnSpan: Int, var borderSpace: Int)

data class HotseatWithBorderAndSpace(
    var widthPx: Int,
    var numShownIcons: Int,
    var columnSpan: Int,
    var qsbWidth: Int,
    var borderSpace: Int,
)

data class HotseatProfile(
    val areNavButtonsInline: Boolean,
    val navButtonsLayoutWidthPx: Int,
    val inlineNavButtonsEndSpacingPx: Int,
    val barEndOffset: Int,
    val springLoadedBarTopMarginPx: Int,
    val barEdgePaddingPx: Int,
    val barWorkspaceSpacePx: Int,
    val qsbHeight: Int,
    val qsbShadowHeight: Int,
    val qsbVisualHeight: Int,
    val minIconSpacePx: Int,
    val minQsbWidthPx: Int,
    val maxIconSpacePx: Int,
    val barBottomSpacePx: Int,
    val qsbSpace: Int,
    val cellHeightPx: Int,
    val barSizePx: Int,
    val widthPx: Int, // not used in vertical bar layout
    val numShownIcons: Int,
    val columnSpan: Int,
    val qsbWidth: Int, // only used when isQsbInline
    val borderSpace: Int,
    val isQsbInline: Boolean,
) {

    fun recalculateHotseatWidthAndBorderSpace(
        inv: InvariantDeviceProfile,
        deviceProfile: DeviceProfile,
        numShownHotseatIconsParam: Int,
    ): HotseatProfile {

        val hotseatProfileInitialValues =
            HotseatProfileInitialValues(
                areNavButtonsInline = areNavButtonsInline,
                navButtonsLayoutWidthPx = navButtonsLayoutWidthPx,
                inlineNavButtonsEndSpacingPx = inlineNavButtonsEndSpacingPx,
                barEndOffset = barEndOffset,
                springLoadedBarTopMarginPx = springLoadedBarTopMarginPx,
                barEdgePaddingPx = barEdgePaddingPx,
                barWorkspaceSpacePx = barWorkspaceSpacePx,
                qsbHeight = qsbHeight,
                qsbShadowHeight = qsbShadowHeight,
                qsbVisualHeight = qsbVisualHeight,
                minIconSpacePx = minIconSpacePx,
                minQsbWidthPx = minQsbWidthPx,
                maxIconSpacePx = maxIconSpacePx,
                barBottomSpacePx = barBottomSpacePx,
                qsbSpace = qsbSpace,
                barSizePx = barSizePx,
                isQsbInline = isQsbInline,
            )

        val hotseatWithBorderAndSpace =
            Factory.recalculateHotseatWidthAndBorderSpace(
                inv = inv,
                hotseatProfileInitialValues = hotseatProfileInitialValues,
                workspaceProfile = deviceProfile.workspaceProfile,
                deviceProperties = deviceProfile.deviceProperties,
                panelCount = deviceProfile.panelCount,
                isScalableGrid = false,
                isVerticalBarLayout = deviceProfile.isVerticalBarLayout,
                numShownHotseatIconsParam = numShownHotseatIconsParam,
            )

        return copy(
            widthPx = hotseatWithBorderAndSpace.widthPx,
            numShownIcons = hotseatWithBorderAndSpace.numShownIcons,
            columnSpan = hotseatWithBorderAndSpace.columnSpan,
            qsbWidth = hotseatWithBorderAndSpace.qsbWidth,
            borderSpace = hotseatWithBorderAndSpace.borderSpace,
        )
    }

    companion object Factory {

        /**
         * QSB width is always calculated because when in 3 button nav the width doesn't follow the
         * width of the hotseat.
         */
        private fun calculateQsbWidth(
            borderAndSpace: HotseatBorderAndSpace,
            workspaceProfile: WorkspaceProfile,
            inv: InvariantDeviceProfile,
            panelCount: Int,
            numShownHotseatIcons: Int,
            isQsbInline: Boolean,
        ): Int {
            val iconExtraSpacePx: Int =
                workspaceProfile.iconSizePx - getIconVisibleSizePx(workspaceProfile.iconSizePx)
            return if (isQsbInline) {
                (workspaceProfile.getIconToIconWidthForColumns(panelCount * inv.numColumns) -
                    workspaceProfile.iconSizePx * numShownHotseatIcons -
                    borderAndSpace.borderSpace * numShownHotseatIcons -
                    iconExtraSpacePx)
            } else {
                (workspaceProfile.getIconToIconWidthForColumns(borderAndSpace.columnSpan) -
                    iconExtraSpacePx)
            }
        }

        fun recalculateHotseatWidthAndBorderSpace(
            inv: InvariantDeviceProfile,
            hotseatProfileInitialValues: HotseatProfileInitialValues,
            workspaceProfile: WorkspaceProfile,
            deviceProperties: DeviceProperties,
            panelCount: Int,
            isScalableGrid: Boolean,
            isVerticalBarLayout: Boolean,
            numShownHotseatIconsParam: Int,
        ): HotseatWithBorderAndSpace {
            if (!isScalableGrid)
                return HotseatWithBorderAndSpace(
                    widthPx = 0,
                    numShownIcons = numShownHotseatIconsParam,
                    columnSpan = inv.numColumns,
                    qsbWidth = 0,
                    borderSpace = 0,
                )

            var numShownHotseatIcons = numShownHotseatIconsParam
            var borderAndSpace =
                updateHotseatWidthAndBorderSpace(
                    inv.numColumns,
                    workspaceProfile = workspaceProfile,
                    numShownHotseatIcons = numShownHotseatIcons,
                    maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                )
            val numWorkspaceColumns: Int = panelCount * inv.numColumns
            if (deviceProperties.isTwoPanels) {
                borderAndSpace =
                    updateHotseatWidthAndBorderSpace(
                        inv.numDatabaseHotseatIcons,
                        workspaceProfile = workspaceProfile,
                        numShownHotseatIcons = numShownHotseatIcons,
                        maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                    )
                // If hotseat doesn't fit with current width, increase column span to fit by
                // multiple
                // of 2.
                while (
                    borderAndSpace.borderSpace < hotseatProfileInitialValues.minIconSpacePx &&
                        borderAndSpace.columnSpan < numWorkspaceColumns
                ) {
                    borderAndSpace =
                        updateHotseatWidthAndBorderSpace(
                            borderAndSpace.columnSpan + 2,
                            workspaceProfile = workspaceProfile,
                            numShownHotseatIcons = numShownHotseatIcons,
                            maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                        )
                }
            }
            if (hotseatProfileInitialValues.isQsbInline) {
                // If QSB is inline, reduce column span until it fits.
                val maxHotseatWidthAllowedPx: Int =
                    workspaceProfile.getIconToIconWidthForColumns(numWorkspaceColumns)
                var minHotseatWidthRequiredPx: Int =
                    hotseatProfileInitialValues.minQsbWidthPx +
                        borderAndSpace.borderSpace +
                        borderAndSpace.widthPx
                while (
                    minHotseatWidthRequiredPx > maxHotseatWidthAllowedPx &&
                        borderAndSpace.columnSpan > 1
                ) {
                    borderAndSpace =
                        updateHotseatWidthAndBorderSpace(
                            borderAndSpace.columnSpan - 1,
                            workspaceProfile = workspaceProfile,
                            numShownHotseatIcons = numShownHotseatIcons,
                            maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                        )
                    minHotseatWidthRequiredPx =
                        (hotseatProfileInitialValues.minQsbWidthPx +
                            borderAndSpace.borderSpace +
                            borderAndSpace.widthPx)
                }
            }
            var hotseatQsbWidth =
                calculateQsbWidth(
                    borderAndSpace = borderAndSpace,
                    workspaceProfile = workspaceProfile,
                    inv = inv,
                    panelCount = panelCount,
                    numShownHotseatIcons = numShownHotseatIcons,
                    isQsbInline = hotseatProfileInitialValues.isQsbInline,
                )

            // Spaces should be correct when the nav buttons are not inline
            if (!hotseatProfileInitialValues.areNavButtonsInline) {
                return HotseatWithBorderAndSpace(
                    widthPx = borderAndSpace.widthPx,
                    numShownIcons = numShownHotseatIcons,
                    columnSpan = borderAndSpace.columnSpan,
                    qsbWidth = hotseatQsbWidth,
                    borderSpace = borderAndSpace.borderSpace,
                )
            }

            // The side space with inline buttons should be what is defined in
            // InvariantDeviceProfile
            val sideSpacePx: Int = hotseatProfileInitialValues.inlineNavButtonsEndSpacingPx
            val maxHotseatWidthPx: Int =
                (deviceProperties.availableWidthPx -
                    sideSpacePx -
                    hotseatProfileInitialValues.barEndOffset)
            var maxHotseatIconsWidthPx: Int =
                maxHotseatWidthPx -
                    (if (hotseatProfileInitialValues.isQsbInline) hotseatQsbWidth else 0)
            borderAndSpace.borderSpace =
                calculateHotseatBorderSpace(
                    maxHotseatIconsWidthPx,
                    (if (hotseatProfileInitialValues.isQsbInline) 1
                    else 0) + /* border between nav buttons and first icon */ 1,
                    numShownHotseatIcons = numShownHotseatIcons,
                    workspaceProfile = workspaceProfile,
                    maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                )

            if (borderAndSpace.borderSpace >= hotseatProfileInitialValues.minIconSpacePx) {
                return HotseatWithBorderAndSpace(
                    widthPx = borderAndSpace.widthPx,
                    numShownIcons = numShownHotseatIcons,
                    columnSpan = borderAndSpace.columnSpan,
                    qsbWidth = hotseatQsbWidth,
                    borderSpace = borderAndSpace.borderSpace,
                )
            }

            // Border space can't be less than the minimum
            borderAndSpace.borderSpace = hotseatProfileInitialValues.minIconSpacePx

            // additionalQsbSpace
            val additionalQsbSpace =
                if (hotseatProfileInitialValues.isQsbInline)
                    (hotseatQsbWidth + borderAndSpace.borderSpace)
                else 0
            val requiredWidth =
                (workspaceProfile.iconSizePx * numShownHotseatIcons +
                    borderAndSpace.borderSpace *
                        (numShownHotseatIcons -
                            (if (hotseatProfileInitialValues.areNavButtonsInline) 0 else 1)) +
                    additionalQsbSpace)

            // If there is an inline qsb, change its size
            if (hotseatProfileInitialValues.isQsbInline) {
                hotseatQsbWidth -= requiredWidth - maxHotseatWidthPx
                if (hotseatQsbWidth >= hotseatProfileInitialValues.minQsbWidthPx) {
                    return HotseatWithBorderAndSpace(
                        widthPx = borderAndSpace.widthPx,
                        numShownIcons = numShownHotseatIcons,
                        columnSpan = borderAndSpace.columnSpan,
                        qsbWidth = hotseatQsbWidth,
                        borderSpace = borderAndSpace.borderSpace,
                    )
                }

                // QSB can't be less than the minimum
                hotseatQsbWidth = hotseatProfileInitialValues.minQsbWidthPx
            }

            maxHotseatIconsWidthPx =
                maxHotseatWidthPx -
                    (if (hotseatProfileInitialValues.isQsbInline) hotseatQsbWidth else 0)

            // If it still doesn't fit, start removing icons
            do {
                numShownHotseatIcons--
                borderAndSpace.borderSpace =
                    calculateHotseatBorderSpace(
                        maxHotseatIconsWidthPx,
                        (if (hotseatProfileInitialValues.isQsbInline) 1
                        else 0) + /* border between nav buttons and first icon */ 1,
                        numShownHotseatIcons = numShownHotseatIcons,
                        workspaceProfile = workspaceProfile,
                        maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                    )
            } while (
                borderAndSpace.borderSpace < hotseatProfileInitialValues.minIconSpacePx &&
                    numShownHotseatIcons > 1
            )

            return HotseatWithBorderAndSpace(
                widthPx = borderAndSpace.widthPx,
                numShownIcons = numShownHotseatIcons,
                columnSpan = borderAndSpace.columnSpan,
                qsbWidth = hotseatQsbWidth,
                borderSpace =
                    if (isVerticalBarLayout) {
                        workspaceProfile.cellLayoutBorderSpacePx.y
                    } else {
                        borderAndSpace.borderSpace
                    },
            )
        }

        private fun updateHotseatWidthAndBorderSpace(
            columns: Int,
            workspaceProfile: WorkspaceProfile,
            numShownHotseatIcons: Int,
            maxIconSpacePx: Int,
        ): HotseatBorderAndSpace {
            val hotseatWidthPx = workspaceProfile.getIconToIconWidthForColumns(columns)
            return HotseatBorderAndSpace(
                columnSpan = columns,
                widthPx = hotseatWidthPx,
                borderSpace =
                    calculateHotseatBorderSpace(
                        hotseatWidthPx,
                        /* numExtraBorder= */ 0,
                        numShownHotseatIcons,
                        workspaceProfile,
                        maxIconSpacePx,
                    ),
            )
        }

        /** This method calculates the space between the icons to achieve a certain width. */
        fun calculateHotseatBorderSpace(
            hotseatWidthPx: Int,
            numExtraBorder: Int,
            numShownHotseatIcons: Int,
            workspaceProfile: WorkspaceProfile,
            maxIconSpacePx: Int,
        ): Int {
            val numBorders: Int = (numShownHotseatIcons - 1 + numExtraBorder)
            if (numBorders <= 0) return 0
            val hotseatIconsTotalPx = workspaceProfile.iconSizePx * numShownHotseatIcons
            val hotseatBorderSpacePx = (hotseatWidthPx - hotseatIconsTotalPx) / numBorders
            return min(hotseatBorderSpacePx, maxIconSpacePx)
        }

        fun createHotseatProfile(
            hotseatProfileInitialValues: HotseatProfileInitialValues,
            workspaceProfile: WorkspaceProfile,
            isVerticalBarLayout: Boolean,
            inv: InvariantDeviceProfile,
            displayOptionSpec: InvariantDeviceProfile.DisplayOptionSpec,
            deviceProperties: DeviceProperties,
            panelCount: Int,
            mIsScalableGrid: Boolean,
        ): HotseatProfile {

            val hotseatWithBorderAndSpace =
                recalculateHotseatWidthAndBorderSpace(
                    inv = inv,
                    hotseatProfileInitialValues = hotseatProfileInitialValues,
                    workspaceProfile = workspaceProfile,
                    deviceProperties = deviceProperties,
                    panelCount = panelCount,
                    isScalableGrid = mIsScalableGrid,
                    isVerticalBarLayout = isVerticalBarLayout,
                    numShownHotseatIconsParam = displayOptionSpec.numShownHotseatIcons,
                )

            return HotseatProfile(
                areNavButtonsInline = hotseatProfileInitialValues.areNavButtonsInline,
                navButtonsLayoutWidthPx = hotseatProfileInitialValues.navButtonsLayoutWidthPx,
                inlineNavButtonsEndSpacingPx =
                    hotseatProfileInitialValues.inlineNavButtonsEndSpacingPx,
                barEndOffset = hotseatProfileInitialValues.barEndOffset,
                springLoadedBarTopMarginPx = hotseatProfileInitialValues.springLoadedBarTopMarginPx,
                barEdgePaddingPx = hotseatProfileInitialValues.barEdgePaddingPx,
                barWorkspaceSpacePx = hotseatProfileInitialValues.barWorkspaceSpacePx,
                qsbHeight = hotseatProfileInitialValues.qsbHeight,
                qsbShadowHeight = hotseatProfileInitialValues.qsbShadowHeight,
                qsbVisualHeight = hotseatProfileInitialValues.qsbVisualHeight,
                minIconSpacePx = hotseatProfileInitialValues.minIconSpacePx,
                minQsbWidthPx = hotseatProfileInitialValues.minQsbWidthPx,
                maxIconSpacePx = hotseatProfileInitialValues.maxIconSpacePx,
                barBottomSpacePx = hotseatProfileInitialValues.barBottomSpacePx,
                qsbSpace = hotseatProfileInitialValues.qsbSpace,
                cellHeightPx = ceil(workspaceProfile.iconSizePx * ICON_OVERLAP_FACTOR).toInt(),
                barSizePx =
                    calculateHotseatBarSizePx(
                        hotseatIconSizePx = workspaceProfile.iconSizePx,
                        barEdgePaddingPx = hotseatProfileInitialValues.barEdgePaddingPx,
                        isVerticalBarLayout = isVerticalBarLayout,
                        barWorkspaceSpacePx = hotseatProfileInitialValues.barWorkspaceSpacePx,
                        qsbVisualHeight = hotseatProfileInitialValues.qsbVisualHeight,
                        barBottomSpacePx = hotseatProfileInitialValues.barBottomSpacePx,
                        qsbSpace = hotseatProfileInitialValues.qsbSpace,
                        isQsbInline = hotseatProfileInitialValues.isQsbInline,
                    ),
                widthPx = hotseatWithBorderAndSpace.widthPx,
                numShownIcons = hotseatWithBorderAndSpace.numShownIcons,
                columnSpan = hotseatWithBorderAndSpace.columnSpan,
                qsbWidth = hotseatWithBorderAndSpace.qsbWidth,
                borderSpace = hotseatWithBorderAndSpace.borderSpace,
                isQsbInline = hotseatProfileInitialValues.isQsbInline,
            )
        }
    }
}
