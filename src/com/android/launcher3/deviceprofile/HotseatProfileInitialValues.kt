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

import android.content.res.Resources
import android.util.DisplayMetrics
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_DEFAULT
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_LANDSCAPE
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_TWO_PANEL_LANDSCAPE
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_TWO_PANEL_PORTRAIT
import com.android.launcher3.responsive.CalculatedCellSpec
import com.android.launcher3.responsive.CalculatedHotseatSpec
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp
import kotlin.math.max

/**
 * TODO(431261051): Either HotseatProfile depends on the icon size or the icon size depends on the
 *   HotseatProfile, this circular dependency is a mistake and should be resolved.
 *
 * Class created to break a circular dependency with the WorkspaceProfile that relies on this values
 * but the HotseatProfile also depends on the values of the WorkspaceProfile. So to break this
 * circular dependency and make this relationship obvious we created this class.
 */
data class HotseatProfileInitialValues(
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
    val barSizePx: Int,
    val isQsbInline: Boolean,
) {

    companion object Factory {

        /** Updates hotseatCellHeightPx and hotseatBarSizePx */
        fun calculateHotseatBarSizePx(
            hotseatIconSizePx: Int,
            barEdgePaddingPx: Int,
            isVerticalBarLayout: Boolean,
            barWorkspaceSpacePx: Int,
            qsbVisualHeight: Int,
            barBottomSpacePx: Int,
            qsbSpace: Int,
            isQsbInline: Boolean,
        ): Int {
            if (isVerticalBarLayout) {
                return (hotseatIconSizePx + barEdgePaddingPx + barWorkspaceSpacePx)
            } else if (isQsbInline) {
                return (max(hotseatIconSizePx, qsbVisualHeight) + barBottomSpacePx)
            } else {
                return (hotseatIconSizePx + qsbSpace + qsbVisualHeight + barBottomSpacePx)
            }
        }

        fun createHotseatProfileInitialValues(
            deviceProperties: DeviceProperties,
            res: Resources,
            inv: InvariantDeviceProfile,
            shouldApplyWidePortraitDimens: Boolean,
            responsiveHotseatSpec: CalculatedHotseatSpec?,
            typeIndex: Int,
            metrics: DisplayMetrics,
            isVerticalBarLayout: Boolean,
            workspacePageIndicatorHeight: Int,
            responsiveWorkspaceCellSpec: CalculatedCellSpec?,
            qsbHeight: Int,
        ): HotseatProfileInitialValues {
            return when {
                responsiveHotseatSpec != null && responsiveWorkspaceCellSpec != null ->
                    createResponsiveHotseatProfileInitialValues(
                        deviceProperties = deviceProperties,
                        res = res,
                        inv = inv,
                        shouldApplyWidePortraitDimens = shouldApplyWidePortraitDimens,
                        responsiveHotseatSpec = responsiveHotseatSpec,
                        responsiveWorkspaceCellSpec = responsiveWorkspaceCellSpec,
                        isVerticalBarLayout = isVerticalBarLayout,
                        typeIndex = typeIndex,
                        qsbHeight = qsbHeight,
                    )
                else ->
                    createNonResponsiveHotseatProfileInitialValues(
                        deviceProperties = deviceProperties,
                        res = res,
                        inv = inv,
                        shouldApplyWidePortraitDimens = shouldApplyWidePortraitDimens,
                        typeIndex = typeIndex,
                        metrics = metrics,
                        isVerticalBarLayout = isVerticalBarLayout,
                        workspacePageIndicatorHeight = workspacePageIndicatorHeight,
                        qsbHeight = qsbHeight,
                    )
            }
        }

        fun createResponsiveHotseatProfileInitialValues(
            deviceProperties: DeviceProperties,
            res: Resources,
            inv: InvariantDeviceProfile,
            shouldApplyWidePortraitDimens: Boolean,
            responsiveHotseatSpec: CalculatedHotseatSpec,
            isVerticalBarLayout: Boolean,
            responsiveWorkspaceCellSpec: CalculatedCellSpec,
            qsbHeight: Int,
            typeIndex: Int,
        ): HotseatProfileInitialValues {

            // For foldable (two panel), we inline the qsb if we have the screen open and we are in
            // either Landscape or Portrait. This cal also be disabled in the device_profile.xml
            val twoPanelCanInline =
                inv.inlineQsb[INDEX_TWO_PANEL_PORTRAIT] || inv.inlineQsb[INDEX_TWO_PANEL_LANDSCAPE]
            // In tablets we inline in both orientations but only if we have enough space in the QSB
            val tabletInlineQsb = inv.inlineQsb[INDEX_DEFAULT] || inv.inlineQsb[INDEX_LANDSCAPE]
            var canQsbInline =
                if (deviceProperties.isTwoPanels) twoPanelCanInline else tabletInlineQsb
            canQsbInline = canQsbInline && qsbHeight > 0

            val isQsbInline = (inv.inlineQsb[typeIndex] && canQsbInline) || inv.isFixedLandscape

            val areNavButtonsInline =
                deviceProperties.taskbarConfiguration.isTaskbarPresent &&
                    !deviceProperties.deviceConfiguration.isGestureMode
            var inlineNavButtonsEndSpacingPx = 0
            var navButtonsLayoutWidthPx = 0
            var barEndOffset = 0
            // 3 nav buttons + Spacing between nav buttons
            if (areNavButtonsInline && !deviceProperties.isPhone) {
                inlineNavButtonsEndSpacingPx =
                    res.getDimensionPixelSize(inv.inlineNavButtonsEndSpacing)
                /* 3 nav buttons + Spacing between nav buttons */
                navButtonsLayoutWidthPx =
                    3 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size) +
                        2 * res.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween)
                barEndOffset = navButtonsLayoutWidthPx + inlineNavButtonsEndSpacingPx
            }
            val springLoadedHotseatBarTopMarginPx =
                if (shouldApplyWidePortraitDimens)
                    res.getDimensionPixelSize(
                        R.dimen.spring_loaded_hotseat_top_margin_wide_portrait
                    )
                else res.getDimensionPixelSize(R.dimen.spring_loaded_hotseat_top_margin)

            val hotseatQsbHeight = res.getDimensionPixelSize(R.dimen.qsb_widget_height)
            val hotseatQsbShadowHeight = res.getDimensionPixelSize(R.dimen.qsb_shadow_height)

            var hotseatQsbSpace: Int = responsiveHotseatSpec.hotseatQsbSpace
            val hotseatBarBottomSpace: Int = responsiveHotseatSpec.edgePadding
            var minQsbMargin = res.getDimensionPixelSize(R.dimen.min_qsb_margin)

            var barBottomSpacePx = 0
            // Have a little space between the inset and the QSB
            if (deviceProperties.insets.bottom + minQsbMargin > hotseatBarBottomSpace) {
                val availableSpace: Int =
                    hotseatQsbSpace - (deviceProperties.insets.bottom - hotseatBarBottomSpace)

                // Only change the spaces if there is space
                if (availableSpace > 0) {
                    // Make sure there is enough space between hotseat/QSB and QSB/navBar
                    if (availableSpace < minQsbMargin * 2) {
                        minQsbMargin = availableSpace / 2
                        hotseatQsbSpace = minQsbMargin
                    } else {
                        hotseatQsbSpace -= minQsbMargin
                    }
                }
                barBottomSpacePx = deviceProperties.insets.bottom + minQsbMargin
            } else {
                barBottomSpacePx = hotseatBarBottomSpace
            }

            // qsbVisualHeight is the total height of the QSB view, we leave some space at the top
            // and bottom for shadows, similar to the icon shadows.
            // This (2 * hotseatQsbShadowHeight) is to account for the top space and the bottom.
            val qsbVisualHeight = hotseatQsbHeight - (2 * hotseatQsbShadowHeight)
            return HotseatProfileInitialValues(
                areNavButtonsInline = areNavButtonsInline,
                navButtonsLayoutWidthPx = navButtonsLayoutWidthPx,
                inlineNavButtonsEndSpacingPx = inlineNavButtonsEndSpacingPx,
                barEndOffset = barEndOffset,
                springLoadedBarTopMarginPx = springLoadedHotseatBarTopMarginPx,
                barEdgePaddingPx = 0,
                barWorkspaceSpacePx = 0,
                qsbHeight = hotseatQsbHeight,
                qsbShadowHeight = hotseatQsbShadowHeight,
                qsbVisualHeight = qsbVisualHeight,
                minIconSpacePx = res.getDimensionPixelSize(R.dimen.min_hotseat_icon_space),
                minQsbWidthPx = res.getDimensionPixelSize(R.dimen.min_hotseat_qsb_width),
                maxIconSpacePx =
                    if (areNavButtonsInline)
                        res.getDimensionPixelSize(R.dimen.max_hotseat_icon_space)
                    else Int.MAX_VALUE,
                barBottomSpacePx = barBottomSpacePx,
                qsbSpace = hotseatQsbSpace,
                barSizePx =
                    calculateHotseatBarSizePx(
                        hotseatIconSizePx = responsiveWorkspaceCellSpec.iconSize,
                        barEdgePaddingPx = 0,
                        isVerticalBarLayout = isVerticalBarLayout,
                        barWorkspaceSpacePx = 0,
                        qsbVisualHeight = qsbVisualHeight,
                        barBottomSpacePx = barBottomSpacePx,
                        qsbSpace = hotseatQsbSpace,
                        isQsbInline = isQsbInline,
                    ),
                isQsbInline = isQsbInline,
            )
        }

        fun createNonResponsiveHotseatProfileInitialValues(
            deviceProperties: DeviceProperties,
            res: Resources,
            inv: InvariantDeviceProfile,
            shouldApplyWidePortraitDimens: Boolean,
            typeIndex: Int,
            metrics: DisplayMetrics,
            isVerticalBarLayout: Boolean,
            workspacePageIndicatorHeight: Int,
            qsbHeight: Int,
        ): HotseatProfileInitialValues {
            // For foldable (two panel), we inline the qsb if we have the screen open and we are in
            // either Landscape or Portrait. This cal also be disabled in the device_profile.xml
            val twoPanelCanInline =
                inv.inlineQsb[INDEX_TWO_PANEL_PORTRAIT] || inv.inlineQsb[INDEX_TWO_PANEL_LANDSCAPE]

            // In tablets we inline in both orientations but only if we have enough space in the QSB
            val tabletInlineQsb = inv.inlineQsb[INDEX_DEFAULT] || inv.inlineQsb[INDEX_LANDSCAPE]
            val canQsbInline =
                (if (deviceProperties.isTwoPanels) twoPanelCanInline else tabletInlineQsb) &&
                    qsbHeight > 0

            val isQsbInline = (inv.inlineQsb[typeIndex] && canQsbInline) || inv.isFixedLandscape

            val areNavButtonsInline =
                deviceProperties.taskbarConfiguration.isTaskbarPresent &&
                    !deviceProperties.deviceConfiguration.isGestureMode
            var inlineNavButtonsEndSpacingPx = 0
            var navButtonsLayoutWidthPx = 0
            var barEndOffset = 0
            // 3 nav buttons + Spacing between nav buttons
            if (areNavButtonsInline && !deviceProperties.isPhone) {
                inlineNavButtonsEndSpacingPx =
                    res.getDimensionPixelSize(inv.inlineNavButtonsEndSpacing)
                /* 3 nav buttons + Spacing between nav buttons */
                navButtonsLayoutWidthPx =
                    3 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size) +
                        2 * res.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween)
                barEndOffset = navButtonsLayoutWidthPx + inlineNavButtonsEndSpacingPx
            }
            val springLoadedHotseatBarTopMarginPx =
                if (shouldApplyWidePortraitDimens)
                    res.getDimensionPixelSize(
                        R.dimen.spring_loaded_hotseat_top_margin_wide_portrait
                    )
                else res.getDimensionPixelSize(R.dimen.spring_loaded_hotseat_top_margin)
            val hotseatBarEdgePaddingPx =
                when {
                    isVerticalBarLayout -> workspacePageIndicatorHeight
                    else -> 0
                }
            val hotseatBarWorkspaceSpacePx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_side_padding)
            val hotseatQsbHeight = res.getDimensionPixelSize(R.dimen.qsb_widget_height)
            val hotseatQsbShadowHeight = res.getDimensionPixelSize(R.dimen.qsb_shadow_height)

            var hotseatQsbSpace = pxFromDp(inv.hotseatQsbSpace[typeIndex], metrics)
            var hotseatBarBottomSpace = pxFromDp(inv.hotseatBarBottomSpace[typeIndex], metrics)

            var minQsbMargin = res.getDimensionPixelSize(R.dimen.min_qsb_margin)

            var barBottomSpacePx = 0
            // Have a little space between the inset and the QSB
            if (deviceProperties.insets.bottom + minQsbMargin > hotseatBarBottomSpace) {
                val availableSpace: Int =
                    hotseatQsbSpace - (deviceProperties.insets.bottom - hotseatBarBottomSpace)

                // Only change the spaces if there is space
                if (availableSpace > 0) {
                    // Make sure there is enough space between hotseat/QSB and QSB/navBar
                    if (availableSpace < minQsbMargin * 2) {
                        minQsbMargin = availableSpace / 2
                        hotseatQsbSpace = minQsbMargin
                    } else {
                        hotseatQsbSpace -= minQsbMargin
                    }
                }
                barBottomSpacePx = deviceProperties.insets.bottom + minQsbMargin
            } else {
                barBottomSpacePx = hotseatBarBottomSpace
            }

            if (isVerticalBarLayout) {
                barBottomSpacePx = 0
                hotseatQsbSpace = pxFromDp(inv.hotseatQsbSpace[typeIndex], metrics)
            }

            val barEdgePaddingPx = hotseatBarEdgePaddingPx
            val barWorkspaceSpacePx = hotseatBarWorkspaceSpacePx
            val qsbVisualHeight = hotseatQsbHeight - 2 * hotseatQsbShadowHeight
            return HotseatProfileInitialValues(
                areNavButtonsInline = areNavButtonsInline,
                navButtonsLayoutWidthPx = navButtonsLayoutWidthPx,
                inlineNavButtonsEndSpacingPx = inlineNavButtonsEndSpacingPx,
                barEndOffset = barEndOffset,
                springLoadedBarTopMarginPx = springLoadedHotseatBarTopMarginPx,
                barEdgePaddingPx = barEdgePaddingPx,
                barWorkspaceSpacePx = barWorkspaceSpacePx,
                qsbHeight = hotseatQsbHeight,
                qsbShadowHeight = hotseatQsbShadowHeight,
                qsbVisualHeight = qsbVisualHeight,
                minIconSpacePx = res.getDimensionPixelSize(R.dimen.min_hotseat_icon_space),
                minQsbWidthPx = res.getDimensionPixelSize(R.dimen.min_hotseat_qsb_width),
                maxIconSpacePx =
                    if (areNavButtonsInline)
                        res.getDimensionPixelSize(R.dimen.max_hotseat_icon_space)
                    else Int.MAX_VALUE,
                barBottomSpacePx = barBottomSpacePx,
                qsbSpace = hotseatQsbSpace,
                barSizePx =
                    calculateHotseatBarSizePx(
                        hotseatIconSizePx = pxFromDp(inv.iconSize[typeIndex], metrics),
                        barEdgePaddingPx = barEdgePaddingPx,
                        isVerticalBarLayout = isVerticalBarLayout,
                        barWorkspaceSpacePx = barWorkspaceSpacePx,
                        qsbVisualHeight = qsbVisualHeight,
                        barBottomSpacePx = barBottomSpacePx,
                        qsbSpace = hotseatQsbSpace,
                        isQsbInline = isQsbInline,
                    ),
                isQsbInline = isQsbInline,
            )
        }
    }
}
