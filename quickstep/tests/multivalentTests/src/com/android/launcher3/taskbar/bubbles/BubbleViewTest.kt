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

package com.android.launcher3.taskbar.bubbles

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.taskbar.bubbles.model.BubbleIcon
import com.android.users.UserType
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.BubbleInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleViewTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var bubbleView: BubbleView
    private lateinit var overflowView: BubbleView
    private lateinit var bubble: BubbleBarBubble
    private val mockController = mock<BubbleView.Controller>()

    @Before
    fun setUp() {
        setupBubbleViews()
        bubbleView.setController(mockController)
        overflowView.setController(mockController)
        whenever(mockController.bubbleBarLocation).thenReturn(BubbleBarLocation.RIGHT)
    }

    @Test
    fun hasUnseenContent_bubble() {
        assertThat(bubbleView.hasUnseenContent()).isTrue()

        bubbleView.markSeen()
        assertThat(bubbleView.hasUnseenContent()).isFalse()
    }

    @Test
    fun hasUnseenContent_overflow() {
        assertThat(overflowView.hasUnseenContent()).isFalse()
    }

    @Test
    fun onInitializeAccessibilityNodeInfo_whenSelected_hasCollapseAction() {
        // Mark the bubble as selected
        bubbleView.setSelected(true)
        val info = AccessibilityNodeInfo()

        // Check the accessibility node info
        bubbleView.onInitializeAccessibilityNodeInfo(info)
        val actions = info.actionList

        // Verify that it has the collapse action and not the expand action
        assertThat(actions).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE)
        assertThat(actions).doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
    }

    @Test
    fun onInitializeAccessibilityNodeInfo_whenNotSelected_hasExpandAction() {
        // Mark the bubble as not selected
        bubbleView.setSelected(false)
        val info = AccessibilityNodeInfo()

        // Check the accessibility node info
        bubbleView.onInitializeAccessibilityNodeInfo(info)
        val actions = info.actionList

        // Verify that it has the expand action and not the collapse action
        assertThat(actions).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
        assertThat(actions)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE)
    }

    @Test
    fun performAccessibilityAction_expand_callsController() {
        // Perform the expand action
        bubbleView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)

        // Verify that the controller's expand method was called
        verify(mockController).expand(bubbleView)
    }

    @Test
    fun performAccessibilityAction_collapse_callsController() {
        // Perform the collapse action
        bubbleView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null)

        // Verify that the controller's collapse method was called
        verify(mockController).collapse()
    }

    @Test
    fun onInitializeAccessibilityNodeInfo_forBubbleOnRight_hasMoveLeftAction() {
        // Set bubble bar location to the right
        whenever(mockController.bubbleBarLocation).thenReturn(BubbleBarLocation.RIGHT)
        val info = AccessibilityNodeInfo()

        // Check the accessibility node info
        bubbleView.onInitializeAccessibilityNodeInfo(info)
        val actions = info.actionList

        // Verify that it has the move left action
        assertThat(actions.any { it.id == R.id.action_move_left }).isTrue()
        assertThat(actions.any { it.id == R.id.action_move_right }).isFalse()
    }

    @Test
    fun onInitializeAccessibilityNodeInfo_forBubbleOnLeft_hasMoveRightAction() {
        // Set bubble bar location to the left
        whenever(mockController.bubbleBarLocation).thenReturn(BubbleBarLocation.LEFT)
        val info = AccessibilityNodeInfo()

        // Check the accessibility node info
        bubbleView.onInitializeAccessibilityNodeInfo(info)
        val actions = info.actionList

        // Verify that it has the move right action
        assertThat(actions.any { it.id == R.id.action_move_right }).isTrue()
        assertThat(actions.any { it.id == R.id.action_move_left }).isFalse()
    }

    @Test
    fun onInitializeAccessibilityNodeInfo_forOverflow_doesNotHaveMoveActions() {
        // Overflow view should not have move actions
        val info = AccessibilityNodeInfo()

        // Check the accessibility node info
        overflowView.onInitializeAccessibilityNodeInfo(info)
        val actions = info.actionList

        // Verify that it does not have move actions
        assertThat(actions.any { it.id == R.id.action_move_left }).isFalse()
        assertThat(actions.any { it.id == R.id.action_move_right }).isFalse()
    }

    @Test
    fun performAccessibilityAction_moveLeft_callsControllerAndReturnsTrue() {
        // Perform the move left action
        val handled = bubbleView.performAccessibilityAction(R.id.action_move_left, null)

        // Verify that the controller's updateBubbleBarLocation method was called
        verify(mockController)
            .updateBubbleBarLocation(
                BubbleBarLocation.LEFT,
                BubbleBarLocation.UpdateSource.A11Y_ACTION_BUBBLE,
            )
        // Verify that the action is marked as handled
        assertThat(handled).isTrue()
    }

    @Test
    fun performAccessibilityAction_moveRight_callsControllerAndReturnsTrue() {
        // Perform the move right action
        val handled = bubbleView.performAccessibilityAction(R.id.action_move_right, null)

        // Verify that the controller's updateBubbleBarLocation method was called
        verify(mockController)
            .updateBubbleBarLocation(
                BubbleBarLocation.RIGHT,
                BubbleBarLocation.UpdateSource.A11Y_ACTION_BUBBLE,
            )
        // Verify that the action is marked as handled
        assertThat(handled).isTrue()
    }

    private fun setupBubbleViews() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)

            val bitmap = ColorDrawable(Color.WHITE).toBitmap(width = 20, height = 20)
            overflowView = inflater.inflate(R.layout.bubblebar_item_view, null, false) as BubbleView
            overflowView.setOverflow(BubbleBarOverflow(overflowView), bitmap)

            val bubbleInfo =
                BubbleInfo(
                    "key",
                    0,
                    null,
                    null,
                    0,
                    context.packageName,
                    null,
                    null,
                    false,
                    null,
                    false,
                    false,
                    UserType.MAIN,
                )
            bubbleView = inflater.inflate(R.layout.bubblebar_item_view, null, false) as BubbleView
            bubble =
                BubbleBarBubble(
                    bubbleInfo,
                    bubbleView,
                    BitmapInfo.of(bitmap, Color.WHITE),
                    BubbleIcon.Custom(bitmap),
                    Color.WHITE,
                    "",
                    null,
                )
            bubbleView.setBubble(bubble)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
