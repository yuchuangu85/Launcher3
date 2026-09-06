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
import android.graphics.Point
import androidx.annotation.DimenRes
import androidx.annotation.StyleRes
import androidx.annotation.StyleableRes
import com.android.launcher3.Flags
import com.android.launcher3.GridType
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.DeviceType
import com.android.launcher3.InvariantDeviceProfile.TYPE_DESKTOP
import com.android.launcher3.R
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.parseTypedMap
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE
import com.android.launcher3.util.XmlElement
import com.android.launcher3.util.XmlElement.Companion.getRootElement
import kotlin.math.min

class GridOption private constructor(ta: TypedArray, displayInfo: LauncherDisplayInfo) {

    @JvmField val name: String = requireNotNull(ta.getString(R.styleable.GridDisplayOption_name))
    @JvmField val gridTitle: String? = ta.getString(R.styleable.GridDisplayOption_gridTitle)
    @JvmField val gridIconId: Int = ta.getResourceId(R.styleable.GridDisplayOption_gridIconId)

    private val gridSize = ta.getDefinedGridSizeSpecOrDefault(displayInfo)
    @JvmField val numRows: Int = gridSize.numRows
    @JvmField val numColumns: Int = gridSize.numColumns
    @JvmField val dbFile: String? = gridSize.dbFile
    @JvmField val defaultLayoutId: Int = gridSize.defaultLayoutId

    @JvmField
    val numSearchContainerColumns: Int =
        ta.getInt(R.styleable.GridDisplayOption_numSearchContainerColumns, numColumns)
    @JvmField
    val deviceCategory: Int =
        ta.getInt(R.styleable.GridDisplayOption_deviceCategory, DEVICE_CATEGORY_ANY)

    @GridType
    @JvmField
    val gridType: Int = ta.getInt(R.styleable.GridDisplayOption_gridType, GridType.GRID_TYPE_ANY)

    @JvmField
    val numFolderRows: IntArray =
        ta.parseTypedMap(
                numRows,
                R.styleable.GridDisplayOption_numFolderRows,
                R.styleable.GridDisplayOption_numFolderRowsLandscape,
                R.styleable.GridDisplayOption_numFolderRowsTwoPanelPortrait,
                R.styleable.GridDisplayOption_numFolderRowsTwoPanelLandscape,
            ) { index, v ->
                getInt(index, v)
            }
            .toIntArray()

    @JvmField
    val numFolderColumns: IntArray =
        ta.parseTypedMap(
                numColumns,
                R.styleable.GridDisplayOption_numFolderColumns,
                R.styleable.GridDisplayOption_numFolderColumnsLandscape,
                R.styleable.GridDisplayOption_numFolderColumnsTwoPanelPortrait,
                R.styleable.GridDisplayOption_numFolderColumnsTwoPanelLandscape,
            ) { index, v ->
                getInt(index, v)
            }
            .toIntArray()

    @StyleRes
    @JvmField
    val folderStyle: Int = ta.getResourceId(R.styleable.GridDisplayOption_folderStyle)

