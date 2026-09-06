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

import android.os.Process
import android.util.Log
import android.view.WindowManagerPolicyConstants
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.testutil.Wait.atMost
import com.android.systemui.shared.system.QuickStepContract
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class IntegrationNavigationModeSwitchRule : TestRule {

    enum class Mode(val overlayPackage: String) {
        THREE_BUTTON(WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY),
        ZERO_BUTTON(WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY),
    }

    // Annotation for tests that need to be run with quickstep enabled and disabled.
    @Retention(AnnotationRetention.RUNTIME)
    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
    )
    annotation class NavigationModeSwitch(val mode: Mode = Mode.ZERO_BUTTON)

    override fun apply(base: Statement, description: Description): Statement {
        val annotation = description.getAnnotation(NavigationModeSwitch::class.java) ?: return base
        val mode = annotation.mode
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                val originalMode = getNavigationMode()
                try {
                    setActiveOverlay(mode)
                    base.evaluate()
                } catch (e: Throwable) {
                    Log.e(TAG, "Error", e)
                    throw e
                } finally {
                    Log.d(TAG, "In Finally block")
                    setActiveOverlay(originalMode)
                }
            }
        }
    }

    fun getNavigationMode(): Mode {
        val res = getInstrumentation().targetContext.resources
        return if (
            QuickStepContract.isGesturalMode(
                res.getInteger(
                    res.getIdentifier("config_navBarInteractionMode", "integer", "android")
                )
            )
        )
            Mode.ZERO_BUTTON
        else Mode.THREE_BUTTON
    }

    @Throws(Exception::class)
    fun setActiveOverlay(expectedMode: Mode) {
        Log.d(TAG, "setActiveOverlay: ${expectedMode.overlayPackage}...")
        UiDevice.getInstance(getInstrumentation())
            .executeShellCommand(
                String.format(
                    "cmd overlay enable-exclusive --user %d --category %s",
                    Process.myUserHandle().identifier,
                    expectedMode.overlayPackage,
                )
            )

        atMost("Couldn't switch to ${expectedMode.overlayPackage}") {
            getNavigationMode() == expectedMode
        }
    }

    companion object {
        const val TAG: String = "IntegrationNavigationModeSwitchRule"
    }
}
