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

package com.android.launcher3.util

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.display.DisplayController
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Used if the test needs to run in Landscape orientation. */
class IntegrationLandscapeRule(val launcherActivity: LauncherActivityScenarioRule<Launcher>) :
    TestRule {

    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val targetContainer = InstrumentationRegistry.getInstrumentation().targetContext

    // Annotation for signaling which test run in landscape.
    @Retention(AnnotationRetention.RUNTIME)
    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
    )
    annotation class Landscape

    override fun apply(base: Statement, description: Description): Statement {
        description.getAnnotation(Landscape::class.java) ?: return base
        return object : Statement() {
            override fun evaluate() {
                try {
                    setEnableRotation(true)
                    goToLandscape()
                    base.evaluate()
                } catch (e: Throwable) {
                    Log.e(TAG, "Error", e)
                    throw e
                } finally {
                    uiDevice.setOrientationNatural()
                    setFixedLandscape(false)
                    setEnableRotation(false)
                }
            }
        }
    }

    private fun setFixedLandscape(isFixedLandscape: Boolean) {
        LauncherPrefs.INSTANCE[targetContainer].put(
            LauncherPrefs.FIXED_LANDSCAPE_MODE,
            isFixedLandscape &&
                DisplayController.INSTANCE[targetContainer].info.deviceType ==
                    InvariantDeviceProfile.TYPE_PHONE,
        )
    }

    private fun setEnableRotation(enable: Boolean) {
        launcherActivity.executeOnLauncher {
            it.getRotationHelper().forceAllowRotationForTesting(enable)
        }
    }

    /** Makes the phone go into Portrait mode. */
    fun goToPortrait() {
        setFixedLandscape(false)
        uiDevice.setOrientationNatural()
    }

    /** Makes the phone go into Landscape mode or FixedLandscape for phones. */
    fun goToLandscape() {
        setFixedLandscape(true)
        uiDevice.setOrientationLeft()
    }

    companion object {
        const val TAG: String = "IntegrationLandscapeRunner"
    }
}
