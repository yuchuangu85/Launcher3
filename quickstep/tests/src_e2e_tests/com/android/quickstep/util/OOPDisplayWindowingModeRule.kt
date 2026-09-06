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

package com.android.quickstep.util

import android.app.WindowConfiguration
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.Locale
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Sets the windowing mode like freeform or fullscreen. For reference look at
 * android.app.WindowConfiguration
 *
 * This rule is intended for Out of process test only, for inproc test one option is to use
 * `WindowManagerGlobal.getWindowManagerService().setWindowingMode`
 */
class OOPDisplayWindowingModeRule(val mode: MODES) : TestRule {

    private fun getModeFromResult(cmdOutput: String): MODES {
        // Example string "display windowing mode=freeform for displayId=0"
        // Using regex because this feature is very new and can change the output of the command
        // in the future.
        // This regex looks for:
        // - "mode"
        // - optional spaces (\s*)
        // - an equals sign (=)
        // - optional spaces (\s*)
        // - and then captures one or more non-space characters ((\S+))
        return enumValueOf<MODES>(
            """mode\s*=\s*(\S+)"""
                .toRegex()
                .find(cmdOutput)
                ?.groupValues
                ?.get(1)!!
                .replace("-", "_")
                .uppercase(Locale.getDefault())
        )
    }

    override fun apply(base: Statement?, description: Description?): Statement {
        val previousMode =
            getModeFromResult(
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand("wm get-display-windowing-mode")
            )
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand("wm set-display-windowing-mode ${mode.value}")
                try {
                    base!!.evaluate()
                } finally {
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .executeShellCommand("wm set-display-windowing-mode ${previousMode.value}")
                }
            }
        }
    }

    enum class MODES(val value: Int) {
        UNDEFINED(WindowConfiguration.WINDOWING_MODE_UNDEFINED),
        FULLSCREEN(WindowConfiguration.WINDOWING_MODE_FULLSCREEN),
        PINNED(WindowConfiguration.WINDOWING_MODE_PINNED),
        FREEFORM(WindowConfiguration.WINDOWING_MODE_FREEFORM),
        MULTI_WINDOW(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW),
    }
}
