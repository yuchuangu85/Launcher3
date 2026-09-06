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

package com.android.launcher3.taskbar

import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.FlagsParameterization.allCombinationsOf
import android.platform.test.flag.junit.SetFlagsRule
import android.view.View
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.size
import com.android.launcher3.Flags.FLAG_ENABLE_TASKBAR_DRAG_AND_DROP
import com.android.launcher3.Flags.FLAG_ENABLE_TASKBAR_ICON_CONTAINER
import com.android.launcher3.R
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarIconType.ALL_APPS
import com.android.launcher3.taskbar.TaskbarIconType.DIVIDER
import com.android.launcher3.taskbar.TaskbarIconType.HANDOFF_SUGGESTION
import com.android.launcher3.taskbar.TaskbarIconType.HOTSEAT
import com.android.launcher3.taskbar.TaskbarIconType.OVERFLOW
import com.android.launcher3.taskbar.TaskbarIconType.RECENT
import com.android.launcher3.taskbar.TaskbarViewTestUtil.assertThat
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHandoffSuggestions
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatWorkspaceItem
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecentTask
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createRecents
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createSplitTask
import com.android.launcher3.taskbar.customization.util.TaskbarIconContainerLayoutParams
import com.android.launcher3.taskbar.rules.TaskbarAnimatorTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.ForceRtl
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext.Companion.getDeviceParams
import com.android.launcher3.testutil.Wait
import com.android.launcher3.util.TestUtil.getOnTaskbarUiThread
import com.android.window.flags.Flags.FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS
import com.android.window.flags.Flags.FLAG_ENABLE_TASKBAR_OVERFLOW
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@EnableFlags(FLAG_ENABLE_TASKBAR_OVERFLOW)
class TaskbarViewTest(deviceName: String, flags: FlagsParameterization) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0},{1}")
        fun getParams(): List<Array<Any>> {
            val devices = getDeviceParams("pixelFoldable2023", "pixelTablet2023")
            val flags = allCombinationsOf(FLAG_ENABLE_TASKBAR_ICON_CONTAINER)
            return devices.flatMap { d -> flags.map { f -> arrayOf(d, f) } } // Cartesian product.
        }
    }

    @get:Rule(order = 0) val animatorTestRule = TaskbarAnimatorTestRule(this)
    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create(deviceName)
    @get:Rule(order = 2) val setFlagsRule = SetFlagsRule(flags)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val activityContext by taskbarUnitTestRule::activityContext

    private val viewController by taskbarUnitTestRule.delegate { it.taskbarViewController }
    private lateinit var taskbarView: TaskbarView

    private val pinnedHitRectBuffer: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.taskbar_pinned_hit_rect_buffer)

    private val iconViews: Array<View>
        get() = taskbarView.iconViews

    private val desktopVisibilityController: DesktopVisibilityController
        get() = DesktopVisibilityController.INSTANCE[context]

    private val maxShownRecents: Int
        get() = taskbarView.maxNumIconViews - 2 // Account for All Apps and Divider.

    private val maxShownHotseat: Int
        get() = activityContext.taskbarSpecsEvaluator.numShownHotseatIcons

    @Before
    fun obtainView() {
        taskbarView = activityContext.dragLayer.findViewById(R.id.taskbar_view)
    }

    @Test
    fun testUpdateItems_noItems_hasOnlyAllApps() {
        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS)
    }

    @Test
    fun testUpdateItems_hotseatItems_hasDividerBetweenAllAppsAndHotseat() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT, HOTSEAT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlWithHotseatItems_hasDividerBetweenHotseatAndAllApps() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(HOTSEAT, HOTSEAT, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_withNullHotseatItem_filtersNullItem() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(*createHotseatItems(2), null), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT, HOTSEAT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlWithNullHotseatItem_filtersNullItem() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(*createHotseatItems(2), null), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(HOTSEAT, HOTSEAT, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_recentsItems_hasDividerBetweenAllAppsAndRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(4), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, *RECENT * 4)
    }

    @Test
    fun testUpdateItems_hotseatItemsAndRecents_hasDividerBetweenHotseatAndRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(3), createRecents(2), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, *HOTSEAT * 3, DIVIDER, *RECENT * 2)
    }

    @Test
    fun testUpdateItems_addHotseatItem_updatesHotseat() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(1),
                createHandoffSuggestions(1),
            )
            taskbarView.updateItems(
                createHotseatItems(2),
                createRecents(1),
                createHandoffSuggestions(1),
            )
        }
        assertThat(taskbarView)
            .hasIconTypes(ALL_APPS, *HOTSEAT * 2, DIVIDER, RECENT, HANDOFF_SUGGESTION)
    }

    @Test
    fun testUpdateItems_removeHotseatItem_updatesHotseat() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(2),
                createRecents(1),
                createHandoffSuggestions(1),
            )
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(1),
                createHandoffSuggestions(1),
            )
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT, HANDOFF_SUGGESTION)
    }

    @Test
    fun testUpdateItems_addRecentsItem_updatesRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(1),
                createHandoffSuggestions(1),
            )
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(2),
                createHandoffSuggestions(1),
            )
        }
        assertThat(taskbarView)
            .hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, *RECENT * 2, HANDOFF_SUGGESTION)
    }

    @Test
    fun testUpdateItems_removeRecentsItem_updatesRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(2),
                createHandoffSuggestions(1),
            )
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(1),
                createHandoffSuggestions(1),
            )
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT, HANDOFF_SUGGESTION)
    }

    @Test
    fun testUpdateItems_addHandoffSuggestion_updatesHandoffSuggestions() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(1),
                createHandoffSuggestions(1),
            )
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT, HANDOFF_SUGGESTION)
    }

    @Test
    fun testUpdateItems_removeHandoffSuggestion_updatesHandoffSuggestions() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(1),
                createRecents(1),
                createHandoffSuggestions(1),
            )
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT)
    }

    @Test
    fun testUpdateItem_addHotseatItemAfterRecentsItem_hotseatItemBeforeDivider() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(1), emptyList())
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_hotseatItems_hasDividerBetweenHotseatAndAllApps() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(*HOTSEAT * 2, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_recentsItems_hasDividerBetweenRecentsAndAllApps() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(4), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(*RECENT * 4, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_recentsItems_recentsAreReversed() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(4), emptyList())
        }
        assertThat(taskbarView).hasRecentsOrder(startIndex = 0, expectedIds = listOf(3, 2, 1, 0))
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_hotseatItemsAndRecents_hasDividerBetweenRecentsAndHotseat() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(3), createRecents(2), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(*RECENT * 2, DIVIDER, *HOTSEAT * 3, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addHotseatItemWithoutRecents_updatesHotseat() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
            taskbarView.updateItems(createHotseatItems(2), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(*HOTSEAT * 2, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addHotseatItemWithRecents_updatesHotseat() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
            taskbarView.updateItems(createHotseatItems(2), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, *HOTSEAT * 2, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeHotseatItem_updatesHotseat() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1), emptyList())
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addRecentsItem_updatesRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
            taskbarView.updateItems(createHotseatItems(1), createRecents(2), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(*RECENT * 2, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeRecentsItem_updatesRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(2), emptyList())
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    fun testUpdateItems_addFirstHotseatItem_addsDivider() {
        // GIVEN no items are present, so there is no divider
        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS)

        // WHEN the first hotseat item is added
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }

        // THEN a divider is also added
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT)
    }

    @Test
    fun testUpdateItems_removeLastHotseatItem_removesDivider() {
        // GIVEN a hotseat item is present, so there is a divider
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT)

        // WHEN the last hotseat item is removed
        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }

        // THEN the divider is also removed
        assertThat(taskbarView).hasIconTypes(ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addFirstHotseatItem_addsDivider() {
        // GIVEN no items are present, so there is no divider
        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        assertThat(taskbarView).hasIconTypes(ALL_APPS)

        // WHEN the first hotseat item is added
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }

        // THEN a divider is also added
        assertThat(taskbarView).hasIconTypes(HOTSEAT, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeLastHotseatItem_removesDivider() {
        // GIVEN a hotseat item is present, so there is a divider
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(HOTSEAT, DIVIDER, ALL_APPS)

        // WHEN the last hotseat item is removed
        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }

        // THEN the divider is also removed
        assertThat(taskbarView).hasIconTypes(ALL_APPS)
    }

    @Test
    fun testUpdateItems_addRecentsItem_viewAddedOnRight() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(1), emptyList())
            val prevIconViews = iconViews

            val newRecents = createRecents(2)
            taskbarView.updateItems(emptyArray(), newRecents, emptyList())

            assertThat(taskbarView).hasRecentsOrder(startIndex = 2, expectedIds = listOf(0, 1))
            Truth.assertThat(iconViews[2]).isSameInstanceAs(prevIconViews[2])
            Truth.assertThat(iconViews.last() in prevIconViews).isFalse()
        }
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_addRecentsItem_viewAddedOnLeft() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(1), emptyList())
            val prevIconViews = iconViews

            val newRecents = createRecents(2)
            taskbarView.updateItems(emptyArray(), newRecents, emptyList())

            assertThat(taskbarView).hasRecentsOrder(startIndex = 0, expectedIds = listOf(1, 0))
            Truth.assertThat(iconViews[1]).isSameInstanceAs(prevIconViews.first())
            Truth.assertThat(iconViews.first() in prevIconViews).isFalse()
        }
    }

    @Test
    fun testUpdateItems_removeFirstRecentsItem_correspondingViewRemoved() {
        runOnTaskbarUiThreadSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents, emptyList())

            val expectedViewToRemove = iconViews[2]
            Truth.assertThat(expectedViewToRemove.tag).isEqualTo(recents.first())

            taskbarView.updateItems(emptyArray(), listOf(recents.last()), emptyList())
            Truth.assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    fun testUpdateItems_removeLastRecentsItem_correspondingViewRemoved() {
        runOnTaskbarUiThreadSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents, emptyList())

            val expectedViewToRemove = iconViews[3]
            Truth.assertThat(expectedViewToRemove.tag).isEqualTo(recents.last())

            taskbarView.updateItems(emptyArray(), listOf(recents.first()), emptyList())
            Truth.assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeFirstRecentsItem_correspondingViewRemoved() {
        runOnTaskbarUiThreadSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents, emptyList())

            val expectedViewToRemove = iconViews[1]
            Truth.assertThat(expectedViewToRemove.tag).isEqualTo(recents.first())

            taskbarView.updateItems(emptyArray(), listOf(recents.last()), emptyList())
            Truth.assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_removeLastRecentsItem_correspondingViewRemoved() {
        runOnTaskbarUiThreadSync {
            val recents = createRecents(2)
            taskbarView.updateItems(emptyArray(), recents, emptyList())

            val expectedViewToRemove = iconViews[0]
            Truth.assertThat(expectedViewToRemove.tag).isEqualTo(recents.last())

            taskbarView.updateItems(emptyArray(), listOf(recents.first()), emptyList())
            Truth.assertThat(expectedViewToRemove in iconViews).isFalse()
        }
    }

    @Test
    fun testUpdateItems_desktopMode_hotseatItem_noRecents_hasAllAppsDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT)
    }

    @Test
    fun testUpdateItems_desktopMode_hotseatItem_hasRecents_hasDividerForRecents() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, DIVIDER, RECENT)
    }

    @Test
    fun testUpdateItems_desktopMode_hotseatItem_noRecents_hasAllAppsDividerAfterDesktopModeChange() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(false)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }

        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), emptyList(), emptyList())
        }

        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, HOTSEAT, HOTSEAT)
    }

    @Test
    fun testUpdateItems_desktopMode_hotseatItem_hasRecents_hasDividerForRecentsAfterDesktopModeChange() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(false)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
        }

        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1), emptyList())
        }

        assertThat(taskbarView).hasIconTypes(ALL_APPS, HOTSEAT, HOTSEAT, DIVIDER, RECENT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlAndDesktopMode_hotseatItem_noRecents_hasAllAppsDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(HOTSEAT, DIVIDER, ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlAndDesktopMode_hotseatItem_hasRecents_hasDividerForRecents() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, HOTSEAT, ALL_APPS)
    }

    @Test
    fun testUpdateItems_desktopMode_recentItem_hasDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, RECENT)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtlAndDesktopMode_recentItem_hasDivider() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(1), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(RECENT, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_maxRecents_noOverflow() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(maxShownRecents), emptyList())
        }
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, *RECENT * maxShownRecents)
    }

    @Test
    fun testUpdateItems_moreThanMaxRecents_overflowShownBeforeRecents() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(recentsSize), emptyList())
        }

        val expectedNumRecents = RECENT * getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView).hasIconTypes(ALL_APPS, DIVIDER, OVERFLOW, *expectedNumRecents)
    }

    @Test
    fun testUpdateItems_clearAllRecentsAfterOverflow_recentsEmpty() {
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(recentsSize), emptyList())
            taskbarView.updateItems(emptyArray(), emptyList(), emptyList())
        }

        assertThat(taskbarView).hasIconTypes(ALL_APPS)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_moreThanMaxRecents_overflowShownAfterRecents() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(recentsSize), emptyList())
        }

        val expectedRecents = RECENT * getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView).hasIconTypes(*expectedRecents, OVERFLOW, DIVIDER, ALL_APPS)
    }

    @Test
    fun testUpdateItems_moreThanMaxRecentsWithHotseat_fewerRecentsShown() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val hotseatSize = 4
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(hotseatSize),
                createRecents(recentsSize),
                emptyList(),
            )
        }

        val expectedRecents = RECENT * getExpectedNumRecentsWithOverflow(hotseatSize)
        assertThat(taskbarView)
            .hasIconTypes(ALL_APPS, *HOTSEAT * hotseatSize, DIVIDER, OVERFLOW, *expectedRecents)
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_moreThanMaxRecentsWithHotseat_fewerRecentsShown() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val hotseatSize = 4
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(hotseatSize),
                createRecents(recentsSize),
                emptyList(),
            )
        }

        val expectedRecents = RECENT * getExpectedNumRecentsWithOverflow(hotseatSize)
        assertThat(taskbarView)
            .hasIconTypes(*expectedRecents, OVERFLOW, DIVIDER, *HOTSEAT * hotseatSize, ALL_APPS)
    }

    @Test
    fun testUpdateItems_moreThanMaxRecents_verifyShownRecentsOrder() {
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(recentsSize), emptyList())
        }

        val expectedNumRecents = getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView)
            .hasRecentsOrder(
                startIndex = iconViews.size - expectedNumRecents,
                expectedIds = ((recentsSize - expectedNumRecents)..<recentsSize).toList(),
            )
    }

    @Test
    @ForceRtl
    fun testUpdateItems_rtl_moreThanMaxRecents_verifyShownRecentsReversed() {
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(recentsSize), emptyList())
        }

        val expectedNumRecents = getExpectedNumRecentsWithOverflow()
        assertThat(taskbarView)
            .hasRecentsOrder(
                startIndex = 0,
                expectedIds = ((recentsSize - expectedNumRecents)..<recentsSize).toList().reversed(),
            )
    }

    @Test
    fun testUpdateItems_splitTask_addsAppPairIconToTaskbar() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(createSplitTask()), emptyList())
        }

        val icon = taskbarView.children.last()
        Truth.assertThat(icon).isInstanceOf(AppPairIcon::class.java)
    }

    @Test
    fun testUpdateItems_withExistingSplitTask_appPairIconIsSameInstance() {
        val splitTask = createSplitTask()
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(splitTask), emptyList())
        }
        val appPairIcon1 = taskbarView.children.last()

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(splitTask), emptyList())
        }
        val appPairIcon2 = taskbarView.children.last()

        Truth.assertThat(appPairIcon1).isSameInstanceAs(appPairIcon2)
    }

    @Test
    fun testUpdateItems_splitTaskReplaced_appPairIconReplaced() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(createSplitTask(0)), emptyList())
        }

        val appPairIcon1 = taskbarView.children.last()

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(createSplitTask(1)), emptyList())
        }

        val appPairIcon2 = taskbarView.children.last()

        Truth.assertThat(appPairIcon1).isNotSameInstanceAs(appPairIcon2)
        Truth.assertThat(appPairIcon1.parent).isNull()
    }

    @Test
    fun testUpdateItems_qsbInline_removesDividerWhenOnlyStaticViewsRemain() {
        // This test runs only on devices with an inline QSB, like tablets.
        assume().that(activityContext.deviceProfile.hotseatProfile.isQsbInline).isTrue()

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(1), emptyList(), emptyList())
        }
        assertThat(taskbarView.taskbarDividerViewContainer?.parent).isNotNull()

        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), emptyList(), emptyList()) }
        assertThat(taskbarView.taskbarDividerViewContainer?.parent).isNull()
    }

    @Test
    fun testOnTaskUpdated_splitTask_bottomRightTaskTitleChanged_updatesTitle() {
        val splitTask = createSplitTask()
        val expectedTitle1 =
            context.getString(
                R.string.app_pair_default_title,
                splitTask.topLeftTask.title,
                splitTask.bottomRightTask.title,
            )
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(splitTask), emptyList())
        }

        val icon = taskbarView.children.last() as AppPairIcon
        Truth.assertThat(icon.titleTextView.text).isEqualTo(expectedTitle1)

        val newTitle = "Task1b"
        splitTask.bottomRightTask.title = newTitle
        val expectedTitle2 =
            context.getString(
                R.string.app_pair_default_title,
                splitTask.topLeftTask.title,
                newTitle,
            )

        runOnTaskbarUiThreadSync {
            viewController.onTaskUpdated(splitTask.bottomRightTask, splitTask)
        }
        Truth.assertThat(icon.titleTextView.text).isEqualTo(expectedTitle2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testUpdateItems_hotseatOverflow_noRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(maxShownHotseat + 1),
                emptyList(),
                emptyList(),
            )
        }
        assertThat(taskbarView)
            .hasIconTypes(ALL_APPS, DIVIDER, *HOTSEAT * (maxShownHotseat - 1), OVERFLOW)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    @ForceRtl
    fun testUpdateItems_rtl_hotseatOverflow_noRecents() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(maxShownHotseat + 1),
                emptyList(),
                emptyList(),
            )
        }
        assertThat(taskbarView)
            .hasIconTypes(OVERFLOW, *HOTSEAT * (maxShownHotseat - 1), DIVIDER, ALL_APPS)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testUpdateItems_hotseatAndRecentsOverflow() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(maxShownHotseat + 1),
                createRecents(recentsSize),
                emptyList(),
            )
        }
        assertThat(taskbarView)
            .hasIconTypes(
                ALL_APPS,
                *HOTSEAT * (maxShownHotseat - 1),
                OVERFLOW,
                DIVIDER,
                OVERFLOW,
                *RECENT * getExpectedNumRecentsWithOverflow(maxShownHotseat),
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    @ForceRtl
    fun testUpdateItems_rtl_hotseatAndRecentsOverflow() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val recentsSize = maxShownRecents + 2
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(maxShownHotseat + 1),
                createRecents(recentsSize),
                emptyList(),
            )
        }

        assertThat(taskbarView)
            .hasIconTypes(
                *RECENT * getExpectedNumRecentsWithOverflow(maxShownHotseat),
                OVERFLOW,
                DIVIDER,
                OVERFLOW,
                *HOTSEAT * (maxShownHotseat - 1),
                ALL_APPS,
            )
    }

    @Test
    fun testAnimateToOverflowOnOverlay_triggersAnimationAndResetsState() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(maxShownRecents), emptyList())
            // Add one more recent app to trigger the overflow animation.
            taskbarView.updateItems(emptyArray(), createRecents(maxShownRecents + 1), emptyList())
        }

        runOnTaskbarUiThreadSync {
            animatorTestRule.advanceTimeBy(TaskbarOverflowView.ITEM_ICON_SIZE_ANIMATION_DURATION)
        }
        assertThat(taskbarView.isRecentsOverflowViewFirstItemHiddenForAnimation).isFalse()
    }

    @Test
    @ForceRtl
    fun testAnimateToOverflowOnOverlay_rtl_triggersAnimationAndResetsState() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), createRecents(maxShownRecents), emptyList())
            // Add one more recent app to trigger the overflow animation.
            taskbarView.updateItems(emptyArray(), createRecents(maxShownRecents + 1), emptyList())
        }

        runOnTaskbarUiThreadSync {
            animatorTestRule.advanceTimeBy(TaskbarOverflowView.ITEM_ICON_SIZE_ANIMATION_DURATION)
        }
        assertThat(taskbarView.isRecentsOverflowViewFirstItemHiddenForAnimation).isFalse()
    }

    @Test
    fun testAnimateFromOverflowOnOverlay_triggersAnimationAndResetsState() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val initialRecents = createRecents(maxShownRecents + 1)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), initialRecents, emptyList())
        }

        val taskThatWillAnimateIn = initialRecents[1]
        var iconToAnimate = taskbarView.iconViews.find { it.tag == taskThatWillAnimateIn }
        assertThat(iconToAnimate).isNull()

        val fewerRecents = initialRecents.dropLast(1)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), fewerRecents, emptyList())
        }

        iconToAnimate = taskbarView.iconViews.find { it.tag == taskThatWillAnimateIn }
        assertThat(iconToAnimate).isNotNull()
        forceLayoutUpdate()
        assertThat(iconToAnimate?.alpha).isEqualTo(0)

        runOnTaskbarUiThreadSync {
            animatorTestRule.advanceTimeBy(TaskbarOverflowView.ITEM_ICON_SIZE_ANIMATION_DURATION)
        }
        assertThat(iconToAnimate?.alpha).isEqualTo(1f)
    }

    @Test
    @ForceRtl
    fun testAnimateFromOverflowOnOverlay_rtl_triggersAnimationAndResetsState() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        val initialRecents = createRecents(maxShownRecents + 1)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), initialRecents, emptyList())
        }

        val taskThatWillAnimateIn = initialRecents[1]

        var iconToAnimate = taskbarView.iconViews.find { it.tag == taskThatWillAnimateIn }
        assertThat(iconToAnimate).isNull()

        val fewerRecents = initialRecents.dropLast(1)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), fewerRecents, emptyList())
        }

        iconToAnimate = taskbarView.iconViews.find { it.tag == taskThatWillAnimateIn }
        assertThat(iconToAnimate).isNotNull()
        forceLayoutUpdate()
        assertThat(iconToAnimate?.alpha).isEqualTo(0)

        runOnTaskbarUiThreadSync {
            animatorTestRule.advanceTimeBy(TaskbarOverflowView.ITEM_ICON_SIZE_ANIMATION_DURATION)
        }
        assertThat(iconToAnimate?.alpha).isEqualTo(1f)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_AND_DROP, FLAG_ENABLE_TASKBAR_ICON_CONTAINER)
    fun testGetHitRect_withHotseatIconContainer_coversHotseatIconContainer() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1), emptyList())
        }
        forceLayoutUpdate()

        val outRect = Rect()
        runOnTaskbarUiThreadSync { taskbarView.getHitRectForPinRelativeToDragLayer(outRect) }
        val expectedRect = Rect()
        runOnTaskbarUiThreadSync {
            activityContext.dragLayer.getDescendantRectRelativeToSelf(
                taskbarView.taskbarHotseatIconsContainer!!,
                expectedRect,
            )
        }

        assertThat(outRect.top).isEqualTo(expectedRect.top)
        assertThat(outRect.bottom).isEqualTo(expectedRect.bottom)
        assertThat(outRect.left).isEqualTo(expectedRect.left - pinnedHitRectBuffer)
        assertThat(outRect.right).isEqualTo(expectedRect.right + pinnedHitRectBuffer)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_AND_DROP)
    @DisableFlags(FLAG_ENABLE_TASKBAR_ICON_CONTAINER)
    fun testGetHitRect_withDivider_coversFromAllAppsToDivider() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1), emptyList())
        }
        forceLayoutUpdate()

        val allAppsButtonContainer = taskbarView.allAppsButtonContainer
        val dividerContainer = taskbarView.taskbarDividerViewContainer!!
        val outRect = Rect()
        runOnTaskbarUiThreadSync { taskbarView.getHitRectForPinRelativeToDragLayer(outRect) }
        val taskbarRectInDragLayer = Rect()
        runOnTaskbarUiThreadSync {
            activityContext.dragLayer.getDescendantRectRelativeToSelf(
                taskbarView,
                taskbarRectInDragLayer,
            )
        }

        assertThat(outRect.top).isEqualTo(taskbarRectInDragLayer.top)
        assertThat(outRect.bottom).isEqualTo(taskbarRectInDragLayer.bottom)
        assertThat(outRect.left)
            .isEqualTo(
                taskbarRectInDragLayer.left + allAppsButtonContainer.left - pinnedHitRectBuffer +
                    taskbarView.allAppsButtonTranslationXOffsetUsedForLayout
            )
        assertThat(outRect.right)
            .isEqualTo(taskbarRectInDragLayer.left + dividerContainer.left + pinnedHitRectBuffer)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_AND_DROP)
    @DisableFlags(FLAG_ENABLE_TASKBAR_ICON_CONTAINER)
    fun testGetHitRect_noDivider_coversUpTohotseatIconsContainer() {
        whenever(desktopVisibilityController.isInDesktopMode(context.displayId)).thenReturn(true)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(3), emptyList(), emptyList())
        }
        forceLayoutUpdate()

        val allAppsButtonContainer = taskbarView.allAppsButtonContainer
        val outRect = Rect()
        runOnTaskbarUiThreadSync { taskbarView.getHitRectForPinRelativeToDragLayer(outRect) }
        val taskbarRectInDragLayer = Rect()
        runOnTaskbarUiThreadSync {
            activityContext.dragLayer.getDescendantRectRelativeToSelf(
                taskbarView,
                taskbarRectInDragLayer,
            )
        }

        val lastIcon = iconViews.last()

        assertThat(outRect.top).isEqualTo(taskbarRectInDragLayer.top)
        assertThat(outRect.bottom).isEqualTo(taskbarRectInDragLayer.bottom)
        assertThat(outRect.left)
            .isEqualTo(
                taskbarRectInDragLayer.left + allAppsButtonContainer.left - pinnedHitRectBuffer +
                    taskbarView.allAppsButtonTranslationXOffsetUsedForLayout
            )
        assertThat(outRect.right)
            .isEqualTo(taskbarRectInDragLayer.left + lastIcon.right + pinnedHitRectBuffer)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_AND_DROP)
    @DisableFlags(FLAG_ENABLE_TASKBAR_ICON_CONTAINER)
    @ForceRtl
    fun testGetHitRect_rtl_withDivider_coversFromDividerToAllApps() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), createRecents(1), emptyList())
        }
        forceLayoutUpdate()

        val allAppsButtonContainer = taskbarView.allAppsButtonContainer
        val dividerContainer = taskbarView.taskbarDividerViewContainer!!
        val outRect = Rect()
        runOnTaskbarUiThreadSync { taskbarView.getHitRectForPinRelativeToDragLayer(outRect) }
        val taskbarRectInDragLayer = Rect()
        runOnTaskbarUiThreadSync {
            activityContext.dragLayer.getDescendantRectRelativeToSelf(
                taskbarView,
                taskbarRectInDragLayer,
            )
        }

        assertThat(outRect.top).isEqualTo(taskbarRectInDragLayer.top)
        assertThat(outRect.bottom).isEqualTo(taskbarRectInDragLayer.bottom)
        assertThat(outRect.left)
            .isEqualTo(taskbarRectInDragLayer.left + dividerContainer.right - pinnedHitRectBuffer)
        assertThat(outRect.right)
            .isEqualTo(
                taskbarRectInDragLayer.left + allAppsButtonContainer.right + pinnedHitRectBuffer -
                    taskbarView.allAppsButtonTranslationXOffsetUsedForLayout
            )
    }

    @Test
    fun testUpdateItems_nonDesktopRecentsChange_viewForRemovedTaskIsRecycledForNewTask() {
        val prevTasks = listOf(createRecentTask(0), createRecentTask(1))
        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), prevTasks, emptyList()) }
        val prevIcons = taskbarView.children.toList().takeLast(2)

        // Task 0 removed and Task 2 added.
        val nextTasks = listOf(createRecentTask(1), createRecentTask(2))
        runOnTaskbarUiThreadSync { taskbarView.updateItems(emptyArray(), nextTasks, emptyList()) }
        val nextIcons = taskbarView.children.toList().takeLast(2)

        // Existing icon for Task 1 should shift left.
        assertThat(nextIcons.first()).isSameInstanceAs(prevIcons.last())
        assertThat(nextIcons.first().tag).isEqualTo(nextTasks.first())

        // Icon for Task 2 should be recycled from Task 0.
        assertThat(nextIcons.last()).isSameInstanceAs(prevIcons.first())
        assertThat(nextIcons.last().tag).isEqualTo(nextTasks.last())
    }

    @Test
    fun testUpdateItems_hotseatItemUnchanged_iconNotRegenerated() {
        val item = spy(createHotseatWorkspaceItem())
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(item), emptyList(), emptyList())
        }
        verify(item, times(1)).newIcon(any(), any())

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(item), emptyList(), emptyList())
        }
        verify(item, times(1)).newIcon(any(), any()) // Icon is not generated a second time.
    }

    @Test
    fun testUpdateItems_hotseatItemIsOpened_tagUpdatedAndIconNotRegenerated() {
        val item = spy(createHotseatWorkspaceItem())
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(item), emptyList(), emptyList())
        }
        verify(item, times(1)).newIcon(any(), any())

        val taskItem = spy(TaskItemInfo(0, item))
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(taskItem), emptyList(), emptyList())
        }

        assertThat(taskbarView.iconViews.last().tag).isSameInstanceAs(taskItem)
        verify(taskItem, never()).newIcon(any(), any()) // Icon is not regenerated from task.
    }

    @Test
    fun testUpdateItems_hotseatTaskItemIsClosed_tagUpdatedAndIconNotRegenerated() {
        val item = spy(createHotseatWorkspaceItem())
        val taskItem = spy(TaskItemInfo(0, item))
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(taskItem), emptyList(), emptyList())
        }
        verify(taskItem, times(1)).newIcon(any(), any())

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(item), emptyList(), emptyList())
        }
        assertThat(taskbarView.iconViews.last().tag).isSameInstanceAs(item)
        verify(item, never()).newIcon(any(), any()) // Icon is not regenerated from item.
    }

    @Test
    fun testUpdateItems_recentTaskUnchanged_iconNotRegenerated() {
        val recentTask = spy(createRecentTask())
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(recentTask), emptyList())
        }
        verify(recentTask, times(1)).bitmapInfos

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(recentTask), emptyList())
        }
        verify(recentTask, times(1)).bitmapInfos // Icon is not generated a second time.
    }

    @Test
    fun testUpdateItems_taskReplaced_reusesHoverListener() {
        val callbacks =
            spy(
                getOnTaskbarUiThread {
                    TaskbarViewCallbacks(activityContext, activityContext.controllers, taskbarView)
                }
            )
        taskbarView.init(callbacks)

        val task1 = createRecentTask(id = 0)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(task1), emptyList())
        }
        val icon1 = taskbarView[taskbarView.size - 1]

        val task2 = createRecentTask(id = 1)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(emptyArray(), listOf(task2), emptyList())
        }
        val icon2 = taskbarView[taskbarView.size - 1]

        assertThat(icon1).isSameInstanceAs(icon2)
        verify(callbacks, times(1)).getIconOnHoverListener(icon1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testIsPointOnOverflowIcon() {
        assertThat(taskbarView.isOverflowViewShowing).isFalse()
        assertThat(taskbarView.isPointOnOverflowIcon(floatArrayOf(0f, 0f))).isFalse()

        val numHotseatIcons = activityContext.deviceProfile.inv.numShownHotseatIcons
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(
                createHotseatItems(numHotseatIcons + 1),
                emptyList(),
                emptyList(),
            )
        }

        forceLayoutUpdate()
        val overflowIcon = taskbarView.getTaskbarPinnedOverflowView()
        assertThat(taskbarView.isOverflowViewShowing).isTrue()
        val overflowRect = Rect()
        activityContext.dragLayer.getDescendantRectRelativeToSelf(overflowIcon, overflowRect)

        // Point inside the overflow icon
        val pointInside =
            floatArrayOf(overflowRect.centerX().toFloat(), overflowRect.centerY().toFloat())
        assertThat(taskbarView.isPointOnOverflowIcon(pointInside)).isTrue()

        // Point outside the overflow icon
        val pointOutside = floatArrayOf(overflowRect.right + 1F, overflowRect.bottom + 1F)
        assertThat(taskbarView.isPointOnOverflowIcon(pointOutside)).isFalse()

        // Point on the edge of the overflow icon
        val pointOnEdge = floatArrayOf(overflowRect.left.toFloat(), overflowRect.top.toFloat())
        assertThat(taskbarView.isPointOnOverflowIcon(pointOnEdge)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_DRAG_AND_DROP)
    fun testReserveDropSlot_addsGhostView() {
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(createHotseatItems(2), emptyList(), emptyList())
        }
        forceLayoutUpdate()

        val hitRect = Rect()
        runOnTaskbarUiThreadSync { taskbarView.getHitRectForPinRelativeToDragLayer(hitRect) }

        val dragX = hitRect.centerX()
        val container = taskbarView.taskbarHotseatIconsContainer ?: taskbarView
        val initialChildCount = container.childCount

        runOnTaskbarUiThreadSync { taskbarView.reserveDropSlotForDragLocation(dragX) }
        assertThat(container.childCount).isEqualTo(initialChildCount + 1)

        runOnTaskbarUiThreadSync { taskbarView.releaseDropSlot() }
        assertThat(container.childCount).isEqualTo(initialChildCount)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testUpdateItems_withPinnedOverflow_addsOverflowIconAndReturnsAllTaskIds() {
        val numShownHotseat = activityContext.taskbarSpecsEvaluator.numShownHotseatIcons
        val numItemsToAdd = numShownHotseat + 1
        val hotseatItems = createHotseatItems(numItemsToAdd)

        // Wrap the last item in a TaskItemInfo to simulate it's running.
        val taskItem = TaskItemInfo(123, hotseatItems.last())
        val finalHotseatItems: Array<ItemInfo> =
            Array(numItemsToAdd) { i -> if (i == numItemsToAdd - 1) taskItem else hotseatItems[i] }

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(finalHotseatItems, emptyList(), emptyList())
        }

        val shownTaskIds = viewController.shownTaskIds

        // If the overflow icon is showing, it should contain the task ID.
        val isOverflowShowing = getOnTaskbarUiThread { taskbarView.isOverflowViewShowing }
        if (isOverflowShowing) {
            assertThat(shownTaskIds).contains(123)
        } else {
            assertThat(shownTaskIds).doesNotContain(123)
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS)
    fun testGetShownTaskIds_withPinnedOverflowOpen_returnsAllTaskIds() {
        val numShownHotseat = activityContext.taskbarSpecsEvaluator.numShownHotseatIcons
        val numItemsToAdd = numShownHotseat + 1
        val hotseatItems = createHotseatItems(numItemsToAdd)

        // Wrap the last item in a TaskItemInfo to simulate it's running.
        val taskItem = TaskItemInfo(123, hotseatItems.last())
        val finalHotseatItems: Array<ItemInfo> =
            Array(numItemsToAdd) { i -> if (i == numItemsToAdd - 1) taskItem else hotseatItems[i] }

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(finalHotseatItems, emptyList(), emptyList())
            // Open the overflow container
            val overflowView = taskbarView.taskbarPinnedOverflowView
            if (overflowView != null) {
                viewController.overflownAppsContainerController.openOverflownAppsView(overflowView)
            }
        }
        val isOverflowShowing = getOnTaskbarUiThread { taskbarView.isOverflowViewShowing }
        if (isOverflowShowing) {
            // Wait for the overflown apps UI to be populated.
            // Without this wait the test assertions execute before overflow UI shows up
            Wait.atMost("Overflown apps not populated") {
                getOnTaskbarUiThread {
                    viewController.overflownAppsContainerController.overflownApps.isNotEmpty()
                }
            }
            assertThat(viewController.shownTaskIds).contains(123)
        } else {
            assertThat(viewController.shownTaskIds).doesNotContain(123)
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASKBAR_ICON_CONTAINER)
    fun testUpdateItems_hotseatItemUnchanged_regeneratesCellInfo() {
        val item = createHotseatWorkspaceItem(0)
        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(item), emptyList(), emptyList())
        }

        val initialBindInfo =
            (taskbarView.iconViews.last().layoutParams as TaskbarIconContainerLayoutParams).bindInfo
        assertThat(initialBindInfo).isNotNull()

        runOnTaskbarUiThreadSync {
            taskbarView.updateItems(arrayOf(item), emptyList(), emptyList())
        }

        val finalBindInfo =
            (taskbarView.iconViews.last().layoutParams as TaskbarIconContainerLayoutParams).bindInfo
        assertThat(finalBindInfo).isNotNull()
        assertThat(finalBindInfo).isNotSameInstanceAs(initialBindInfo)
    }

    /** Returns the number of expected recents outside of the overflow based on [hotseatSize]. */
    private fun getExpectedNumRecentsWithOverflow(hotseatSize: Int = 0): Int {
        return 0.coerceAtLeast(maxShownRecents - hotseatSize - 1)
    }

    /**
     * Forces the TaskbarView to measure and layout its children to ensure that view bounds are
     * correctly calculated.
     */
    private fun forceLayoutUpdate() {
        runOnTaskbarUiThreadSync {
            taskbarView.measure(
                View.MeasureSpec.makeMeasureSpec(taskbarView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(taskbarView.height, View.MeasureSpec.EXACTLY),
            )
            taskbarView.layout(
                taskbarView.left,
                taskbarView.top,
                taskbarView.right,
                taskbarView.bottom,
            )
        }
    }
}
