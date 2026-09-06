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
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.Workspace
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.statemanager.StateManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class WorkspaceA11yFocusTest {

    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Test
    fun testFocusRequestedOnStateTransitionComplete() {
        launcherActivity.executeOnLauncher { launcher ->
            val mockView = mock<View>()
            val workspace = launcher.workspace

            workspace.setMoveItemA11yState(mockView, false)

            val listener = workspace.accessibilityDropListener

            // Call listener
            listener.onStateTransitionComplete(LauncherState.NORMAL)

            // Verify
            verify(mockView).performAccessibilityAction(
                eq(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),
                anyOrNull()
            )
        }
    }

    @Test
    fun testFocusNotRequestedOnStateTransitionComplete_whenDeferAccessibilityFocus() {
        launcherActivity.executeOnLauncher { launcher ->
            val mockView = mock<View>()
            val workspace = launcher.workspace

            workspace.setMoveItemA11yState(mockView, true)

            val listener = workspace.accessibilityDropListener

            listener.onStateTransitionComplete(LauncherState.NORMAL)

            verify(mockView, never()).performAccessibilityAction(
                eq(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),
                anyOrNull()
            )
        }
    }

    @Test
    fun testFocusRequestedOnLayoutTransitionEnd() {
        launcherActivity.executeOnLauncher { launcher ->
            val mockView = mock<View>()
            val workspace = launcher.workspace

            workspace.setMoveItemA11yState(mockView, true)

            workspace.onLayoutTransitionEnd()

            verify(mockView).performAccessibilityAction(
                eq(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),
                anyOrNull()
            )
        }
    }

    @Test
    fun testFocusNotRequestedOnLayoutTransitionEnd_whenNotDeferAccessibilityFocus() {
        launcherActivity.executeOnLauncher { launcher ->
            val mockView = mock<View>()
            val workspace = launcher.workspace

            workspace.setMoveItemA11yState(mockView, false)

            workspace.onLayoutTransitionEnd()

            verify(mockView, never()).performAccessibilityAction(
                eq(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),
                anyOrNull()
            )
        }
    }
}
