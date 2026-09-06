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

package com.android.launcher3.celllayout

import android.content.Context
import android.graphics.Point
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.DeniedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.CellLayout
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherAppState.Companion.getIDP
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutTestCaseReader.Board
import com.android.launcher3.celllayout.CellLayoutTestCaseReader.TestSection
import com.android.launcher3.celllayout.board.CellLayoutBoard
import com.android.launcher3.celllayout.board.TestWorkspaceBuilder
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.integration.util.TestUtils.getWidgetAtCell
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.ModelTestExtensions.clearModelDb
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.resize.AppWidgetResizeFrameCompose
import com.android.launcher3.widget.resize.Edge
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An integration test that verifies integration between AppWidgetResizeFrameCompose, ResizeManager
 * & cellLayout / grid by performing resize on a widget.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS)
@DeniedDevices(denied = [DeviceProduct.ROBOLECTRIC])
class IntegrationResizeWidgetsTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @get:Rule var launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private var targetContext: Context = getInstrumentation().targetContext

    private lateinit var workspaceBuilder: TestWorkspaceBuilder
    private lateinit var resizeFrame: AppWidgetResizeFrameCompose
    private lateinit var widgetView: LauncherAppWidgetHostView
    private lateinit var cellLayout: CellLayout
    private var touchTargetSize: Int = 0

    @Before
    fun setup() {
        workspaceBuilder = TestWorkspaceBuilder(targetContext)
        touchTargetSize =
            targetContext.resources.getDimensionPixelSize(R.dimen.resize_frame_touch_target_size)
    }

    @After
    fun tearDown() {
        LauncherAppState.getInstance(context).model.clearModelDb()
    }

    @Test
    fun simpleResize() =
        runTest(timeout = TIMEOUT) {
            runTestCaseMap(getTestMap("ResizeWidgets/simple_resize_case"))
        }

    @Test
    fun resizeWithSibling_hasSpace_movesSiblings() =
        runTest(timeout = TIMEOUT) {
            runTestCaseMap(getTestMap("ResizeWidgets/resize_with_reorder_case"))
        }

    @Test
    fun invalidResize_noChange() =
        runTest(timeout = TIMEOUT) {
            runTestCaseMap(getTestMap("ResizeWidgets/resize_invalid_case"))
        }

    private fun workspaceToBoard(): CellLayoutBoard =
        checkNotNull(
            launcherActivity.getFromLauncher { launcher ->
                CellLayoutTestUtils.workspaceToBoards(launcher)[0]
            }
        )

    private fun runTestCase(testCase: ResizeTestCase) {
        launcherActivity.initializeActivity()

        // Initialize the board
        val layout = LauncherLayoutBuilder()
        val startBoard = CellLayoutBoard.boardFromString(testCase.startBoard)
        workspaceBuilder.buildFromBoard(startBoard, layout, 0)
        targetContext.setModelLayout(layout)
        getInstrumentation().waitForIdleSync()

        // Get the widget under test
        launcherActivity.executeOnLauncher {
            widgetView =
                getWidgetAtCell(
                    workspace = it.workspace,
                    cellX = testCase.widgetCellX,
                    cellY = testCase.widgetCellY,
                )

            Assume.assumeTrue(
                "Pending widgets cannot be resized",
                widgetView !is PendingAppWidgetHostView,
            )
            cellLayout = checkNotNull(it.workspace.getPageAt(0) as CellLayout)
            resizeFrame = AppWidgetResizeFrameCompose.show(it, widgetView, cellLayout)
        }
        getInstrumentation().waitForIdleSync()

        val viewModel = resizeFrame.getViewModelForTest()
        // Simulate a drag
        launcherActivity.executeOnLauncher {
            val dragAmount = identifyDragOffset(testCase, cellLayout, testCase.cellsToDrag)
            val handleCenterOffset = getHandleCenterOffset(resizeFrame, testCase.handle)
            viewModel.onDragStart(
                testCase.handle,
                handleCenterOffset,
                IntSize(resizeFrame.width, resizeFrame.height),
            )
            viewModel.onDrag(dragAmount)
            viewModel.onDragEnd()
        }
        getInstrumentation().waitForIdleSync()

        // Close frame
        launcherActivity.executeOnLauncher { resizeFrame.close(false) }
        getInstrumentation().waitForIdleSync()

        val finalBoard = workspaceToBoard()
        val expectedBoard = CellLayoutBoard.boardFromString(testCase.endBoard)
        assertThat(expectedBoard.compareTo(finalBoard)).isEqualTo(0)
    }

    private fun identifyDragOffset(
        testCase: ResizeTestCase,
        cl: CellLayout,
        cellsToDrag: Int,
    ): Offset {
        val handleEdge = testCase.handle
        // Identify how much is one cell's size
        val dp = getIDP(cl.context).getDeviceProfile(cl.context)
        val paddedCellWidth =
            (cl.cellWidth + dp.workspaceProfile.cellLayoutBorderSpacePx.x).toFloat()
        val paddedCellHeight =
            (cl.cellHeight + dp.workspaceProfile.cellLayoutBorderSpacePx.y).toFloat()

        // We want to drag as much as the request number of cells
        val dragX = (paddedCellWidth * cellsToDrag * handleEdge.expandDirX)
        val dragY = (paddedCellHeight * cellsToDrag * handleEdge.expandDirY)

        return Offset(dragX, dragY)
    }

    private fun getHandleCenterOffset(
        resizeFrame: AppWidgetResizeFrameCompose,
        handle: Edge,
    ): Offset {
        val frameWidth = resizeFrame.width.toFloat()
        val frameHeight = resizeFrame.height.toFloat()
        val touchTargetSize = this.touchTargetSize.toFloat()

        val widgetLeft = touchTargetSize
        val widgetTop = touchTargetSize
        val widgetRight = frameWidth - touchTargetSize
        val widgetBottom = frameHeight - touchTargetSize
        val widgetCenterX = frameWidth / 2f
        val widgetCenterY = frameHeight / 2f

        val (offsetX, offsetY) =
            when (handle) {
                Edge.Left -> widgetLeft to widgetCenterY
                Edge.Right -> widgetRight to widgetCenterY
                Edge.Top -> widgetCenterX to widgetTop
                Edge.Bottom -> widgetCenterX to widgetBottom
            }
        return Offset(offsetX, offsetY)
    }

    private fun addTestCase(
        sections: Iterator<TestSection>,
        testCaseMap: MutableMap<Point, ResizeTestCase>,
    ) {
        val startBoard = sections.next() as Board
        val args = sections.next() as CellLayoutTestCaseReader.Arguments
        val endBoard = sections.next() as Board

        testCaseMap[startBoard.gridSize] =
            ResizeTestCase(
                startBoard = startBoard.board,
                widgetCellX = args.arguments[0].toInt(),
                widgetCellY = args.arguments[1].toInt(),
                handle = Edge.valueOf(args.arguments[2]),
                cellsToDrag = args.arguments[3].toInt(),
                endBoard = endBoard.board,
            )
    }

    private fun getTestMap(testPath: String): Map<Point, ResizeTestCase> {
        val testCaseMap: MutableMap<Point, ResizeTestCase> = HashMap()
        val iterableSection: Iterator<TestSection> =
            CellLayoutTestCaseReader.readFromFile(testPath).parse().iterator()
        while (iterableSection.hasNext()) {
            addTestCase(iterableSection, testCaseMap)
        }
        return testCaseMap
    }

    private fun runTestCaseMap(testCaseMap: Map<Point, ResizeTestCase>) {
        val dp = checkNotNull(launcherActivity.getFromLauncher { it.deviceProfile })
        val iconGridDimensions = Point(dp.inv.numColumns, dp.inv.numRows)
        Assume.assumeTrue(
            "Test case does not support grid size $iconGridDimensions",
            testCaseMap.containsKey(iconGridDimensions),
        )
        testCaseMap[iconGridDimensions]?.let { runTestCase(it) }
    }

    companion object {
        private val TIMEOUT = 30.seconds
    }

    private data class ResizeTestCase(
        val startBoard: String,
        val widgetCellX: Int,
        val widgetCellY: Int,
        val handle: Edge,
        val cellsToDrag: Int,
        val endBoard: String,
    )
}
