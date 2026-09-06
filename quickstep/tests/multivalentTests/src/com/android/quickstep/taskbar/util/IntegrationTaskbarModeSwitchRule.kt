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

package com.android.quickstep.taskbar.util

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.get
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.testutil.Wait.atMost
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.launcher3.util.TaskbarModeUtil
import com.android.quickstep.integration.TISBinderRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * This rule switches the taskbar mode between persistent and transient. It can be used directly by
 * calling setTaskbarMode or the test method can specify the mode by adding the TaskbarModeSwitch
 * annotation.
 *
 * This is similar to {TaskbarModeSwitchRule} used for E2E tests.
 */
class IntegrationTaskbarModeSwitchRule(val tisBinderRule: TISBinderRule) : TestRule {

    enum class Mode {
        TRANSIENT,
        PERSISTENT,
    }

    fun currentMode(): Mode {
        val info =
            getInstrumentation().targetContext.applicationContext.appComponent.displayController.info
        if (TaskbarModeUtil.INSTANCE[getInstrumentation().targetContext].isTransient(info)) {
            return Mode.TRANSIENT
        }
        return Mode.PERSISTENT
    }

    // Annotation for tests that need to be run with quickstep enabled and disabled.
    @Retention(AnnotationRetention.RUNTIME)
    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
    )
    annotation class TaskbarModeSwitch(val mode: Mode = Mode.TRANSIENT)

    override fun apply(base: Statement, description: Description): Statement {
        val annotation = description.getAnnotation(TaskbarModeSwitch::class.java) ?: return base
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                val currentMode = currentMode()
                try {
                    setTaskbarMode(annotation.mode)
                    base.evaluate()
                } catch (e: Throwable) {
                    Log.e(TAG, "Error", e)
                    throw e
                } finally {
                    try {
                        setTaskbarMode(currentMode)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error", e)
                        throw e
                    }
                }
            }
        }
    }

    /** Forces the taskbar to behave in a different mode. */
    fun setTaskbarMode(mode: Mode) {
        if (mode == currentMode()) {
            return
        }
        // Setting TASKBAR_PINNING to true will activate PERSISTENT Taskbar and setting it to false
        // will trigger the TRANSIENT mode.
        get(getInstrumentation().targetContext)
            .put(LauncherPrefs.TASKBAR_PINNING, mode == Mode.PERSISTENT)

        tisBinderRule.withTISBinder {
            taskbarManager!!.recreateTaskbars()
            waitForTaskbarUiThreadSync()
        }
        atMost("Taskbar didn't switch to ${mode.name}") { currentMode() == mode }
    }

    private fun waitForTaskbarUiThreadSync() {
        getTaskbarUiThread().submit {}.get()
    }

    companion object {
        const val TAG: String = "TaskbarModeSwitchRule"
    }
}
