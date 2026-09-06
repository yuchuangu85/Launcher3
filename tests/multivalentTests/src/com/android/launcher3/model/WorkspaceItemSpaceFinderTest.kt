/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.model

import android.graphics.Point
import android.graphics.Rect
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemCoordinates
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.ModelTestExtensions.bgDataModel
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [WorkspaceItemSpaceFinder] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class WorkspaceItemSpaceFinderTest : AbstractWorkspaceModelTest() {

    @Before
    override fun setup() {
        super.setup()
    }

    private fun findSpace(
        spanX: Int,
        spanY: Int,
        excludedScreens: IntSet = IntSet.wrap(FIRST_SCREEN_ID),
        startingFrom: WorkspaceItemCoordinates = WorkspaceItemCoordinates(FIRST_SCREEN_ID, 0, 0),
    ): WorkspaceItemCoordinates =
        WorkspaceItemSpaceFinder(
                mTargetContext.bgDataModel,
                mAppState.invariantDeviceProfile,
                model,
            )
            .findSpaceForItem(mAddedWorkspaceItems, spanX, spanY, excludedScreens, startingFrom)

    private fun assertRegionVacant(newItemSpace: WorkspaceItemCoordinates, spanX: Int, spanY: Int) {
        assertThat(
                mScreenOccupancy[newItemSpace.screenId].isRegionVacant(
                    newItemSpace.cellX,
                    newItemSpace.cellY,
                    spanX,
                    spanY,
                )
            )
            .isTrue()
    }

    @Test
    fun justEnoughSpaceOnFirstScreen_whenFindSpaceForItem_thenReturnFirstScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 space
            //  2 spaces of sizes 3x2 and 2x3
            screen2 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
        )

        val spaceFound = findSpace(1, 1)

        assertThat(spaceFound.screenId).isEqualTo(1)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isTrue()
        assertRegionVacant(spaceFound, 1, 1)
    }

    @Test
    fun workspaceItemsAddedButNotYetCommittedToDbShouldBeTakenIntoAccountInFindSpaceForItem() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = listOf(Rect(2, 2, 4, 4)), // 2x2 space
        )
        val itemInfo = ItemInfo()
        itemInfo.cellX = 2
        itemInfo.cellY = 2
        itemInfo.screenId = 1
        itemInfo.container = CONTAINER_DESKTOP
        mAddedWorkspaceItems.add(itemInfo)

        val itemInfo2 = ItemInfo()
        itemInfo2.cellX = 3
        itemInfo2.cellY = 2
        itemInfo2.screenId = 1
        itemInfo2.container = CONTAINER_DESKTOP
        mAddedWorkspaceItems.add(itemInfo2)

        val spaceFound = findSpace(1, 1)

        assertThat(spaceFound.screenId).isEqualTo(1)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isTrue()
        assertThat(spaceFound.cellX).isEqualTo(2)
        assertThat(spaceFound.cellY).isEqualTo(3)
        assertRegionVacant(spaceFound, 1, 1)

        mAddedWorkspaceItems.clear()
    }

    @Test
    fun notEnoughSpaceOnFirstScreen_whenFindSpaceForItem_thenReturnSecondScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 space
            //  2 spaces of sizes 3x2 and 2x3
            screen2 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
        )

        // Find a larger space
        val spaceFound = findSpace(2, 3)

        assertThat(spaceFound.screenId).isEqualTo(2)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isTrue()
        assertRegionVacant(spaceFound, 2, 3)
    }

    @Test
    fun notEnoughSpaceOnExistingScreens_returnNewScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            //  2 spaces of sizes 3x2 and 2x3
            screen1 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
            //  2 spaces of sizes 1x2 and 2x2
            screen2 = listOf(Rect(1, 0, 2, 2), Rect(3, 2, 5, 4)),
        )

        val spaceFound = findSpace(3, 3)

        assertThat(spaceFound.screenId).isEqualTo(3)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isFalse()
    }

    @Test
    fun firstScreenIsEmptyButSecondIsNotEmpty_returnSecondScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            // empty screens are skipped
            screen2 = listOf(Rect(2, 0, 5, 2)), // 3x2 space
        )

        val spaceFound = findSpace(2, 1)

        assertThat(spaceFound.screenId).isEqualTo(2)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isTrue()
        assertRegionVacant(spaceFound, 2, 1)
    }

    @Test
    fun twoEmptyMiddleScreens_returnThirdScreen() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            // empty screens are skipped
            screen3 = listOf(Rect(1, 1, 4, 4)), // 3x3 space
        )

        val spaceFound = findSpace(2, 3)

        assertThat(spaceFound.screenId).isEqualTo(3)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isTrue()
        assertRegionVacant(spaceFound, 2, 3)
    }

    @Test
    fun allExistingPagesAreFull_returnNewScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = fullScreenSpaces,
            screen2 = fullScreenSpaces,
        )

        val spaceFound = findSpace(2, 3)

        assertThat(spaceFound.screenId).isEqualTo(3)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isFalse()
    }

    @Test
    fun firstTwoPagesAreFull_and_ThirdPageIsEmpty_returnThirdPage() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = fullScreenSpaces, // full screens are skipped
            screen2 = fullScreenSpaces, // full screens are skipped
            screen3 = emptyScreenSpaces,
        )

        val spaceFound = findSpace(3, 1)

        assertThat(spaceFound.screenId).isEqualTo(3)
        assertThat(mExistingScreens.contains(spaceFound.screenId)).isTrue()
        assertRegionVacant(spaceFound, 3, 1)
    }

    @Test
    fun testFindSpaceStartingFromWhenCoordsAreOutOfBounds() {
        // Case: Starting from cellX is above bounds.
        testFindSpaceStartingFrom(
            screen0 = listOf(emptySpaceAt(1, 1)),
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(1, Int.MAX_VALUE, 1),
            expected = WorkspaceItemCoordinates(0, 1, 1),
        )

        // Case: Starting from cellX is below bounds.
        testFindSpaceStartingFrom(
            screen0 = listOf(emptySpaceAt(1, 1)),
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(1, Int.MIN_VALUE, 1),
            expected = WorkspaceItemCoordinates(0, 1, 1),
        )

        // Case: Starting from cellY is above bounds.
        testFindSpaceStartingFrom(
            screen0 = listOf(emptySpaceAt(1, 1)),
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(1, 1, Int.MAX_VALUE),
            expected = WorkspaceItemCoordinates(0, 1, 1),
        )

        // Case: Starting from cellY is below bounds.
        testFindSpaceStartingFrom(
            screen0 = listOf(emptySpaceAt(1, 1)),
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(1, 1, Int.MIN_VALUE),
            expected = WorkspaceItemCoordinates(0, 1, 1),
        )

        // Case: Starting from screen is above bounds.
        testFindSpaceStartingFrom(
            screen0 = listOf(emptySpaceAt(1, 1)),
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(Int.MAX_VALUE, 1, 1),
            expected = WorkspaceItemCoordinates(0, 1, 1),
        )

        // Case: Starting from screen is below bounds.
        testFindSpaceStartingFrom(
            screen0 = listOf(emptySpaceAt(1, 1)),
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(Int.MIN_VALUE, 1, 1),
            expected = WorkspaceItemCoordinates(0, 1, 1),
        )
    }

    @Test
    fun testFindSpaceStartingFromWhenSpaceIsAvailable() {
        // Case: Starting from coords are after empty space.
        testFindSpaceStartingFrom(
            screen0 = emptyScreenSpaces,
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(1, 2, 2),
            expected = WorkspaceItemCoordinates(2, 0, 0),
        )

        // Case: Starting from coords are before empty space.
        testFindSpaceStartingFrom(
            screen0 = emptyScreenSpaces,
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(1, 0, 0),
            expected = WorkspaceItemCoordinates(1, 1, 1),
        )

        // Case: Starting from coords are equal to empty space.
        testFindSpaceStartingFrom(
            screen0 = emptyScreenSpaces,
            screen1 = listOf(emptySpaceAt(1, 1)),
            startingFrom = WorkspaceItemCoordinates(1, 1, 1),
            expected = WorkspaceItemCoordinates(1, 1, 1),
        )
    }

    @Test
    fun testFindSpaceStartingFromWhenSpaceIsUnavailable() {
        // Case: Starting from screen is full.
        testFindSpaceStartingFrom(
            screen0 = emptyScreenSpaces,
            screen1 = fullScreenSpaces,
            startingFrom = WorkspaceItemCoordinates(1, 0, 0),
            expected = WorkspaceItemCoordinates(2, 0, 0),
        )
    }

    private fun testFindSpaceStartingFrom(
        screen0: List<Rect>,
        screen1: List<Rect>,
        startingFrom: WorkspaceItemCoordinates,
        expected: WorkspaceItemCoordinates,
    ) {
        setup()
        setupWorkspacesWithSpaces(screen0 = screen0, screen1 = screen1)

        assertThat(
                findSpace(
                    spanX = 1,
                    spanY = 1,
                    excludedScreens = IntSet(),
                    startingFrom = startingFrom,
                )
            )
            .isEqualTo(expected)

        if (expected.screenId <= 1) {
            assertTrue(mExistingScreens.contains(expected.screenId))
            assertRegionVacant(expected, spanX = 1, spanY = 1)
        } else {
            assertFalse(mExistingScreens.contains(expected.screenId))
        }
    }

    private fun emptySpaceAt(cellX: Int, cellY: Int) = emptySpaceAt(Point(cellX, cellY))

    private fun emptySpaceAt(cell: Point, span: Size = Size(1, 1)) =
        Rect(cell.x, cell.y, cell.x + span.width, cell.y + span.height)
}
