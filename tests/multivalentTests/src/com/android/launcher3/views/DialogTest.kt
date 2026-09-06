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

package com.android.launcher3.views

import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.provider.Settings
import android.view.WindowManager.LayoutParams
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.AbstractFloatingView.TYPE_DIALOG_LISTENER
import com.android.launcher3.AbstractFloatingViewHelper.closeOpenViews
import com.android.launcher3.Launcher
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit

/** Tests for [Dialog]. */
@Ignore("b/493665827")
@LargeTest
@RunWith(AndroidJUnit4::class)
@SkipOnDeviceless
class DialogTest {
    @get:Rule(order = 0) val limitDevices = LimitDevicesRule()
    @get:Rule(order = 1) val mockito = MockitoJUnit.rule()
    @get:Rule(order = 2) val app = SandboxApplication().withModelDependency()
    @get:Rule(order = 3) val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule(order = 4) val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private lateinit var dialog: SimpleDialog

    @Before
    fun setUp() {
        dialog = createAndShowDialog()
        assertTrue(dialog.isShowing())
    }

    @After
    fun tearDown() {
        launcherActivity.executeOnLauncher { dialog.dismiss(animate = false) }
    }

    @Test
    fun testActivityRecreation() {
        // Activity recreation should dismiss the dialog.
        launcherActivity.activity.recreate()
        assertFalse(dialog.isShowing())
    }

    @Test
    fun testCloseOpenViews() {
        // Closing open views should dismiss the dialog.
        launcherActivity.executeOnLauncher { closeOpenViews(it, false, TYPE_DIALOG_LISTENER) }
        assertFalse(dialog.isShowing())
    }

    @Test
    fun testDismiss() {
        // It should be possible to explicitly dismiss the dialog.
        launcherActivity.executeOnLauncher { dialog.dismiss(animate = false) }
        assertFalse(dialog.isShowing())
    }

    @Test
    fun testViewCompositionDisposal() {
        // Dismissing the dialog should dispose of the view composition.
        dialog.content!!.run {
            launcherActivity.waitUntil("View composition not created") { hasComposition }
            launcherActivity.executeOnLauncher { dialog.dismiss(animate = false) }
            launcherActivity.waitUntil("View composition not disposed") { !hasComposition }
        }
    }

    @Test
    fun testWindowFocusChange() {
        // Blurring of the window should dismiss the dialog.
        launcherActivity.executeOnLauncher { dialog.dialog?.onWindowFocusChanged(false) }
        assertFalse(dialog.isShowing())
    }

    @Test
    fun testWindowType() {
        // The dialog should have the expected window type.
        launcherActivity.executeOnLauncher {
            assertThat(dialog.dialog?.window?.attributes?.type)
                .isEqualTo(
                    if (Settings.canDrawOverlays(it)) LayoutParams.TYPE_APPLICATION_OVERLAY
                    else LayoutParams.TYPE_APPLICATION
                )
        }
    }

    /** Creates and shows a dialog. */
    private fun createAndShowDialog() =
        launcherActivity.getFromLauncher { launcher ->
            SimpleDialog(
                    launcher,
                    SimpleDialogViewModel(
                        title = "Title",
                        content = {},
                        neutralButton = "Neutral",
                        positiveButton = "Positive",
                        onPositiveButtonClick = { true },
                    ),
                )
                .also(SimpleDialog::show)
        }!!
}
