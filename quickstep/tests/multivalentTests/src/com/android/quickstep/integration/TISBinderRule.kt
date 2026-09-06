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
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.testutil.Wait
import com.android.launcher3.util.Executors.getTaskbarUiThread
import com.android.quickstep.dagger.SysUIConnectionComponent
import com.android.quickstep.sysuiconnection.TISBinder
import java.util.concurrent.CompletableFuture
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Test rule for providing interactions with [TISBinder] */
class TISBinderRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {

        return object : Statement() {

            override fun evaluate() {
                val context = getInstrumentation().targetContext
                val info = context.appComponent.displayController.info
                val wasTransient = context.appComponent.taskbarModeUtil.isTransient(info)
                context.setTransientTaskbar(
                    description.getAnnotation(TaskbarMode::class.java)?.mode?.isTransient
                        ?: wasTransient
                )

                val errors = mutableListOf<Throwable>()
                try {
                    base.evaluate()
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    try {
                        // Revert original value
                        context.appComponent.launcherPrefs.put(TASKBAR_PINNING, !wasTransient)
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }

                if (errors.isNotEmpty()) {
                    throw errors.first()
                }
            }
        }
    }

    private fun Context.setTransientTaskbar(isTransient: Boolean) {
        appComponent.launcherPrefs.put(TASKBAR_PINNING, !isTransient)
        val info = appComponent.displayController.info
        Wait.atMost(
            "Taskbar mode did not change",
            { appComponent.taskbarModeUtil.isTransient(info) == isTransient },
        )

        withTISBinder {
            taskbarManager.recreateTaskbars()
            waitForTaskbarUiThreadSync()
        }

        // Wait for taskbar to be initialized
        Wait.atMost("Taskbar not initialized ") {
            withTISBinder { taskbarManager.getCurrentActivityContext() } != null
        }

        /** Reset any active input which may be caching the last activity context */
        withTISBinder {
            taskbarManager.getCurrentActivityContext()?.displayId?.let {
                touchInteractionHandler.reset(it)
            }
        }
    }

    private fun waitForTaskbarUiThreadSync() {
        getTaskbarUiThread().submit {}.get()
    }

    /**
     * Runs the given command on the UI thread, after ensuring we are connected to
     * TouchInteractionService.
     */
    fun <T : Any?> withTISBinder(block: SysUIConnectionComponent.() -> T): T {
        val result = CompletableFuture<T>()
        getInstrumentation()
            .targetContext
            .appComponent
            .sysUIConnectionTracker
            .activeComponent
            .forEach(getTaskbarUiThread()) { if (it != null) result.complete(block.invoke(it)) }
            .use {
                return result.get()
            }
    }

    enum class Mode(internal val isTransient: Boolean) {
        TRANSIENT(true),
        PERSISTENT(false),
    }

    // Annotation for tests that need to be run with quickstep enabled and disabled.
    @Retention(RUNTIME) @Target(FUNCTION, CLASS) annotation class TaskbarMode(val mode: Mode)
}
