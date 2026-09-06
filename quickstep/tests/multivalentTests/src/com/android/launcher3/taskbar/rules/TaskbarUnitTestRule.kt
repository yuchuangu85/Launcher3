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

package com.android.launcher3.taskbar.rules

import android.provider.Settings.Secure.NAV_BAR_KIDS_MODE
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import android.provider.Settings.Secure.getUriFor
import android.view.View
import android.view.ViewGroup.OnHierarchyChangeListener
import androidx.core.view.isNotEmpty
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.TaskbarControllers
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.taskbar.TaskbarManagerImpl
import com.android.launcher3.taskbar.TaskbarUIController
import com.android.launcher3.util.LauncherMultivalentJUnit.Companion.isRunningInRobolectric
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.ThreadSafeRunnableList
import com.android.quickstep.dagger.SysUIConnectionComponent
import com.google.common.truth.Truth.assertWithMessage
import com.google.common.truth.TruthJUnit.assume
import java.util.Locale
import kotlin.reflect.KProperty
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Manages the Taskbar lifecycle for unit tests.
 *
 * Tests should provide their target [context] through the constructor.
 *
 * The rule interacts with [TaskbarManager] on the main thread. A good rule of thumb for tests is
 * that code that is executed on the main thread in production should also happen on that thread
 * when tested.
 *
 * `@UiThreadTest` is incompatible with this rule. The annotation causes this rule to run on the
 * main thread, but it needs to be run on the test thread for it to work properly. Instead, only run
 * code that requires the main thread using something like [runOnTaskbarUiThreadSync] or
 * [TestUtil.getOnTaskbarUiThread].
 *
 * ```
 * @Test
 * fun example() {
 *     instrumentation.runOnMainSync { doWorkThatPostsMessage() }
 *     // Second lambda will not execute until message is processed.
 *     instrumentation.runOnMainSync { verifyMessageResults() }
 * }
 * ```
 */
class TaskbarUnitTestRule(
    private val context: TaskbarWindowSandboxContext,
    private val activityInitializedCallback: () -> Unit = {},
) : TestRule {

    private val cleanup = ThreadSafeRunnableList()
    private val sysUIConnection: SysUIConnectionComponent by lazy {
        context.base.appComponent.sysUIConnectionComponentBuilder
            .setConnectionCleaner(cleanup)
            .build()
    }

    val taskbarManager: TaskbarManagerImpl by lazy {
        sysUIConnection.taskbarManager.getFromImplSync { it }
    }

    val activityContext: TaskbarActivityContext
        get() {
            return taskbarManager.currentActivityContext
                ?: throw RuntimeException("Failed to obtain TaskbarActivityContext.")
        }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {

                // Only run test when Taskbar is enabled.
                runOnTaskbarUiThreadSync {
                    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
                    val isTaskbarPresent =
                        targetContext.appComponent.idp
                            .getDeviceProfile(targetContext)
                            .deviceProperties
                            .taskbarConfiguration
                            .isTaskbarPresent
                    if (isRunningInRobolectric) {
                        // Fail if emulated device does not have a Taskbar.
                        assertWithMessage("isTaskbarPresent is false due to device emulation issue")
                            .that(isTaskbarPresent)
                            .isTrue()
                    } else {
                        // Skip tests for devices that do not have a Taskbar (e.g. phones).
                        assume()
                            .withMessage("Ignoring test because isTaskbarPresent is false")
                            .that(isTaskbarPresent)
                            .isTrue()
                    }
                }

                // Process secure setting annotations.
                context.settingsCacheSandbox[getUriFor(USER_SETUP_COMPLETE)] =
                    if (description.getAnnotation(UserSetupMode::class.java) != null) 0 else 1
                context.settingsCacheSandbox[getUriFor(NAV_BAR_KIDS_MODE)] =
                    if (description.getAnnotation(NavBarKidsMode::class.java) != null) 1 else 0

                TestUtil.getOnTaskbarUiThread {
                    taskbarManager.apply {
                        val root = primaryResource.rootLayout
                        root.setOnHierarchyChangeListener(
                            object : OnHierarchyChangeListener {
                                override fun onChildViewAdded(p0: View, p1: View) {
                                    initCurrentActivity()
                                }

                                override fun onChildViewRemoved(p0: View, p1: View) {}
                            }
                        )
                        if (root.isNotEmpty()) initCurrentActivity()
                    }
                }

                if (description.getAnnotation(ForceRtl::class.java) != null) {
                    // Needs to be set on window context instead of sandbox context, because it does
                    // does not propagate between them. However, this change will impact created
                    // TaskbarActivityContext instances, since they wrap the window context.
                    // TODO: iterate through all window contexts and do this.
                    taskbarManager.primaryWindowContext.resources.configuration.setLayoutDirection(
                        RTL_LOCALE
                    )
                    runOnTaskbarUiThreadSync { taskbarManager.recreateTaskbars() }
                }

                try {
                    base.evaluate()
                } finally {
                    cleanup.complete()
                    runOnTaskbarUiThreadSync {}
                }
            }
        }
    }

    /** Simulates Taskbar recreation lifecycle. */
    fun recreateTaskbar() {
        runOnTaskbarUiThreadSync { taskbarManager.recreateTaskbars() }
    }

    // Don't use TaskbarManager property, because the function can be called before initialization.
    private fun TaskbarManagerImpl.initCurrentActivity() {
        val activityContext = currentActivityContext ?: return

        // TODO(b/346394875): we should test a non-default uiController.
        activityContext.setUIController(TaskbarUIController.DEFAULT)
        activityInitializedCallback.invoke()
    }

    fun <T> delegate(provider: (TaskbarControllers) -> T) = ControllerDelegate(provider)

    inner class ControllerDelegate<T>(private val provider: (TaskbarControllers) -> T) {

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
            provider(activityContext.controllers)
    }

    /** Overrides [USER_SETUP_COMPLETE] to be `false` for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class UserSetupMode

    /** Overrides [NAV_BAR_KIDS_MODE] to be `true` for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class NavBarKidsMode

    /** Forces RTL UI for tests. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class ForceRtl
}

private val RTL_LOCALE = Locale.of("ar", "XB")