    @StyleRes
    @JvmField
    val cellStyle: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_cellStyle, R.style.CellStyleDefault)

    @StyleRes
    @JvmField
    val allAppsStyle: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_allAppsStyle, R.style.AllAppsStyleDefault)
    @JvmField val numAllAppsColumns: Int
    @JvmField
    val numAllAppsRowsForCellHeightCalculation: Int =
        ta.getInt(R.styleable.GridDisplayOption_numAllAppsRowsForCellHeightCalculation, numRows)
    @JvmField val numDatabaseAllAppsColumns: Int
    @JvmField
    val numHotseatIcons: Int = ta.getInt(R.styleable.GridDisplayOption_numHotseatIcons, numColumns)
    @JvmField
    val numDatabaseHotseatIcons: Int =
        ta.getInt(R.styleable.GridDisplayOption_numExtendedHotseatIcons, 2 * numHotseatIcons)

    @JvmField
    val inlineQsb: BooleanArray =
        ta.getInt(R.styleable.GridDisplayOption_inlineQsb, DONT_INLINE_QSB)
            .mapToFlagArray(
                INLINE_QSB_FOR_PORTRAIT,
                INLINE_QSB_FOR_LANDSCAPE,
                INLINE_QSB_FOR_TWO_PANEL_PORTRAIT,
                INLINE_QSB_FOR_TWO_PANEL_LANDSCAPE,
            )

    @DimenRes
    @JvmField
    val inlineNavButtonsEndSpacing: Int =
        ta.getResourceId(
            R.styleable.GridDisplayOption_inlineNavButtonsEndSpacing,
            R.dimen.taskbar_button_margin_default,
        )

    @JvmField
    val isScalable: Boolean = ta.getBoolean(R.styleable.GridDisplayOption_isScalable, false)
    @JvmField val mIsDualGrid = ta.getBoolean(R.styleable.GridDisplayOption_isDualGrid, false)
    @JvmField
    val devicePaddingId: Int = ta.getResourceId(R.styleable.GridDisplayOption_devicePaddingId)
    @JvmField
    val workspaceSpecsId: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_workspaceSpecsId, INVALID_RESOURCE_HANDLE)
    @JvmField
    val workspaceSpecsTwoPanelId: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_workspaceSpecsTwoPanelId, workspaceSpecsId)
    @JvmField
    val allAppsSpecsId: Int = ta.getResourceId(R.styleable.GridDisplayOption_allAppsSpecsId)
    @JvmField
    val allAppsSpecsTwoPanelId: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_allAppsSpecsTwoPanelId, allAppsSpecsId)
    @JvmField val folderSpecsId: Int = ta.getResourceId(R.styleable.GridDisplayOption_folderSpecsId)
    @JvmField
    val folderSpecsTwoPanelId: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_folderSpecsTwoPanelId, folderSpecsId)
    @JvmField
    val hotseatSpecsId: Int = ta.getResourceId(R.styleable.GridDisplayOption_hotseatSpecsId)
    @JvmField
    val hotseatSpecsTwoPanelId: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_hotseatSpecsTwoPanelId, hotseatSpecsId)
    @JvmField
    val workspaceCellSpecsId: Int =
        ta.getResourceId(
            R.styleable.GridDisplayOption_workspaceCellSpecsId,
            INVALID_RESOURCE_HANDLE,
        )
    @JvmField
    val workspaceCellSpecsTwoPanelId: Int =
        ta.getResourceId(
            R.styleable.GridDisplayOption_workspaceCellSpecsTwoPanelId,
            workspaceCellSpecsId,
        )
    @JvmField
    val allAppsCellSpecsId: Int =
        ta.getResourceId(R.styleable.GridDisplayOption_allAppsCellSpecsId, INVALID_RESOURCE_HANDLE)
    @JvmField
    val allAppsCellSpecsTwoPanelId: Int =
        ta.getResourceId(
            R.styleable.GridDisplayOption_allAppsCellSpecsTwoPanelId,
            allAppsCellSpecsId,
        )

    @JvmField
    val isFixedLandscape: Boolean =
        ta.getBoolean(R.styleable.GridDisplayOption_isFixedLandscape, false)

    // If non-negative, the index of workspace row with which the top of the all apps container
    // should be aligned with.
    @JvmField val allAppsAlignedWithWorkspaceRow: Int

    init {
        val allAppsSize = ta.getDefinedAllAppsSizeOrDefault(displayInfo, numColumns)
        numAllAppsColumns = allAppsSize.numColumns
        allAppsAlignedWithWorkspaceRow = allAppsSize.alignWithWorkspaceRow
        numDatabaseAllAppsColumns =
            ta.getInt(
                R.styleable.GridDisplayOption_numExtendedAllAppsColumns,
                2 * numAllAppsColumns,
            )
    }

    override fun toString(): String {
        return ("GridConfig{" +
            "name='" +
            name +
            '\'' +
            ", gridTitle='" +
            gridTitle +
            '\'' +
            ", gridIconId=" +
            gridIconId +
            ", numRows=" +
            numRows +
            ", numColumns=" +
            numColumns +
            ", gridType=" +
            gridType +
            ", mIsFixedLandscape=" +
            isFixedLandscape +
            '}')
    }

    fun isEnabled(@DeviceType deviceType: Int): Boolean {
        return when (deviceType) {
            InvariantDeviceProfile.TYPE_PHONE ->
                (deviceCategory and DEVICE_CATEGORY_PHONE) == DEVICE_CATEGORY_PHONE
            InvariantDeviceProfile.TYPE_TABLET ->
                (deviceCategory and DEVICE_CATEGORY_TABLET) == DEVICE_CATEGORY_TABLET
            InvariantDeviceProfile.TYPE_MULTI_DISPLAY ->
                ((deviceCategory and DEVICE_CATEGORY_MULTI_DISPLAY) ==
                    DEVICE_CATEGORY_MULTI_DISPLAY)

            InvariantDeviceProfile.TYPE_DESKTOP ->
                ((deviceCategory and DEVICE_CATEGORY_DESKTOP) == DEVICE_CATEGORY_DESKTOP)

            else -> false
        }
    }

    /** Returns true if the grid option should be used given the flags that are toggled on/off. */
    fun filterByFlag(deviceType: Int, isFixedLandscape: Boolean): Boolean {
        if (deviceType == TYPE_DESKTOP) {
            if (Flags.orientationFriendlyDesktopGridSpec()) {
                return ((gridType and GridType.GRID_TYPE_DUAL_OPTIMIZED_GRID) ==
                    GridType.GRID_TYPE_DUAL_OPTIMIZED_GRID)
            }
            return ((gridType and GridType.GRID_TYPE_LANDSCAPE_OPTIMIZED_GRID) ==
                GridType.GRID_TYPE_LANDSCAPE_OPTIMIZED_GRID)
        }

        if (deviceType == InvariantDeviceProfile.TYPE_TABLET) {
            return Flags.oneGridRotationHandling() == mIsDualGrid
        }

        // Here we return true if fixed landscape mode should be on.
        if (this.isFixedLandscape || isFixedLandscape) {
            return this.isFixedLandscape && isFixedLandscape
        }

        return true
    }

    private class GridSize(
        val numRows: Int,
        val numColumns: Int,
        val dbFile: String?,
        val defaultLayoutId: Int,
    )

    private class AllAppsSize(
        // Number of columns to be shown in all apps.
        val numColumns: Int,

        // The workspace row with which top of all apps container should be aligned with.
        // Negative value will be ignored, and cause all apps container to fill up vertical space.
        val alignWithWorkspaceRow: Int = -1,
    )

    companion object {
        const val TAG_NAME: String = "grid-option"

        private const val DEVICE_CATEGORY_PHONE = 1 shl 0
        private const val DEVICE_CATEGORY_TABLET = 1 shl 1
        private const val DEVICE_CATEGORY_MULTI_DISPLAY = 1 shl 2
        private const val DEVICE_CATEGORY_DESKTOP = 1 shl 3
        private const val DEVICE_CATEGORY_ANY =
            (DEVICE_CATEGORY_PHONE or
                DEVICE_CATEGORY_TABLET or
                DEVICE_CATEGORY_MULTI_DISPLAY or
                DEVICE_CATEGORY_DESKTOP)

        private const val INLINE_QSB_FOR_PORTRAIT = 1 shl 0
        private const val INLINE_QSB_FOR_LANDSCAPE = 1 shl 1
        private const val INLINE_QSB_FOR_TWO_PANEL_PORTRAIT = 1 shl 2
        private const val INLINE_QSB_FOR_TWO_PANEL_LANDSCAPE = 1 shl 3
        private const val DONT_INLINE_QSB = 0

        private fun findMinWidthAndHeightPxForDevice(displayInfo: LauncherDisplayInfo): Point {
            var minDisplayWidthPx = Int.MAX_VALUE
            var minDisplayHeightPx = Int.MAX_VALUE
            for (display in displayInfo.allDisplays) {
                minDisplayWidthPx = min(minDisplayWidthPx, display.size.x)
                minDisplayHeightPx = min(minDisplayHeightPx, display.size.y)
            }
            return Point(minDisplayWidthPx, minDisplayHeightPx)
        }

        /**
         * Returns An `AllAppsSize` spec with min width at most `targetWidthPx`. If multiple specs
         * are available, selects the one closest to the `targetWidthPx`.
         */
        private fun TypedArray.getDefinedAllAppsSizeOrDefault(
            displayInfo: LauncherDisplayInfo,
            numColumns: Int,
        ): AllAppsSize {
            val defaultColumns = getInt(R.styleable.GridDisplayOption_numAllAppsColumns, numColumns)
            if (!Flags.enableScalabilityForDesktopExperience()) return AllAppsSize(defaultColumns)

            val resId = getResourceId(R.styleable.GridDisplayOption_allAppsSizeSpecsId)
            if (resId == INVALID_RESOURCE_HANDLE) return AllAppsSize(defaultColumns)

            val stableDensityScale = displayInfo.getStableDensityScale()
            val minDeviceWidthPx = findMinWidthAndHeightPxForDevice(displayInfo).x
            displayInfo.context.resources.getXml(resId).use { xml ->
                return xml.getRootElement()
                    .children("AllAppsSize")
                    .map {
                        val ta = it.obtainAttrs(displayInfo.context, R.styleable.AllAppsSize)

                        val minWidth =
                            ta.getFloat(R.styleable.AllAppsSize_minDeviceWidthDp, 0f) *
                                stableDensityScale
                        (if (minWidth <= minDeviceWidthPx) {
                                AllAppsSize(
                                    numColumns =
                                        ta.getInt(
                                            R.styleable.AllAppsSize_allAppsColumns,
                                            defaultColumns,
                                        ),
                                    alignWithWorkspaceRow =
                                        ta.getInt(R.styleable.AllAppsSize_alignWithWorkspaceRow, -1),
                                )
                            } else null)
                            .also { ta.recycle() }
                    }
                    .filterNotNull()
                    .maxByOrNull { it.numColumns } ?: AllAppsSize(defaultColumns)
            }
        }

        private fun TypedArray.getDefinedGridSizeSpecOrDefault(
            displayInfo: LauncherDisplayInfo
        ): GridSize {
            return getGridSize(
                gridSizeSpecsId =
                    getResourceId(
                        R.styleable.GridDisplayOption_gridSizeSpecsId,
                        INVALID_RESOURCE_HANDLE,
                    ),
                displayInfo = displayInfo,
            )
                ?: GridSize(
                    numRows = getInt(R.styleable.GridDisplayOption_numRows, 0),
                    numColumns = getInt(R.styleable.GridDisplayOption_numColumns, 0),
                    dbFile = getString(R.styleable.GridDisplayOption_dbFile),
                    defaultLayoutId =
                        getResourceId(R.styleable.GridDisplayOption_defaultLayoutId, 0),
                )
        }

        /**
         * Parses through the xml to find GridSize specs. Then returns the biggest grid size that
         * fits the display dimensions. If no best grid size is found, return null.
         */
        private fun getGridSize(gridSizeSpecsId: Int, displayInfo: LauncherDisplayInfo): GridSize? {
            if (gridSizeSpecsId == INVALID_RESOURCE_HANDLE) return null
            val stableDensityScale = displayInfo.getStableDensityScale()
            // Finds the min width and height in px for all displays.
            val minSize = findMinWidthAndHeightPxForDevice(displayInfo)

            return displayInfo.context.resources.getXml(gridSizeSpecsId).use { xml ->
                xml.getRootElement()
                    .children("GridSize")
                    .map {
                        val ta = it.obtainAttrs(displayInfo.context, R.styleable.GridSize)

                        val minDeviceWidthPx =
                            (ta.getInt(R.styleable.GridSize_minDeviceWidthPx, 0) *
                                stableDensityScale)
                        val minDeviceHeightPx =
                            (ta.getInt(R.styleable.GridSize_minDeviceHeightPx, 0) *
                                stableDensityScale)

                        (if (minDeviceWidthPx <= minSize.x && minDeviceHeightPx <= minSize.y) {
                                GridSize(
                                    numRows = ta.getInt(R.styleable.GridSize_numGridRows, 0),
                                    numColumns = ta.getInt(R.styleable.GridSize_numGridColumns, 0),
                                    dbFile = ta.getString(R.styleable.GridSize_dbFile),
                                    defaultLayoutId =
                                        ta.getResourceId(R.styleable.GridSize_defaultLayoutId, 0),
                                )
                            } else {
                                null
                            })
                            .also { ta.recycle() }
                    }
                    .filterNotNull()
                    .reduceOrNull { a, b ->
                        if (a.numColumns <= b.numColumns && a.numRows <= b.numRows) b else a
                    }
            }
        }

        /**
         * Difference between grid sizes available for different display size breakpoints is more
         * stark on desktop devices, so using grid size matched against display pixel sizes results
         * in noticeable worse UI on devices with larger DPI. Compromise by matching grid size
         * breakpoints against pixel size for stable device density on desktop, to ensure optimal
         * grid size is selected for the default display size.
         *
         * TODO(b/420970288): Ideally, this should use the current DPI, and update grid content if
         *   the change in display size changes the grid size.
         */
        private fun LauncherDisplayInfo.getStableDensityScale(): Float {
            val matchAgainstDefaultDpSize =
                deviceType == TYPE_DESKTOP && Flags.enableScalabilityForDesktopExperience()
            return if (matchAgainstDefaultDpSize) stableDensityScaleFactor else 1.0f
        }

        private fun TypedArray.getResourceId(@StyleableRes index: Int) =
            getResourceId(index, INVALID_RESOURCE_HANDLE)

        private fun Int.mapToFlagArray(vararg mask: Int) =
            BooleanArray(mask.size) { (this and mask[it]) == mask[it] }

        /** @return all the grid options that can be shown on the device */
        fun <T> parseAllDefined(
            context: Context,
            displayInfo: LauncherDisplayInfo,
            filter: (GridOption) -> Boolean = { true },
            mapper: (GridOption, XmlElement) -> T,
        ): List<T> {
            return context.resources.getXml(R.xml.device_profiles).use { xml ->
                xml.getRootElement()
                    .children(TAG_NAME)
                    .mapNotNull { el ->
                        val ta = el.obtainAttrs(displayInfo.context, R.styleable.GridDisplayOption)
                        val option = GridOption(ta, displayInfo)
                        ta.recycle()

                        if (filter.invoke(option)) mapper.invoke(option, el) else null
                    }
                    .toList()
            }
        }

        @JvmStatic
        fun parseAllValid(
            context: Context,
            displayInfo: LauncherDisplayInfo,
            deviceType: Int,
            isFixedLandscape: Boolean,
        ) =
            parseAllDefined(
                context,
                displayInfo,
                filter = {
                    it.isEnabled(deviceType) && it.filterByFlag(deviceType, isFixedLandscape)
                },
                mapper = { it, _ -> it },
            )
    }
}
