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

import android.view.Display.DEFAULT_DISPLAY
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.display.DisplayController
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnTaskbarUiThreadSync
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.RoboApiWrapper.convertToSpy
import com.android.launcher3.util.TaskbarModeUtil
import com.android.launcher3.util.launcheremulator.TestWindowManagerProxy
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/**
 * Allows tests to specify which Taskbar [Mode] to run under.
 *
 * [context] should match the test's target context, so that Dagger singleton instances are properly
 * sandboxed.
 *
 * Annotate tests with [TaskbarMode] to set a mode. If the annotation is omitted for any tests, this
 * rule is a no-op.
 *
 * Make sure this rule precedes any rules that depend on [DisplayController], or else the instance
 * might be inconsistent across the test lifecycle.
 */
class TaskbarModeRule(private val context: TaskbarWindowSandboxContext) : TestRule {
    /** The selected Taskbar mode. */
    enum class Mode {
        TRANSIENT,
        PINNED,
        THREE_BUTTONS,
        DESKTOP_TASKBAR,
    }

    /** Overrides Taskbar [mode] for a test. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    annotation class TaskbarMode(val mode: Mode)

    override fun apply(base: Statement, description: Description): Statement {
        val taskbarMode = description.getAnnotation(TaskbarMode::class.java) ?: return base

        return object : Statement() {
            override fun evaluate() {
                val mode = taskbarMode.mode

                runOnTaskbarUiThreadSync {
                    TaskbarModeUtil.INSTANCE[context]?.stub {
                        doReturn(
                                when (mode) {
                                    Mode.TRANSIENT -> false
                                    Mode.PINNED -> true
                                    Mode.THREE_BUTTONS -> false
                                    Mode.DESKTOP_TASKBAR -> true
                                }
                            )
                            .whenever(it)
                            .isPinned(any())

                        doReturn(
                                when (mode) {
                                    Mode.TRANSIENT -> true
                                    Mode.PINNED -> false
                                    Mode.THREE_BUTTONS -> false
                                    Mode.DESKTOP_TASKBAR -> false
                                }
                            )
                            .whenever(it)
                            .isTransient(any())
                    }

                    TaskbarFeatureEvaluator.INSTANCE[context][context.displayId]?.stub {
                        doReturn(
                                when (mode) {
                                    Mode.TRANSIENT -> false
                                    Mode.PINNED -> true
                                    Mode.THREE_BUTTONS -> false
                                    Mode.DESKTOP_TASKBAR -> true
                                }
                            )
                            .whenever(it)
                            .isPinned

                        doReturn(
                                when (mode) {
                                    Mode.TRANSIENT -> true
                                    Mode.PINNED -> false
                                    Mode.THREE_BUTTONS -> false
                                    Mode.DESKTOP_TASKBAR -> false
                                }
                            )
                            .whenever(it)
                            .isTransient

                        doReturn(
                                when (mode) {
                                    Mode.TRANSIENT -> false
                                    Mode.PINNED -> true
                                    Mode.THREE_BUTTONS -> true
                                    Mode.DESKTOP_TASKBAR -> true
                                }
                            )
                            .whenever(it)
                            .isPersistent
                    }
                }

                runOnTaskbarUiThreadSync {
                    val navMode =
                        when (mode) {
                            Mode.TRANSIENT,
                            Mode.PINNED,
                            Mode.DESKTOP_TASKBAR -> NavigationMode.NO_BUTTON

                            Mode.THREE_BUTTONS -> NavigationMode.THREE_BUTTONS
                        }
                    val wmProxy = context.appComponent.wmProxy
                    if (wmProxy is TestWindowManagerProxy) {
                        wmProxy.setNavigationMode(navMode)
                        wmProxy.setShowDesktopTaskbarForFreeformDisplay(
                            mode == Mode.DESKTOP_TASKBAR
                        )
                    } else {
                        if (!mockingDetails(wmProxy).run { isMock || isSpy }) {
                            wmProxy.convertToSpy()
                        }
                        doReturn(navMode).whenever(wmProxy).getNavigationMode(any())
                        doReturn(mode == Mode.DESKTOP_TASKBAR)
                            .whenever(wmProxy)
                            .showDesktopTaskbarForFreeformDisplay(any())
                    }
                    context.appComponent.displayController.notifyConfigChange(DEFAULT_DISPLAY)
                }

                base.evaluate()
            }
        }
    }
}
