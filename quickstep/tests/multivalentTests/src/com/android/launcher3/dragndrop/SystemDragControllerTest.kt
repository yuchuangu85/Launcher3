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

package com.android.launcher3.dragndrop

import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.filters.LargeTest
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.Flags.enableSystemDrag
import com.android.launcher3.Launcher
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.SandboxApplication
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/** Tests for {@link SystemDragController}. */
@LargeTest
@RunWith(ParameterizedAndroidJunit4::class)
class SystemDragControllerTest(flag: FlagsParameterization) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams() = FlagsParameterization.allCombinationsOf(FLAG_ENABLE_SYSTEM_DRAG)
    }

    @get:Rule val flags: SetFlagsRule = SetFlagsRule(flag)
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    private lateinit var controller: SystemDragController

    @Before
    fun setUp() {
        controller =
            launcherActivity.getFromLauncher { launcher ->
                launcher.activityComponent.systemDragController
            }!!
    }

    @Test
    fun testInjection() {
        assertTrue(
            if (enableSystemDrag()) controller is SystemDragControllerImpl
            else controller is SystemDragControllerStub
        )
    }
}
