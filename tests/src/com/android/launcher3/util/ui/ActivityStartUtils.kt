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

package com.android.launcher3.util.ui

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.launcher3.tapl.TestHelpers
import com.android.launcher3.util.TestUtil.DEFAULT_UI_TIMEOUT
import com.google.common.truth.Truth.assertWithMessage

/** Utility class for starting activities and intents in TAPL tests. */
object ActivityStartUtils {

    private const val DEFAULT_ACTIVITY_TIMEOUT = 10000L

    @JvmStatic
    @JvmOverloads
    fun startAppFast(packageName: String, displayId: Int = DEFAULT_DISPLAY) {
        startIntent(
            displayId = displayId,
            intent =
                getInstrumentation()
                    .context
                    .packageManager
                    .getLaunchIntentForPackage(packageName)!!,
            selector = By.pkg(packageName).displayId(displayId).depth(0),
            newTask = true,
        )
    }

    @JvmStatic
    @JvmOverloads
    fun startTestActivity(activityNumber: Int, displayId: Int = DEFAULT_DISPLAY) {
        startTestActivity("Activity$activityNumber", "TestActivity$activityNumber", displayId)
    }

    @JvmStatic
    @JvmOverloads
    fun startTestActivity(
        activityName: String,
        activityLabel: String,
        displayId: Int = DEFAULT_DISPLAY,
    ) {
        startTestComponent(displayId, "com.android.launcher3.tests.$activityName", activityLabel)
    }

    @JvmStatic
    @JvmOverloads
    fun startImeTestActivity(displayId: Int = DEFAULT_DISPLAY) {
        startTestComponent(
            displayId,
            "com.android.launcher3.testcomponent.ImeTestActivity",
            "ImeTestActivity",
        )
    }

    @JvmStatic
    @JvmOverloads
    fun startExcludeFromRecentsTestActivity(displayId: Int = DEFAULT_DISPLAY) {
        startTestComponent(
            displayId,
            "com.android.launcher3.testcomponent.ExcludeFromRecentsTestActivity",
            "ExcludeFromRecentsTestActivity",
        )
    }

    private fun startTestComponent(displayId: Int, componentClassName: String, label: String) {
        val packageName = getAppPackageName()
        val intent =
            getInstrumentation()
                .context
                .packageManager
                .getLaunchIntentForPackage(packageName)!!
                .apply { component = ComponentName(packageName, componentClassName) }
        startIntent(
            displayId = displayId,
            intent = intent,
            selector = By.pkg(packageName).text(label).displayId(displayId),
            newTask = false,
        )
    }

    @JvmStatic
    fun startIntent(displayId: Int, intent: Intent, selector: BySelector, newTask: Boolean) {
        intent.apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                if (newTask) Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                else Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            )
        }
        val options: Bundle? =
            if (displayId == DEFAULT_DISPLAY) {
                null
            } else {
                ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
            }
        getInstrumentation().targetContext.startActivity(intent, options)
        assertWithMessage("App didn't start: $selector")
            .that(TestHelpers.wait(Until.hasObject(selector), DEFAULT_UI_TIMEOUT))
            .isTrue()

        // Wait for the Launcher to stop.
        if (displayId == DEFAULT_DISPLAY) {
            val launcherInstrumentation = LauncherInstrumentation(displayId, true)
            if (!launcherInstrumentation.shouldShowHomeBehindDesktop()) {
                launcherInstrumentation.waitForCondition(
                    "Launcher activity didn't stop",
                    DEFAULT_ACTIVITY_TIMEOUT,
                ) {
                    !launcherInstrumentation.isLauncherActivityStarted
                }
            } else {
                // On desktop, the launcher activity might still be considered "started"
                // even if another app is on top. We skip this check for desktop devices.
                val idp = InvariantDeviceProfile.INSTANCE.get(getInstrumentation().targetContext)
                if (idp.deviceType != InvariantDeviceProfile.TYPE_DESKTOP) {
                    assertWithMessage("Launcher activity not started when it should be")
                        .that(launcherInstrumentation.isLauncherActivityStarted)
                        .isTrue()
                }
            }
        }
    }

    @JvmStatic
    fun resolveSystemAppInfo(category: String) =
        getInstrumentation()
            .context
            .packageManager
            .resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(category),
                PackageManager.MATCH_SYSTEM_ONLY,
            )!!
            .activityInfo

    @JvmStatic fun resolveSystemApp(category: String) = resolveSystemAppInfo(category).packageName

    @JvmStatic fun getAppPackageName() = getInstrumentation().context.packageName
}
