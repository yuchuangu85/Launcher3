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
package com.android.quickstep.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.core.view.isVisible
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities.findViewByPredicate
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarView
import com.android.launcher3.testutil.LauncherTestInteractions
import com.android.launcher3.testutil.Wait.atMost
import com.android.launcher3.util.IntegrationLandscapeRule
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.ModelTestExtensions.setModelLayout
import com.android.launcher3.util.TestUtil
import com.android.launcher3.views.DoubleShadowBubbleTextView
import com.android.quickstep.taskbar.util.IntegrationNavigationModeSwitchRule
import com.android.quickstep.taskbar.util.IntegrationTaskbarModeSwitchRule
import java.io.IOException
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Base class for writing taskbar integration tests.
 *
 * This should instead be a rule, but is kept as a base class for easier migration from TAPL
 */
open class BaseTaskbarIntegrationTest {

    val targetContext: Context = getInstrumentation().targetContext

    @get:Rule val navigationModeSwitch = IntegrationNavigationModeSwitchRule()

    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @get:Rule val mIntegrationLandscapeRule = IntegrationLandscapeRule(launcherActivity)

    var interactions = LauncherTestInteractions(launcherActivity)

    val tisBinderRule = TISBinderRule()

    val taskbarModeSwitchRule = IntegrationTaskbarModeSwitchRule(tisBinderRule)

    // This makes sure that tisBinderRule was executed before taskbarModeSwitchRule since it
    // depends on it.
    @get:Rule val chainedRules = RuleChain.outerRule(tisBinderRule).around(taskbarModeSwitchRule)

    val uiDevice = UiDevice.getInstance(getInstrumentation())

    val deviceProfile: DeviceProfile by lazy {
        targetContext.appComponent.idp.getDeviceProfile(targetContext)
    }

    @Before
    fun setup() {
        assumeTrue(
            "Ignoring test because device is not a tablet",
            deviceProfile.deviceProperties.isLargeScreen,
        )
        targetContext.setModelLayout(
            LauncherLayoutBuilder()
                .atHotseat(0)
                .putApp(CALCULATOR_APP_PACKAGE, CALCULATOR_APP_CLASS)
        )
        // Starting the launcher activity ensures that we are in a fresh state
        targetContext.appComponent.testableModelState.model.loadModelSync()
        launcherActivity.initializeActivity()
        if (startCalendarAppDuringSetup()) interactions.startAppFast(CALCULATOR_APP_PACKAGE)
        executeOnTaskManager {
            it.getFromImplSync {}

            it.getCurrentActivityContext()?.let { ctx ->
                ctx.enableBlockingTimeoutDuringTests(true)
                ctx.unstashTaskbarIfStashed()
            }
        }
    }

    fun executeOnTaskManager(f: (TaskbarManager) -> Unit) =
        tisBinderRule.withTISBinder { f(taskbarManager) }

    fun <T> getFromTaskManager(f: (TaskbarManager) -> T?): T? =
        tisBinderRule.withTISBinder { f(taskbarManager) }

    @After
    open fun tearDown() {
        executeOnTaskManager {
            it.getCurrentActivityContext()?.enableBlockingTimeoutDuringTests(false)
        }
    }

    protected open fun startCalendarAppDuringSetup(): Boolean = true

    /** Clicks the first icon on the taskbar */
    protected fun clickTaskbarAppIcon(iconText: String) {
        // ((iconViews.get(1) as DoubleShadowBubbleTextView).tag as ItemInfo).intent.component
        verifyTaskbarState("Couldn't click taskbar icon") {
            controllers
                ?.taskbarViewController
                ?.run {
                    iconViews.filterIsInstance<DoubleShadowBubbleTextView>().first {
                        it.text == iconText
                    }
                }!!
                .performClick()
        }
        uiDevice.findWindow(By.Window.pkg(TEST_PACKAGE))
        uiDevice.waitForIdle()
    }

    protected fun waitForIcons(vararg iconNames: String) {
        waitForTaskbarManagerCondition(
            "Taskbar never contained all the icon names = $iconNames",
            {
                it.getCurrentActivityContext()!!
                    .controllers
                    .taskbarViewController
                    .iconViews
                    .filterIsInstance<DoubleShadowBubbleTextView>()
                    .map { it.text }
                    .containsAll(iconNames.asList())
            },
        )
    }

