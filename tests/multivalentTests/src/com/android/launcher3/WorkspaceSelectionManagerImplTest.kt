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

package com.android.launcher3

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.WorkspaceSelectionManager.ItemSelectionType
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.LauncherBindableItemsContainer
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.views.ActivityContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WorkspaceSelectionManagerImplTest {

    private lateinit var selectionManager: WorkspaceSelectionManagerImpl
    private lateinit var view1: View
    private lateinit var view2: View
    private lateinit var view3: View
    private lateinit var allViews: List<View>
    private val context: Context = mock(Context::class.java)

    private val mockActivityContext = mock(ActivityContext::class.java)
    private val mockItemsContainer = mock(LauncherBindableItemsContainer::class.java)

    // Helper function to create mock views
    private fun createMockBubbleTextView(): BubbleTextView {
        val mockView = mock(BubbleTextView::class.java)
        whenever(mockView.context).thenReturn(context)
        var selected = false
        whenever(mockView.isSelected).thenAnswer { selected }
        whenever(mockView.setSelected(anyBoolean())).thenAnswer { invocation ->
            selected = invocation.arguments[0] as Boolean
            null
        }
        whenever(mockView.tag).thenReturn(ItemInfo())
        whenever(mockView.getLocationInWindow(any())).thenAnswer {}
        whenever(mockView.width).thenReturn(100)
        whenever(mockView.height).thenReturn(100)
        return mockView
    }

    private fun createMockFolderIcon(): FolderIcon {
        val mockView = mock(FolderIcon::class.java)
        whenever(mockView.context).thenReturn(context)
        var selected = false
        whenever(mockView.isSelected).thenAnswer { selected }
        whenever(mockView.setSelected(anyBoolean())).thenAnswer { invocation ->
            selected = invocation.arguments[0] as Boolean
            null
        }
        whenever(mockView.tag).thenReturn(ItemInfo())
        whenever(mockView.getLocationInWindow(any())).thenAnswer {}
        whenever(mockView.width).thenReturn(100)
        whenever(mockView.height).thenReturn(100)
        return mockView
    }

    @Before
    fun setUp() {
        whenever(mockActivityContext.content).thenReturn(mockItemsContainer)
        selectionManager = WorkspaceSelectionManagerImpl(mockActivityContext)

        view1 = createMockBubbleTextView()
        view2 = createMockFolderIcon()
        view3 = createMockBubbleTextView()
        allViews = listOf(view1, view2, view3)

        // Stub the mapOverVisibleItems call
        doAnswer { invocation ->
                val op = invocation.getArgument<ItemOperator>(0)
                for (view in allViews) {
                    op.evaluate(view.tag as ItemInfo, view)
                }
                null
            }
            .whenever(mockItemsContainer)
            .mapOverVisibleItems(any())
    }

    @Test
    fun testSelectSingleNode_selectsOneDeselectsOthers() {
        // Initial state: view2 is selected
        view2.isSelected = true
        selectionManager.updateItemSelection(view1, ItemSelectionType.SELECT_SINGLE)

        assertTrue(view1.isSelected)
        assertFalse(view2.isSelected)
        assertFalse(view3.isSelected)
    }

    @Test
    fun testToggleSingle_togglesSelectionState() {
        // Initial state: view1 not selected
        assertFalse(view1.isSelected)
        selectionManager.updateItemSelection(view1, ItemSelectionType.TOGGLE_SINGLE)
        assertTrue(view1.isSelected)

        // Toggle again
        selectionManager.updateItemSelection(view1, ItemSelectionType.TOGGLE_SINGLE)
        assertFalse(view1.isSelected)
    }

    @Test
    fun testNewSelection_replacesExistingSelection() {
        // Initial state: view1 and view2 are selected
        view1.isSelected = true
        view2.isSelected = true
        selectionManager.updateItemSelection(view3, ItemSelectionType.SELECT_SINGLE)

        assertFalse(view1.isSelected)
        assertFalse(view2.isSelected)
        assertTrue(view3.isSelected)
    }

    @Test
    fun testToggleMultiple_appendsToSelection() {
        // Initial state: view1 is selected
        view1.isSelected = true
        selectionManager.updateItemSelection(view2, ItemSelectionType.TOGGLE_SINGLE)
        selectionManager.updateItemSelection(view3, ItemSelectionType.TOGGLE_SINGLE)

        assertTrue(view1.isSelected)
        assertTrue(view2.isSelected)
        assertTrue(view3.isSelected)
    }

    @Test
    fun testBoxSelection_selectsItemsInBounds() {
        // Set view locations
        doAnswer {
                val location = it.arguments[0] as IntArray
                location[0] = 0
                location[1] = 0
                null
            }
            .whenever(view1)
            .getLocationInWindow(any())
        doAnswer {
                val location = it.arguments[0] as IntArray
                location[0] = 200
                location[1] = 200
                null
            }
            .whenever(view2)
            .getLocationInWindow(any())

        selectionManager.startBoxSelection(isAppending = false)
        selectionManager.updateBoxSelection(Rect(0, 0, 150, 150))
        selectionManager.endBoxSelection()

        assertTrue(view1.isSelected)
        assertFalse(view2.isSelected)
    }

    @Test
    fun testBoxSelection_withShiftPressed_appendsToSelection() {
        // Initial state: view1 is selected
        view1.isSelected = true

        // Set view locations
        doAnswer {
                val location = it.arguments[0] as IntArray
                location[0] = 0
                location[1] = 0
                null
            }
            .whenever(view1)
            .getLocationInWindow(any())
        doAnswer {
                val location = it.arguments[0] as IntArray
                location[0] = 200
                location[1] = 200
                null
            }
            .whenever(view2)
            .getLocationInWindow(any())

        selectionManager.startBoxSelection(isAppending = true)
        selectionManager.updateBoxSelection(Rect(200, 200, 250, 250))
        selectionManager.endBoxSelection()

        assertTrue(view1.isSelected)
        assertTrue(view2.isSelected)
    }

    @Test
    fun testBoxSelection_dragEnds_selectionStateIsInactive() {
        // Set view locations
        doAnswer {
                val location = it.arguments[0] as IntArray
                location[0] = 0
                location[1] = 0
                null
            }
            .whenever(view1)
            .getLocationInWindow(any())

        selectionManager.startBoxSelection(isAppending = false)
        selectionManager.updateBoxSelection(Rect(0, 0, 150, 150))
        selectionManager.endBoxSelection()

        // After drag end, another onBoxSelection should not change anything
        selectionManager.updateBoxSelection(Rect(0, 0, 150, 150))

        assertTrue(view1.isSelected)
    }
}
