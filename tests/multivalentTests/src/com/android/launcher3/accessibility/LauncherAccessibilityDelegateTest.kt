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

package com.android.launcher3.accessibility

import android.view.View
import androidx.test.filters.SmallTest
import com.android.launcher3.DropTargetBar
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate.ADD_TO_WORKSPACE
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.SandboxApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.whenever

/** Tests for {@link LauncherAccessibilityDelegate}. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class LauncherAccessibilityDelegateTest {

    @get:Rule val app = SandboxApplication()
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var dropTargetBar: DropTargetBar
    @Mock private lateinit var host: View
    @Mock private lateinit var launcher: Launcher

    private lateinit var delegate: LauncherAccessibilityDelegate

    @Before
    fun setUp() {
        whenever(dropTargetBar.dropTargets).thenReturn(emptyArray())
        whenever(launcher.dropTargetBar).thenReturn(dropTargetBar)
        delegate = LauncherAccessibilityDelegate(launcher)
    }

    @Test
    fun testSupportAddToWorkspaceWithAppInfo() {
        testSupportAddToWorkspace(AppInfo())
    }

    @Test
    fun testSupportAddToWorkspaceWithPendingAddItemInfo() {
        testSupportAddToWorkspace(PendingAddItemInfo())
    }

    @Test
    fun testSupportAddToWorkspaceWithWorkspaceItemInfo() {
        testSupportAddToWorkspace(WorkspaceItemInfo())
    }

    private fun testSupportAddToWorkspace(itemInfo: ItemInfoWithIcon) {
        testSupportAddToWorkspace(
            itemInfo.apply {
                container = LauncherSettings.Favorites.CONTAINER_ALL_APPS
                runtimeStatusFlags = 0
            },
            expected = true,
        )
        testSupportAddToWorkspace(
            itemInfo.apply {
                container = LauncherSettings.Favorites.CONTAINER_ALL_APPS
                runtimeStatusFlags = FLAG_NOT_PINNABLE
            },
            expected = false,
        )
        testSupportAddToWorkspace(
            itemInfo.apply {
                container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                runtimeStatusFlags = 0
            },
            expected = false,
        )
        testSupportAddToWorkspace(
            itemInfo.apply {
                container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                runtimeStatusFlags = FLAG_NOT_PINNABLE
            },
            expected = false,
        )
    }

    private fun testSupportAddToWorkspace(itemInfo: ItemInfo, expected: Boolean) {
        val actions = mutableListOf<BaseAccessibilityDelegate<Launcher>.LauncherAction>()
        delegate.getSupportedActions(host, itemInfo, actions)
        assertEquals(
            expected,
            actions.any { action -> action.accessibilityAction.id == ADD_TO_WORKSPACE },
        )
    }
}
