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

import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.View.Visibility
import androidx.test.filters.LargeTest
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.dragndrop.SystemDragItemInfo
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.widget.PendingAddWidgetInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for {@link Hotseat}. */
@LargeTest
@RunWith(LauncherMultivalentJUnit::class)
class HotseatTest {

    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Test
    fun testIsValidDropTarget() {
        testIsValidDropTarget(
            dragInfo = mock<ItemInfo>(),
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(VISIBLE),
            expected = true,
        )
    }

    @Test
    fun testIsValidDropTargetWithFileSystemItemDrag() {
        testIsValidDropTarget(
            dragInfo = ItemInfo().apply { itemType = ITEM_TYPE_FILE_SYSTEM_FILE },
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(VISIBLE),
            expected = false,
        )
        testIsValidDropTarget(
            dragInfo = ItemInfo().apply { itemType = ITEM_TYPE_FILE_SYSTEM_FOLDER },
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(VISIBLE),
            expected = false,
        )
    }

    @Test
    fun testIsValidDropTargetWithNonVisibleShortcutAndWidgetContainer() {
        testIsValidDropTarget(
            dragInfo = mock<ItemInfo>(),
            shortcutAndWidgetContainer = null,
            expected = false,
        )
        testIsValidDropTarget(
            dragInfo = mock<ItemInfo>(),
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(GONE),
            expected = false,
        )
        testIsValidDropTarget(
            dragInfo = mock<ItemInfo>(),
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(INVISIBLE),
            expected = false,
        )
    }

    @Test
    fun testIsValidDropTargetWithSystemDrag() {
        testIsValidDropTarget(
            dragInfo = SystemDragItemInfo(),
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(VISIBLE),
            expected = false,
        )
    }

    @Test
    fun testIsValidDropTargetWithWidgetDrag() {
        testIsValidDropTarget(
            dragInfo = mock<LauncherAppWidgetInfo>(),
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(VISIBLE),
            expected = false,
        )
        testIsValidDropTarget(
            dragInfo = mock<PendingAddWidgetInfo>(),
            shortcutAndWidgetContainer = createShortcutAndWidgetContainer(VISIBLE),
            expected = false,
        )
    }

    private fun testIsValidDropTarget(
        dragInfo: ItemInfo,
        shortcutAndWidgetContainer: ShortcutAndWidgetContainer?,
        expected: Boolean,
    ) {
        executeOnHotseat { hotseat ->
            hotseat.convertToSpy()
            val dragObject = mock<DropTarget.DragObject>().apply { this@apply.dragInfo = dragInfo }
            whenever(hotseat.shortcutsAndWidgets).thenReturn(shortcutAndWidgetContainer)
            assertEquals(expected, hotseat.isValidDropTarget(dragObject))
        }
    }

    private fun createShortcutAndWidgetContainer(@Visibility viz: Int) =
        mock<ShortcutAndWidgetContainer>().apply { whenever(visibility).thenReturn(viz) }

    private fun executeOnHotseat(callable: (hotSeat: Hotseat) -> Unit) =
        launcherActivity.executeOnLauncher { launcher -> callable.invoke(launcher.hotseat) }
}