    protected fun waitForTaskbarManagerCondition(
        message: String,
        condition: (TaskbarManager) -> Boolean,
        timeout: Long = 30000L,
    ) = atMost(message, timeout) { getFromTaskManager(condition)!! }

    protected fun waitForTaskbarVisible() {
        waitForTaskbarManagerCondition(
            "Taskbar didn't become visible",
            {
                findViewByPredicate<TaskbarView>(it.getCurrentActivityContext()!!.rootView) {
                        (it is TaskbarView)
                    }
                    ?.isVisible == true
            },
        )
    }

    fun waitForObjectBySelector(selector: BySelector): UiObject2 {
        return uiDevice.wait(Until.findObject(selector), WAIT_TIME_MS.toLong()).also {
            assertNotNull("Can't find a view in Launcher, selector: $selector", it)
        }
    }

    fun assertAppInDesktop(expectedFocusedPackageName: String) {
        waitForObjectBySelector(By.res(SYSTEMUI_PACKAGE, "desktop_mode_caption"))
        // dumpViewHierarchy(uiDevice)
        waitForObjectBySelector(By.pkg(expectedFocusedPackageName).focused(true))
    }

    /** Waits until the taskbar state matches the provided [condition] */
    protected fun verifyTaskbarState(
        msg: String,
        condition: TaskbarActivityContext.() -> Boolean?,
    ) {
        uiDevice.waitForIdle()
        atMost(
            msg,
            { getFromTaskManager { it.getCurrentActivityContext()?.let(condition) } ?: false },
        )
    }

    /** Clears all recent tasks */
    fun clearAllRecentTasks() {
        try {
            uiDevice.executeShellCommand(
                "dumpsys activity service SystemUIService WMShell desktopmode removeAllDesks"
            )
            uiDevice.executeShellCommand(
                "dumpsys activity service SystemUIService WMShell recents clearAll"
            )
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun waitForFreeformWindow(packageName: String, activityText: String) {
        waitForObjectBySelector(By.pkg(packageName).text(activityText))
        waitForObjectBySelector(By.res(SYSTEMUI_PACKAGE, DESKTOP_WINDOW_SPECIFIC_VIEW_RES_ID))
    }

    private fun waitForFullscreenWindow(packageName: String, activityText: String) {
        waitForObjectBySelector(By.pkg(packageName).text(activityText))
        assertTrue(
            "Unexpected system object visible: $DESKTOP_WINDOW_SPECIFIC_VIEW_RES_ID",
            uiDevice.wait(
                Until.gone(By.res(SYSTEMUI_PACKAGE, DESKTOP_WINDOW_SPECIFIC_VIEW_RES_ID)),
                WAIT_TIME_MS.toLong(),
            ),
        )
    }

    fun moveFocusedActivityToFullscreen(packageName: String, activityText: String) {
        waitForFreeformWindow(packageName, activityText)
        getInstrumentation()
            .uiAutomation
            .injectInputEvent(
                TestUtil.createKeyEvent(
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON,
                    true,
                ),
                true,
                false,
            )
        getInstrumentation()
            .uiAutomation
            .injectInputEvent(
                TestUtil.createKeyEvent(
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON,
                    false,
                ),
                true,
                false,
            )
        waitForFullscreenWindow(packageName, activityText)
    }

    companion object {
        val CALCULATOR_APP_PACKAGE: String by lazy {
            getInstrumentation()
                .context
                .packageManager
                .resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR),
                    PackageManager.MATCH_SYSTEM_ONLY,
                )!!
                .activityInfo
                .packageName
        }

        val CALCULATOR_APP_CLASS: String by lazy {
            getInstrumentation()
                .context
                .packageManager
                .resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR),
                    PackageManager.MATCH_SYSTEM_ONLY,
                )!!
                .componentInfo
                .componentName
                .className
        }

        const val DESKTOP_WINDOW_SPECIFIC_VIEW_RES_ID: String = "close_window"
        const val SYSTEMUI_PACKAGE: String = "com.android.systemui"
        const val WAIT_TIME_MS: Int = 30000
    }
}
