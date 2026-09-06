/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.CellLayout
import com.android.launcher3.folder.FolderPagedView
import com.android.launcher3.util.TestActivityContext
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderAccessibilityHelperTest {
    @get:Rule val mContext = TestActivityContext()
    private val countX = 4
    private val countY = 3
    private val index = 1

    private val mockParent: FolderPagedView = mock()

    private val mockLayout: CellLayout = mock {
        on { parent } doReturn mockParent
        on { context } doReturn mContext
        on { countX } doReturn countX
        on { countY } doReturn countY
    }

    private lateinit var folderAccessibilityHelper: FolderAccessibilityHelper

    @Before
    fun setUp() {
        whenever(mockParent.indexOfChild(mockLayout)).thenReturn(index)

        folderAccessibilityHelper = FolderAccessibilityHelper(mockLayout)
    }

    @Test
    fun testIntersectsValidDropTarget() {
        // Setup
        val id = 5
        val allocatedContentSize = 20
        // Make layout function public @VisibleForTesting
        whenever(mockParent.allocatedContentSize).thenReturn(allocatedContentSize)

        // Execute
        val result = folderAccessibilityHelper.intersectsValidDropTarget(id)

        // Verify
        val expectedResult = min(id, allocatedContentSize - (index * countX * countY) - 1)
        assertEquals(expectedResult, result)
    }

    @Test
    fun testGetLocationDescriptionForIconDrop() {
        // Setup
        val id = 5

        // Execute
        val result = folderAccessibilityHelper.getLocationDescriptionForIconDrop(id)

        // Verify
        val expectedResult = "Move to position ${id + (index * countX * countY) + 1}"
        assertEquals(expectedResult, result)
    }
}
