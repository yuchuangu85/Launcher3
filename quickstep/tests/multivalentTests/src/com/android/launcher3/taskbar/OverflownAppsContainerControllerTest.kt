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
package com.android.launcher3.taskbar

import android.view.MotionEvent
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_EXIT
import android.view.ViewGroup
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverflownAppsContainerControllerTest {
    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val viewController by taskbarUnitTestRule.delegate { it.taskbarViewController }
    private lateinit var overflownController: OverflownAppsContainerController

    private lateinit var overflowIcon: TaskbarOverflowView
    private val taskbarActivityContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val numShownHotseatIcons
        get() = taskbarActivityContext.taskbarSpecsEvaluator.numShownHotseatIcons

    @Before
    fun setUp() {
        overflownController = viewController.overflownAppsContainerController
        overflowIcon = getOnTaskbarUiThread { TaskbarOverflowView(taskbarActivityContext) }
    }

    @Test
    fun testToggleOverflownAppsView_showsContainer() {
        val apps = createHotseatItems(numShownHotseatIcons + 3).toList()

        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isFalse()

        // Toggle the OverflownAppsView should open the container view.
        toggleOverflownAppsView(apps)

        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isTrue()
    }

    @Test
    fun testToggleOverflownAppsView_closesContainer() {
        val apps = createHotseatItems(numShownHotseatIcons + 3).toList()

        // Toggle the OverflownAppsView should open the container view.
        toggleOverflownAppsView(apps)

        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isTrue()

        // Toggle the OverflownAppsView again should close the container view.
        toggleOverflownAppsView(apps)

        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isFalse()
    }

    @Test
    fun testOverflownAppsContainer_closesOnOutsideClick() {
        val apps = createHotseatItems(numShownHotseatIcons + 1).toList()

        toggleOverflownAppsView(apps)

        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isTrue()

        // Simulate a click on the drag layer to close the container.
        runOnTaskbarUiThreadSync {
            taskbarActivityContext.dragLayer.dispatchTouchEvent(
                MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            )
            taskbarActivityContext.dragLayer.dispatchTouchEvent(
                MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
            )
        }
        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isFalse()
    }

    @Test
    fun testOverflownAppsContainer_showsCorrectApps() {
        val numOverflownApps = 3
        val apps = createHotseatItems(numOverflownApps).toList()

        toggleOverflownAppsView(apps)

        val overflownContent: ViewGroup =
            taskbarActivityContext.dragLayer.findViewById(R.id.overflown_content)

        assertThat(overflownContent.children.map { it.tag }.toList())
            .containsExactlyElementsIn(apps)
    }

    @Test
    fun testOverflownAppsContainerDoesNotOverlapTaskbar() {
        val apps = createHotseatItems(numShownHotseatIcons + 3).toList()
        toggleOverflownAppsView(apps)

        verifyOverflowViewDoesNotOverlapTaskbar()
    }

    @Test
    fun testOverflownAppsContainerDoesNotOverlapTaskbarAfterClosingTooltip() {
        val apps = createHotseatItems(numShownHotseatIcons + 3)
        runOnTaskbarUiThreadSync {
            val taskbar: TaskbarView =
                taskbarActivityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbar.updateItems(apps, emptyList(), emptyList())
            val iconView: BubbleTextView =
                taskbar.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            iconView.dispatchGenericMotionEvent(
                MotionEvent.obtain(0, 0, ACTION_HOVER_ENTER, 0f, 0f, 0)
            )
            overflownController.toggleOverflownAppsView(overflowIcon, apps.toList())
            iconView.dispatchGenericMotionEvent(
                MotionEvent.obtain(0, 0, ACTION_HOVER_EXIT, 0f, 0f, 0)
            )
        }
        runOnTaskbarUiThreadSync {
            // Run an empty frame so that the taskbar drag layer can resize and show the overflown
            // container.
        }

        verifyOverflowViewDoesNotOverlapTaskbar()
    }

    private fun verifyOverflowViewDoesNotOverlapTaskbar() {
        runOnTaskbarUiThreadSync {
            val container: AbstractFloatingView =
                AbstractFloatingView.getOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            assertThat(container).isNotNull()

            val location = IntArray(2)
            container.getLocationOnScreen(location)
            val containerTop = location[1]

            val overflownContent: ViewGroup =
                taskbarActivityContext.dragLayer.findViewById(R.id.overflown_content)

            assertThat(container.height).isAtLeast(overflownContent.height)
            assertThat(container.height)
                .isAtLeast(taskbarActivityContext.deviceProfile.taskbarProfile.iconSize)

            val taskbar: TaskbarView =
                taskbarActivityContext.dragLayer.findViewById(R.id.taskbar_view)
            taskbar.getLocationOnScreen(location)
            val taskbarTop = location[1]

            assertThat(containerTop + container.height).isAtMost(taskbarTop)
        }
    }

    private fun toggleOverflownAppsView(apps: List<ItemInfo>) {
        runOnTaskbarUiThreadSync { overflownController.toggleOverflownAppsView(overflowIcon, apps) }
        runOnTaskbarUiThreadSync {
            // Run an empty frame so that the taskbar drag layer can resize and show the overflown
            // container.
        }
    }
}
