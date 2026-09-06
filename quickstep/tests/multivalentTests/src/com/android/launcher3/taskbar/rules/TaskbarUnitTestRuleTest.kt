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

import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.ForceRtl
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.NavBarKidsMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.UserSetupMode
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class TaskbarUnitTestRuleTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val setFlagsRule = SetFlagsRule()

    @Test
    fun testSetup_taskbarInitialized() {
        onSetup { assertThat(activityContext).isInstanceOf(TaskbarActivityContext::class.java) }
    }

    @Test
    fun testRecreateTaskbar_activityContextChanged() {
        onSetup {
            val context1 = activityContext
            recreateTaskbar()
            val context2 = activityContext
            assertThat(context1).isNotSameInstanceAs(context2)
        }
    }

    @Test
    fun testTeardown_taskbarDestroyed() {
        val testRule = TaskbarUnitTestRule(context)
        testRule.apply(EMPTY_STATEMENT, DESCRIPTION).evaluate()
        assertThrows(RuntimeException::class.java) { testRule.activityContext }
    }

    @Test
    fun testUserSetupMode_default_isComplete() {
        onSetup { assertThat(activityContext.isUserSetupComplete).isTrue() }
    }

    @Test
    fun testUserSetupMode_withAnnotation_isIncomplete() {
        @UserSetupMode class Mode
        onSetup(description = Description.createSuiteDescription(Mode::class.java)) {
            assertThat(activityContext.isUserSetupComplete).isFalse()
        }
    }

    @Test
    fun testNavBarKidsMode_default_navBarNotForcedVisible() {
        onSetup { assertThat(activityContext.isNavBarForceVisible).isFalse() }
    }

    @Test
    fun testNavBarKidsMode_withAnnotation_navBarForcedVisible() {
        @NavBarKidsMode class Mode
        onSetup(description = Description.createSuiteDescription(Mode::class.java)) {
            assertThat(activityContext.isNavBarForceVisible).isTrue()
        }
    }

    @Test
    fun testForceRtlAnnotation_setsActivityContextLayoutDirection() {
        @ForceRtl class Rtl
        onSetup(description = Description.createSuiteDescription(Rtl::class.java)) {
            assertThat(Utilities.isRtl(activityContext.resources)).isTrue()
        }
    }

    /**
     * Executes [runTest] after the [testRule] setup phase completes.
     *
     * A [description] can also be provided to mimic annotating a test or test class.
     */
    private fun onSetup(
        testRule: TaskbarUnitTestRule = TaskbarUnitTestRule(context),
        description: Description = DESCRIPTION,
        runTest: TaskbarUnitTestRule.() -> Unit,
    ) {
        testRule
            .apply(
                object : Statement() {
                    override fun evaluate() = runTest(testRule)
                },
                description,
            )
            .evaluate()
    }

    private companion object {
        private val EMPTY_STATEMENT =
            object : Statement() {
                override fun evaluate() = Unit
            }
        private val DESCRIPTION =
            Description.createSuiteDescription(TaskbarUnitTestRuleTest::class.java)
    }
}
