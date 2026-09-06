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
package com.android.launcher3.workspace

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.rule.LimitDevicesRule
import android.platform.test.rule.SkipOnDeviceless
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate
import com.android.launcher3.dagger.LauncherComponentProvider.get
import com.android.launcher3.icons.mono.ThemedIconDelegate
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.ArrowPopup
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.util.BaseLauncherActivityTest
import com.android.launcher3.util.Executors
import com.android.launcher3.util.ModelTestExtensions.setEmptyModelLayout
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.rule.TestStabilityRule.DesktopStability
import com.android.launcher3.util.rule.TestStabilityRule.LOCAL
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for theme icon support in Launcher
 *
 * Note running these tests will clear the workspace on the device.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ThemeIconsTest : BaseLauncherActivityTest<Launcher>() {
    /**
     * This test can't run in Robolectric because APP_NAME and TEST_APP_NAME can't found when
     * running the test in Robolectric.
     */
    @get:Rule(order = 0) var mlimitDevicesRule: LimitDevicesRule = LimitDevicesRule()
    @get:Rule(order = 1)
    val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    @Test
    @SkipOnDeviceless
    @Throws(Exception::class)
    fun testIconWithoutTheme() {
        setThemeEnabled(false)
        targetContext().setEmptyModelLayout()
        loadLauncherSync()

        scrollToAppIcon(APP_NAME)
        val btv =
            launcherActivity.getFromLauncher { l: Launcher ->
                verifyIconTheme(APP_NAME, l.appsView, false)
            }
        launcherTestInteractions.addToWorkspace(btv!!)
        launcherActivity.executeOnLauncher { l: Launcher ->
            verifyIconTheme(APP_NAME, l.workspace, false)
        }
    }

    @Test
    @SkipOnDeviceless
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_EXPANDABLE_LONG_PRESS_MENU)
    fun testShortcutIconWithoutTheme() {
        setThemeEnabled(false)
        targetContext().setEmptyModelLayout()
        loadLauncherSync()

        scrollToAppIcon(TEST_APP_NAME)
        val btv =
            launcherActivity.getFromLauncher { l: Launcher -> findBtv(TEST_APP_NAME, l.appsView) }
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { btv!!.performLongClick() }
        addAppShortcut()

        launcherActivity.executeOnLauncher { l: Launcher ->
            verifyIconTheme(SHORTCUT_NAME, l.workspace, false)
        }
    }

    @Test
    @SkipOnDeviceless
    @Throws(Exception::class)
    @DisableFlags(Flags.FLAG_EXPANDABLE_LONG_PRESS_MENU)
    fun testShortcutIconWithoutTheme_oldPopup() {
        setThemeEnabled(false)
        targetContext().setEmptyModelLayout()
        loadLauncherSync()

        scrollToAppIcon(TEST_APP_NAME)
        val btv =
            launcherActivity.getFromLauncher { l: Launcher -> findBtv(TEST_APP_NAME, l.appsView) }
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { btv!!.performLongClick() }

        val menuItem =
            launcherActivity.getOnceNotNull(
                "Popup menu not open",
                { l: Launcher? ->
                    val ap =
                        AbstractFloatingView.getOpenView<AbstractFloatingView>(
                            l,
                            AbstractFloatingView.TYPE_ACTION_POPUP,
                        )
                    if (ap is ArrowPopup<*>) findBtv(SHORTCUT_NAME, ap) else null
                },
            )
        launcherTestInteractions.addToWorkspace(menuItem!!)
        launcherActivity.executeOnLauncher { l: Launcher ->
            verifyIconTheme(SHORTCUT_NAME, l.workspace, false)
        }
    }

    @Test
    @SkipOnDeviceless
    @Throws(Exception::class)
    fun testIconWithTheme() {
        setThemeEnabled(true)
        targetContext().setEmptyModelLayout()
        loadLauncherSync()

        scrollToAppIcon(APP_NAME)
        val btv =
            launcherActivity.getFromLauncher { l: Launcher ->
                verifyIconTheme(APP_NAME, l.appsView, false)
            }

        launcherTestInteractions.addToWorkspace(btv!!)
        launcherActivity.executeOnLauncher { l: Launcher ->
            verifyIconTheme(APP_NAME, l.workspace, true)
        }
    }

    @Test
    @SkipOnDeviceless
    @Throws(Exception::class)
    @EnableFlags(Flags.FLAG_EXPANDABLE_LONG_PRESS_MENU)
    @DesktopStability(flavors = LOCAL, bug = 486280969)
    fun testShortcutIconWithTheme() {
        setThemeEnabled(true)
        targetContext().setEmptyModelLayout()
        loadLauncherSync()

        scrollToAppIcon(TEST_APP_NAME)
        val btv =
            launcherActivity.getFromLauncher { l: Launcher -> findBtv(TEST_APP_NAME, l.appsView) }
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { btv!!.performLongClick() }
        addAppShortcut()

        launcherActivity.executeOnLauncher { l: Launcher ->
            verifyIconTheme(SHORTCUT_NAME, l.workspace, true)
        }
    }

    @Test
    @SkipOnDeviceless
    @Throws(Exception::class)
    @DisableFlags(Flags.FLAG_EXPANDABLE_LONG_PRESS_MENU)
    fun testShortcutIconWithTheme_oldPopup() {
        setThemeEnabled(true)
        targetContext().setEmptyModelLayout()
        loadLauncherSync()

        scrollToAppIcon(TEST_APP_NAME)
        val btv =
            launcherActivity.getFromLauncher { l: Launcher -> findBtv(TEST_APP_NAME, l.appsView) }
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) { btv!!.performLongClick() }

        val menuItem =
            launcherActivity.getOnceNotNull(
                "Popup menu not open",
                { l: Launcher? ->
                    val ap =
                        AbstractFloatingView.getOpenView<AbstractFloatingView>(
                            l,
                            AbstractFloatingView.TYPE_ACTION_POPUP,
                        )
                    if (ap is ArrowPopup<*>) findBtv(SHORTCUT_NAME, ap) else null
                },
            )
        launcherTestInteractions.addToWorkspace(menuItem!!)
        launcherActivity.executeOnLauncher { l: Launcher ->
            verifyIconTheme(SHORTCUT_NAME, l.workspace, true)
        }
    }

    private fun addAppShortcut() {
        // Wait for deep shortcuts to load in ViewModel
        launcherActivity.waitUntil("Shortcut '$SHORTCUT_NAME' was not loaded in ViewModel") {
            l: Launcher ->
            val container = PopupContainer.getOpen(l)
            container?.viewModel?.state?.deepShortcuts?.any {
                it?.title?.toString() == SHORTCUT_NAME
            } == true
        }

        // Add shortcut to workspace programmatically
        launcherActivity.executeOnLauncher { l: Launcher ->
            val container = PopupContainer.getOpen(l)
            val shortcutInfo =
                container?.viewModel?.state?.deepShortcuts?.filterNotNull()?.first {
                    it.title.toString() == SHORTCUT_NAME
                }
            (l.accessibilityDelegate as LauncherAccessibilityDelegate).addToWorkspace(
                shortcutInfo!!,
                false,
                null,
            )
        }

        launcherActivity.waitUntil("Shortcut '$SHORTCUT_NAME' was not added to workspace") {
            l: Launcher ->
            findBtv(SHORTCUT_NAME, l.workspace) != null
        }
    }

    private fun findBtv(title: String, parent: ViewGroup): BubbleTextView? {
        // Wait for Launcher model to be completed
        try {
            Executors.MODEL_EXECUTOR.submit {}.get()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        return Utilities.findViewByPredicate(parent) { v: View ->
            v is BubbleTextView &&
                v.getContentDescription() != null &&
                title == v.getContentDescription().toString()
        }
    }

    private fun verifyIconTheme(
        title: String,
        parent: ViewGroup,
        isThemed: Boolean,
    ): BubbleTextView {
        val icon = findBtv(title, parent)
        Assert.assertNotNull(icon!!.icon)
        Assert.assertEquals(isThemed, icon.icon.delegate is ThemedIconDelegate)
        return icon
    }

    private fun setThemeEnabled(isEnabled: Boolean) {
        val uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(targetContext().packageName + ".grid_control")
                .appendPath("set_icon_themed")
                .build()
        val values = ContentValues()
        values.put("boolean_value", isEnabled)

        val result =
            get(targetContext()).gridCustomizationsProxy.update(uri, values, null, null, null)
        Assert.assertTrue(result > 0)
    }

    private fun scrollToAppIcon(appName: String) {
        launcherTestInteractions.scrollToAllAppIcon { info: ItemInfo ->
            appName == info.title.toString()
        }
    }

    companion object {
        private const val APP_NAME = "IconThemedActivity"
        private const val SHORTCUT_NAME = "Shortcut 1"

        const val TEST_APP_NAME: String = "LauncherTestApp"
    }
}
