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
package com.android.launcher3.util

import android.content.Context
import android.platform.test.rule.LimitDevicesRule
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.LauncherTestInteractions
import com.android.launcher3.testutil.Wait.atMost
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.rule.TestStabilityRule
import org.junit.Rule

@Deprecated("Use LauncherActivityScenarioRule instead")
/**
 * Base class for tests which use Launcher activity with some utility methods.
 *
 * This should instead be a rule, but is kept as a base class for easier migration from TAPL
 */
open class BaseLauncherActivityTest<LAUNCHER_TYPE : Launcher> {

    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val testStabilityRule = TestStabilityRule()

    @get:Rule val launcherActivity = LauncherActivityScenarioRule<LAUNCHER_TYPE>()

    var launcherTestInteractions = LauncherTestInteractions(launcherActivity)

    @JvmField val uiDevice = UiDevice.getInstance(getInstrumentation())

    protected fun loadLauncherSync() {
        LauncherAppState.getInstance(targetContext()).model.loadModelSync()
        launcherActivity.initializeActivity()
    }

    protected fun targetContext(): Context = getInstrumentation().targetContext

    protected fun waitForLauncherCondition(message: String, condition: (LAUNCHER_TYPE) -> Boolean) =
        atMost(message) { launcherActivity.getFromLauncher(condition)!! }

    protected fun waitForLauncherCondition(
        message: String,
        condition: (LAUNCHER_TYPE) -> Boolean,
        timeout: Long,
    ) = atMost(message, timeout) { launcherActivity.getFromLauncher(condition)!! }
}
